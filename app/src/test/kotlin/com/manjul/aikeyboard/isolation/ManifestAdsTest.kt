/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.isolation

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * S4 Task 6 (TRD §11.1 1.1): the GMA SDK is linked, but nothing of it runs at process start. Checks the debug and
 * release merged manifests (`:app:testDebugUnitTest` depends on both `process<Variant>MainManifest` tasks).
 *
 * The full exported-component allow-list of the merged manifest is enforced by `verify<Variant>ExportedComponents`
 * in `check`. This test pins that GMA adds no exported component to it.
 */
class ManifestAdsTest {
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

    private fun Element.attr(name: String): String = getAttributeNS(androidNs, name)

    private fun Element.isExported(): Boolean = when (attr("exported")) {
        "true" -> true
        "false" -> false
        else -> getElementsByTagName("intent-filter").length > 0
    }

    private fun check(variant: String) {
        val doc = merged(variant)
        val application = doc.elements("application").single()
        val meta = doc.elements("meta-data").filter { it.parentNode == application }
        val metaValues = meta.associate { it.attr("name") to it.attr("value") }

        assertTrue(APPLICATION_ID in metaValues, "$variant: $APPLICATION_ID meta-data is missing")
        for (flag in OPTIMIZE_FLAGS) assertEquals("true", metaValues[flag], "$variant: $flag")

        val providers = doc.elements("provider").map { it.attr("name") }
        assertEquals(
            emptyList(),
            providers.filter { it.startsWith(GMA_PACKAGE) || it.endsWith(".MobileAdsInitProvider") },
            "$variant: GMA must not register a provider (it would run at process start)",
        )
        val startupInitializers = doc.elements("provider")
            .filter { it.attr("name") == "androidx.startup.InitializationProvider" }
            .flatMap { provider ->
                val entries = provider.getElementsByTagName("meta-data")
                (0 until entries.length).map { (entries.item(it) as Element).attr("name") }
            }
        assertEquals(emptyList(), startupInitializers.filter { it.startsWith(GMA_PACKAGE) }, "$variant initializers")

        val permissions = doc.elements("uses-permission").map { it.attr("name") }
        assertTrue(AD_ID_PERMISSION in permissions, "$variant: $AD_ID_PERMISSION is missing")

        val exportedGma = COMPONENT_TAGS.flatMap { doc.elements(it) }
            .filter { it.attr("name").startsWith(GMA_PACKAGE) && it.isExported() }
            .map { it.attr("name") }
        assertEquals(emptyList(), exportedGma, "$variant: GMA components must not be exported")
    }

    @Test
    fun `debug merged manifest has the AdMob app id and no GMA auto-init`() {
        check("debug")
        val appId = merged("debug").elements("meta-data").single { it.attr("name") == APPLICATION_ID }.attr("value")
        assertEquals(SAMPLE_APP_ID, appId, "debug uses Google's sample AdMob app id unless aikb.admob.appId is set")
    }

    @Test
    fun `release merged manifest has the AdMob app id and no GMA auto-init`() = check("release")

    private companion object {
        const val GMA_PACKAGE = "com.google.android.gms.ads."
        const val APPLICATION_ID = "com.google.android.gms.ads.APPLICATION_ID"
        const val AD_ID_PERMISSION = "com.google.android.gms.permission.AD_ID"
        const val SAMPLE_APP_ID = "ca-app-pub-3940256099942544~3347511713"
        val OPTIMIZE_FLAGS = listOf(
            "com.google.android.gms.ads.flag.OPTIMIZE_INITIALIZATION",
            "com.google.android.gms.ads.flag.OPTIMIZE_AD_LOADING",
        )
        val COMPONENT_TAGS = listOf("activity", "activity-alias", "service", "receiver", "provider")
    }
}
