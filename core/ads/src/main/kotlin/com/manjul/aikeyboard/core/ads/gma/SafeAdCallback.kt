/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.gma

import com.manjul.aikeyboard.core.common.NonFatalReporter

/**
 * Runs an ad SDK call or callback and contains its failure (TRD §11.1 1.3): the app and the IME share one process,
 * so an ad SDK exception must never crash it. A caught exception is reported once as [AdCallbackError] and the call
 * returns null. [area] is a fixed snake_case label, never data.
 */
inline fun <T> safeAdCallback(reporter: NonFatalReporter, area: String, block: () -> T): T? =
    try {
        block()
    } catch (e: Exception) {
        runCatching { reporter.report(area, AdCallbackError.of(e)) }
        null
    }

/**
 * The reported form of an ad SDK exception: its class name and stack trace only. The message and the cause chain
 * are dropped, because they can hold data (PC-10, OPS-001).
 */
class AdCallbackError private constructor(className: String) : RuntimeException(className) {
    companion object {
        fun of(error: Throwable): AdCallbackError =
            AdCallbackError(error.javaClass.name).also { it.stackTrace = error.stackTrace }
    }
}
