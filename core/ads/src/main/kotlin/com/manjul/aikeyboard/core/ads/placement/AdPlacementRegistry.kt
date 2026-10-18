/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.placement

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
 * The app's placements. **Empty in Sprint 0.** Sprint 4 adds `app_open`, `inter_feature_exit`,
 * `banner_home`, `native_translator`, `native_history`, `native_dictionary` and
 * `rewarded_game_hint` (TRD §4.11, PRD FR-27). There is deliberately no `rewarded_ai_credit`.
 */
object AdPlacements {
    val registry: AdPlacementRegistry = AdPlacementRegistry.EMPTY
}
