/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.TranslateResult

/**
 * One bar translation request, backed by the router in the app. [seq] is the bar's request number so the caller
 * can drop stale results. [text] is user content: implementations never log it.
 */
fun interface TranslateCall {
    suspend fun translate(text: String, pair: LangPair, seq: Long): TranslateResult
}
