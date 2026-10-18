/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.buildlogic

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Why one component may be exported, and the conditions it must keep meeting. */
data class ExportedComponentAllowance(
    val reason: String,
    /** When set, the component must require exactly this `android:permission`. */
    val requiredPermission: String? = null,
    /** When true, the component may only be exported in a debuggable build. */
    val debuggableOnly: Boolean = false,
)

/**
 * TRD §6.3 / §7.1: the exported-component allow-list, applied to the **merged** manifest. The source
 * manifest test (`ManifestHardeningTest` in `:app`) cannot see components that libraries merge in, so
 * a new dependency could otherwise export an activity, receiver or provider unnoticed.
 *
 * Keep [ALLOW_LIST]'s `:app` entries in sync with `ManifestHardeningTest.EXPORTED_ALLOW_LIST`.
 */
object ExportedComponentRules {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    private val COMPONENT_TAGS = listOf("activity", "activity-alias", "service", "receiver", "provider")

    /** Exported component (fully qualified) -> why it may be exported. */
    val ALLOW_LIST: Map<String, ExportedComponentAllowance> = mapOf(
        "com.manjul.aikeyboard.FlorisImeService" to ExportedComponentAllowance(
            "The IME itself; bound by the system only",
            requiredPermission = "android.permission.BIND_INPUT_METHOD",
        ),
        "com.manjul.aikeyboard.FlorisSpellCheckerService" to ExportedComponentAllowance(
            "Upstream spell checker; bound by the system only",
            requiredPermission = "android.permission.BIND_TEXT_SERVICE",
        ),
        "com.manjul.aikeyboard.app.FlorisAppActivity" to ExportedComponentAllowance(
            "Settings activity referenced by method.xml/spellchecker.xml; ui:// deep links; extension import",
        ),
        "com.manjul.aikeyboard.SettingsLauncherAlias" to ExportedComponentAllowance("Launcher entry (MAIN/LAUNCHER)"),
        "com.manjul.aikeyboard.ime.clipboard.FlorisCopyToClipboardActivity" to ExportedComponentAllowance(
            "Upstream share target (SEND image/*) that copies an image into the clipboard",
        ),
        // androidx.profileinstaller (via Compose/baseline profiles): lets adb/benchmark tooling install or dump
        // the baseline profile. Only callers holding DUMP (shell/system) can reach it.
        "androidx.profileinstaller.ProfileInstallReceiver" to ExportedComponentAllowance(
            "Baseline profile install/dump hook for adb and benchmarks; DUMP-protected",
            requiredPermission = "android.permission.DUMP",
        ),
        // androidx.compose.ui:ui-tooling is a debugImplementation dependency: Android Studio launches
        // @Preview composables through it. It must never reach a non-debuggable build.
        "androidx.compose.ui.tooling.PreviewActivity" to ExportedComponentAllowance(
            "Android Studio @Preview launcher from ui-tooling (debug builds only)",
            debuggableOnly = true,
        ),
    )

    /** @return one message per violation; empty when every exported component is allowed. */
    fun findViolations(
        manifest: InputStream,
        allowList: Map<String, ExportedComponentAllowance> = ALLOW_LIST,
    ): List<String> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(manifest)
        val root = document.documentElement
        val packageName = root.getAttribute("package")
        val application = root.getElementsByTagName("application").item(0) as Element?
        val debuggable = application?.getAttributeNS(ANDROID_NS, "debuggable") == "true"

        val violations = mutableListOf<String>()
        for (tag in COMPONENT_TAGS) {
            val nodes = document.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val component = nodes.item(i) as Element
                if (!component.isExported()) continue
                val name = qualify(component.getAttributeNS(ANDROID_NS, "name"), packageName)
                val allowance = allowList[name]
                if (allowance == null) {
                    violations += "$tag $name is exported but not on the allow-list"
                    continue
                }
                val permission = component.getAttributeNS(ANDROID_NS, "permission")
                if (allowance.requiredPermission != null && permission != allowance.requiredPermission) {
                    violations += "$tag $name must require ${allowance.requiredPermission}, has '$permission'"
                }
                if (allowance.debuggableOnly && !debuggable) {
                    violations += "$tag $name is allowed in debuggable builds only"
                }
            }
        }
        return violations
    }

    /** Explicit android:exported wins; without it a component with an intent filter is exported. */
    private fun Element.isExported(): Boolean = when (getAttributeNS(ANDROID_NS, "exported")) {
        "true" -> true
        "false" -> false
        else -> getElementsByTagName("intent-filter").length > 0
    }

    private fun qualify(name: String, packageName: String): String =
        if (name.startsWith(".")) packageName + name else name
}
