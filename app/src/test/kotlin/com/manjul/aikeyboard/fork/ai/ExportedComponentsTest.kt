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
 * Sprint 3 (S3-ai): the AI consent trampoline is reachable only from inside the app (the keyboard starts it with an
 * explicit intent), so the exported set of the source manifest stays exactly what it was before the AI sprint.
 */
class ExportedComponentsTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val manifest: Document by lazy {
        val file = listOf(File("src/main"), File("app/src/main")).map { File(it, "AndroidManifest.xml") }
            .firstOrNull { it.isFile } ?: fail("Cannot locate the app manifest from ${File(".").absolutePath}")
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)
    }

    private fun Element.attr(name: String): String = getAttributeNS(androidNs, name)

    private fun elements(tag: String): List<Element> {
        val nodes = manifest.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.isExported(): Boolean = when (attr("exported")) {
        "true" -> true
        "false" -> false
        else -> getElementsByTagName("intent-filter").length > 0
    }

    private fun qualify(name: String) = if (name.startsWith(".")) "com.manjul.aikeyboard$name" else name

    private val consentActivity: Element by lazy {
        elements("activity").singleOrNull { qualify(it.attr("name")) == CONSENT_ACTIVITY }
            ?: fail("$CONSENT_ACTIVITY is not declared")
    }

    @Test
    fun `the exported set is unchanged`() {
        val exported = COMPONENT_TAGS.flatMap(::elements).filter { it.isExported() }.map { qualify(it.attr("name")) }.toSet()
        assertEquals(EXPORTED_BEFORE_S3_AI, exported)
    }

    @Test
    fun `the consent trampoline is not exported, has no intent filter and leaves no trace`() {
        assertEquals("false", consentActivity.attr("exported"))
        assertEquals(0, consentActivity.getElementsByTagName("intent-filter").length)
        assertEquals("true", consentActivity.attr("excludeFromRecents"))
        assertEquals("", consentActivity.attr("taskAffinity"))
        assertTrue(consentActivity.hasAttributeNS(androidNs, "taskAffinity"), "taskAffinity must be set to \"\"")
        assertEquals("@style/FlorisAppTheme.Transparent", consentActivity.attr("theme"))
    }

    private companion object {
        const val CONSENT_ACTIVITY = "com.manjul.aikeyboard.fork.ai.AiConsentActivity"

        val COMPONENT_TAGS = listOf("activity", "activity-alias", "service", "receiver", "provider")

        /** Same as `ManifestHardeningTest.EXPORTED_ALLOW_LIST`; adding the AI features must not add to it. */
        val EXPORTED_BEFORE_S3_AI = setOf(
            "com.manjul.aikeyboard.FlorisImeService",
            "com.manjul.aikeyboard.FlorisSpellCheckerService",
            "com.manjul.aikeyboard.app.FlorisAppActivity",
            "com.manjul.aikeyboard.SettingsLauncherAlias",
            "com.manjul.aikeyboard.ime.clipboard.FlorisCopyToClipboardActivity",
            "com.manjul.aikeyboard.fork.processtext.TranslateTextActivity",
        )
    }
}
