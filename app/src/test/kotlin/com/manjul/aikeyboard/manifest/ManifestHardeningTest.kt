/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.manifest

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * TRD §6.3 / §7.1 "Manifest/config tests" over the source manifest and XML config of :app:
 * exported-component allow-list, IME/spell-checker bind permissions, FileProvider paths, backup rules
 * and the network security config.
 *
 * Adding an exported component must be a reviewed change: extend [EXPORTED_ALLOW_LIST] with a reason.
 * Components merged in from libraries are checked on the merged manifest by
 * `verify<Variant>ExportedComponents` (build-logic `ExportedComponentRules`); keep both lists in sync.
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
            TRANSLATE_TEXT_ACTIVITY to "Translate sheet for PROCESS_TEXT and SEND text/plain; no data URIs",
        )

        const val TRANSLATE_TEXT_ACTIVITY = "com.manjul.aikeyboard.fork.processtext.TranslateTextActivity"

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

    @Test
    fun `backup rules never include the translation history database`() {
        val sections = listOf(xmlRes(applicationResource("fullBackupContent")).documentElement) +
            listOf("cloud-backup", "device-transfer").map {
                xmlRes(applicationResource("dataExtractionRules")).documentElement.children(it).single()
            }
        for (section in sections) {
            for (rule in section.children("include")) {
                val domain = rule.getAttribute("domain")
                val path = rule.getAttribute("path")
                assertFalse(domain == "database", "${section.tagName} includes the database domain ($path)")
                assertFalse(path.contains("app_database"), "${section.tagName} includes $path")
            }
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

    @Test
    fun `IME and spell checker services are not direct boot aware`() {
        for (name in listOf(IME_SERVICE, SPELL_CHECKER_SERVICE)) {
            val service = manifest.elements("service").single { qualify(it.attr("name")) == name }
            assertNotEquals("true", service.attr("directBootAware"), "$name must not be directBootAware")
        }
    }

    @Test
    fun `manifest queries the speech recognition service`() {
        val actions = manifest.elements("queries").single().children("action").map { it.attr("name") }
        assertTrue("android.speech.RecognitionService" in actions)
    }

    @Test
    fun `mic permission trampoline keeps its result and stays hidden`() {
        val activity = manifest.elements("activity")
            .single { it.attr("name") == "com.manjul.aikeyboard.fork.voice.MicPermissionActivity" }
        // noHistory drops the RequestPermission result, so the rationale and voice_permission_result never happen.
        assertNotEquals("true", activity.attr("noHistory"), "MicPermissionActivity must not be noHistory")
        assertEquals("true", activity.attr("excludeFromRecents"))
        assertEquals("", activity.attr("taskAffinity"))
        assertTrue(activity.hasAttributeNS(androidNs, "taskAffinity"))
        assertEquals("false", activity.attr("exported"))
    }

    @Test
    fun `translate activity accepts only process text and send of plain text`() {
        // S2-processtext, PRD FR-14 AC5: exactly two filters, text/plain only, no data URI of any kind.
        val activity = manifest.elements("activity").single { it.attr("name") == TRANSLATE_TEXT_ACTIVITY }
        assertEquals("true", activity.attr("exported"))
        assertEquals("true", activity.attr("excludeFromRecents"))
        assertTrue(activity.hasAttributeNS(androidNs, "taskAffinity"))
        assertEquals("", activity.attr("taskAffinity"))
        val filters = activity.children("intent-filter").map { filter ->
            val data = filter.children("data")
            for (d in data) {
                val attrs = (0 until d.attributes.length).map { d.attributes.item(it).localName }
                assertEquals(listOf("mimeType"), attrs, "TranslateTextActivity <data> may only set mimeType")
            }
            Triple(
                filter.children("action").map { it.attr("name") }.toSet(),
                filter.children("category").map { it.attr("name") }.toSet(),
                data.map { it.attr("mimeType") }.toSet(),
            )
        }
        val default = setOf("android.intent.category.DEFAULT")
        assertEquals(
            setOf(
                Triple(setOf("android.intent.action.PROCESS_TEXT"), default, setOf("text/plain")),
                Triple(setOf("android.intent.action.SEND"), default, setOf("text/plain")),
            ),
            filters.toSet(),
        )
        assertEquals(2, filters.size)
    }

    @Test
    fun `ml kit init provider is removed so ml kit never starts with the process`() {
        // S2-mlkit, TRD §2.3: the merged manifest is checked by verify<Variant>NoAdAutoInit (com.google.mlkit.
        // providers are forbidden there); this pins the removal at its source.
        val toolsNs = "http://schemas.android.com/tools"
        val mlKitManifest = File(mainDir, "../../../core/translate-mlkit/src/main/AndroidManifest.xml")
        val removed = parse(mlKitManifest).elements("provider")
            .single { it.attr("name") == "com.google.mlkit.common.internal.MlKitInitProvider" }
        assertEquals("remove", removed.getAttributeNS(toolsNs, "node"))
        assertTrue(manifest.elements("provider").none { it.attr("name").startsWith("com.google.mlkit.") })
    }
}
