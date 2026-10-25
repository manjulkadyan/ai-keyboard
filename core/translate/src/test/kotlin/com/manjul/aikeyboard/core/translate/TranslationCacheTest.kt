/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TranslationCacheTest {
    private fun key(i: Int, engine: EngineId = EngineId.MLKIT) = CacheKey(engine, Lang("en"), Lang("es"), "text $i")

    @Test fun entry201EvictsTheLeastRecentlyUsed() {
        val cache = TranslationCache()
        repeat(200) { cache.put(key(it), "v$it") }
        // Touch the oldest so the second-oldest becomes least recently used.
        assertEquals("v0", cache.get(key(0)))
        cache.put(key(200), "v200")
        assertEquals(200, cache.size)
        assertEquals("v0", cache.get(key(0)))
        assertNull(cache.get(key(1)))
        assertEquals("v200", cache.get(key(200)))
    }

    @Test fun clearEmptiesTheCache() {
        val cache = TranslationCache()
        cache.put(key(1), "a")
        cache.clear()
        assertNull(cache.get(key(1)))
        assertEquals(0, cache.size)
    }

    @Test fun keysDifferByEngine() {
        val cache = TranslationCache()
        cache.put(key(1, EngineId.MLKIT), "device")
        cache.put(key(1, EngineId.CLOUD), "cloud")
        assertEquals("device", cache.get(key(1, EngineId.MLKIT)))
        assertEquals("cloud", cache.get(key(1, EngineId.CLOUD)))
    }

    @Test fun keyToStringHasNoText() {
        assertFalse("secret" in CacheKey(EngineId.MLKIT, Lang("en"), Lang("es"), "secret").toString())
    }
}
