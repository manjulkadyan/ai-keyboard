/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import android.content.Intent
import com.manjul.aikeyboard.core.common.entry.EntrySource
import com.manjul.aikeyboard.fork.routes.RouteRegistry

/**
 * The [EntrySource] of an intent to the main Activity. It reads the action and the presence of extras only, never
 * their values. A return to the running Activity without a new intent is [EntrySource.RESUME] (see [AdsAppGraph]).
 * Nothing opens the app from a notification yet, so [EntrySource.NOTIFICATION] is not produced.
 */
internal object AdEntry {
    fun classify(intent: Intent?): EntrySource = when {
        intent == null -> EntrySource.LAUNCHER
        intent.hasExtra(RouteRegistry.EXTRA_TRANSLATOR_TEXT) -> EntrySource.PROCESS_TEXT
        intent.action == Intent.ACTION_SEND -> EntrySource.SEND
        intent.action == Intent.ACTION_VIEW || intent.hasExtra(RouteRegistry.EXTRA_OPEN_SCREEN) -> EntrySource.DEEP_LINK
        else -> EntrySource.LAUNCHER
    }
}
