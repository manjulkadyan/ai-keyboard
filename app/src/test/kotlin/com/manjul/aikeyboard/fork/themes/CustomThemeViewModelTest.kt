/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.theme.CustomTheme
import com.manjul.aikeyboard.core.theme.CustomTheme.Element
import com.manjul.aikeyboard.core.theme.HexColor
import com.manjul.aikeyboard.core.theme.Hsv
import com.manjul.aikeyboard.core.theme.ThemeSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
class CustomThemeViewModelTest {
    private val store = FeatureSettingsStore(RuntimeEnvironment.getApplication(), UnconfinedTestDispatcher())
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private val previews = mutableListOf<ThemeSpec?>()
    private val installed = mutableListOf<ThemeSpec>()
    private val base = CustomTheme.DEFAULT_BASE

    private fun vm(install: suspend (ThemeSpec) -> Unit = { installed.add(it) }) = CustomThemeViewModel(
        settings = store,
        scope = scope,
        preview = { previews.add(it) },
        install = install,
    )

    private fun awaitUntil(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(5)
        assertTrue(condition())
    }

    private fun reset() = runBlocking {
        store.update {
            it.copy(recentColors = emptyList(), customThemeColors = emptyList(), pendingPremiumThemeId = "")
        }
    }

    @Before fun setUp() = reset()

    @After fun tearDown() {
        scope.cancel()
        reset()
    }

    private fun othersUnchanged(before: Map<Element, Long>, after: Map<Element, Long>, changed: Element) {
        for (element in Element.entries) {
            if (element != changed) assertEquals(before[element], after[element], element.name)
        }
    }

    @Test fun `a wheel pick changes only the selected element`() {
        val vm = vm()
        vm.select(Element.ACCENT)
        val before = vm.state.value.colors
        val wheel = Hsv(120f, 0.5f, 0.75f).toArgb()
        vm.pick(wheel)
        assertEquals(wheel, vm.state.value.colors[Element.ACCENT])
        othersUnchanged(before, vm.state.value.colors, Element.ACCENT)
    }

    @Test fun `a hex pick changes only the selected element`() {
        val vm = vm()
        vm.select(Element.BACKGROUND)
        val before = vm.state.value.colors
        vm.pick(requireNotNull(HexColor.parse("#80ff8800")))
        assertEquals(0x80FF8800, vm.state.value.colors[Element.BACKGROUND])
        othersUnchanged(before, vm.state.value.colors, Element.BACKGROUND)
    }

    @Test fun `a recent pick changes only the selected element`() = runBlocking {
        store.update { it.copy(recentColors = listOf(0xFF112233, 0xFF445566)) }
        val vm = vm()
        assertEquals(listOf(0xFF112233, 0xFF445566), vm.state.value.recents)
        vm.select(Element.KEYS)
        val before = vm.state.value.colors
        vm.pick(vm.state.value.recents[1])
        assertEquals(0xFF445566, vm.state.value.colors[Element.KEYS])
        othersUnchanged(before, vm.state.value.colors, Element.KEYS)
    }

    @Test fun `each change previews the new spec`() {
        val vm = vm()
        previews.clear()
        vm.select(Element.KEYS)
        vm.pick(0xFF123456)
        assertEquals(0xFF123456, previews.last()?.colors?.key)
        vm.clearPreview()
        assertNull(previews.last())
    }

    @Test fun `only Done adds the colour to the recents, and recents survive a new view model`() {
        val vm = vm()
        vm.select(Element.ACCENT)
        vm.pick(0xFFAA0000)
        vm.pick(0xFFBB0000)
        assertEquals(emptyList(), vm.state.value.recents)
        vm.done()
        assertEquals(listOf(0xFFBB0000), vm.state.value.recents)
        awaitUntil { store.current.recentColors == listOf(0xFFBB0000) }

        val next = vm()
        assertEquals(listOf(0xFFBB0000), next.state.value.recents)
    }

    @Test fun `low contrast sets the warning and does not block the save`() {
        val vm = vm()
        assertFalse(vm.state.value.lowContrast)
        vm.select(Element.KEYS)
        vm.pick(0xFF777777)
        vm.select(Element.KEY_TEXT)
        vm.pick(0xFF888888)
        assertTrue(vm.state.value.lowContrast)

        vm.save()
        awaitUntil { vm.state.value.saveStatus == SaveStatus.SAVED }
        assertEquals(0xFF777777, installed.single().colors.key)
        assertEquals(0xFF888888, installed.single().colors.text)

        vm.pick(0xFF000000)
        assertFalse(vm.state.value.lowContrast)
    }

    @Test fun `save stores the colours and cancels a pending premium theme`() = runBlocking {
        store.update { it.copy(pendingPremiumThemeId = "sunset") }
        val vm = vm()
        vm.select(Element.BACKGROUND)
        vm.pick(0xFF010203)
        vm.save()
        awaitUntil { vm.state.value.saveStatus == SaveStatus.SAVED }
        awaitUntil { store.current.customThemeColors.isNotEmpty() }
        assertEquals(0xFF010203, vm().state.value.colors[Element.BACKGROUND])
        assertEquals(base.colors.key, vm().state.value.colors[Element.KEYS])
        assertEquals("", store.current.pendingPremiumThemeId)
    }

    @Test fun `a failed install reports the failure`() {
        val vm = vm(install = { error("disk full") })
        vm.save()
        awaitUntil { vm.state.value.saveStatus == SaveStatus.FAILED }
    }
}
