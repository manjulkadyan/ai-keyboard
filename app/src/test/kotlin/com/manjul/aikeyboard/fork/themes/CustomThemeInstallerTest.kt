/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import com.manjul.aikeyboard.app.FlorisPreferenceModel
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.core.theme.CustomTheme
import com.manjul.aikeyboard.core.theme.CustomTheme.Element
import com.manjul.aikeyboard.core.theme.SnyggGenerator
import com.manjul.aikeyboard.ime.theme.ThemeExtension
import com.manjul.aikeyboard.lib.ext.ExtensionComponentName
import kotlinx.coroutines.runBlocking
import org.florisboard.lib.snygg.SnyggStylesheet
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CustomThemeInstallerTest {
    private val prefs: FlorisPreferenceModel by FlorisPreferenceStore
    private val app = RuntimeEnvironment.getApplication()

    /** What `ExtensionManager.import` was handed: the extension and the files in its working dir at that time. */
    private data class Imported(val ext: ThemeExtension, val files: Map<String, String>)

    private val imports = mutableListOf<Imported>()

    private fun installer() = CustomThemeInstaller(
        workRoot = app.cacheDir,
        importExtension = { ext ->
            val dir = requireNotNull(ext.workingDir)
            val files = dir.walkTopDown().filter { it.isFile }
                .associate { it.relativeTo(dir).invariantSeparatorsPath to it.readText() }
            imports.add(Imported(ext as ThemeExtension, files))
        },
        prefs = prefs,
    )

    private val colors = mapOf(
        Element.KEYS to 0xFF223344,
        Element.KEY_TEXT to 0xFFF0F0F0,
        Element.BACKGROUND to 0xFF101820,
        Element.ACCENT to 0xFFFFB000,
    )

    @After fun tearDown() = runBlocking {
        prefs.theme.dayThemeId.reset()
        prefs.theme.nightThemeId.reset()
        Unit
    }

    @Test fun `save writes one stylesheet into the custom extension`() = runBlocking {
        val spec = CustomTheme.spec(colors, CustomTheme.DEFAULT_BASE)
        installer().install(spec)

        val imported = imports.single()
        assertEquals(CustomTheme.EXTENSION_ID, imported.ext.meta.id)
        val theme = imported.ext.themes.single()
        assertEquals(CustomTheme.STYLESHEET_ID, theme.id)
        assertTrue(theme.isNightTheme)
        assertEquals(setOf("stylesheets/${CustomTheme.STYLESHEET_ID}.json"), imported.files.keys)
        val css = imported.files.values.single()
        assertEquals(SnyggGenerator.stylesheet(spec, keyBorder = true), css)
        assertTrue(SnyggStylesheet.fromJson(css).isSuccess)
    }

    @Test fun `the upstream theme prefs point to the custom id`() = runBlocking {
        val name = installer().install(CustomTheme.spec(colors, CustomTheme.DEFAULT_BASE))
        val expected = ExtensionComponentName(CustomTheme.EXTENSION_ID, CustomTheme.STYLESHEET_ID)
        assertEquals(expected, name)
        assertEquals(expected, prefs.theme.dayThemeId.get())
        assertEquals(expected, prefs.theme.nightThemeId.get())
    }

    @Test fun `a second save replaces the stylesheet and leaves no stale file`() = runBlocking {
        val installer = installer()
        installer.install(CustomTheme.spec(colors, CustomTheme.DEFAULT_BASE))
        File(requireNotNull(File(app.cacheDir, CustomThemeInstaller.WORK_DIR_NAME)), "stale.json").apply {
            parentFile?.mkdirs()
            writeText("{}")
        }
        installer.install(CustomTheme.spec(colors + (Element.ACCENT to 0xFF00FF00), CustomTheme.DEFAULT_BASE))
        assertEquals(2, imports.size)
        assertEquals(1, imports[1].files.size)
        assertTrue("#00ff00" in imports[1].files.values.single())
    }

    @Test fun `the custom theme is free, so PremiumThemeGuard never touches it`() = runBlocking {
        installer().install(CustomTheme.spec(colors, CustomTheme.DEFAULT_BASE))
        val custom = ExtensionComponentName(CustomTheme.EXTENSION_ID, CustomTheme.STYLESHEET_ID)
        assertTrue(!PremiumThemeGuard.isPremium(custom))
        assertTrue(!PremiumThemeGuard.check(isPro = false, prefs = prefs))
        assertEquals(custom, prefs.theme.dayThemeId.get())
        assertEquals(custom, prefs.theme.nightThemeId.get())
    }
}
