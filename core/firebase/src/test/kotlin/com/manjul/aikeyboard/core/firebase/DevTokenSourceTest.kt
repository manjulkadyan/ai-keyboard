/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.firebase

import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.TokenState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DevTokenSourceTest {
    @Test
    fun `dev auth sends only X-Dev-Uid`() = runTest {
        assertEquals(TokenState.Ready(mapOf(AuthHeaders.DEV_UID to "dev-1")), DevTokenSource("dev-1").headers())
    }

    @Test
    fun `a uid the backend would reject fails fast`() {
        assertFailsWith<IllegalArgumentException> { DevTokenSource("has space") }
        assertFailsWith<IllegalArgumentException> { DevTokenSource("") }
    }

    @Test
    fun `firebase config needs every value`() {
        assertFalse(FirebaseConfig("", "", "", "").isConfigured)
        assertFalse(FirebaseConfig("p", "a", "k", " ").isConfigured)
        assertTrue(FirebaseConfig("p", "a", "k", "1").isConfigured)
        assertFalse(FirebaseConfig("p", "a", "secret-key", "1").toString().contains("secret-key"))
    }
}
