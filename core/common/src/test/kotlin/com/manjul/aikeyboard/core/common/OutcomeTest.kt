/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.common

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class OutcomeTest {
    @Test
    fun `success maps and folds`() {
        val outcome = outcomeOf { 21 }.map { it * 2 }
        assertEquals(Outcome.Success(42), outcome)
        assertEquals("42", outcome.fold({ it.toString() }, { "error" }))
    }

    @Test
    fun `exception becomes failure`() {
        val outcome = outcomeOf<Int> { error("boom") }
        assertIs<Outcome.Failure>(outcome)
        assertFalse(outcome.isSuccess)
        assertNull(outcome.getOrNull())
        assertEquals("boom", outcome.fold({ "ok" }, { it.message }))
    }

    @Test
    fun `cancellation is rethrown`() {
        assertThrows<CancellationException> {
            outcomeOf { throw CancellationException("cancelled") }
        }
    }
}
