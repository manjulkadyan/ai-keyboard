/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import com.manjul.aikeyboard.core.suggest.LearnedWords
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * The on-device user dictionary (PRD FR-05, the `user_word` and `user_bigram` tables) as the suggestion engine's
 * [LearnedWords].
 *
 * The engine calls [boost], [bigram] and [isBlocked] on the typing path, so they answer from an in-memory snapshot
 * and never touch disk; [load] fills the snapshot for a language. [recordAccepted] and [block] update the snapshot at
 * once and write to the database in the background. All database work runs one operation at a time, in call order,
 * on a worker in [scope], so a [clearAll] always wins over the writes queued before it.
 *
 * Words are stored lowercased ([String.lowercase], as [LearnedWords] documents). They are user content: nothing here
 * logs them or puts them in an exception. Callers gate learning (PC-1): never call [recordAccepted] when the field
 * forbids learning or in a private session.
 */
class UserWordRepository(
    private val dao: UserWordDao,
    private val clock: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope,
) : LearnedWords {
    private class WordState(var count: Int, var lastUsedAt: Long, var blocked: Boolean)

    private class LocaleSnapshot {
        val words = HashMap<String, WordState>()
        val bigrams = HashMap<String, Int>()
    }

    private val lock = Any()
    private val snapshots = HashMap<String, LocaleSnapshot>()

    /** Bumped by every [clearAll]; guarded by [lock]. */
    private var clearGeneration = 0
    private val ops = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (op in ops) {
                try {
                    op()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A failed write loses one learned use; the exception is dropped because SQLite messages can
                    // quote the bound words (PC-10).
                }
            }
        }
    }

    /** Reads [locale]'s learned words into memory. Uses recorded meanwhile are kept; a [clearAll] meanwhile wins. */
    suspend fun load(locale: String) {
        // The generation and the read are taken together under the lock, as clearAll bumps it and queues its delete
        // under the lock: a read queued before a clear sees a changed generation and is dropped, not merged.
        val (generation, read) = synchronized(lock) {
            clearGeneration to send { dao.words(locale) to dao.bigrams(locale) }
        }
        val (words, bigrams) = read.await()
        synchronized(lock) {
            if (generation != clearGeneration) return
            val snap = snapshots.getOrPut(locale) { LocaleSnapshot() }
            for (row in words) {
                val state = snap.words[row.word]
                if (state == null) {
                    snap.words[row.word] = WordState(row.count, row.lastUsedAt, row.blocked)
                } else {
                    state.count = maxOf(state.count, row.count)
                    state.lastUsedAt = maxOf(state.lastUsedAt, row.lastUsedAt)
                    state.blocked = state.blocked || row.blocked
                }
            }
            for (row in bigrams) {
                val key = bigramKey(row.prev, row.word)
                snap.bigrams[key] = maxOf(snap.bigrams[key] ?: 0, row.count)
            }
            evictFromSnapshot(snap)
        }
    }

    /** Runs [block] on the worker after every operation queued before it, and returns its result. */
    private suspend fun <T> enqueue(block: suspend () -> T): T = send(block).await()

    /** Queues [block] on the worker without waiting; the result completes once it has run. Never suspends. */
    private fun <T> send(block: suspend () -> T): CompletableDeferred<T> {
        val result = CompletableDeferred<T>()
        ops.trySend {
            try {
                result.complete(block())
            } catch (e: Throwable) {
                result.completeExceptionally(e)
                if (e is CancellationException) throw e
            }
        }
        return result
    }

    /** Counts one accepted use of [word] in [locale], after [prev] when there was a previous word. Never blocks. */
    fun recordAccepted(locale: String, word: String, prev: String?) {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return
        val prevKey = prev?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val now = clock()
        synchronized(lock) {
            val snap = snapshots.getOrPut(locale) { LocaleSnapshot() }
            val state = snap.words.getOrPut(key) { WordState(0, now, blocked = false) }
            state.count += 1
            state.lastUsedAt = now
            if (prevKey != null) {
                val bk = bigramKey(prevKey, key)
                snap.bigrams[bk] = (snap.bigrams[bk] ?: 0) + 1
            }
            evictFromSnapshot(snap)
        }
        ops.trySend { dao.recordUse(locale, key, prevKey, now, MAX_WORDS_PER_LOCALE) }
    }

    /** The user removed [word] from suggestions in [locale]; it is never suggested again. Never blocks. */
    fun block(locale: String, word: String) {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return
        val now = clock()
        synchronized(lock) {
            val snap = snapshots.getOrPut(locale) { LocaleSnapshot() }
            snap.words.getOrPut(key) { WordState(0, now, blocked = true) }.blocked = true
        }
        ops.trySend { dao.block(locale, key, now) }
    }

    /** Deletes every learned word and bigram in every language, on disk and in memory. */
    suspend fun clearAll() {
        // Writes queued before this point are deleted by the clear; uses recorded after it are written after it,
        // so memory and disk agree.
        // A load whose read was queued before this delete must not merge what it read (see [load]).
        val cleared = synchronized(lock) {
            snapshots.clear()
            clearGeneration++
            send { dao.clearAll() }
        }
        cleared.await()
    }

    /** Learned words in all languages (blocked ones included), counted after every write queued before the call. */
    suspend fun count(): Int = enqueue { dao.count() }

    override fun boost(locale: String, word: String): Int {
        val state = synchronized(lock) { snapshots[locale]?.words?.get(word) } ?: return 0
        if (state.blocked) return 0
        return min(state.count, BOOST_MAX_USES) * BOOST_PER_USE
    }

    override fun bigram(locale: String, prev: String, word: String): Int =
        synchronized(lock) { snapshots[locale]?.bigrams?.get(bigramKey(prev, word)) } ?: 0

    override fun isBlocked(locale: String, word: String): Boolean =
        synchronized(lock) { snapshots[locale]?.words?.get(word)?.blocked } ?: false

    /** Same rule as [UserWordDao.recordUse]: least recently used first, ties by word. Caller holds [lock]. */
    private fun evictFromSnapshot(snap: LocaleSnapshot) {
        val excess = snap.words.size - MAX_WORDS_PER_LOCALE
        if (excess <= 0) return
        val order = compareBy<Map.Entry<String, WordState>> { it.value.lastUsedAt }.thenBy { it.key }
        // Usually one word over the cap: a linear scan, not a sort, on the typing path.
        val evicted = if (excess == 1) {
            hashSetOf(snap.words.entries.minWith(order).key)
        } else {
            snap.words.entries.sortedWith(order).take(excess).mapTo(HashSet()) { it.key }
        }
        snap.words.keys.removeAll(evicted)
        snap.bigrams.keys.removeAll { it.substringAfter(SEP) in evicted }
    }

    companion object {
        /** Learned words kept per language; above this the least recently used are evicted. */
        const val MAX_WORDS_PER_LOCALE = 5_000

        /** Uses that count towards [boost]. */
        const val BOOST_MAX_USES = 20

        /** [boost] per use, on the dictionary's 0–255 frequency scale. */
        const val BOOST_PER_USE = 6

        private const val SEP = '\u0000'

        private fun bigramKey(prev: String, word: String) = "$prev$SEP$word"
    }
}
