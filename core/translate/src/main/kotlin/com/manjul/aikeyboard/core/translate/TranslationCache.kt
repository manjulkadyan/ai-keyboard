/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

/**
 * Cache key. [text] is user content, never logged; [toString] omits it.
 */
data class CacheKey(val engine: EngineId, val source: Lang, val target: Lang, val text: String) {
    override fun toString(): String = "CacheKey(engine=$engine, source=$source, target=$target, chars=${text.length})"
}

/**
 * In-memory, access-ordered LRU of finished translations (TRD §4.3.1), at most [maxEntries]. Keys and values
 * are user content: never persisted, never logged, dropped with [clear] when a field turns sensitive.
 * Thread-safe.
 */
class TranslationCache(private val maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val map = object : LinkedHashMap<CacheKey, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, String>?): Boolean = size > maxEntries
    }

    fun get(key: CacheKey): String? = synchronized(map) { map[key] }

    fun put(key: CacheKey, value: String) {
        synchronized(map) { map[key] = value }
    }

    fun clear() {
        synchronized(map) { map.clear() }
    }

    val size: Int get() = synchronized(map) { map.size }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 200
    }
}
