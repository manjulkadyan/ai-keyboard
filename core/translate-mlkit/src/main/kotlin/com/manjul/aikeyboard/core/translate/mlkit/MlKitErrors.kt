/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import com.google.mlkit.common.MlKitException
import com.manjul.aikeyboard.core.translate.FailKind

/**
 * Maps an ML Kit failure to a [FailKind]. The exception and its message are dropped here: an ML Kit message
 * may quote the text, so it must never reach a log, an analytics event or a result (PC-10).
 */
object MlKitErrors {
    private const val MAX_CAUSE_DEPTH = 5

    fun kind(error: Throwable): FailKind {
        val mlKit = generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).filterIsInstance<MlKitException>().firstOrNull()
            ?: return FailKind.ENGINE
        return when (mlKit.errorCode) {
            MlKitException.NOT_ENOUGH_SPACE,
            MlKitException.UNAVAILABLE,
            MlKitException.NOT_FOUND,
            -> FailKind.NO_PACK
            MlKitException.NETWORK_ISSUE -> FailKind.OFFLINE
            MlKitException.DEADLINE_EXCEEDED -> FailKind.TIMEOUT
            else -> FailKind.ENGINE
        }
    }
}
