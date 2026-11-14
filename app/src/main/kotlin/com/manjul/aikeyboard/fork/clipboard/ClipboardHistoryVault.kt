/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import android.util.Log
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardItem
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Deletes the whole clipboard history when its key is lost: every row, the media files, then the key alias, so
 * that the next write makes a new key. Logs one WARN without content. A wipe already running is not repeated.
 */
class ClipboardWipe(
    private val deleteRows: () -> Unit,
    private val deleteFiles: () -> Unit,
    private val keys: KeyProvider,
) {
    private val running = AtomicBoolean(false)

    fun wipe() {
        if (!running.compareAndSet(false, true)) return
        try {
            runCatching(deleteRows)
            runCatching(deleteFiles)
            runCatching(keys::delete)
            Log.w(TAG, "clipboard history key lost; history cleared")
        } finally {
            running.set(false)
        }
    }

    companion object {
        const val TAG = "AikbClipboard"
    }
}

/**
 * The seal and open steps around every `ClipboardHistoryDao` write and read in `ClipboardManager`. In-memory items
 * hold plain text, so upstream paste, pin and duplicate checks work unchanged.
 *
 * - Write: the text is sealed; with no key, null, and the caller stores nothing.
 * - Read: a row without the `e1:` prefix (written before S5, not yet migrated) passes through as plain text. A
 *   sealed row that does not open means the key is lost: [openAll] returns no items and starts [wipe] through
 *   [launchWipe] (the manager runs it on its IO scope, because reads arrive on the main thread).
 */
class ClipboardHistoryVault(
    private val cipher: ClipboardCipher,
    private val wipe: ClipboardWipe,
    private val launchWipe: (() -> Unit) -> Unit = { it() },
) {
    fun sealForWrite(item: ClipboardItem): ClipboardItem? {
        val text = item.text ?: return item
        return cipher.sealOrNull(text)?.let { item.copy(text = it) }
    }

    fun openAll(rows: List<ClipboardItem>): List<ClipboardItem> {
        val opened = ArrayList<ClipboardItem>(rows.size)
        for (row in rows) {
            val text = row.text
            if (text == null || !cipher.isSealed(text)) {
                opened += row
                continue
            }
            val plain = cipher.open(text)
            if (plain == null) {
                launchWipe(wipe::wipe)
                return emptyList()
            }
            opened += row.copy(text = plain)
        }
        return opened
    }
}
