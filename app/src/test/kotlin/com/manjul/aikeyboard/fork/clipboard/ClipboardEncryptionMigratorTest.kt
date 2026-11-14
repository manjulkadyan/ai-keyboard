/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import android.app.Application
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Log
import androidx.room.Room
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardHistoryDao
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardHistoryDatabase
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardItem
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
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ClipboardEncryptionMigratorTest {
    private val db = Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ClipboardHistoryDatabase::class.java)
        .allowMainThreadQueries().build()
    private val dao = db.clipboardItemDao()
    private val keys = TestKeys()
    private val cipher = ClipboardCipher(keys)
    private var filesDeleted = 0
    private val wipe = ClipboardWipe(deleteRows = { dao.deleteAll() }, deleteFiles = { filesDeleted++ }, keys = keys)
    private val migrator = ClipboardEncryptionMigrator(dao, cipher, wipe, Dispatchers.Unconfined)

    private val texts = listOf("first clip", "second clip, a bit longer", "third éè clip")

    @Before fun setUp() {
        ShadowLog.clear()
        texts.forEach { dao.insert(ClipboardItem.text(it)) }
    }

    @After fun tearDown() {
        db.close()
    }

    @Test fun `three plaintext rows become sealed and still open to the same text`() = runBlocking {
        val result = migrator.migrate()
        assertEquals(ClipboardEncryptionMigrator.Result.Sealed(3), result)
        val rows = dao.getAll()
        assertEquals(3, rows.size)
        rows.forEach { row ->
            assertTrue(cipher.isSealed(row.text!!))
            assertFalse(texts.any { row.text!!.contains(it) })
        }
        assertEquals(texts.toSet(), rows.map { cipher.open(it.text!!) }.toSet())
    }

    @Test fun `a second run changes nothing`() = runBlocking {
        migrator.migrate()
        val first = dao.getAll()
        assertEquals(ClipboardEncryptionMigrator.Result.Sealed(0), migrator.migrate())
        assertEquals(first, dao.getAll())
    }

    @Test fun `runOnce runs once per instance`() = runBlocking {
        assertTrue(migrator.runOnce())
        dao.insert(ClipboardItem.text("late plaintext row"))
        assertFalse(migrator.runOnce())
        assertEquals(1, dao.getAll().count { !cipher.isSealed(it.text!!) })
    }

    @Test fun `pinned state and timestamps survive the migration`() = runBlocking {
        val pinned = dao.getAll().first()
        dao.update(pinned.copy(isPinned = true))
        migrator.migrate()
        val after = dao.getAll().first { it.id == pinned.id }
        assertTrue(after.isPinned)
        assertEquals(pinned.creationTimestampMs, after.creationTimestampMs)
    }

    @Test fun `a lost key deletes all rows, the files and the key, with one warning and no content`() = runBlocking {
        migrator.migrate()
        val lostKey = keys.current
        keys.current = TestKeys.newKey() // the alias now holds another key: sealed rows no longer open
        assertEquals(ClipboardEncryptionMigrator.Result.Lost, migrator.migrate())
        assertEquals(emptyList(), dao.getAll())
        assertEquals(1, filesDeleted)
        assertEquals(1, keys.deletes)
        assertNotEquals(lostKey, keys.current)
        val logs = ShadowLog.getLogsForTag(ClipboardWipe.TAG)
        assertEquals(listOf(Log.WARN), logs.map { it.type })
        assertTrue(logs.none { log -> texts.any { log.msg.contains(it) } || log.throwable != null })
    }

    @Test fun `with no key plaintext rows stay and nothing is wiped`() = runBlocking {
        keys.current = null
        assertEquals(ClipboardEncryptionMigrator.Result.NoKey, migrator.migrate())
        assertEquals(3, dao.getAll().size)
        assertEquals(0, keys.deletes)
    }

    @Test fun `with no key sealed rows stay, nothing is wiped, and they open when the key comes back`() = runBlocking {
        migrator.migrate()
        val key = keys.current
        keys.current = null
        assertEquals(ClipboardEncryptionMigrator.Result.NoKey, migrator.migrate())
        assertEquals(3, dao.getAll().size)
        assertEquals(0, keys.deletes)
        assertEquals(0, filesDeleted)
        assertTrue(ShadowLog.getLogsForTag(ClipboardWipe.TAG).isEmpty())
        keys.current = key
        assertEquals(ClipboardEncryptionMigrator.Result.Sealed(0), migrator.migrate())
        assertEquals(texts.toSet(), dao.getAll().map { cipher.open(it.text!!) }.toSet())
    }

    @Test fun `a permanently invalidated key wipes the history once`() = runBlocking {
        migrator.migrate()
        keys.lost = true
        assertEquals(ClipboardEncryptionMigrator.Result.Lost, migrator.migrate())
        assertEquals(emptyList(), dao.getAll())
        assertEquals(1, keys.deletes)
        assertEquals(1, filesDeleted)
        assertEquals(listOf(Log.WARN), ShadowLog.getLogsForTag(ClipboardWipe.TAG).map { it.type })
    }

    @Test fun `a read and the migrator that both see one lost key wipe once and keep the new clips`() = runBlocking {
        migrator.migrate()
        val vault = ClipboardHistoryVault(cipher, wipe)
        val staleRows = dao.getAll()
        keys.current = TestKeys.newKey() // the old key is gone: the sealed rows no longer open
        var fresh: Long = -1
        // The migrator reads the rows, then the panel read wipes and a new clip arrives, then the migrator opens.
        val racingDao = object : ClipboardHistoryDao by dao {
            override fun getAll(): List<ClipboardItem> {
                vault.read { staleRows }
                fresh = dao.insert(assertNotNull(vault.sealForWrite(ClipboardItem.text("after the wipe"))))
                return staleRows
            }
        }
        val raced = ClipboardEncryptionMigrator(racingDao, cipher, wipe, Dispatchers.Unconfined)
        assertEquals(ClipboardEncryptionMigrator.Result.Lost, raced.migrate())
        assertEquals(1, keys.deletes)
        assertEquals(1, filesDeleted)
        assertEquals(listOf(Log.WARN), ShadowLog.getLogsForTag(ClipboardWipe.TAG).map { it.type })
        assertEquals(listOf("after the wipe"), vault.read { dao.getAll() }.map { it.text })
        assertEquals(fresh, dao.getAll().single().id)
    }

    @Test fun `only a bad tag, an invalidated key or a bare unrecoverable key count as a lost key`() {
        assertTrue(isPermanentKeyLoss(AEADBadTagException()))
        assertTrue(isPermanentKeyLoss(KeyPermanentlyInvalidatedException()))
        assertTrue(isPermanentKeyLoss(UnrecoverableKeyException()))
        assertTrue(isPermanentKeyLoss(IllegalStateException(UnrecoverableKeyException())))
        val invalidated = UnrecoverableKeyException().apply { initCause(KeyPermanentlyInvalidatedException()) }
        assertTrue(isPermanentKeyLoss(invalidated))
        assertFalse(isPermanentKeyLoss(TestKeys.wrappedKeyStoreError()))
        assertFalse(isPermanentKeyLoss(UnrecoverableKeyException().apply { initCause(androidKeyStoreException()) }))
        assertFalse(isPermanentKeyLoss(KeyStoreException()))
        assertFalse(isPermanentKeyLoss(ProviderException()))
        assertFalse(isPermanentKeyLoss(IllegalStateException()))
    }

    @Test fun `a wrapped key store error leaves every row and wipes nothing`() = runBlocking {
        migrator.migrate()
        keys.failure = TestKeys.wrappedKeyStoreError()
        assertEquals(ClipboardEncryptionMigrator.Result.NoKey, migrator.migrate())
        assertEquals(3, dao.getAll().size)
        assertEquals(0, keys.deletes)
        assertEquals(0, filesDeleted)
        keys.failure = null
        assertEquals(texts.toSet(), dao.getAll().map { cipher.open(it.text!!) }.toSet())
    }

    @Test fun `a bare unrecoverable key wipes the history once`() = runBlocking {
        migrator.migrate()
        keys.failure = UnrecoverableKeyException()
        assertEquals(ClipboardEncryptionMigrator.Result.Lost, migrator.migrate())
        assertEquals(emptyList(), dao.getAll())
        assertEquals(1, keys.deletes)
        assertEquals(listOf(Log.WARN), ShadowLog.getLogsForTag(ClipboardWipe.TAG).map { it.type })
    }

    /** The framework's `android.security.KeyStoreException` (its constructor is hidden), as keystore2 throws it. */
    private fun androidKeyStoreException(): Exception {
        val type = Class.forName("android.security.KeyStoreException")
        val ctor = type.getDeclaredConstructor(Int::class.javaPrimitiveType, String::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(SYSTEM_ERROR, "system error") as Exception
    }

    private companion object {
        const val SYSTEM_ERROR = 4 // keystore2 ResponseCode.SYSTEM_ERROR
    }
}
