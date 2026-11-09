/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import com.manjul.aikeyboard.core.ads.consent.ConsentError
import com.manjul.aikeyboard.core.common.NonFatalReporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S4 Task 11: a UMP privacy options form error is reported by code only and fails the result. */
class PrivacyOptionsResultTest {
    private val reports = mutableListOf<Pair<String, Throwable>>()
    private val reporter = object : NonFatalReporter {
        override fun report(area: String, error: Throwable) {
            reports += area to error
        }
    }

    @Test
    fun `a dismissed form succeeds and reports nothing`() {
        assertTrue(privacyOptionsResult(null, reporter))
        assertTrue(reports.isEmpty())
    }

    @Test
    fun `a form error fails and reports only its code`() {
        assertFalse(privacyOptionsResult(ConsentError(code = 3, message = "secret detail"), reporter))
        val (area, error) = reports.single()
        assertEquals("ads_privacy_options:code=3", area)
        assertIs<PrivacyOptionsFormError>(error)
        assertNull(error.message)
    }

    @Test
    fun `a failing reporter still fails the result`() {
        val broken = object : NonFatalReporter {
            override fun report(area: String, error: Throwable) = error("boom")
        }
        assertFalse(privacyOptionsResult(ConsentError(code = 1, message = ""), broken))
    }
}
