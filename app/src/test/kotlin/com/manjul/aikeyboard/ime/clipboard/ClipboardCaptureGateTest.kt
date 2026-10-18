/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.os.PersistableBundle
import androidx.room.Room
import com.manjul.aikeyboard.fork.clipboard.ClipboardCaptureGate
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardHistoryDatabase
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardItem
import com.manjul.aikeyboard.ime.clipboard.provider.ItemType
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `ClipboardManager` itself needs `FlorisApplication` (native library, managers), so the capture decision lives in
 * [ClipboardCaptureGate], which the manager calls before `fromClipData` and before every insert. The 24 h expiry is
 * tested on the DAO query the manager's `enforceExpiryDate` delegates to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClipboardCaptureGateTest {
    private var decision: PolicyDecision? = PolicyDecision.Unrestricted
    private val gate = ClipboardCaptureGate { decision }
    private val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ClipboardHistoryDatabase::class.java)
        .allowMainThreadQueries().build()

    @After
    fun tearDown() {
        db.close()
    }

    private fun clip(text: String, sensitiveExtra: String? = null): ClipData {
        val data = ClipData.newPlainText("label", text)
        if (sensitiveExtra != null) {
            data.description.extras = PersistableBundle().apply { putBoolean(sensitiveExtra, true) }
        }
        return data
    }

    @Test
    fun `normal clip is captured`() {
        assertTrue(gate.mayCapture(clip("buy milk and eggs")))
    }

    @Test
    fun `clip flagged with EXTRA_IS_SENSITIVE is not captured`() {
        assertFalse(gate.mayCapture(clip("buy milk", ClipDescription.EXTRA_IS_SENSITIVE)))
    }

    @Test
    fun `clip flagged with the literal legacy key is not captured`() {
        assertFalse(gate.mayCapture(clip("buy milk", ClipboardCaptureGate.EXTRA_IS_SENSITIVE_KEY)))
    }

    @Test
    fun `otp text is not captured`() {
        assertFalse(gate.mayCapture(clip("Your code is 482913")))
    }

    @Test
    fun `nothing is captured while the policy forbids clipboard history`() {
        decision = PolicyDecision.Locked
        assertFalse(gate.mayCapture(clip("buy milk and eggs")))
        decision = PolicyDecision.Unrestricted.copy(allowClipboardHistory = false)
        assertFalse(gate.mayCapture(clip("buy milk and eggs")))
    }

    @Test
    fun `nothing is captured without a policy decision`() {
        decision = null
        assertFalse(gate.mayCapture(clip("buy milk and eggs")))
        assertFalse(gate.allowsHistory())
    }

    private fun item(text: String, ageHours: Long, pinned: Boolean, now: Long) = ClipboardItem(
        type = ItemType.TEXT, text = text, uri = null,
        creationTimestampMs = now - ageHours * 3_600_000L, isPinned = pinned, mimeTypes = listOf("text/plain"),
    )

    @Test
    fun `expiry deletes old unpinned items and keeps pinned and fresh ones`() {
        val now = System.currentTimeMillis()
        val dao = db.clipboardItemDao()
        dao.insert(item("old", 25, pinned = false, now = now))
        dao.insert(item("old pinned", 25, pinned = true, now = now))
        dao.insert(item("fresh", 23, pinned = false, now = now))

        dao.deleteUnpinnedOlderThan(ClipboardCaptureGate.expiryCutoffMs(now, 1440))

        assertEquals(setOf("old pinned", "fresh"), dao.getAll().map { it.text }.toSet())
    }

    @Test
    fun `expiry cutoff is minutes before now`() {
        assertEquals(1_000_000L - 24 * 3_600_000L, ClipboardCaptureGate.expiryCutoffMs(1_000_000L, 1440))
    }
}
