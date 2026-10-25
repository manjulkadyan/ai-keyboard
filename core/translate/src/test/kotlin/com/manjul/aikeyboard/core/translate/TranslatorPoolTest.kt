/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TranslatorPoolTest {
    private class Handle(val key: String) : AutoCloseable {
        var closes = 0
        val closed get() = closes > 0

        override fun close() {
            closes++
        }
    }

    private class Rig(scope: TestScope) {
        val opened = mutableListOf<Handle>()
        val pool = TranslatorPool<String, Handle>(scope = scope.backgroundScope) { key -> Handle(key).also { opened += it } }

        fun handle(key: String) = opened.last { it.key == key }
    }

    @Test fun openingASecondKeyClosesTheFirst() = runTest {
        val r = Rig(this)
        r.pool.use("A") {}
        r.pool.use("B") {}
        assertTrue(r.handle("A").closed)
        assertFalse(r.handle("B").closed)
        assertEquals(listOf("B"), r.pool.openKeys)
    }

    @Test fun reusingAKeyDoesNotReopen() = runTest {
        val r = Rig(this)
        r.pool.use("A") {}
        r.pool.use("A") {}
        assertEquals(1, r.opened.size)
    }

    @Test fun idleHandleClosesAfter60Seconds() = runTest {
        val r = Rig(this)
        r.pool.use("A") {}
        advanceTimeBy(59_000)
        runCurrent()
        assertFalse(r.handle("A").closed)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(r.handle("A").closed)
        assertEquals(emptyList(), r.pool.openKeys)
    }

    @Test fun everyUseRestartsTheIdleTimer() = runTest {
        val r = Rig(this)
        r.pool.use("A") {}
        advanceTimeBy(30_000)
        r.pool.use("A") {}
        advanceTimeBy(59_000)
        runCurrent()
        assertFalse(r.handle("A").closed)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(r.handle("A").closed)
    }

    @Test fun trimAtRunningLowClosesButLowerLevelsDoNot() = runTest {
        val r = Rig(this)
        r.pool.use("A") {}
        r.pool.onTrimMemory(5)
        assertFalse(r.handle("A").closed)
        r.pool.onTrimMemory(TranslatorPool.TRIM_MEMORY_RUNNING_LOW)
        assertTrue(r.handle("A").closed)
    }

    @Test fun closeAllIsIdempotent() = runTest {
        val r = Rig(this)
        r.pool.use("A") {}
        r.pool.closeAll()
        r.pool.closeAll()
        assertEquals(1, r.handle("A").closes)
        advanceTimeBy(120_000)
        assertEquals(1, r.handle("A").closes)
    }

    @Test fun handleInUseIsNotClosedByIdleTimerOrTrim() = runTest {
        val r = Rig(this)
        val job = launch { r.pool.use("A") { delay(120_000) } }
        advanceTimeBy(100_000)
        runCurrent()
        assertFalse(r.handle("A").closed)
        r.pool.onTrimMemory(15)
        assertFalse(r.handle("A").closed)
        advanceTimeBy(20_001)
        job.join()
        assertEquals(1, r.handle("A").closes)
    }

    @Test fun inUseHandleEvictedByAnotherKeyClosesWhenItsUserFinishes() = runTest {
        val r = Rig(this)
        val job = launch { r.pool.use("A") { delay(1_000) } }
        runCurrent()
        r.pool.use("B") {}
        assertFalse(r.handle("A").closed)
        job.join()
        assertTrue(r.handle("A").closed)
        assertFalse(r.handle("B").closed)
    }
}
