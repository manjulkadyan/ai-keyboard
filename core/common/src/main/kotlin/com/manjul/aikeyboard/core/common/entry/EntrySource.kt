/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.common.entry

/**
 * How the user reached the app's main Activity (S4 Task 7). The ad rules read it: an app-open ad needs [LAUNCHER] or
 * [RESUME], and an interstitial never follows [PROCESS_TEXT], [SEND] or [NOTIFICATION]. The update prompt (Task 8)
 * reads it too.
 */
enum class EntrySource {
    /** The launcher icon, or another plain `MAIN` start. */
    LAUNCHER,

    /** A return to an Activity that was already started. */
    RESUME,

    /** "Open in app" from the text selection sheet (`PROCESS_TEXT`). */
    PROCESS_TEXT,

    /** A share (`ACTION_SEND`) into the app. */
    SEND,

    /** A tap on one of the app's notifications. */
    NOTIFICATION,

    /** A route link (`open_screen`, `ui://`, `app://` or a browsable `VIEW`). */
    DEEP_LINK,
}
