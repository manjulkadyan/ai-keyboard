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
    /**
     * When set, the component's intent filters must be exactly these (order ignored), and no filter may declare a
     * `<data>` URI (scheme, host, port or path), only MIME types.
     */
    val intentFilters: Set<IntentFilterSpec>? = null,
)

/** One `<intent-filter>`: its actions, categories and `<data android:mimeType>` values. */
data class IntentFilterSpec(
    val actions: Set<String>,
    val mimeTypes: Set<String>,
    val categories: Set<String> = setOf("android.intent.category.DEFAULT"),
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

    /** `<data>` attributes that make a filter match URIs (PRD FR-14 AC5: no data URI handling). */
    private val URI_ATTRIBUTES = listOf(
        "scheme", "host", "port", "path", "pathPrefix", "pathPattern", "pathAdvancedPattern", "pathSuffix",
        "ssp", "sspPrefix", "sspPattern", "sspSuffix", "sspAdvancedPattern",
    )

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
        // S2-processtext (PRD FR-14 AC5): the system "Translate" selection action and share target, text/plain only
        "com.manjul.aikeyboard.fork.processtext.TranslateTextActivity" to ExportedComponentAllowance(
            "Translate sheet for PROCESS_TEXT and SEND text/plain; no data URIs",
            intentFilters = setOf(
                IntentFilterSpec(setOf("android.intent.action.PROCESS_TEXT"), setOf("text/plain")),
                IntentFilterSpec(setOf("android.intent.action.SEND"), setOf("text/plain")),
            ),
        ),
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
        // androidx.compose.ui:ui-test-manifest is a debugImplementation dependency: Compose unit tests host
        // their content in this empty activity. It must never reach a non-debuggable build.
        "androidx.activity.ComponentActivity" to ExportedComponentAllowance(
            "Compose test host activity from ui-test-manifest (debug builds only)",
            debuggableOnly = true,
        ),
        // androidx.work (Whisper model downloads): JobScheduler binds the job service, and adb can ask for
        // WorkManager diagnostics. Only the system (BIND_JOB_SERVICE) or shell (DUMP) can reach them.
        "androidx.work.impl.background.systemjob.SystemJobService" to ExportedComponentAllowance(
            "WorkManager's JobScheduler service; bound by the system only",
            requiredPermission = "android.permission.BIND_JOB_SERVICE",
        ),
        "androidx.work.impl.diagnostics.DiagnosticsReceiver" to ExportedComponentAllowance(
            "WorkManager diagnostics dump for adb; DUMP-protected",
            requiredPermission = "android.permission.DUMP",
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
                if (allowance.intentFilters != null) {
                    violations += intentFilterViolations("$tag $name", component, allowance.intentFilters)
                }
            }
        }
        return violations
    }

    private fun intentFilterViolations(label: String, component: Element, allowed: Set<IntentFilterSpec>): List<String> {
        val violations = mutableListOf<String>()
        val specs = component.childElements("intent-filter").map { filter ->
            val data = filter.childElements("data")
            if (data.any { d -> URI_ATTRIBUTES.any { d.hasAttributeNS(ANDROID_NS, it) } }) {
                violations += "$label declares a data URI in an intent filter; only MIME types are allowed"
            }
            IntentFilterSpec(
                actions = filter.childElements("action").map { it.getAttributeNS(ANDROID_NS, "name") }.toSet(),
                mimeTypes = data.mapNotNull { it.getAttributeNS(ANDROID_NS, "mimeType").ifEmpty { null } }.toSet(),
                categories = filter.childElements("category").map { it.getAttributeNS(ANDROID_NS, "name") }.toSet(),
            )
        }
        if (specs.size != allowed.size || specs.toSet() != allowed) {
            violations += "$label must declare exactly the intent filters $allowed, has $specs"
        }
        return violations
    }

    private fun Element.childElements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
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
