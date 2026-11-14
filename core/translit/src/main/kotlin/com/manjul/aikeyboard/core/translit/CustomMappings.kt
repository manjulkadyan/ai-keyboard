/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import java.util.Locale

/**
 * The user's own Roman to native mappings for one language, loaded by the app (from Room, in a later task). Keys are
 * lower-case. Reads are lock-free: every write swaps in a new map, so the IME thread never sees a half-done
 * [replaceAll]. Mappings are user content and never appear in [toString].
 */
class CustomMappings {
    @Volatile private var entries: Map<String, String> = emptyMap()

    fun put(roman: String, native: String) = update { it[key(roman)] = native }

    fun remove(roman: String) = update { it.remove(key(roman)) }

    fun replaceAll(entries: Map<String, String>) {
        val next = HashMap<String, String>(entries.size * 2)
        for ((roman, native) in entries) next[key(roman)] = native
        synchronized(this) { this.entries = next }
    }

    fun get(roman: String): String? = entries[key(roman)]

    private inline fun update(change: (HashMap<String, String>) -> Unit) {
        synchronized(this) { entries = HashMap(entries).also(change) }
    }

    private fun key(roman: String): String = roman.lowercase(Locale.ROOT)

    override fun toString(): String = "CustomMappings(size=${entries.size})"
}
