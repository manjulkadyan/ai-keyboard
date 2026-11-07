/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.firebase

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashlyticsScrubTest {
    @Test
    fun `a scrubbed error keeps class names and stacks and drops every message`() {
        val root = IllegalStateException("typed text: secret")
        val error = IOException("host com.bank.app", root)
        val scrubbed = CrashlyticsReporter.scrub("rc_fetch", error)
        val all = generateSequence(scrubbed) { it.cause }.toList()
        assertEquals(listOf("rc_fetch: java.io.IOException", "java.lang.IllegalStateException"), all.map { it.message })
        assertTrue(all.none { it.message.orEmpty().contains("secret") || it.message.orEmpty().contains("bank") })
        assertEquals(error.stackTrace.toList(), scrubbed.stackTrace.toList())
        assertEquals(root.stackTrace.toList(), all[1].stackTrace.toList())
        assertFalse(scrubbed.toString().contains("secret"))
    }

    @Test
    fun `fetch interval is 12 h in release and 1 h in debug`() {
        assertEquals(43_200L, FirebaseRemoteConfigSource.fetchInterval(debug = false))
        assertEquals(3_600L, FirebaseRemoteConfigSource.fetchInterval(debug = true))
    }
}
