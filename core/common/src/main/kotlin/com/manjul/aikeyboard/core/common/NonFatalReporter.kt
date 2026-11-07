/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.common

/**
 * Reports a caught error that the user did not see fail (S4 Task 1). [area] is a fixed snake_case label chosen by
 * the caller, never data. Implementations send only the error's class name and stack trace, never its message,
 * because a message can hold user content (PC-10).
 */
interface NonFatalReporter {
    fun report(area: String, error: Throwable)
}

/** Drops every report. Used when Crashlytics is not configured, and in tests. */
object NoopNonFatalReporter : NonFatalReporter {
    override fun report(area: String, error: Throwable) = Unit
}
