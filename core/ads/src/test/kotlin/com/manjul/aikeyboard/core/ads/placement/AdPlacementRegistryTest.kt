/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.placement

import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdPlacementRegistryTest {
    private val home = AdPlacement(AdPlacementId("banner_home"), AdFormat.BANNER)
    private val exit = AdPlacement(AdPlacementId("inter_feature_exit"), AdFormat.INTERSTITIAL)

    @Test
    fun `no placements are defined in Sprint 0`() {
        assertTrue(AdPlacements.registry.all.isEmpty())
        assertFalse(AdPlacementId("app_open") in AdPlacements.registry)
    }

    @Test
    fun `lookup by id keeps registration order`() {
        val registry = AdPlacementRegistry(listOf(home, exit))
        assertEquals(listOf(home, exit), registry.all)
        assertEquals(exit, registry[AdPlacementId("inter_feature_exit")])
        assertEquals(home, registry.require(AdPlacementId("banner_home")))
        assertNull(registry[AdPlacementId("native_history")])
    }

    @Test
    fun `unregistered placement cannot be required`() {
        assertThrows<IllegalArgumentException> { AdPlacementRegistry.EMPTY.require(AdPlacementId("app_open")) }
    }

    @Test
    fun `duplicate ids are rejected`() {
        val e = assertThrows<IllegalArgumentException> {
            AdPlacementRegistry(listOf(home, home.copy(format = AdFormat.NATIVE)))
        }
        assertTrue("banner_home" in e.message.orEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = ["app_open", "inter_feature_exit", "native_translator", "rewarded_game_hint", "banner2"])
    fun `valid placement ids`(id: String) {
        assertEquals(id, AdPlacementId(id).value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "AppOpen", "app-open", "_app", "app_", "app__open", "1app", "app open"])
    fun `invalid placement ids are rejected`(id: String) {
        assertThrows<IllegalArgumentException> { AdPlacementId(id) }
    }
}
