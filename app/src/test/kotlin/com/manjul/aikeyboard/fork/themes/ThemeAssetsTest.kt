/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.manjul.aikeyboard.app.FlorisPreferenceModel
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.core.theme.SnyggGenerator
import com.manjul.aikeyboard.core.theme.ThemePresets
import com.manjul.aikeyboard.ime.theme.ThemeExtension
import com.manjul.aikeyboard.lib.ext.ExtensionComponentName
import com.manjul.aikeyboard.lib.ext.ExtensionJsonConfig
import org.florisboard.lib.snygg.SnyggStylesheet
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The committed `com.manjul.aikeyboard.themes` extension (S4 Task 9) is the exact `:tools:themegen` output, loads the
 * way `ExtensionManager` and `ThemeManager` load it, and sits beside the upstream theme packs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ThemeAssetsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dir = "ime/theme/${SnyggGenerator.EXTENSION_ID}"

    private fun asset(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

    private fun extension(id: String): ThemeExtension =
        ExtensionJsonConfig.decodeFromString(ThemeExtension.serializer(), asset("ime/theme/$id/extension.json"))

    @Test fun `the extension lists 24 stylesheets and equals the generator output`() {
        assertEquals(SnyggGenerator.extension(ThemePresets.all), asset("$dir/extension.json"))
        val ext = extension(SnyggGenerator.EXTENSION_ID)
        assertEquals(SnyggGenerator.EXTENSION_ID, ext.meta.id)
        assertEquals(24, ext.themes.size)
        assertEquals(24, context.assets.list("$dir/stylesheets")!!.size)
    }

    @Test fun `every committed stylesheet parses and equals the generator output`() {
        val ext = extension(SnyggGenerator.EXTENSION_ID)
        for (preset in ThemePresets.all) {
            for (keyBorder in listOf(true, false)) {
                val id = if (keyBorder) preset.id else "${preset.id}_borderless"
                val component = ext.themes.single { it.id == id }
                val json = asset("$dir/${component.stylesheetPath()}")
                assertEquals(SnyggGenerator.stylesheet(preset.spec, keyBorder), json, id)
                SnyggStylesheet.fromJson(json).getOrThrow()
            }
        }
    }

    @Test fun `the upstream theme packs still load`() {
        for (id in listOf("org.florisboard.themes", "org.florisboard.themes.my")) {
            val ext = extension(id)
            assertTrue(ext.themes.isNotEmpty(), id)
            for (component in ext.themes) {
                SnyggStylesheet.fromJson(asset("ime/theme/$id/${component.stylesheetPath()}")).getOrThrow()
            }
        }
    }

    @Test fun `default day and night themes point at our light and dark`() {
        val prefs: FlorisPreferenceModel by FlorisPreferenceStore
        assertEquals(ExtensionComponentName(SnyggGenerator.EXTENSION_ID, "light"), prefs.theme.dayThemeId.default)
        assertEquals(ExtensionComponentName(SnyggGenerator.EXTENSION_ID, "dark"), prefs.theme.nightThemeId.default)
        val ids = extension(SnyggGenerator.EXTENSION_ID).themes.map { it.id }
        assertTrue("light" in ids && "dark" in ids)
    }

    @Test fun `the LICENSES file records the themes as original works`() {
        val licenses = asset("LICENSES.md")
        assertTrue(SnyggGenerator.EXTENSION_ID in licenses)
        assertTrue("Apache-2.0" in licenses)
    }
}
