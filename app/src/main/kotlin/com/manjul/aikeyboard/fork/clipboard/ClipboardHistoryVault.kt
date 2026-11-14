/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import android.util.Log
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardItem

/**
 * Deletes the whole clipboard history when its key is lost: every row, the media files, then the key alias, so
 * that the next write makes a new key. Logs one WARN without content.
 *
 * A wipe happens at most once per key generation. A caller passes the [currentGeneration] it saw before it read
 * the rows. If a wipe ran since then, those rows were sealed with the old key, so the call does nothing. That keeps
 * a racing panel read and the migrator to one wipe, and the new key and new clips survive.
 */
class ClipboardWipe(
    private val deleteRows: () -> Unit,
    private val deleteFiles: () -> Unit,
    private val keys: KeyProvider,
) {
    @Volatile
    var currentGeneration: Int = 0
        private set

    /** True if this call wiped. */
    @Synchronized
    fun wipe(seenGeneration: Int): Boolean {
        if (seenGeneration != currentGeneration) return false
        runCatching(deleteRows)
        runCatching(deleteFiles)
        runCatching(keys::delete)
        currentGeneration = seenGeneration + 1
        Log.w(TAG, "clipboard history key lost; history cleared")
        return true
    }

    companion object {
        const val TAG = "AikbClipboard"
    }
}

/**
 * The seal and open steps around every `ClipboardHistoryDao` write and read in `ClipboardManager`. In-memory items
 * hold plain text, so upstream paste, pin and duplicate checks work unchanged.
 *
 * - Write: the text is sealed. With no key, the result is null and the caller stores nothing.
 * - Read: a row without the `e1:` prefix (written before S5, not yet migrated) passes through as plain text.
 * - Read with an unavailable key: sealed rows are hidden for this read and nothing is deleted.
 * - Read with a lost key (see [isPermanentKeyLoss]): [openAll] returns no items and starts [ClipboardWipe.wipe]
 *   through [launchWipe]. The manager runs it on its IO scope, because reads arrive on the main thread.
 */
class ClipboardHistoryVault(
    private val cipher: ClipboardCipher,
    private val wipe: ClipboardWipe,
    private val launchWipe: (() -> Unit) -> Unit = { it() },
) {
    fun sealForWrite(item: ClipboardItem): ClipboardItem? {
        val text = item.text ?: return item
        return when (val sealing = cipher.trySeal(text)) {
            is Sealing.Done -> item.copy(text = sealing.value)
            Sealing.Unavailable -> null
            Sealing.Lost -> {
                val generation = wipe.currentGeneration
                launchWipe { wipe.wipe(generation) }
                null
            }
        }
    }

    /** [generation] is [ClipboardWipe.currentGeneration] as it was when [rows] were read. */
    fun openAll(rows: List<ClipboardItem>, generation: Int = wipe.currentGeneration): List<ClipboardItem> {
        val opened = ArrayList<ClipboardItem>(rows.size)
        for (row in rows) {
            val text = row.text
            if (text == null || !cipher.isSealed(text)) {
                opened += row
                continue
            }
            when (val opening = cipher.tryOpen(text)) {
                is Opening.Opened -> opened += row.copy(text = opening.text)
                Opening.Unavailable -> Unit
                Opening.Lost -> {
                    launchWipe { wipe.wipe(generation) }
                    return emptyList()
                }
            }
        }
        return opened
    }
}
