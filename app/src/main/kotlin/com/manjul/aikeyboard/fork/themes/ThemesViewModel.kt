/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import android.os.Build
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * `FeatureSettings.pendingPremiumThemeId` and applied once [isPro] turns true. `theme_apply` fires once per apply.
 */
class ThemesViewModel(
    private val prefs: AppPrefs,
    private val settings: FeatureSettingsStore,
    private val isPro: Flow<Boolean>,
    private val analytics: (AnalyticsEvent) -> Unit,
    private val previewThemeId: MutableStateFlow<ExtensionComponentName?>,
    private val scope: CoroutineScope,
    sdkInt: Int = Build.VERSION.SDK_INT,
    allPresets: List<ThemePreset> = ThemePresets.all,
) {
    /** The presets this device can show: Material You needs API 31. */
    val presets: List<ThemePreset> = allPresets.filter { it.minSdk <= sdkInt }

    private var proNow = false

    init {
        scope.launch {
            isPro.distinctUntilChanged().collect { pro ->
                proNow = pro
                if (pro) applyPending()
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

    private fun applyPending() {
        val pending = settings.current.pendingPremiumThemeId
        if (pending.isEmpty()) return
        scope.launch { settings.update { it.copy(pendingPremiumThemeId = "") } }
        val preset = PremiumThemeGuard.presetFor(pending, presets) ?: return
        write(preset, pending)
    }

    private fun write(preset: ThemePreset, stylesheetId: String) {
        val component = name(stylesheetId)
        scope.launch {
            prefs.theme.dayThemeId.set(component)
            prefs.theme.nightThemeId.set(component)
        }
        analytics(EventCatalog.themeApply(preset.id, preset.premium))
    }

    private fun name(stylesheetId: String) = ExtensionComponentName(SnyggGenerator.EXTENSION_ID, stylesheetId)
}
