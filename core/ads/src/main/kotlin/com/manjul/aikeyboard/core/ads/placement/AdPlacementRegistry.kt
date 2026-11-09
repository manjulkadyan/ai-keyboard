/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.placement

import com.manjul.aikeyboard.core.ads.cap.AdCapConfig
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The closed set of placements the app may show. Code asks the registry for a placement by id;
 * an id that is not registered cannot be shown.
 */
class AdPlacementRegistry(placements: Collection<AdPlacement>) {
    private val byId: Map<AdPlacementId, AdPlacement>

    init {
        val duplicates = placements.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate ad placement ids: ${duplicates.joinToString()}" }
        byId = placements.associateBy { it.id }
    }

    /** Registration order is kept. */
    val all: List<AdPlacement> = byId.values.toList()

    operator fun get(id: AdPlacementId): AdPlacement? = byId[id]

    operator fun contains(id: AdPlacementId): Boolean = id in byId

    fun require(id: AdPlacementId): AdPlacement =
        byId[id] ?: throw IllegalArgumentException("Ad placement '$id' is not registered")

    companion object {
        val EMPTY = AdPlacementRegistry(emptyList())
    }
}

/**
 * The app's placements (S4 Task 7, TRD §4.11, PRD FR-27). There is deliberately no `rewarded_ai_credit`.
 * `native_dictionary` and `rewarded_game_hint` have no UI yet. The global cap of [AdCapConfig] (3 min and 6 a day
 * across interstitial and app-open) applies on top of the caps here.
 */
object AdPlacements {
    val APP_OPEN = AdPlacementId("app_open")
    val INTER_FEATURE_EXIT = AdPlacementId("inter_feature_exit")
    val BANNER_HOME = AdPlacementId("banner_home")
    val NATIVE_TRANSLATOR = AdPlacementId("native_translator")
    val NATIVE_HISTORY = AdPlacementId("native_history")
    val NATIVE_DICTIONARY = AdPlacementId("native_dictionary")
    val REWARDED_GAME_HINT = AdPlacementId("rewarded_game_hint")

    val registry: AdPlacementRegistry = AdPlacementRegistry(
        listOf(
            AdPlacement(APP_OPEN, AdFormat.APP_OPEN, FrequencyCap(minInterval = 4.hours)),
            AdPlacement(INTER_FEATURE_EXIT, AdFormat.INTERSTITIAL, FrequencyCap(minInterval = 3.minutes, maxPerDay = 6)),
            AdPlacement(BANNER_HOME, AdFormat.BANNER),
            AdPlacement(NATIVE_TRANSLATOR, AdFormat.NATIVE),
            AdPlacement(NATIVE_HISTORY, AdFormat.NATIVE),
            AdPlacement(NATIVE_DICTIONARY, AdFormat.NATIVE),
            AdPlacement(REWARDED_GAME_HINT, AdFormat.REWARDED),
        ),
    )
}
