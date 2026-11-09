/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import com.manjul.aikeyboard.core.ads.consent.ConsentError
import com.manjul.aikeyboard.core.common.NonFatalReporter

/** The non-fatal for a failed UMP privacy options form. It carries no message: the code is in the report area. */
class PrivacyOptionsFormError : RuntimeException()

/**
 * Maps the dismissal of the UMP privacy options form (S4 Task 11) to a success flag for Settings → Privacy. A form
 * error is reported once as a non-fatal whose area holds only the UMP error code, never the UMP message.
 */
internal fun privacyOptionsResult(error: ConsentError?, reporter: NonFatalReporter): Boolean {
    if (error == null) return true
    runCatching { reporter.report("$PRIVACY_OPTIONS_AREA:code=${error.code}", PrivacyOptionsFormError()) }
    return false
}

internal const val PRIVACY_OPTIONS_AREA = "ads_privacy_options"
