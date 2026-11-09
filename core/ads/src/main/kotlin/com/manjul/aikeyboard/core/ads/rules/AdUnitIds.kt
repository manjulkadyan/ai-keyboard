/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.rules

import com.manjul.aikeyboard.core.ads.placement.AdPlacementId
import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.config.RemoteConfig

/**
 * Ad unit ids from Remote Config (`ad_unit_<placement>`, TRD §4.11). No unit id is compiled in or kept in the repo.
 * An empty or blank value turns the placement off: [unitFor] returns null.
 */
class AdUnitIds(private val config: RemoteConfig) {
    fun unitFor(id: AdPlacementId): String? {
        val key = "ad_unit_${id.value}"
        if (key !in ConfigKeys.AD_UNITS) return null
        return runCatching { config.string(key) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
