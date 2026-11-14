/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.manjul.aikeyboard.BuildConfig
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.content.stickers.StickerUi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * S5 Task 6, on a device: a host EditText that declares no content MIME types cannot take commitContent, so a tap
 * on a sticker ends in the share fallback, sent only to the host package, which can read the stream URI.
 */
@RunWith(AndroidJUnit4::class)
class StickerCompatInsertTest {
    @Test fun aHostWithoutMimeTypesReceivesTheStickerThroughShare() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext
        val hostPackage = instrumentation.context.packageName
        StickerShareReceiverActivity.reset()

        val editor = EditorInfo()
        // A plain EditText fills in no content MIME types; its connection is not needed for the share path.
        instrumentation.runOnMainSync { EditText(instrumentation.context).onCreateInputConnection(editor) }
        editor.packageName = hostPackage

        val src = File(app.cacheDir, "compat-test.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val inserter = StickerInserter(
            context = app,
            shareCache = ShareCache(app.cacheDir) { System.currentTimeMillis() },
            isPro = { false },
            analytics = object : AnalyticsSink {
                override fun log(event: AnalyticsEvent) = Unit
            },
            notify = {},
        )
        // insert is suspend: it stages on Dispatchers.IO, then sends on this thread.
        val sticker = StickerUi("starter/01", "starter", src, premium = false)
        val method = runBlocking { inserter.insert(sticker, editor, null) }

        assertEquals(InsertMethod.SHARE, method)
        assertTrue(StickerShareReceiverActivity.latch.await(10, TimeUnit.SECONDS))
        val uri = StickerShareReceiverActivity.received
        assertNotNull(uri)
        assertEquals("${BuildConfig.APPLICATION_ID}.provider.file", uri!!.authority)
        assertTrue(StickerShareReceiverActivity.readable)
    }
}
