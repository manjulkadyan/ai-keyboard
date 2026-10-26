/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class AiLruTest {
    private fun key(i: Int) = AiLru.Key.of(mode = "fix", target = null, locale = "en", text = "text $i")

    @Test
    fun `the 33rd entry evicts the oldest`() {
        val lru = AiLru()
        for (i in 1..32) lru.put(key(i), "out $i")
        assertEquals(32, lru.size)
        lru.put(key(33), "out 33")
        assertEquals(32, lru.size)
        assertNull(lru.get(key(1)))
        assertEquals("out 2", lru.get(key(2)))
        assertEquals("out 33", lru.get(key(33)))
    }

    @Test
    fun `a read makes an entry recent`() {
        val lru = AiLru(capacity = 2)
        lru.put(key(1), "a")
        lru.put(key(2), "b")
        lru.get(key(1))
        lru.put(key(3), "c")
        assertEquals("a", lru.get(key(1)))
        assertNull(lru.get(key(2)))
    }

    @Test
    fun `clear empties it`() {
        val lru = AiLru()
        lru.put(key(1), "a")
        lru.clear()
        assertEquals(0, lru.size)
        assertNull(lru.get(key(1)))
    }

    @Test
    fun `the key is the mode, target, locale and text hash`() {
        val base = AiLru.Key.of("fix", null, "en", "hello")
        assertEquals(base, AiLru.Key.of("fix", null, "en", "hello"))
        assertNotEquals(base, AiLru.Key.of("formal", null, "en", "hello"))
        assertNotEquals(base, AiLru.Key.of("fix", "es", "en", "hello"))
        assertNotEquals(base, AiLru.Key.of("fix", null, "fr", "hello"))
        assertNotEquals(base, AiLru.Key.of("fix", null, "en", "hello!"))
        assertEquals(64, base.textSha256.length)
        assertFalse(base.toString().contains("hello"))
    }

    @Test
    fun `keys for requests`() {
        val rewrite = AiRequest.Rewrite(RewriteMode.TRANSLATE, "en", "hello", "es", "r1")
        assertEquals(AiLru.Key.of("translate", "es", "en", "hello"), AiLru.Key.of(rewrite))
        val polishA = AiRequest.Polish("hello", null, "en", OutputScript.MATCH_INPUT, "p1")
        val polishB = AiRequest.Polish("hello", null, "en", OutputScript.ROMAN, "p2")
        assertNotEquals(AiLru.Key.of(polishA), AiLru.Key.of(polishB))
        // client_req_id is not part of the key: a retry of the same text hits the cache
        assertEquals(AiLru.Key.of(polishA), AiLru.Key.of(polishA.copy(clientReqId = "p3")))
    }
}
