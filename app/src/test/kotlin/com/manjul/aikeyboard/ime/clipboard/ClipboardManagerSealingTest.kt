/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.clipboard

import android.app.Application
import android.content.ClipData
import android.os.Looper
import androidx.room.Room
import com.manjul.aikeyboard.app.FlorisPreferenceModel
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.fork.clipboard.ClipboardCaptureGate
import com.manjul.aikeyboard.fork.clipboard.ClipboardCipher
import com.manjul.aikeyboard.fork.clipboard.TestKeys
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardHistoryDatabase
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardItem
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The real upstream [ClipboardManager] (final review I2): each write path stores sealed text (`e1:`) in the
 * `clipboard_history` DB, and its history flow reads it back as plain text. Pinning and unpinning rewrite the row
 * sealed. A future write path that skips `sealForWrite` fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ClipboardManagerSealingTest {
    private val app = RuntimeEnvironment.getApplication()
    private val keys = TestKeys()
    private val cipher = ClipboardCipher(keys)
    private val prefs: FlorisPreferenceModel by FlorisPreferenceStore
    private lateinit var manager: ClipboardManager

    @Before fun setUp() {
        runBlocking { prefs.clipboard.historyEnabled.set(true) }
        manager = ClipboardManager(app, ClipboardCaptureGate { PolicyDecision.Unrestricted }, keys)
        manager.initializeForContext(app)
        // The manager opens its DB on its IO scope and drops writes until then (upstream). The migrator's first
        // query creates the file, after the DB is set.
        await("the manager to open its DB") { app.getDatabasePath(DB_NAME).isFile }
    }

    @After fun tearDown() {
        manager.close()
        runBlocking { prefs.clipboard.historyEnabled.reset() }
        app.deleteDatabase(DB_NAME)
    }

    private fun rawRows(): List<ClipboardItem> {
        val db = Room.databaseBuilder(app, ClipboardHistoryDatabase::class.java, DB_NAME)
            .allowMainThreadQueries().build()
        try {
            return db.clipboardItemDao().getAll()
        } finally {
            db.close()
        }
    }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for $what")
            Thread.sleep(20)
        }
    }

    private fun historyTexts(): Set<String?> = manager.currentHistory.all.map { it.text }.toSet()

    @Test fun `a new clip is stored sealed and reads back as plain text`() {
        manager.addNewPlaintext(FIRST)
        await("the first clip in the DB") { rawRows().size == 1 }
        val row = rawRows().single()
        assertTrue(cipher.isSealed(row.text!!), "stored text is not sealed")
        assertTrue(!row.text!!.contains(FIRST))
        assertEquals(FIRST, cipher.open(row.text!!))
        await("the history to show the clip") { historyTexts() == setOf(FIRST) }
    }

    @Test fun `a clip from the system clipboard is stored sealed`() {
        val system = app.getSystemService(android.content.ClipboardManager::class.java)
        system.setPrimaryClip(ClipData.newPlainText("label", FIRST))
        await("the system clip in the DB") { rawRows().size == 1 }
        assertTrue(rawRows().all { cipher.isSealed(it.text!!) })
        await("the history to show the clip") { historyTexts() == setOf(FIRST) }
    }

    @Test fun `copying a clip again, pinning and unpinning keep every row sealed`() {
        manager.addNewPlaintext(FIRST)
        await("the first clip in the history") { historyTexts() == setOf(FIRST) }
        manager.addNewPlaintext(SECOND)
        await("both clips in the history") { historyTexts() == setOf(FIRST, SECOND) }
        val oldIds = rawRows().map { it.id }.toSet()
        manager.addNewPlaintext(FIRST) // moveToTheBeginning: delete the old row, insert a new one
        await("the moved clip in the history") {
            val ids = manager.currentHistory.all.map { it.id }
            ids.size == 2 && ids.any { it !in oldIds }
        }
        assertTrue(rawRows().all { cipher.isSealed(it.text!!) }, "a moved row is not sealed")

        val first = manager.currentHistory.all.first { it.text == FIRST }
        manager.pinClip(first)
        await("the pin") { rawRows().any { it.isPinned } }
        assertTrue(rawRows().all { cipher.isSealed(it.text!!) }, "a pinned row is not sealed")
        await("the pinned clip in the history") { manager.currentHistory.pinned.map { it.text } == listOf(FIRST) }

        manager.unpinClip(manager.currentHistory.pinned.single())
        await("the unpin") { rawRows().none { it.isPinned } }
        val rows = rawRows()
        assertEquals(2, rows.size)
        assertTrue(rows.all { cipher.isSealed(it.text!!) }, "an unpinned row is not sealed")
        assertEquals(setOf(FIRST, SECOND), rows.map { cipher.open(it.text!!) }.toSet())
        await("the history after the unpin") { historyTexts() == setOf(FIRST, SECOND) }
    }

    private companion object {
        const val FIRST = "first copied text"
        const val SECOND = "second copied text"
        const val DB_NAME = "clipboard_history" // ClipboardHistoryDatabase.new
    }
}
