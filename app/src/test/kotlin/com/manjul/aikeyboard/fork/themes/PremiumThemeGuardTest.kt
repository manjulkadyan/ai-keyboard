/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import com.manjul.aikeyboard.app.FlorisPreferenceModel
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.core.theme.SnyggGenerator
import com.manjul.aikeyboard.lib.ext.ExtensionComponentName
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PremiumThemeGuardTest {
    private val prefs: FlorisPreferenceModel by FlorisPreferenceStore

    private fun name(id: String) = ExtensionComponentName(SnyggGenerator.EXTENSION_ID, id)

    @After fun tearDown() = runBlocking {
        prefs.theme.dayThemeId.reset()
        prefs.theme.nightThemeId.reset()
        Unit
    }

    @Test fun `a lapsed Pro with sunset active reverts to light and dark`() = runBlocking {
        prefs.theme.dayThemeId.set(name("sunset"))
        prefs.theme.nightThemeId.set(name("sunset_borderless"))
        assertTrue(PremiumThemeGuard.check(isPro = false, prefs = prefs))
        assertEquals(name("light"), prefs.theme.dayThemeId.get())
        assertEquals(name("dark"), prefs.theme.nightThemeId.get())
    }

    @Test fun `only the premium slot reverts`() = runBlocking {
        prefs.theme.dayThemeId.set(name("ocean"))
        prefs.theme.nightThemeId.set(name("midnight"))
        assertTrue(PremiumThemeGuard.check(isPro = false, prefs = prefs))
        assertEquals(name("ocean"), prefs.theme.dayThemeId.get())
        assertEquals(name("dark"), prefs.theme.nightThemeId.get())
    }

    @Test fun `a free theme stays`() = runBlocking {
        prefs.theme.dayThemeId.set(name("forest_borderless"))
        prefs.theme.nightThemeId.set(name("slate"))
        assertFalse(PremiumThemeGuard.check(isPro = false, prefs = prefs))
        assertEquals(name("forest_borderless"), prefs.theme.dayThemeId.get())
        assertEquals(name("slate"), prefs.theme.nightThemeId.get())
    }

    @Test fun `Pro keeps a premium theme and other extensions are never touched`() = runBlocking {
        prefs.theme.dayThemeId.set(name("sunset"))
        prefs.theme.nightThemeId.set(ExtensionComponentName("org.florisboard.themes", "sunset"))
        assertFalse(PremiumThemeGuard.check(isPro = true, prefs = prefs))
        assertEquals(name("sunset"), prefs.theme.dayThemeId.get())
        assertTrue(PremiumThemeGuard.check(isPro = false, prefs = prefs))
        assertEquals(name("light"), prefs.theme.dayThemeId.get())
        assertEquals(ExtensionComponentName("org.florisboard.themes", "sunset"), prefs.theme.nightThemeId.get())
    }
}
