/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardHistoryDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Seals every clipboard text row that has no `e1:` prefix (rows written before S5). `ClipboardManager` is
 * app-scoped, so [runOnce] on its instance runs once per process. With an unavailable key it stops and changes
 * nothing. Only a lost key (see [isPermanentKeyLoss]) wipes the history, once per key generation.
 * [compact] runs after rows were sealed, so that SQLite drops the
 * free pages that still hold the old plain text.
 */
class ClipboardEncryptionMigrator(
    private val dao: ClipboardHistoryDao,
    private val cipher: ClipboardCipher,
    private val wipe: ClipboardWipe,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val compact: () -> Unit = {},
) {
    sealed interface Result {
        data class Sealed(val rows: Int) : Result
        /** The key store is unavailable: every row stays as it is until the next process start. */
        data object NoKey : Result
        /** The key is lost. The history was wiped, unless a wipe for this key generation already ran. */
        data object Lost : Result
    }

    private val started = AtomicBoolean(false)

    /** Runs [migrate] the first time only. True if this call ran it. */
    suspend fun runOnce(): Boolean {
        if (!started.compareAndSet(false, true)) return false
        migrate()
        return true
    }

    suspend fun migrate(): Result = withContext(dispatcher) {
        val generation = wipe.currentGeneration
        val rows = dao.getAll()
        val (sealed, plain) = rows.filter { it.text != null }.partition { cipher.isSealed(it.text!!) }
        for (row in sealed) {
            when (cipher.tryOpen(row.text!!)) {
                is Opening.Opened -> Unit
                Opening.Unavailable -> return@withContext Result.NoKey
                Opening.Lost -> {
                    wipe.wipe(generation)
                    return@withContext Result.Lost
                }
            }
        }
        var count = 0
        for (row in plain) {
            val text = when (val sealing = cipher.trySeal(row.text!!)) {
                is Sealing.Done -> sealing.value
                Sealing.Unavailable -> return@withContext Result.NoKey
                Sealing.Lost -> {
                    wipe.wipe(generation)
                    return@withContext Result.Lost
                }
            }
            if (dao.replaceText(row.id, row.text, text) > 0) count++
        }
        if (count > 0) runCatching(compact)
        Result.Sealed(count)
    }
}
