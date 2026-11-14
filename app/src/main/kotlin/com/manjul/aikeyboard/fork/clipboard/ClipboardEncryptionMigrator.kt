/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import androidx.sqlite.db.SupportSQLiteDatabase
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardHistoryDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Seals every clipboard text row that has no `e1:` prefix (rows written before S5). `ClipboardManager` is
 * app-scoped, so [runOnce] on its instance runs once per process. With an unavailable key it stops and changes
 * nothing. Only a lost key (see [isPermanentKeyLoss]) wipes the history, once per key generation.
 *
 * [compact] runs after rows were sealed, so that SQLite drops the free pages and WAL frames that still hold the old
 * plain text. [compact] throws when it did not finish (a busy database, a checkpoint that could not complete). The
 * need is kept in [pendingCompaction], which survives a process restart: it is set before the first row is sealed and
 * cleared only after [compact] returns. Each later start retries [compact] while the mark is set. Compaction never
 * deletes a row.
 */
class ClipboardEncryptionMigrator(
    private val dao: ClipboardHistoryDao,
    private val cipher: ClipboardCipher,
    private val wipe: ClipboardWipe,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pendingCompaction: CompactionMark = CompactionMark.InMemory(),
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
        if (plain.isNotEmpty()) runCatching { pendingCompaction.set(true) }
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
        if (count > 0 || runCatching { pendingCompaction.isSet() }.getOrDefault(false)) {
            if (runCatching(compact).isSuccess) runCatching { pendingCompaction.set(false) }
        }
        Result.Sealed(count)
    }
}

/** Whether a compaction of the clipboard history database is still owed. Holds no clipboard content. */
interface CompactionMark {
    fun isSet(): Boolean
    fun set(pending: Boolean)

    /** For tests and callers that do not need the mark to survive a restart. */
    class InMemory(private var pending: Boolean = false) : CompactionMark {
        override fun isSet(): Boolean = pending
        override fun set(pending: Boolean) {
            this.pending = pending
        }
    }

    /** An empty marker file: present means pending. Put it in `noBackupFilesDir`. */
    class FileMark(private val file: File) : CompactionMark {
        override fun isSet(): Boolean = file.isFile

        override fun set(pending: Boolean) {
            if (pending) {
                file.parentFile?.mkdirs()
                if (!file.isFile) file.createNewFile()
            } else if (file.exists()) {
                check(file.delete()) { "Cannot clear the compaction mark" }
            }
        }
    }
}

/**
 * Checkpoints the WAL, runs `VACUUM`, then checkpoints again, so that freed pages and old WAL frames no longer hold
 * the plain text the migration replaced. Throws if a checkpoint stayed busy (a reader held the database) or `VACUUM`
 * failed, so that [ClipboardEncryptionMigrator] keeps the compaction pending. The message holds no content.
 */
fun compactClipboardDatabase(sql: SupportSQLiteDatabase) {
    checkpoint(sql)
    sql.execSQL("VACUUM")
    checkpoint(sql)
}

private fun checkpoint(sql: SupportSQLiteDatabase) {
    sql.query("PRAGMA wal_checkpoint(TRUNCATE)").use { c ->
        // One row: busy (0 or 1), WAL frames, frames checkpointed. Not in WAL mode: -1 for the counts.
        check(!c.moveToFirst() || c.getInt(0) == 0) { "Clipboard database checkpoint was busy" }
    }
}
