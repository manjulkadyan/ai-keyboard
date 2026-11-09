/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import com.manjul.aikeyboard.app.FlorisPreferenceModel
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.theme.SnyggGenerator
import com.manjul.aikeyboard.lib.ext.ExtensionComponentName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ThemesViewModelTest {
    private val prefs: FlorisPreferenceModel by FlorisPreferenceStore
    private val store = FeatureSettingsStore(RuntimeEnvironment.getApplication(), UnconfinedTestDispatcher())
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private val isPro = MutableStateFlow(false)
    private val preview = MutableStateFlow<ExtensionComponentName?>(null)
    private val events = mutableListOf<AnalyticsEvent>()

    private fun vm(sdkInt: Int = 34) = ThemesViewModel(
        prefs = prefs,
        settings = store,
        isPro = isPro,
        analytics = { events.add(it) },
        previewThemeId = preview,
        scope = scope,
        sdkInt = sdkInt,
    )

    private fun name(id: String) = ExtensionComponentName(SnyggGenerator.EXTENSION_ID, id)

    private fun reset() = runBlocking {
        prefs.theme.dayThemeId.reset()
        prefs.theme.nightThemeId.reset()
        store.update { it.copy(pendingPremiumThemeId = "") }
    }

    @Before fun setUp() = reset()

    @After fun tearDown() {
        scope.cancel()
        reset()
    }

    @Test fun `a free theme applies to day and night and logs one theme_apply`() {
        val result = vm().apply("ocean", keyBorder = false)
        assertEquals(ApplyResult.Applied, result)
        assertEquals(name("ocean_borderless"), prefs.theme.dayThemeId.get())
        assertEquals(name("ocean_borderless"), prefs.theme.nightThemeId.get())
        assertEquals(1, events.size)
        assertEquals("theme_apply", events.single().name)
        assertEquals(mapOf("theme_id" to "ocean", "premium" to false), events.single().params)
    }

    @Test fun `a premium theme without Pro opens the paywall and changes nothing`() {
        val result = vm().apply("sunset", keyBorder = true)
        assertEquals(ApplyResult.NeedsPro("paywall?trigger=premium_theme"), result)
        assertEquals(name("light"), prefs.theme.dayThemeId.get())
        assertEquals(name("dark"), prefs.theme.nightThemeId.get())
        assertTrue(events.isEmpty())
        assertEquals("sunset", store.current.pendingPremiumThemeId)
    }

    @Test fun `Pro turning true applies the pending theme once and clears it`() {
        val vm = vm()
        vm.apply("midnight", keyBorder = false)
        isPro.value = true
        assertEquals(name("midnight_borderless"), prefs.theme.dayThemeId.get())
        assertEquals(name("midnight_borderless"), prefs.theme.nightThemeId.get())
        assertEquals("", store.current.pendingPremiumThemeId)
        assertEquals(listOf(mapOf<String, Any>("theme_id" to "midnight", "premium" to true)), events.map { it.params })
        // A later Pro refresh has nothing left to apply.
        isPro.value = false
        isPro.value = true
        assertEquals(1, events.size)
    }

    @Test fun `a premium theme with Pro applies at once`() {
        isPro.value = true
        assertEquals(ApplyResult.Applied, vm().apply("rose", keyBorder = true))
        assertEquals(name("rose"), prefs.theme.dayThemeId.get())
        assertEquals(1, events.size)
    }

    @Test fun `API 30 hides the two Material You presets`() {
        val ids30 = vm(sdkInt = 30).presets.map { it.id }
        assertEquals(10, ids30.size)
        assertFalse("material_you_light" in ids30 || "material_you_dark" in ids30)
        val ids31 = vm(sdkInt = 31).presets.map { it.id }
        assertEquals(12, ids31.size)
    }

    @Test fun `select drives the live preview and clearPreview ends it`() {
        val vm = vm()
        vm.select("forest")
        assertEquals(name("forest"), preview.value)
        vm.select("forest", keyBorder = false)
        assertEquals(name("forest_borderless"), preview.value)
        vm.clearPreview()
        assertNull(preview.value)
        assertTrue(events.isEmpty())
    }

    @Test fun `an unknown id is ignored`() {
        vm().apply("nope", keyBorder = true)
        assertEquals(name("light"), prefs.theme.dayThemeId.get())
        assertTrue(events.isEmpty())
    }
}
