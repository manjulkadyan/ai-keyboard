/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

/**
 * One translate call. [text] is user content, never logged: [toString] omits it so the request can show up
 * in a crash report or test failure without leaking what the user typed (PC-10).
 *
 * @param source the language the user picked, or `null` to detect it.
 */
data class TranslateRequest(
    val text: String,
    val source: Lang?,
    val target: Lang,
    val origin: Origin,
) {
    override fun toString(): String = "TranslateRequest(chars=${text.length}, source=$source, target=$target, origin=$origin)"
}
