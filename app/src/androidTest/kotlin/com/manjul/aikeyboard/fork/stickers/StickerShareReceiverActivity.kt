/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.util.concurrent.CountDownLatch

/** The host app's share target in [StickerCompatInsertTest]: records the stream URI it receives, then finishes. */
class StickerShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        received = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        readable = received?.let { uri -> runCatching { contentResolver.openInputStream(uri)?.use { it.read() } } }
            ?.getOrNull() != null
        latch.countDown()
        finish()
    }

    companion object {
        @Volatile var received: Uri? = null
        @Volatile var readable = false
        @Volatile var latch = CountDownLatch(1)

        fun reset() {
            received = null
            readable = false
            latch = CountDownLatch(1)
        }
    }
}
