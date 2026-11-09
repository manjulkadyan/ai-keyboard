/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import com.manjul.aikeyboard.app.FlorisPreferenceModel
import com.manjul.aikeyboard.core.theme.SnyggGenerator
import com.manjul.aikeyboard.core.theme.ThemePreset
import com.manjul.aikeyboard.core.theme.ThemePresets
import com.manjul.aikeyboard.lib.ext.ExtensionComponentName

/** The upstream preferences model of the app (`AppPrefs.kt`); the brief calls it `AppPrefs`. */
typealias AppPrefs = FlorisPreferenceModel

/**
 * Keeps premium themes for Pro (S4 Task 10). When Pro has lapsed, an active premium day theme goes back to our
 * `light` and an active premium night theme to our `dark`. Free presets and themes of other extensions stay.
 * The keyboard calls it from `FlorisImeService.onCreate` once the preferences and the Pro cache are loaded.
 */
object PremiumThemeGuard {
    private val LIGHT = ExtensionComponentName(SnyggGenerator.EXTENSION_ID, "light")
    private val DARK = ExtensionComponentName(SnyggGenerator.EXTENSION_ID, "dark")

    /** Returns true when it changed a theme. */
    suspend fun check(isPro: Boolean, prefs: AppPrefs): Boolean {
        if (isPro) return false
        var changed = false
        if (isPremium(prefs.theme.dayThemeId.get())) {
            prefs.theme.dayThemeId.set(LIGHT)
            changed = true
        }
        if (isPremium(prefs.theme.nightThemeId.get())) {
            prefs.theme.nightThemeId.set(DARK)
            changed = true
        }
        return changed
    }

    fun isPremium(name: ExtensionComponentName): Boolean =
        name.extensionId == SnyggGenerator.EXTENSION_ID && presetFor(name.componentId)?.premium == true

    /** The preset of a stylesheet id (`<preset>` or `<preset>_borderless`), or null for an unknown id. */
    fun presetFor(stylesheetId: String, presets: List<ThemePreset> = ThemePresets.all): ThemePreset? {
        val presetId = stylesheetId.removeSuffix(SnyggGenerator.BORDERLESS_SUFFIX)
        return presets.firstOrNull { it.id == presetId }
    }
}
