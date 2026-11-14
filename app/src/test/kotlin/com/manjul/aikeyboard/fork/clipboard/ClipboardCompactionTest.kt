/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import android.app.Application
import androidx.room.Room
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardHistoryDatabase
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The plain text of pre-S5 rows must leave the database file, even when the first compaction fails (final I1).
 * SQLite on Android zeroes deleted cells (`secure_delete` is on), but the old pages stay in the main file until the
 * WAL is checkpointed. The compaction checkpoints, vacuums and checkpoints again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ClipboardCompactionTest {
    private val app = RuntimeEnvironment.getApplication()
    private val keys = TestKeys()
    private val cipher = ClipboardCipher(keys)
    private val markers = (1..5).map { "PLAINMARKER-$it-" + "q".repeat(200) }
    private var db = open()

    private fun open(): ClipboardHistoryDatabase =
        Room.databaseBuilder(app, ClipboardHistoryDatabase::class.java, DB_NAME).allowMainThreadQueries().build()

    private fun wipe(database: ClipboardHistoryDatabase) =
        ClipboardWipe(deleteRows = { database.clipboardItemDao().deleteAll() }, deleteFiles = {}, keys = keys)

    @After fun tearDown() {
        db.close()
        app.deleteDatabase(DB_NAME)
        mark.set(false)
    }

    private fun fileBytes(): String = listOf("", "-wal", "-journal")
        .map { File(app.getDatabasePath(DB_NAME).path + it) }
        .filter { it.isFile }
        .joinToString("") { String(it.readBytes(), Charsets.ISO_8859_1) }

    private val mark = CompactionMark.FileMark(File(app.noBackupFilesDir, "compaction-test.pending"))

    @Test fun `a compaction that fails at the first start runs again at the next start`() = runBlocking {
        markers.forEach { db.clipboardItemDao().insert(ClipboardItem.text(it)) }
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        assertTrue(markers.all { fileBytes().contains(it) }, "precondition: the plain text is in the file")

        val first = ClipboardEncryptionMigrator(
            db.clipboardItemDao(), cipher, wipe(db), Dispatchers.Unconfined, mark,
            compact = { throw IllegalStateException("database is locked") },
        )
        assertEquals(ClipboardEncryptionMigrator.Result.Sealed(5), first.migrate())
        assertTrue(markers.all { fileBytes().contains(it) }, "precondition: a failed compaction leaves the plain text")

        // Process restart: a new migrator. The connection stays open, as a process killed before a checkpoint
        // leaves the file (closing it would checkpoint the WAL and hide the missing compaction).
        var compactions = 0
        val second = ClipboardEncryptionMigrator(
            db.clipboardItemDao(), cipher, wipe(db), Dispatchers.Unconfined, mark,
            compact = { compactions++; compactClipboardDatabase(db.openHelper.writableDatabase) },
        )
        assertEquals(ClipboardEncryptionMigrator.Result.Sealed(0), second.migrate())
        assertEquals(1, compactions)
        val bytes = fileBytes()
        assertFalse(markers.any { bytes.contains(it) }, "plain clipboard text is still in the database file")
        assertEquals(markers.toSet(), db.clipboardItemDao().getAll().map { cipher.open(it.text!!) }.toSet())
        assertFalse(mark.isSet())

        // A third start owes nothing: no compaction runs.
        val third = ClipboardEncryptionMigrator(
            db.clipboardItemDao(), cipher, wipe(db), Dispatchers.Unconfined, mark, compact = { compactions++ },
        )
        assertEquals(ClipboardEncryptionMigrator.Result.Sealed(0), third.migrate())
        assertEquals(1, compactions)
    }

    @Test fun `with no key nothing is sealed and no compaction is owed`() = runBlocking {
        markers.forEach { db.clipboardItemDao().insert(ClipboardItem.text(it)) }
        keys.current = null
        var compactions = 0
        val migrator = ClipboardEncryptionMigrator(
            db.clipboardItemDao(), cipher, wipe(db), Dispatchers.Unconfined, mark, compact = { compactions++ },
        )
        assertEquals(ClipboardEncryptionMigrator.Result.NoKey, migrator.migrate())
        assertEquals(0, compactions)
        assertEquals(5, db.clipboardItemDao().getAll().size)
    }

    @Test fun `a key that goes away part way keeps the compaction owed and keeps every row`() = runBlocking {
        markers.forEach { db.clipboardItemDao().insert(ClipboardItem.text(it)) }
        val key = keys.current
        var seals = 0
        val flaky = object : KeyProvider by keys {
            override fun lookup(): KeyLookup = if (seals++ < 2) keys.lookup() else KeyLookup.Unavailable
        }
        val migrator = ClipboardEncryptionMigrator(
            db.clipboardItemDao(), ClipboardCipher(flaky), wipe(db), Dispatchers.Unconfined, mark, compact = {},
        )
        assertEquals(ClipboardEncryptionMigrator.Result.NoKey, migrator.migrate())
        assertTrue(mark.isSet())
        assertEquals(5, db.clipboardItemDao().getAll().size)
        keys.current = key
        var compactions = 0
        val next = ClipboardEncryptionMigrator(
            db.clipboardItemDao(), cipher, wipe(db), Dispatchers.Unconfined, mark, compact = { compactions++ },
        )
        assertEquals(ClipboardEncryptionMigrator.Result.Sealed(3), next.migrate())
        assertEquals(1, compactions)
        assertFalse(mark.isSet())
        assertEquals(markers.toSet(), db.clipboardItemDao().getAll().map { cipher.open(it.text!!) }.toSet())
    }

    private companion object {
        const val DB_NAME = "clipboard_compaction_test"
    }
}
