/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.firebase

import com.manjul.aikeyboard.core.config.ConfigCatalog

/** Computes the next IME mirror from the activated Remote Config (decision 25). Pure, so it is unit tested. */
object RcMirrorWriter {
    private val catalogNames: Set<String> = ConfigCatalog.all.mapTo(HashSet()) { it.name }

    /**
     * With [updatedKeys] null (cold start) the result is every catalog key present in [activated]; keys outside
     * the catalog are dropped. With a live update, only keys in `updatedKeys ∩ killSwitches` change, and every
     * other key keeps its value from [current] until the next cold start.
     */
    fun next(
        current: Map<String, String>,
        activated: Map<String, String>,
        updatedKeys: Set<String>?,
    ): Map<String, String> {
        if (updatedKeys == null) return activated.filterKeys { it in catalogNames }
        val result = LinkedHashMap(current)
        for (key in updatedKeys) {
            if (key !in ConfigCatalog.killSwitches) continue
            val value = activated[key]
            if (value == null) result.remove(key) else result[key] = value
        }
        return result
    }
}
