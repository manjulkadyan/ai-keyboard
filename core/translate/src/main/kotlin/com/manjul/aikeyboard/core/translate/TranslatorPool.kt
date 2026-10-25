/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps at most [maxLoaded] engine handles (ML Kit `Translator`s) open, keyed by [K] (usually a [LangPair])
 * (TRD §6.4, §11.5 #5.4). Opening a new key closes the least recently used one; a handle closes [idleMs] after
 * its last [use] ends, or at once on [onTrimMemory] at `TRIM_MEMORY_RUNNING_LOW` or above. A handle that is
 * in use is never closed under its user: it is retired and closed when the last user finishes.
 *
 * Holds no text; whatever [use]'s block translates is user content and is not seen here.
 */
class TranslatorPool<K : Any, H : AutoCloseable>(
    private val maxLoaded: Int = 1,
    private val idleMs: Long = DEFAULT_IDLE_MS,
    private val scope: CoroutineScope,
    private val open: suspend (K) -> H,
) {
    init {
        require(maxLoaded > 0) { "maxLoaded must be positive" }
    }

    private class Entry<H>(val handle: H) {
        var users = 0
        var retired = false
        var idleJob: Job? = null
    }

    private val lock = Any()

    /** Serialises opening, so two concurrent first uses of a key open it once. */
    private val openMutex = Mutex()

    /** Access-ordered: the first entry is the least recently used. Guarded by [lock]. */
    private val loaded = LinkedHashMap<K, Entry<H>>(4, 0.75f, true)

    /** Keys with an open handle, least recently used first. For tests and diagnostics. */
    val openKeys: List<K> get() = synchronized(lock) { loaded.keys.toList() }

    suspend fun <R> use(key: K, block: suspend (H) -> R): R {
        val entry = acquire(key)
        try {
            return block(entry.handle)
        } finally {
            release(key, entry)
        }
    }

    /** Closes every idle handle now when [level] is `TRIM_MEMORY_RUNNING_LOW` (10) or above. */
    fun onTrimMemory(level: Int) {
        if (level >= TRIM_MEMORY_RUNNING_LOW) closeAll()
    }

    /** Closes every handle (in-use ones when their user finishes). Idempotent. */
    fun closeAll() {
        synchronized(lock) {
            loaded.values.toList().forEach(::retire)
            loaded.clear()
        }
    }

    private suspend fun acquire(key: K): Entry<H> = openMutex.withLock {
        synchronized(lock) {
            loaded[key]?.let { existing ->
                existing.users++
                existing.idleJob?.cancel()
                existing.idleJob = null
                return@withLock existing
            }
            // Close before opening so two models are never loaded at once.
            while (loaded.size >= maxLoaded) {
                val eldest = loaded.entries.first()
                loaded.remove(eldest.key)
                retire(eldest.value)
            }
        }
        val entry = Entry(open(key)).apply { users = 1 }
        synchronized(lock) { loaded[key] = entry }
        entry
    }

    private fun release(key: K, entry: Entry<H>) {
        synchronized(lock) {
            entry.users--
            if (entry.users > 0) return
            if (entry.retired) {
                close(entry)
                return
            }
            entry.idleJob = scope.launch {
                delay(idleMs)
                synchronized(lock) {
                    if (entry.users == 0 && loaded[key] === entry) {
                        loaded.remove(key)
                        retire(entry)
                    }
                }
            }
        }
    }

    /** Caller holds [lock] and has removed [entry] from [loaded]. */
    private fun retire(entry: Entry<H>) {
        if (entry.retired) return
        entry.retired = true
        entry.idleJob?.cancel()
        entry.idleJob = null
        if (entry.users == 0) close(entry)
    }

    private fun close(entry: Entry<H>) {
        // A failing close must not break the caller; nothing is logged (the error could carry engine state).
        runCatching { entry.handle.close() }
    }

    companion object {
        const val DEFAULT_IDLE_MS = 60_000L

        /** `ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW`, mirrored so the module stays pure JVM. */
        const val TRIM_MEMORY_RUNNING_LOW = 10
    }
}
