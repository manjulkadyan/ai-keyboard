/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Sprint 3 (S3-net): adding Firebase must not change what runs at process start or what is exported.
 * - no `FirebaseInitProvider` (Firebase starts lazily in `:core:firebase`, TRD §2.3);
 * - the Firebase Auth redirect activities are removed, so the exported set is unchanged (TRD §6.3);
 * - debug builds allow cleartext only to the local kb-api hosts.
 */
class ManifestFirebaseTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val appDir: File = listOf(File("."), File("app"))
        .firstOrNull { File(it, "src/main/AndroidManifest.xml").isFile }
        ?: fail("Cannot locate the app module from ${File(".").absolutePath}")

    /**
     * The app's debug merged manifest, the file `verifyDebugExportedComponents` checks. (The unit-test manifest
     * from `test_config.properties` is a separate merge that ignores :app's `tools:node="remove"` entries.)
     */
    private val mergedManifest: Document by lazy {
        val file = File(appDir, "build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml")
        if (!file.isFile) fail("No merged manifest at $file; run :app:processDebugMainManifest")
        parse(file)
    }

    @Test
    fun `no Firebase or Google content provider starts with the process`() {
        val providers = mergedManifest.elements("provider").map { it.getAttributeNS(androidNs, "name") }
        val firebase = providers.filter { it.startsWith("com.google.firebase.") }
        assertEquals(emptyList(), firebase, "Firebase providers run at process start")
    }

    @Test
    fun `Firebase Auth redirect activities are not merged in`() {
        val names = COMPONENT_TAGS.flatMap { mergedManifest.elements(it) }.map { it.getAttributeNS(androidNs, "name") }
        assertEquals(emptyList(), names.filter { it.startsWith("com.google.firebase.auth.") })
    }

    @Test
    fun `exported components are exactly the pre-Firebase allow-list`() {
        val exported = COMPONENT_TAGS.flatMap { mergedManifest.elements(it) }
            .filter { it.isExported() }
            .map { qualify(it.getAttributeNS(androidNs, "name")) }
            .toSet()
        val unexpected = exported - (APP_EXPORTED + LIBRARY_EXPORTED + DEBUG_ONLY_EXPORTED)
        assertEquals(emptySet(), unexpected, "new exported components in the merged manifest")
        assertTrue(exported.containsAll(APP_EXPORTED), "app components missing: ${APP_EXPORTED - exported}")
        assertTrue(exported.none { it.startsWith("com.google.firebase.") || it.startsWith("com.google.android.gms.") })
    }

    @Test
    fun `debug network config allows cleartext only to the local kb-api`() {
        val config = parse(File(appDir, "src/debug/res/xml/network_security_config.xml"))
        val base = config.elements("base-config").single()
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        val cleartext = config.elements("domain-config")
            .filter { it.getAttribute("cleartextTrafficPermitted") == "true" }
        val domains = cleartext.flatMap { dc -> dc.childElements("domain") }
        assertEquals(setOf("10.0.2.2", "localhost"), domains.map { it.textContent.trim() }.toSet())
        assertTrue(domains.all { it.getAttribute("includeSubdomains") != "true" }, "no subdomain wildcards")
    }

    @Test
    fun `main network config still forbids all cleartext`() {
        val config = parse(File(appDir, "src/main/res/xml/network_security_config.xml"))
        assertEquals("false", config.elements("base-config").single().getAttribute("cleartextTrafficPermitted"))
        assertTrue(config.elements("domain-config").none { it.getAttribute("cleartextTrafficPermitted") == "true" })
    }

    private fun parse(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(file)
    }

    private fun Document.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.childElements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.isExported(): Boolean = when (getAttributeNS(androidNs, "exported")) {
        "true" -> true
        "false" -> false
        else -> getElementsByTagName("intent-filter").length > 0
    }

    private fun qualify(name: String): String = if (name.startsWith(".")) "com.manjul.aikeyboard$name" else name

    private companion object {
        val COMPONENT_TAGS = listOf("activity", "activity-alias", "service", "receiver", "provider")

        val APP_EXPORTED = setOf(
            "com.manjul.aikeyboard.FlorisImeService",
            "com.manjul.aikeyboard.FlorisSpellCheckerService",
            "com.manjul.aikeyboard.app.FlorisAppActivity",
            "com.manjul.aikeyboard.SettingsLauncherAlias",
            "com.manjul.aikeyboard.ime.clipboard.FlorisCopyToClipboardActivity",
            "com.manjul.aikeyboard.fork.processtext.TranslateTextActivity",
        )
        val LIBRARY_EXPORTED = setOf(
            "androidx.profileinstaller.ProfileInstallReceiver",
            "androidx.work.impl.background.systemjob.SystemJobService",
            "androidx.work.impl.diagnostics.DiagnosticsReceiver",
        )
        val DEBUG_ONLY_EXPORTED = setOf(
            "androidx.compose.ui.tooling.PreviewActivity",
            "androidx.activity.ComponentActivity",
        )
    }
}
