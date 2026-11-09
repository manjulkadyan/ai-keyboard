/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.rules

import com.manjul.aikeyboard.core.ads.placement.AdPlacementId
import com.manjul.aikeyboard.core.ads.placement.AdPlacements
import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.config.DefaultsRemoteConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdUnitIdsTest {
    @Test
    fun `an empty value gives null for every placement`() {
        val ids = AdUnitIds(DefaultsRemoteConfig())
        for (placement in AdPlacements.registry.all) assertNull(ids.unitFor(placement.id), placement.id.value)
    }

    @Test
    fun `a blank value gives null`() {
        val ids = AdUnitIds(DefaultsRemoteConfig(mapOf(ConfigKeys.AD_UNIT_BANNER_HOME to "  ")))
        assertNull(ids.unitFor(AdPlacements.BANNER_HOME))
    }

    @Test
    fun `a set value is read from ad_unit_ plus the placement`() {
        val unit = "ca-app-pub-3940256099942544/9214589741"
        val ids = AdUnitIds(DefaultsRemoteConfig(mapOf(ConfigKeys.AD_UNIT_BANNER_HOME to unit)))
        assertEquals(unit, ids.unitFor(AdPlacements.BANNER_HOME))
        assertNull(ids.unitFor(AdPlacements.APP_OPEN))
    }

    @Test
    fun `a placement without a config key gives null`() {
        assertNull(AdUnitIds(DefaultsRemoteConfig()).unitFor(AdPlacementId("not_a_placement")))
    }
}
