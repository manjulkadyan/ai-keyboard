/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.rc

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * S4 Task 1 (decision 17): Analytics, the advertising id and Crashlytics collect nothing until the app turns them on
 * after the consent and opt-out checks, and Firebase still does not start with the process. Checks the debug and
 * release merged manifests (`:app:testDebugUnitTest` depends on `processReleaseMainManifest`).
 */
class ManifestAnalyticsDefaultsTest {
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private val appDir: File = listOf(File("."), File("app"))
        .firstOrNull { File(it, "src/main/AndroidManifest.xml").isFile }
        ?: fail("Cannot locate the app module from ${File(".").absolutePath}")

    private fun merged(variant: String): Document {
        val task = "process${variant.replaceFirstChar { it.uppercase() }}MainManifest"
        val file = File(appDir, "build/intermediates/merged_manifest/$variant/$task/AndroidManifest.xml")
        if (!file.isFile) fail("No merged manifest at $file; run :app:$task")
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)
    }

    private fun Document.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun check(variant: String) {
        val doc = merged(variant)
        val application = doc.elements("application").single()
        val meta = doc.elements("meta-data")
            .filter { it.parentNode == application }
            .associate { it.getAttributeNS(androidNs, "name") to it.getAttributeNS(androidNs, "value") }
        for (name in COLLECTION_FLAGS) assertEquals("false", meta[name], "$variant: $name")
        val providers = doc.elements("provider").map { it.getAttributeNS(androidNs, "name") }
        assertEquals(emptyList(), providers.filter { it.startsWith("com.google.firebase.") }, "$variant providers")
    }

    @Test
    fun `debug merged manifest keeps collection off and Firebase lazy`() = check("debug")

    @Test
    fun `release merged manifest keeps collection off and Firebase lazy`() = check("release")

    private companion object {
        val COLLECTION_FLAGS = listOf(
            "firebase_analytics_collection_enabled",
            "google_analytics_adid_collection_enabled",
            "firebase_crashlytics_collection_enabled",
            // I-1: Consent Mode v2 defaults are denied until the UMP result sets them
            "google_analytics_default_allow_analytics_storage",
            "google_analytics_default_allow_ad_storage",
            "google_analytics_default_allow_ad_user_data",
            "google_analytics_default_allow_ad_personalization_signals",
        )
    }
}
