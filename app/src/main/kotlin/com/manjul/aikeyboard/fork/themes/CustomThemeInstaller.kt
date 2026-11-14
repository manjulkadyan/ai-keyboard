/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import com.manjul.aikeyboard.core.theme.CustomTheme
import com.manjul.aikeyboard.core.theme.SnyggGenerator
import com.manjul.aikeyboard.core.theme.ThemeSpec
import com.manjul.aikeyboard.ime.theme.ThemeExtension
import com.manjul.aikeyboard.ime.theme.ThemeExtensionComponentImpl
import com.manjul.aikeyboard.lib.ext.Extension
import com.manjul.aikeyboard.lib.ext.ExtensionComponentName
import com.manjul.aikeyboard.lib.ext.ExtensionMaintainer
import com.manjul.aikeyboard.lib.ext.ExtensionMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves the "Custom colours" theme (S5 Task 8). It writes one Snygg stylesheet into the internal extension
 * [CustomTheme.EXTENSION_ID], imports it through [importExtension] (`ExtensionManager.import` in the app) and sets
 * it as the day and night theme. A save replaces the previous one. The theme is free, so `PremiumThemeGuard` does
 * not touch it. Nothing leaves the device.
 */
class CustomThemeInstaller(
    private val workRoot: File,
    private val importExtension: (Extension) -> Unit,
    private val prefs: AppPrefs,
) {
    suspend fun install(spec: ThemeSpec): ExtensionComponentName {
        withContext(Dispatchers.IO) {
            val dir = File(workRoot, WORK_DIR_NAME)
            // A clean dir per save: a stale file would be zipped into the extension.
            dir.deleteRecursively()
            val stylesheet = File(dir, "stylesheets/${CustomTheme.STYLESHEET_ID}.json")
            check(stylesheet.parentFile!!.mkdirs()) { "Cannot create the theme working dir" }
            stylesheet.writeText(SnyggGenerator.stylesheet(spec, keyBorder = true))
            val ext = extension(SnyggGenerator.isNight(spec))
            ext.workingDir = dir
            try {
                importExtension(ext)
            } finally {
                dir.deleteRecursively()
            }
        }
        val name = ExtensionComponentName(CustomTheme.EXTENSION_ID, CustomTheme.STYLESHEET_ID)
        prefs.theme.dayThemeId.set(name)
        prefs.theme.nightThemeId.set(name)
        return name
    }

    private fun extension(isNight: Boolean) = ThemeExtension(
        meta = ExtensionMeta(
            id = CustomTheme.EXTENSION_ID,
            version = "1.0.0",
            title = TITLE,
            maintainers = listOf(ExtensionMaintainer(name = "AI Keyboard")),
            license = "Apache-2.0",
        ),
        themes = listOf(
            ThemeExtensionComponentImpl(
                id = CustomTheme.STYLESHEET_ID,
                label = TITLE,
                authors = listOf("AI Keyboard"),
                isNightTheme = isNight,
            ),
        ),
    )

    companion object {
        /** The working dir under the cache dir. `ExtensionManager.import` zips it into the `.flex`. */
        const val WORK_DIR_NAME: String = "custom-theme-work"
        private const val TITLE = "Custom colours"
    }
}
