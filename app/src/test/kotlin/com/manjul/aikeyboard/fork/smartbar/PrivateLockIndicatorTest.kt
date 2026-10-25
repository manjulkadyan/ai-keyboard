/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import android.app.Application
import com.manjul.aikeyboard.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PrivateLockIndicatorTest {
    @Test
    fun `the lock's description says translation is off`() {
        val text = RuntimeEnvironment.getApplication().getString(R.string.private_lock__description)
        assertTrue(text.contains("translation", ignoreCase = true), text)
    }
}
