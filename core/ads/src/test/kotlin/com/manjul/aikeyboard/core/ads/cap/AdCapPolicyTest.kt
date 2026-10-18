/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.cap

import com.manjul.aikeyboard.core.ads.placement.AdFormat
import com.manjul.aikeyboard.core.ads.placement.AdPlacement
import com.manjul.aikeyboard.core.ads.placement.AdPlacementId
import com.manjul.aikeyboard.core.ads.placement.FrequencyCap
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AdCapPolicyTest {
    private val policy = AdCapPolicy() // TRD defaults: 1 per 3 min, 6 per rolling 24 h
    private val t0 = 1_800_000_000_000L
    private val minute = 1.minutes.inWholeMilliseconds
    private val hour = 1.hours.inWholeMilliseconds

    private val interExit = AdPlacement(AdPlacementId("inter_feature_exit"), AdFormat.INTERSTITIAL)
    private val interOther = AdPlacement(AdPlacementId("inter_other"), AdFormat.INTERSTITIAL)
    private val appOpen = AdPlacement(AdPlacementId("app_open"), AdFormat.APP_OPEN, FrequencyCap(minInterval = 4.hours))
    private val banner = AdPlacement(AdPlacementId("banner_home"), AdFormat.BANNER)
    private val rewarded = AdPlacement(AdPlacementId("rewarded_game_hint"), AdFormat.REWARDED)

    private fun AdCapState.shown(placement: AdPlacement, at: Long) = policy.recordShown(placement, this, at)

    private fun blocked(placement: AdPlacement, state: AdCapState, now: Long): AdCapDecision.Blocked =
        assertIs<AdCapDecision.Blocked>(policy.evaluate(placement, state, now))

    @Test
    fun `empty state allows every format`() {
        listOf(interExit, appOpen, banner, rewarded).forEach {
            assertEquals(AdCapDecision.Allowed, policy.evaluate(it, AdCapState(), t0))
        }
    }

    @Test
    fun `global interval blocks any capped placement for 3 minutes`() {
        val state = AdCapState().shown(interExit, t0)
        val decision = blocked(interOther, state, t0 + 2 * minute)
        assertEquals(setOf(AdCapBlockReason.GLOBAL_INTERVAL), decision.reasons)
        assertEquals(t0 + 3 * minute, decision.allowedAtEpochMillis)
        assertEquals(AdCapDecision.Allowed, policy.evaluate(interOther, state, t0 + 3 * minute))
    }

    @Test
    fun `app open counts towards the global cap, so it never follows an interstitial within 30 s`() {
        val state = AdCapState().shown(interExit, t0)
        assertEquals(setOf(AdCapBlockReason.GLOBAL_INTERVAL), blocked(appOpen, state, t0 + 30.seconds.inWholeMilliseconds).reasons)
    }

    @Test
    fun `banner, native and rewarded are not globally capped`() {
        val state = AdCapState().shown(interExit, t0)
        assertEquals(AdCapDecision.Allowed, policy.evaluate(banner, state, t0 + 1))
        assertEquals(AdCapDecision.Allowed, policy.evaluate(rewarded, state, t0 + 1))
        assertEquals(
            AdCapDecision.Allowed,
            policy.evaluate(AdPlacement(AdPlacementId("native_history"), AdFormat.NATIVE), state, t0 + 1),
        )
    }

    @Test
    fun `uncapped shows do not consume the global cap`() {
        val state = AdCapState().shown(rewarded, t0).shown(banner, t0)
        assertEquals(AdCapDecision.Allowed, policy.evaluate(interExit, state, t0 + 1))
    }

    @Test
    fun `seventh show in a rolling day is blocked until the oldest leaves the window`() {
        var state = AdCapState()
        repeat(6) { i -> state = state.shown(interExit, t0 + i * hour) }
        val now = t0 + 6 * hour
        val decision = blocked(interExit, state, now)
        assertEquals(setOf(AdCapBlockReason.GLOBAL_DAILY), decision.reasons)
        assertEquals(t0 + 24 * hour, decision.allowedAtEpochMillis)
        assertEquals(AdCapDecision.Allowed, policy.evaluate(interExit, state, t0 + 24 * hour))
    }

    @Test
    fun `placement interval applies to its own placement only`() {
        val state = AdCapState().shown(appOpen, t0)
        val decision = blocked(appOpen, state, t0 + 1 * hour)
        assertEquals(setOf(AdCapBlockReason.PLACEMENT_INTERVAL), decision.reasons)
        assertEquals(t0 + 4 * hour, decision.allowedAtEpochMillis)
        assertEquals(AdCapDecision.Allowed, policy.evaluate(interExit, state, t0 + 1 * hour))
    }

    @Test
    fun `placement daily cap`() {
        val capped = AdPlacement(AdPlacementId("inter_capped"), AdFormat.INTERSTITIAL, FrequencyCap(maxPerDay = 2))
        val state = AdCapState().shown(capped, t0).shown(capped, t0 + hour)
        val decision = blocked(capped, state, t0 + 2 * hour)
        assertEquals(setOf(AdCapBlockReason.PLACEMENT_DAILY), decision.reasons)
        assertEquals(t0 + 24 * hour, decision.allowedAtEpochMillis)
    }

    @Test
    fun `several reasons report the latest clearing time`() {
        val state = AdCapState().shown(appOpen, t0)
        val decision = blocked(appOpen, state, t0 + minute)
        assertEquals(setOf(AdCapBlockReason.GLOBAL_INTERVAL, AdCapBlockReason.PLACEMENT_INTERVAL), decision.reasons)
        assertEquals(t0 + 4 * hour, decision.allowedAtEpochMillis)
    }

    @Test
    fun `records in the future after a clock change are ignored and dropped`() {
        val state = AdCapState().shown(interExit, t0 + 10 * hour)
        assertEquals(AdCapDecision.Allowed, policy.evaluate(interExit, state, t0))
        val recorded = state.shown(interExit, t0)
        assertEquals(listOf(AdShowRecord(interExit.id, AdFormat.INTERSTITIAL, t0)), recorded.records)
    }

    @Test
    fun `record prunes the day window but keeps each placement's latest show`() {
        val state = AdCapState()
            .shown(interExit, t0)
            .shown(interExit, t0 + hour)
            .shown(appOpen, t0 + 2 * hour)
            .shown(interExit, t0 + 30 * hour)
        assertEquals(
            listOf(
                AdShowRecord(appOpen.id, AdFormat.APP_OPEN, t0 + 2 * hour),
                AdShowRecord(interExit.id, AdFormat.INTERSTITIAL, t0 + 30 * hour),
            ),
            state.records,
        )
    }

    @Test
    fun `custom config is honoured`() {
        val strict = AdCapPolicy(AdCapConfig(globalMinInterval = 10.minutes, globalMaxPerDay = 1))
        val state = strict.recordShown(interExit, AdCapState(), t0)
        val decision = assertIs<AdCapDecision.Blocked>(strict.evaluate(interOther, state, t0 + 11 * minute))
        assertEquals(setOf(AdCapBlockReason.GLOBAL_DAILY), decision.reasons)
    }

    @Test
    fun `invalid config is rejected`() {
        assertThrows<IllegalArgumentException> { AdCapConfig(globalMaxPerDay = 0) }
        assertThrows<IllegalArgumentException> { AdCapConfig(globalMinInterval = 0.minutes) }
        assertThrows<IllegalArgumentException> { FrequencyCap(maxPerDay = 0) }
        assertThrows<IllegalArgumentException> { FrequencyCap(minInterval = (-1).minutes) }
    }
}
