/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.manifest

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * TRD §6.3 / §7.1 "Manifest/config tests" over the source manifest and XML config of :app:
 * exported-component allow-list, IME/spell-checker bind permissions, FileProvider paths, backup rules
 * and the network security config.
 *
 * Adding an exported component must be a reviewed change: extend [EXPORTED_ALLOW_LIST] with a reason.
 */
class ManifestHardeningTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"
    private val appNamespace = "com.manjul.aikeyboard"

    private companion object {
        const val IME_SERVICE = "com.manjul.aikeyboard.FlorisImeService"
        const val SPELL_CHECKER_SERVICE = "com.manjul.aikeyboard.FlorisSpellCheckerService"

        /** Exported component -> why it must be exported. */
        val EXPORTED_ALLOW_LIST: Map<String, String> = mapOf(
            IME_SERVICE to "The IME itself; bound by the system only (BIND_INPUT_METHOD)",
            SPELL_CHECKER_SERVICE to "Upstream spell checker; bound by the system only (BIND_TEXT_SERVICE)",
            "com.manjul.aikeyboard.app.FlorisAppActivity" to
                "Settings activity referenced by method.xml/spellchecker.xml; ui:// deep links; extension import",
            "com.manjul.aikeyboard.SettingsLauncherAlias" to "Launcher entry (MAIN/LAUNCHER)",
            "com.manjul.aikeyboard.ime.clipboard.FlorisCopyToClipboardActivity" to
                "Upstream share target (SEND image/*) that copies an image into the clipboard",
        )

        val COMPONENT_TAGS = listOf("activity", "activity-alias", "service", "receiver", "provider")
    }

    private val mainDir: File = listOf(File("src/main"), File("app/src/main"))
        .firstOrNull { File(it, "AndroidManifest.xml").isFile }
        ?: fail("Cannot locate app/src/main from working dir ${File(".").absolutePath}")

    private fun parse(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(file)
    }

    private val manifest: Document by lazy { parse(File(mainDir, "AndroidManifest.xml")) }

    private fun xmlRes(name: String): Document = parse(File(mainDir, "res/xml/$name.xml"))

    private fun qualify(name: String): String = if (name.startsWith(".")) appNamespace + name else name

    private fun Element.attr(name: String): String = getAttributeNS(androidNs, name)

    private fun Document.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.children(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun components(): List<Pair<String, Element>> = COMPONENT_TAGS.flatMap { tag ->
        manifest.elements(tag).map { tag to it }
    }

    /** Explicit android:exported wins; without it a component with an intent filter is exported. */
    private fun Element.isExported(): Boolean = when (attr("exported")) {
        "true" -> true
        "false" -> false
        else -> children("intent-filter").isNotEmpty()
    }

    private fun applicationResource(attribute: String): String {
        val application = manifest.elements("application").single()
        val value = application.attr(attribute)
        assertTrue(value.startsWith("@xml/"), "<application android:$attribute> must reference an @xml resource, was '$value'")
        return value.removePrefix("@xml/")
    }

    @Test
    fun `every exported component is on the allow-list`() {
        val exported = components()
            .filter { (_, element) -> element.isExported() }
            .map { (_, element) -> qualify(element.attr("name")) }
            .toSet()
        val unexpected = exported - EXPORTED_ALLOW_LIST.keys
        assertTrue(
            unexpected.isEmpty(),
            "Exported components not on the allow-list (review, then add to EXPORTED_ALLOW_LIST with a reason): $unexpected",
        )
        val stale = EXPORTED_ALLOW_LIST.keys - exported
        assertTrue(stale.isEmpty(), "Allow-list entries that are no longer exported, remove them: $stale")
    }

    @Test
    fun `components with intent filters declare android exported explicitly`() {
        val implicit = components()
            .filter { (_, element) -> element.children("intent-filter").isNotEmpty() && element.attr("exported").isEmpty() }
            .map { (_, element) -> element.attr("name") }
        assertTrue(implicit.isEmpty(), "Set android:exported explicitly on: $implicit")
    }

    @Test
    fun `IME service requires BIND_INPUT_METHOD`() {
        val service = manifest.elements("service").singleOrNull { qualify(it.attr("name")) == IME_SERVICE }
        assertNotNull(service, "$IME_SERVICE is not declared")
        assertEquals("android.permission.BIND_INPUT_METHOD", service.attr("permission"))
    }

    @Test
    fun `exported services are protected by a system bind permission`() {
        val expected = mapOf(
            IME_SERVICE to "android.permission.BIND_INPUT_METHOD",
            SPELL_CHECKER_SERVICE to "android.permission.BIND_TEXT_SERVICE",
        )
        for (service in manifest.elements("service").filter { it.isExported() }) {
            val name = qualify(service.attr("name"))
            val permission = assertNotNull(expected[name], "Exported service $name has no expected bind permission")
            assertEquals(permission, service.attr("permission"), "$name must require $permission")
        }
    }

    @Test
    fun `providers are not exported and FileProvider paths are narrow`() {
        val providers = manifest.elements("provider")
        for (provider in providers) {
            assertEquals("false", provider.attr("exported"), "Provider ${provider.attr("name")} must not be exported")
        }
        val fileProvider = providers.single { it.attr("name") == "androidx.core.content.FileProvider" }
        assertEquals("true", fileProvider.attr("grantUriPermissions"))
        val pathsRes = fileProvider.children("meta-data")
            .single { it.attr("name") == "android.support.FILE_PROVIDER_PATHS" }
            .attr("resource").removePrefix("@xml/")
        val paths = xmlRes(pathsRes).documentElement
        val entries = (0 until paths.childNodes.length).map { paths.childNodes.item(it) }.filterIsInstance<Element>()
        assertTrue(entries.isNotEmpty(), "$pathsRes declares no paths")
        for (entry in entries) {
            // No root-path, external-*-path or files-path: only app cache sub-directories may be shared.
            assertEquals("cache-path", entry.tagName, "FileProvider path type ${entry.tagName} is too broad")
            val path = entry.getAttribute("path").trim('/')
            assertTrue(path.isNotEmpty() && path != "." && !path.contains(".."), "FileProvider path '$path' must be a cache sub-directory")
        }
    }

    @Test
    fun `backup rules only include settings and extensions`() {
        assertEquals("true", manifest.elements("application").single().attr("allowBackup"))
        val fullBackup = xmlRes(applicationResource("fullBackupContent")).documentElement
        assertEquals("full-backup-content", fullBackup.tagName)
        assertSafeIncludes("fullBackupContent", fullBackup)

        val extraction = xmlRes(applicationResource("dataExtractionRules")).documentElement
        assertEquals("data-extraction-rules", extraction.tagName)
        for (section in listOf("cloud-backup", "device-transfer")) {
            val element = extraction.children(section).singleOrNull()
            assertNotNull(element, "data extraction rules need an explicit <$section> section")
            assertSafeIncludes(section, element)
        }
    }

    /**
     * A section must be include-based (so everything else is excluded) and must not include
     * databases, shared prefs or any clipboard/dictionary data (user-typed content).
     */
    private fun assertSafeIncludes(label: String, section: Element) {
        val includes = section.children("include")
        assertTrue(includes.isNotEmpty(), "$label must use <include> rules; without them every file is backed up")
        for (include in includes) {
            val domain = include.getAttribute("domain")
            val path = include.getAttribute("path")
            assertFalse(domain == "database" || domain == "sharedpref", "$label must not back up domain '$domain' ($path)")
            assertFalse(path.isBlank() || path == "." || path == "/", "$label must not include a whole domain ($domain)")
            for (banned in listOf("clipboard", "dictionary", "history", "no_backup", "cache")) {
                assertFalse(path.contains(banned, ignoreCase = true), "$label must not include '$path'")
            }
        }
    }

    @Test
    fun `network security config blocks cleartext and user CAs outside debug`() {
        val config = xmlRes(applicationResource("networkSecurityConfig")).documentElement
        val base = config.children("base-config").single()
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        val baseCerts = base.children("certificates").map { it.getAttribute("src") }
        assertEquals(listOf("system"), baseCerts, "Release trust anchors must be system CAs only")
        for (domainConfig in config.children("domain-config")) {
            assertFalse(domainConfig.getAttribute("cleartextTrafficPermitted") == "true", "No domain may allow cleartext")
        }
        val userCerts = config.children("certificates").filter { it.getAttribute("src") == "user" }
        for (cert in userCerts) {
            assertEquals("debug-overrides", (cert.parentNode.parentNode as Element).tagName, "User CAs are allowed only in debug-overrides")
        }
        assertTrue(manifest.elements("application").single().attr("usesCleartextTraffic") != "true")
    }
}
