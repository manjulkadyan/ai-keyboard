/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.manjul.aikeyboard.BuildConfig
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.content.stickers.StickerUi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
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
        val latch = CountDownLatch(1)
        var receivedUri: String? = null
        var readable = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                receivedUri = intent.getStringExtra(StickerShareReceiverActivity.EXTRA_URI)
                readable = intent.getBooleanExtra(StickerShareReceiverActivity.EXTRA_READABLE, false)
                latch.countDown()
            }
        }
        // The receiver Activity runs in the test package's process and reports back by broadcast.
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(StickerShareReceiverActivity.ACTION_RECEIVED),
            ContextCompat.RECEIVER_EXPORTED,
        )

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

        try {
            assertEquals(InsertMethod.SHARE, method)
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            val uri = Uri.parse(receivedUri)
            assertEquals("${BuildConfig.APPLICATION_ID}.provider.file", uri.authority)
            assertTrue(readable)
        } finally {
            app.unregisterReceiver(receiver)
        }
    }
}
