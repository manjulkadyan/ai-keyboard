/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import com.manjul.aikeyboard.core.common.entry.EntrySource
import com.manjul.aikeyboard.fork.routes.RouteRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AdEntryTest {
    private val activity = ComponentName("com.manjul.aikeyboard", "com.manjul.aikeyboard.app.FlorisAppActivity")

    private fun check(expected: EntrySource, intent: Intent?, name: String) =
        assertEquals(name, expected, AdEntry.classify(intent))

    @Test
    fun `only a launcher intent is LAUNCHER`() {
        check(
            EntrySource.LAUNCHER,
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(activity),
            "launcher",
        )
        // FlorisImeService.launchSettings(): the keyboard settings key, with no action and no extras.
        val imeSettings = Intent().setComponent(activity).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
        check(EntrySource.DEEP_LINK, imeSettings, "bare IME launchSettings intent")
        check(EntrySource.DEEP_LINK, Intent(Intent.ACTION_MAIN), "bare ACTION_MAIN after the Activity reset")
        check(EntrySource.DEEP_LINK, null, "no intent")
    }

    @Test
    fun `restricted and deep link intents`() {
        val translator = Intent().setComponent(activity)
            .putExtra(RouteRegistry.EXTRA_OPEN_SCREEN, RouteRegistry.TRANSLATOR)
            .putExtra(RouteRegistry.EXTRA_TRANSLATOR_TEXT, "x")
        check(EntrySource.PROCESS_TEXT, translator, "translatorIntent extras")
        check(EntrySource.PROCESS_TEXT, Intent(Intent.ACTION_PROCESS_TEXT), "ACTION_PROCESS_TEXT")
        check(EntrySource.SEND, Intent(Intent.ACTION_SEND).setType("text/plain"), "ACTION_SEND")
        check(EntrySource.DEEP_LINK, Intent(Intent.ACTION_VIEW), "ACTION_VIEW")
        check(
            EntrySource.DEEP_LINK,
            Intent().putExtra(RouteRegistry.EXTRA_OPEN_SCREEN, RouteRegistry.TRANSLATOR),
            "open_screen extra",
        )
    }
}
