/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translator

import androidx.annotation.StringRes
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.bar.BarStatus

/**
 * Friendly copy for the Translator screen's failure states. Only string resources: an engine's exception text never
 * reaches the screen (engines map errors to [FailKind] and drop the message).
 */
object TranslatorErrors {
    /** The message for [status], or `null` for [BarStatus.Idle] and [BarStatus.Ok], which show nothing. */
    @StringRes
    fun message(status: BarStatus): Int? = when (status) {
        BarStatus.Idle, BarStatus.Ok -> null
        is BarStatus.NeedsDownload -> R.string.translator__error_no_pack
        BarStatus.CloudOnlyPro -> R.string.translator__error_cloud_pro
        BarStatus.Offline -> R.string.translator__error_offline
        is BarStatus.Error -> message(status.kind)
    }

    @StringRes
    fun message(kind: FailKind): Int = when (kind) {
        FailKind.NO_PACK -> R.string.translator__error_no_pack
        FailKind.OFFLINE -> R.string.translator__error_offline
        FailKind.TIMEOUT -> R.string.translator__error_timeout
        FailKind.QUOTA -> R.string.translator__error_quota
        FailKind.UNSUPPORTED -> R.string.translator__error_unsupported
        FailKind.CLOUD_UNAVAILABLE -> R.string.translator__error_cloud_pro
        FailKind.POLICY -> R.string.translator__error_policy
        FailKind.ENGINE -> R.string.translator__error_engine
    }

    /** Whether "Retry" makes sense: a transient failure, not a missing pack or an unsupported pair. */
    fun retryable(status: BarStatus): Boolean = when (status) {
        BarStatus.Offline -> true
        is BarStatus.Error -> status.kind == FailKind.TIMEOUT || status.kind == FailKind.ENGINE ||
            status.kind == FailKind.QUOTA || status.kind == FailKind.OFFLINE
        else -> false
    }
}
