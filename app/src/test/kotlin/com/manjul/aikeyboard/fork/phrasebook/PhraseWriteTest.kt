/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.phrasebook

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Review I1: a failed phrasebook write reports false (the screen shows "Phrase not saved"); it never logs. */
class PhraseWriteTest {
    @Test fun `a write that succeeds reports true`() = runTest {
        var ran = 0
        assertTrue(phraseWrite { ran++ })
        assertEquals(1, ran)
    }

    @Test fun `a missing user phrase or a bad ref reports false`() = runTest {
        assertFalse(phraseWrite { requireNotNull(null) { "no user phrase 7" } })
    }

    @Test fun `a Room error reports false`() = runTest {
        // Room raises IllegalStateException (closed database) or SQLiteException, both plain Exceptions here.
        assertFalse(phraseWrite { throw IllegalStateException("database closed") })
    }

    @Test fun `cancellation is not swallowed`() = runTest {
        assertFailsWith<CancellationException> { phraseWrite { throw CancellationException("gone") } }
    }
}
