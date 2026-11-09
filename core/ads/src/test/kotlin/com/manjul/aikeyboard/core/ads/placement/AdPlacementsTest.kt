/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.placement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** S4 Task 7: the closed placement set of TRD §4.11 and PRD FR-27. */
class AdPlacementsTest {
    private val expected = listOf(
        AdPlacement(AdPlacementId("app_open"), AdFormat.APP_OPEN, FrequencyCap(minInterval = 4.hours)),
        AdPlacement(
            AdPlacementId("inter_feature_exit"),
            AdFormat.INTERSTITIAL,
            FrequencyCap(minInterval = 3.minutes, maxPerDay = 6),
        ),
        AdPlacement(AdPlacementId("banner_home"), AdFormat.BANNER, FrequencyCap.NONE),
        AdPlacement(AdPlacementId("native_translator"), AdFormat.NATIVE, FrequencyCap.NONE),
        AdPlacement(AdPlacementId("native_history"), AdFormat.NATIVE, FrequencyCap.NONE),
        AdPlacement(AdPlacementId("native_dictionary"), AdFormat.NATIVE, FrequencyCap.NONE),
        AdPlacement(AdPlacementId("rewarded_game_hint"), AdFormat.REWARDED, FrequencyCap.NONE),
    )

    @Test
    fun `exactly the seven placements with their formats and caps`() {
        assertEquals(expected, AdPlacements.registry.all)
    }

    @Test
    fun `there is exactly one interstitial placement`() {
        assertEquals(
            listOf(AdPlacementId("inter_feature_exit")),
            AdPlacements.registry.all.filter { it.format == AdFormat.INTERSTITIAL }.map { it.id },
        )
    }

    @Test
    fun `the named constants are the registered placements`() {
        assertEquals(
            expected.map { it.id },
            listOf(
                AdPlacements.APP_OPEN, AdPlacements.INTER_FEATURE_EXIT, AdPlacements.BANNER_HOME,
                AdPlacements.NATIVE_TRANSLATOR, AdPlacements.NATIVE_HISTORY, AdPlacements.NATIVE_DICTIONARY,
                AdPlacements.REWARDED_GAME_HINT,
            ),
        )
    }
}
