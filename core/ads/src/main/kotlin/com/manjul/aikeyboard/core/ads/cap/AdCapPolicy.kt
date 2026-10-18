/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.cap

import com.manjul.aikeyboard.core.ads.placement.AdFormat
import com.manjul.aikeyboard.core.ads.placement.AdPlacement
import com.manjul.aikeyboard.core.ads.placement.AdPlacementId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Global cap shared by the interrupting full-screen formats (TRD §4.11 "Interstitial", PRD FR-27):
 * at most one show per [globalMinInterval] and [globalMaxPerDay] shows in a rolling 24 h window.
 */
data class AdCapConfig(
    val globalMinInterval: Duration = 3.minutes,
    val globalMaxPerDay: Int = 6,
    val globalFormats: Set<AdFormat> = setOf(AdFormat.INTERSTITIAL, AdFormat.APP_OPEN),
) {
    init {
        require(globalMinInterval.isPositive()) { "globalMinInterval must be positive" }
        require(globalMaxPerDay > 0) { "globalMaxPerDay must be > 0" }
    }
}

/** One completed show. Times are wall-clock epoch millis so the state survives process death. */
data class AdShowRecord(
    val placementId: AdPlacementId,
    val format: AdFormat,
    val shownAtEpochMillis: Long,
)

/** Persisted cap state (TRD §4.11: caps are persisted, not per process). */
data class AdCapState(val records: List<AdShowRecord> = emptyList())

enum class AdCapBlockReason {
    GLOBAL_INTERVAL,
    GLOBAL_DAILY,
    PLACEMENT_INTERVAL,
    PLACEMENT_DAILY,
}

sealed interface AdCapDecision {
    data object Allowed : AdCapDecision

    data class Blocked(
        val reasons: Set<AdCapBlockReason>,
        /** Earliest time at which every blocking rule is satisfied, assuming nothing else is shown. */
        val allowedAtEpochMillis: Long,
    ) : AdCapDecision
}

/**
 * Pure global + per-placement frequency cap. No clock, storage or Android types: callers pass `now`
 * and the state, and persist what [recordShown] returns through an [AdCapStateStore].
 *
 * Clock changes: records stamped after `now` (the user moved the clock back) are ignored when
 * evaluating and dropped when recording, so a clock change can never block ads indefinitely.
 */
class AdCapPolicy(private val config: AdCapConfig = AdCapConfig()) {

    fun evaluate(placement: AdPlacement, state: AdCapState, nowEpochMillis: Long): AdCapDecision {
        val past = state.records.filter { it.shownAtEpochMillis <= nowEpochMillis }
        val blocks = mutableMapOf<AdCapBlockReason, Long>()

        if (placement.format in config.globalFormats) {
            val global = past.filter { it.format in config.globalFormats }
            intervalBlock(global, config.globalMinInterval, nowEpochMillis)
                ?.let { blocks[AdCapBlockReason.GLOBAL_INTERVAL] = it }
            dailyBlock(global, config.globalMaxPerDay, nowEpochMillis)
                ?.let { blocks[AdCapBlockReason.GLOBAL_DAILY] = it }
        }

        val own = past.filter { it.placementId == placement.id }
        placement.cap.minInterval?.let { interval ->
            intervalBlock(own, interval, nowEpochMillis)?.let { blocks[AdCapBlockReason.PLACEMENT_INTERVAL] = it }
        }
        placement.cap.maxPerDay?.let { max ->
            dailyBlock(own, max, nowEpochMillis)?.let { blocks[AdCapBlockReason.PLACEMENT_DAILY] = it }
        }

        return if (blocks.isEmpty()) {
            AdCapDecision.Allowed
        } else {
            AdCapDecision.Blocked(blocks.keys.toSet(), blocks.values.max())
        }
    }

    /**
     * Returns the state with this show appended. Records older than the 24 h window are pruned,
     * except the latest record of each placement (a placement interval may be longer than a day).
     */
    fun recordShown(placement: AdPlacement, state: AdCapState, nowEpochMillis: Long): AdCapState {
        val records = state.records.filter { it.shownAtEpochMillis <= nowEpochMillis } +
            AdShowRecord(placement.id, placement.format, nowEpochMillis)
        val windowStart = nowEpochMillis - DAY_MILLIS
        val latestPerPlacement = records.groupBy { it.placementId }
            .values
            .map { group -> group.maxBy { it.shownAtEpochMillis } }
            .toSet()
        val kept = records.filter { it.shownAtEpochMillis > windowStart || it in latestPerPlacement }
        return AdCapState(kept.sortedBy { it.shownAtEpochMillis })
    }

    /** Time the interval rule clears, or null when it is already satisfied. */
    private fun intervalBlock(records: List<AdShowRecord>, interval: Duration, now: Long): Long? {
        val last = records.maxOfOrNull { it.shownAtEpochMillis } ?: return null
        val allowedAt = last + interval.inWholeMilliseconds
        return allowedAt.takeIf { now < it }
    }

    /** Time enough records leave the rolling 24 h window, or null when under the limit. */
    private fun dailyBlock(records: List<AdShowRecord>, max: Int, now: Long): Long? {
        val inWindow = records.map { it.shownAtEpochMillis }.filter { now - it < DAY_MILLIS }.sorted()
        if (inWindow.size < max) return null
        return inWindow[inWindow.size - max] + DAY_MILLIS
    }

    private companion object {
        val DAY_MILLIS = 24.hours.inWholeMilliseconds
    }
}
