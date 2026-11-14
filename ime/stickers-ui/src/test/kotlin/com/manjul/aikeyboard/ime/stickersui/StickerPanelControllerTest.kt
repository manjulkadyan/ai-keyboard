/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.stickersui

import com.manjul.aikeyboard.core.common.Outcome
import com.manjul.aikeyboard.core.content.stickers.StickerCatalog
import com.manjul.aikeyboard.core.content.stickers.StickerPackUi
import com.manjul.aikeyboard.core.content.stickers.StickerUi
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitivityReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The sticker panel controller: policy re-checks, the premium gate and pack selection (S5 Task 6). */
class StickerPanelControllerTest {
    private val free = StickerUi("starter/01", "starter", File("01.png"), premium = false)
    private val premium = StickerUi("starter_plus/01", "starter_plus", File("p01.png"), premium = true)

    private class FakeCatalog(packs: List<StickerPackUi>) : StickerCatalog {
        override val packs: StateFlow<List<StickerPackUi>> = MutableStateFlow(packs)
        var refreshes = 0
        val installs = mutableListOf<String>()

        override suspend fun refresh() {
            refreshes++
        }

        override suspend fun install(packId: String): Outcome<Unit> {
            installs += packId
            return Outcome.Success(Unit)
        }
    }

    private val catalog = FakeCatalog(
        listOf(
            StickerPackUi("starter", "Starter", premium = false, installed = true, stickers = listOf(free)),
            StickerPackUi("starter_plus", "Starter Plus", premium = true, installed = true, stickers = listOf(premium)),
        ),
    )
    private var isPro = false
    private var decision = PolicyDecision.Unrestricted
    private val password = PolicyDecision.Locked.copy(reasons = setOf(SensitivityReason.PASSWORD))

    private fun TestScope.controller() =
        StickerPanelController(catalog, { isPro }, { decision }, TestScope(UnconfinedTestDispatcher(testScheduler)))

    @Test fun `a password field gives Blocked and no refresh`() = runTest {
        decision = password
        val controller = controller()
        controller.onOpen()
        assertFalse(controller.state.value.open)
        assertEquals(0, catalog.refreshes)
        assertEquals(TapResult.Blocked, controller.onStickerTapped(free))
    }

    @Test fun `no cloud gives no refresh`() = runTest {
        decision = PolicyDecision.Unrestricted.copy(allowCloud = false)
        val controller = controller()
        controller.onOpen()
        assertEquals(0, catalog.refreshes)
        assertEquals(TapResult.Blocked, controller.onStickerTapped(free))
    }

    @Test fun `opening refreshes and lists the packs`() = runTest {
        val controller = controller()
        controller.onOpen()
        assertTrue(controller.state.value.open)
        assertEquals(1, catalog.refreshes)
        assertEquals(listOf("starter", "starter_plus"), controller.state.value.packs.map { it.id })
        assertEquals("starter", controller.state.value.selectedPackId)
    }

    @Test fun `a free sticker inserts`() = runTest {
        val controller = controller()
        controller.onOpen()
        assertEquals(TapResult.Insert(free), controller.onStickerTapped(free))
    }

    @Test fun `a premium sticker without Pro needs Pro and shows the card`() = runTest {
        val controller = controller()
        controller.onOpen()
        assertEquals(TapResult.NeedsPro("paywall?trigger=premium_sticker"), controller.onStickerTapped(premium))
        assertTrue(controller.state.value.premiumCard)
    }

    @Test fun `Pro turning true unlocks the premium sticker`() = runTest {
        val controller = controller()
        controller.onOpen()
        assertIs<TapResult.NeedsPro>(controller.onStickerTapped(premium))
        isPro = true
        assertEquals(TapResult.Insert(premium), controller.onStickerTapped(premium))
        assertFalse(controller.state.value.premiumCard)
    }

    @Test fun `a policy change to sensitive while open closes the panel`() = runTest {
        val controller = controller()
        controller.onOpen()
        assertTrue(controller.state.value.open)
        decision = password
        controller.onDecisionChanged()
        assertFalse(controller.state.value.open)
        assertEquals(TapResult.Blocked, controller.onStickerTapped(free))
    }

    @Test fun `selecting a premium pack that is not installed shows the card without Pro`() = runTest {
        val notInstalled = FakeCatalog(
            listOf(StickerPackUi("starter_plus", "Starter Plus", premium = true, installed = false, emptyList())),
        )
        val controller = StickerPanelController(
            notInstalled, { isPro }, { decision }, TestScope(UnconfinedTestDispatcher(testScheduler)),
        )
        controller.onOpen()
        controller.onPackSelected("starter_plus")
        assertTrue(controller.state.value.premiumCard)
        assertTrue(notInstalled.installs.isEmpty())

        isPro = true
        controller.onPackSelected("starter_plus")
        assertEquals(listOf("starter_plus"), notInstalled.installs)
    }
}
