/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.voice

import android.content.Intent
import com.manjul.aikeyboard.app.FlorisAppActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class VoiceSettingsIntentTest {
    @Test fun theWhisperDownloadChipOpensVoiceSettingsInANewTask() {
        val app = RuntimeEnvironment.getApplication()
        val intent = voiceSettingsIntent(app)
        assertEquals(FlorisAppActivity::class.java.name, intent.component!!.className)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        // FlorisAppActivity hands BROWSABLE view intents to the nav graph, where Settings.Voice owns this link.
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals("ui://florisboard/settings/voice", intent.dataString)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
