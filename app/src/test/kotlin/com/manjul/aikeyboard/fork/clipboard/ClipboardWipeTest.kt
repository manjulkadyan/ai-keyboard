/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import android.app.Application
import android.database.sqlite.SQLiteException
import android.util.Log
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [ClipboardWipe] reports a wipe only when the rows are gone (final review m1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ClipboardWipeTest {
    private val keys = TestKeys()
    private var rowsFail = true
    private var rowDeletes = 0
    private val wipe = ClipboardWipe(
        deleteRows = {
            rowDeletes++
            if (rowsFail) throw SQLiteException("disk I/O error")
        },
        deleteFiles = {},
        keys = keys,
    )

    @Before fun setUp() = ShadowLog.clear()

    @Test fun `a failed row delete is not reported as cleared and can run again`() {
        assertFalse(wipe.wipe(0))
        assertEquals(0, wipe.currentGeneration)
        val logs = ShadowLog.getLogsForTag(ClipboardWipe.TAG)
        assertEquals(listOf(Log.WARN), logs.map { it.type })
        assertTrue(logs.single().msg.contains("failed"))
        assertTrue(logs.none { it.throwable != null })

        ShadowLog.clear()
        rowsFail = false
        assertTrue(wipe.wipe(0))
        assertEquals(2, rowDeletes)
        assertEquals(1, wipe.currentGeneration)
        val cleared = ShadowLog.getLogsForTag(ClipboardWipe.TAG).map { it.msg }
        assertEquals(listOf("clipboard history key lost; history cleared"), cleared)
    }
}
