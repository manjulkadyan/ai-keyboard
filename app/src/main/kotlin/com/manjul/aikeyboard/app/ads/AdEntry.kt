/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import android.content.Intent
import android.os.Bundle
import com.manjul.aikeyboard.core.common.entry.EntrySource
import com.manjul.aikeyboard.fork.routes.RouteRegistry

/**
 * The [EntrySource] of an intent to the main Activity. It reads the action, the categories and the presence of
 * extras only, never their values.
 *
 * Only a launcher intent (`ACTION_MAIN` with `CATEGORY_LAUNCHER`) is [EntrySource.LAUNCHER]. Every other intent
 * without a known source is [EntrySource.DEEP_LINK], which never shows an app-open ad. This covers the bare intent
 * of the keyboard settings key (`FlorisImeService.launchSettings`): no IME action opens the app into an ad.
 * A return to the running Activity without a new intent is [EntrySource.RESUME] (see [AdEntryTracker]).
 * Nothing opens the app from a notification yet, so [EntrySource.NOTIFICATION] is not produced.
 */
internal object AdEntry {
    fun classify(intent: Intent?): EntrySource = when {
        intent == null -> EntrySource.DEEP_LINK
        intent.hasExtra(RouteRegistry.EXTRA_TRANSLATOR_TEXT) -> EntrySource.PROCESS_TEXT
        intent.action == Intent.ACTION_PROCESS_TEXT -> EntrySource.PROCESS_TEXT
        intent.action == Intent.ACTION_SEND -> EntrySource.SEND
        intent.hasExtra(RouteRegistry.EXTRA_OPEN_SCREEN) -> EntrySource.DEEP_LINK
        intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER) -> EntrySource.LAUNCHER
        else -> EntrySource.DEEP_LINK
    }
}

/**
 * Feeds the entry source of one Activity instance to [AdsController]. It classifies only a new intent: the first
 * `onCreate` and a real `onNewIntent`. A recreation (rotation, process restore) keeps the saved entry, because the
 * Activity resets its intent to a bare `ACTION_MAIN` and replays it through `onNewIntent`.
 */
internal class AdEntryTracker(private val controller: AdsController) {
    private var stopped = false

    /** [restored] is the entry from the saved state, or null on a fresh start. */
    fun onCreate(intent: Intent?, restored: EntrySource?) {
        controller.onIntent(restored ?: AdEntry.classify(intent))
    }

    /** [current] is `Activity.getIntent()`. The Activity replays that same object, which is not a new intent. */
    fun onNewIntent(intent: Intent, current: Intent?) {
        if (intent === current) return
        stopped = false
        controller.onIntent(AdEntry.classify(intent))
    }

    fun onResume() {
        if (stopped) controller.onIntent(EntrySource.RESUME)
        stopped = false
    }

    fun onStop() {
        stopped = true
    }

    fun save(): Bundle = Bundle().apply { putString(KEY_ENTRY, controller.currentEntry.name) }

    companion object {
        const val SAVED_STATE_KEY = "com.manjul.aikeyboard.app.ads.entry"
        private const val KEY_ENTRY = "entry"

        fun restore(bundle: Bundle?): EntrySource? =
            bundle?.getString(KEY_ENTRY)?.let { name -> EntrySource.entries.firstOrNull { it.name == name } }
    }
}
