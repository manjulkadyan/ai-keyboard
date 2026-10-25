/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translator

import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.bar.BarStatus
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TranslatorErrorsTest {
    private val res = RuntimeEnvironment.getApplication().resources

    @Test
    fun everyFailKindMapsToAString() {
        for (kind in FailKind.entries) {
            val text = res.getString(TranslatorErrors.message(kind))
            assertTrue(text.isNotBlank(), "$kind")
        }
    }

    @Test
    fun everyFailingStatusMapsToAString() {
        val statuses = listOf(
            BarStatus.NeedsDownload(LangPair(Lang.English, Lang("ur")), 30L),
            BarStatus.CloudOnlyPro,
            BarStatus.Offline,
        ) + FailKind.entries.map { BarStatus.Error(it) }
        for (status in statuses) {
            val id = assertNotNull(TranslatorErrors.message(status), "$status")
            assertTrue(res.getString(id).isNotBlank())
        }
        assertNull(TranslatorErrors.message(BarStatus.Idle))
        assertNull(TranslatorErrors.message(BarStatus.Ok))
    }

    @Test
    fun onlyTransientFailuresAreRetryable() {
        assertTrue(TranslatorErrors.retryable(BarStatus.Error(FailKind.TIMEOUT)))
        assertTrue(TranslatorErrors.retryable(BarStatus.Offline))
        assertEquals(false, TranslatorErrors.retryable(BarStatus.Error(FailKind.UNSUPPORTED)))
        assertEquals(false, TranslatorErrors.retryable(BarStatus.NeedsDownload(LangPair(Lang.English, Lang("ur")), 1L)))
    }
}
