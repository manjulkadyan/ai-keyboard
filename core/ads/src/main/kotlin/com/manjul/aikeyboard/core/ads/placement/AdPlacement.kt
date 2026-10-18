/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.placement

import kotlin.time.Duration

/**
 * Stable id of an ad placement, e.g. `inter_feature_exit` (TRD §4.11, PRD FR-27). Used as the
 * analytics `placement` value and as the Remote Config key suffix for the ad unit id, so it is
 * restricted to lower-case snake case.
 */
@JvmInline
value class AdPlacementId(val value: String) {
    init {
        require(PATTERN.matches(value)) { "Invalid placement id '$value': expected lower_snake_case" }
    }

    override fun toString(): String = value

    private companion object {
        val PATTERN = Regex("[a-z][a-z0-9]*(_[a-z0-9]+)*")
    }
}

enum class AdFormat {
    APP_OPEN,
    INTERSTITIAL,
    /** User-initiated (opt-in value exchange). */
    REWARDED,
    BANNER,
    NATIVE,
}

/**
 * Per-placement frequency cap. `null` fields mean "no limit from this placement"; the global cap
 * still applies to formats that count towards it.
 */
data class FrequencyCap(
    /** Minimum time between two shows of this placement (e.g. 4 h for app open). */
    val minInterval: Duration? = null,
    /** Maximum shows of this placement in a rolling 24 h window. */
    val maxPerDay: Int? = null,
) {
    init {
        require(minInterval == null || minInterval.isPositive()) { "minInterval must be positive" }
        require(maxPerDay == null || maxPerDay > 0) { "maxPerDay must be > 0" }
    }

    companion object {
        val NONE = FrequencyCap()
    }
}

/**
 * One ad placement. The ad unit id is not part of this type: it comes from Remote Config with an
 * in-app default (TRD §4.11) and is resolved when ads are wired in Sprint 4.
 */
data class AdPlacement(
    val id: AdPlacementId,
    val format: AdFormat,
    val cap: FrequencyCap = FrequencyCap.NONE,
)
