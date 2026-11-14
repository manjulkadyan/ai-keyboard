/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.room.Room
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.FlorisPreferenceModel
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.ime.clipboard.ClipboardHistory
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardHistoryDatabase
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardItem
import com.manjul.aikeyboard.ime.clipboard.provider.ItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The clipboard panel's history as `ClipboardManager` keeps it: every DAO write goes through
 * [ClipboardHistoryVault.sealForWrite] and every DAO read through [ClipboardHistoryVault.openAll], exactly as the
 * manager calls them. `ClipboardManager` itself needs `FlorisApplication` (native library), which Robolectric
 * cannot load, so the upstream behaviours are asserted on the same DAO queries, prefs and helpers it uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ClipboardPanelTest {
    private val app = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(app, ClipboardHistoryDatabase::class.java)
        .allowMainThreadQueries().build()
    private val dao = db.clipboardItemDao()
    private val keys = TestKeys()
    private val cipher = ClipboardCipher(keys)
    private var filesDeleted = 0
    private val wipe = ClipboardWipe(deleteRows = { dao.deleteAll() }, deleteFiles = { filesDeleted++ }, keys = keys)
    private val vault = ClipboardHistoryVault(cipher, wipe)
    private val prefs: FlorisPreferenceModel by FlorisPreferenceStore

    @Before fun setUp() {
        ShadowLog.clear()
    }

    @After fun tearDown() {
        db.close()
    }

    /** What `ClipboardManager.insertClip` does. */
    private fun write(item: ClipboardItem): ClipboardItem {
        val sealed = assertNotNull(vault.sealForWrite(item))
        item.id = dao.insert(sealed)
        return item
    }

    /** What the manager's `getAllLive` observer hands to `updateHistory`. */
    private fun history(): ClipboardHistory =
        ClipboardHistory(vault.openAll(dao.getAll()).sortedByDescending { it.creationTimestampMs })

    private fun textAt(text: String, ageMs: Long, pinned: Boolean = false) =
        ClipboardItem.text(text).copy(creationTimestampMs = System.currentTimeMillis() - ageMs, isPinned = pinned)

    @Test fun `history is off by default and the panel offers to turn it on`() {
        assertFalse(prefs.clipboard.historyEnabled.default)
        assertTrue(app.getString(R.string.clipboard__disabled__enable_button).isNotBlank())
    }

    @Test fun `a raw DAO read finds no plaintext after a write`() {
        write(ClipboardItem.text("my street address 12"))
        val raw = dao.getAll().single()
        assertTrue(cipher.isSealed(raw.text!!))
        assertFalse(raw.text!!.contains("street"))
        assertEquals("my street address 12", history().all.single().text)
    }

    @Test fun `in-memory items hold plain text, so the duplicate check works unchanged`() {
        write(ClipboardItem.text("dup"))
        val newItem = ClipboardItem.text("dup")
        assertNotNull(history().all.firstOrNull { it.type == ItemType.TEXT && it.text == newItem.text })
    }

    @Test fun `pin and unpin keep the text sealed`() {
        val item = write(ClipboardItem.text("pin me"))
        dao.update(assertNotNull(vault.sealForWrite(item.copy(isPinned = true))))
        assertTrue(history().pinned.single().text == "pin me")
        assertTrue(cipher.isSealed(dao.getAll().single().text!!))
        dao.update(assertNotNull(vault.sealForWrite(item.copy(isPinned = false))))
        assertEquals(listOf("pin me"), history().unpinned.map { it.text })
        assertTrue(cipher.isSealed(dao.getAll().single().text!!))
    }

    @Test fun `at most 20 unpinned items are kept`() {
        assertTrue(prefs.clipboard.historySizeLimitEnabled.default)
        assertEquals(20, prefs.clipboard.historySizeLimit.default)
        write(textAt("pinned", ageMs = 100_000L, pinned = true))
        repeat(25) { write(textAt("clip $it", ageMs = it * 1_000L)) }
        val h = history()
        val over = ClipboardCaptureGate.itemsOverLimit(h.recent + h.other, prefs.clipboard.historySizeLimit.default)
        dao.delete(over)
        val after = history()
        assertEquals(20, after.unpinned.size)
        assertEquals((0 until 20).map { "clip $it" }, after.unpinned.map { it.text })
        assertEquals(listOf("pinned"), after.pinned.map { it.text })
    }

    @Test fun `pinned items are kept past 24 h`() {
        assertTrue(prefs.clipboard.historyAutoCleanOldEnabled.default)
        val dayAndAnHour = 25 * 60 * 60 * 1000L
        write(textAt("old pinned", dayAndAnHour, pinned = true))
        write(textAt("old unpinned", dayAndAnHour))
        write(textAt("fresh", 1_000L))
        val afterMinutes = prefs.clipboard.historyAutoCleanOldAfter.default
        dao.deleteUnpinnedOlderThan(ClipboardCaptureGate.expiryCutoffMs(System.currentTimeMillis(), afterMinutes))
        assertEquals(setOf("old pinned", "fresh"), history().all.map { it.text }.toSet())
    }

    @Test fun `clear clipboard history removes unpinned items only`() {
        write(textAt("keep", 0L, pinned = true))
        write(textAt("drop 1", 0L))
        write(textAt("drop 2", 0L))
        dao.delete(history().unpinned) // the panel's "Clear history" (clearExactHistory)
        assertEquals(listOf("keep"), history().all.map { it.text })
        write(textAt("drop 3", 0L))
        dao.deleteAllUnpinned() // ClipboardManager.clearHistory
        assertEquals(listOf("keep"), history().all.map { it.text })
    }

    @Test fun `the recent clip suggestion sees plain text for 60 s`() {
        assertTrue(prefs.clipboard.suggestionEnabled.default)
        assertEquals(60, prefs.clipboard.suggestionTimeout.default)
        write(textAt("suggest me", 5_000L))
        val item = history().all.single()
        assertEquals("suggest me", item.text)
        val timeoutMs = prefs.clipboard.suggestionTimeout.default * 1000
        assertTrue(System.currentTimeMillis() - item.creationTimestampMs < timeoutMs)
    }

    @Test fun `a read that races the migrator passes plaintext rows through and wipes nothing`() = runBlocking {
        listOf("pre-S5 one", "pre-S5 two").forEach { dao.insert(ClipboardItem.text(it)) } // written before S5
        write(ClipboardItem.text("sealed already"))
        val beforeMigration = history()
        assertEquals(setOf("pre-S5 one", "pre-S5 two", "sealed already"), beforeMigration.all.map { it.text }.toSet())
        assertEquals(3, dao.getAll().size)
        assertEquals(0, keys.deletes)
        assertEquals(0, filesDeleted)
        ClipboardEncryptionMigrator(dao, cipher, wipe, Dispatchers.Unconfined).migrate()
        assertTrue(dao.getAll().all { cipher.isSealed(it.text!!) })
        assertEquals(beforeMigration.all.map { it.text }.toSet(), history().all.map { it.text }.toSet())
    }

    @Test fun `media rows without text pass through`() {
        val image = ClipboardItem(
            type = ItemType.IMAGE, text = null, uri = Uri.parse("content://x/clips/images/1"),
            creationTimestampMs = 0L, isPinned = false, mimeTypes = listOf("image/png"),
        )
        write(image)
        assertEquals(ItemType.IMAGE, history().all.single().type)
    }

    @Test fun `a sealed row that no longer opens wipes the history once, with a warning and no content`() {
        write(ClipboardItem.text("private note"))
        keys.current = TestKeys.newKey()
        assertEquals(emptyList(), history().all)
        assertEquals(emptyList(), dao.getAll())
        assertEquals(1, filesDeleted)
        assertEquals(1, keys.deletes)
        val logs = ShadowLog.getLogsForTag(ClipboardWipe.TAG)
        assertEquals(listOf(Log.WARN), logs.map { it.type })
        assertTrue(logs.none { it.msg.contains("private") || it.throwable != null })
    }

    @Test fun `with no key new clips are not stored and sealed rows are lost`() {
        write(ClipboardItem.text("before the key went away"))
        keys.current = null
        assertNull(vault.sealForWrite(ClipboardItem.text("not stored")))
        assertEquals(emptyList(), history().all)
        assertEquals(emptyList(), dao.getAll())
    }
}
