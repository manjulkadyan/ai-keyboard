/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import android.os.Build
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.datastore.isProAt
import com.manjul.aikeyboard.core.theme.SnyggGenerator
import com.manjul.aikeyboard.core.theme.ThemePreset
import com.manjul.aikeyboard.core.theme.ThemePresets
import com.manjul.aikeyboard.fork.paywall.PaywallTrigger
import com.manjul.aikeyboard.fork.paywall.paywall
import com.manjul.aikeyboard.fork.routes.RouteRegistry
import com.manjul.aikeyboard.lib.ext.ExtensionComponentName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface ApplyResult {
    data object Applied : ApplyResult

    /** A premium theme without Pro: open [route] (the paywall); the theme is kept as pending. */
    data class NeedsPro(val route: String) : ApplyResult
}

/**
 * The Themes screen logic (S4 Task 10, PRD FR-32). [select] drives the upstream live preview
 * (`ThemeManager.previewThemeId`). [apply] writes the chosen stylesheet to both the day and night theme, which
 * the keyboard picks up without a restart. A premium theme without Pro is stored as
 * `FeatureSettings.pendingPremiumThemeId` and applied when Pro turns true while this view model lives (the first
 * settings value counts, so a Pro bought elsewhere applies it on the next screen open). Every applied theme clears
 * the pending one, so a later free apply cancels it. Pro and the pending id come from the same settings value.
 * `theme_apply` fires once per apply.
 */
class ThemesViewModel(
    private val prefs: AppPrefs,
    private val settings: FeatureSettingsStore,
    private val settingsFlow: Flow<FeatureSettings> = settings.flow,
    private val now: () -> Long = System::currentTimeMillis,
    private val analytics: (AnalyticsEvent) -> Unit,
    private val previewThemeId: MutableStateFlow<ExtensionComponentName?>,
    private val scope: CoroutineScope,
    sdkInt: Int = Build.VERSION.SDK_INT,
    allPresets: List<ThemePreset> = ThemePresets.all,
) {
    /** The presets this device can show: Material You needs API 31. */
    val presets: List<ThemePreset> = allPresets.filter { it.minSdk <= sdkInt }

    /** Seeded from the cached settings, so a Pro user is not sent to the paywall before the first emission. */
    private var proNow = settings.current.isProAt(now())

    init {
        scope.launch {
            settingsFlow
                .map { it to it.isProAt(now()) }
                .distinctUntilChangedBy { it.second }
                .collect { (value, pro) ->
                    proNow = pro
                    if (pro) applyPending(value.pendingPremiumThemeId)
                }
        }
    }

    fun select(id: String, keyBorder: Boolean = true) {
        if (presets.none { it.id == id }) return
        previewThemeId.value = name(SnyggGenerator.stylesheetId(id, keyBorder))
    }

    fun clearPreview() {
        previewThemeId.value = null
    }

    fun apply(id: String, keyBorder: Boolean): ApplyResult {
        val preset = presets.firstOrNull { it.id == id } ?: return ApplyResult.Applied
        val stylesheetId = SnyggGenerator.stylesheetId(id, keyBorder)
        if (preset.premium && !proNow) {
            scope.launch { settings.update { it.copy(pendingPremiumThemeId = stylesheetId) } }
            return ApplyResult.NeedsPro(RouteRegistry.paywall(PaywallTrigger.PREMIUM_THEME))
        }
        write(preset, stylesheetId)
        return ApplyResult.Applied
    }

    fun setKeySound(sound: KeySound) {
        scope.launch { settings.update { it.copy(keySound = sound.wire) } }
    }

    private fun applyPending(pending: String) {
        if (pending.isEmpty()) return
        val preset = PremiumThemeGuard.presetFor(pending, presets)
        if (preset == null) {
            scope.launch { settings.update { it.copy(pendingPremiumThemeId = "") } }
            return
        }
        write(preset, pending)
    }

    private fun write(preset: ThemePreset, stylesheetId: String) {
        val component = name(stylesheetId)
        scope.launch {
            // Any applied theme cancels a pending premium one (a free apply after a dismissed paywall wins).
            settings.update { if (it.pendingPremiumThemeId.isEmpty()) it else it.copy(pendingPremiumThemeId = "") }
        }
        scope.launch {
            prefs.theme.dayThemeId.set(component)
            prefs.theme.nightThemeId.set(component)
        }
        analytics(EventCatalog.themeApply(preset.id, preset.premium))
    }

    private fun name(stylesheetId: String) = ExtensionComponentName(SnyggGenerator.EXTENSION_ID, stylesheetId)
}
