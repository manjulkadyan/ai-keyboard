/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.buildlogic

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * TRD §2.3 / §11.1 1.1: in a single process every merged `ContentProvider` (and every `androidx.startup`
 * initializer) runs at process start, including when the keyboard is what starts the process. No ad or
 * consent SDK may run code there. This finds such entries in a merged manifest.
 *
 * ML Kit is held to the same rule (S2-mlkit): its `MlKitInitProvider` would start ML Kit and its Firebase
 * components in the keyboard process, so `:core:translate-mlkit` removes it and ML Kit starts on first use.
 */
object AdAutoInitRules {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /** Packages of ad/consent SDKs (GMA, GMA next-gen, UMP), of our own `:core:ads`, and of ML Kit. */
    val FORBIDDEN_PREFIXES = listOf(
        "com.google.android.gms.ads.",
        "com.google.android.libraries.ads.",
        "com.google.android.ump.",
        "com.manjul.aikeyboard.core.ads.",
        "com.google.mlkit.",
    )

    fun findViolations(manifest: InputStream): List<String> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(manifest)
        val providers = document.getElementsByTagName("provider").let { nodes ->
            (0 until nodes.length).map { nodes.item(it) as Element }
        }
        val violations = mutableListOf<String>()
        for (provider in providers) {
            val name = provider.getAttributeNS(ANDROID_NS, "name")
            if (isForbidden(name) || name.endsWith(".MobileAdsInitProvider")) {
                violations += "provider $name runs at process start"
            }
            val metaData = provider.getElementsByTagName("meta-data")
            for (i in 0 until metaData.length) {
                val entry = metaData.item(i) as Element
                val entryName = entry.getAttributeNS(ANDROID_NS, "name")
                if (isForbidden(entryName)) {
                    violations += "initializer $entryName is registered in provider $name"
                }
            }
        }
        return violations
    }

    private fun isForbidden(className: String): Boolean = FORBIDDEN_PREFIXES.any { className.startsWith(it) }
}
