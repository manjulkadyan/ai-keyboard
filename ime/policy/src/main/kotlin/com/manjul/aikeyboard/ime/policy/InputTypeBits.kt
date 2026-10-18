/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.policy

/** Constants from `android.text.InputType` and `android.view.inputmethod.EditorInfo`, copied so the module stays pure JVM. */
object InputTypeBits {
    const val MASK_CLASS = 0x0000000f
    const val MASK_VARIATION = 0x00000ff0

    const val CLASS_TEXT = 0x00000001
    const val CLASS_NUMBER = 0x00000002
    const val CLASS_PHONE = 0x00000003
    const val CLASS_DATETIME = 0x00000004

    const val TEXT_FLAG_NO_SUGGESTIONS = 0x00080000

    const val TEXT_VARIATION_URI = 0x00000010
    const val TEXT_VARIATION_EMAIL_ADDRESS = 0x00000020
    const val TEXT_VARIATION_PASSWORD = 0x00000080
    const val TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
    const val TEXT_VARIATION_WEB_EMAIL_ADDRESS = 0x000000d0
    const val TEXT_VARIATION_WEB_PASSWORD = 0x000000e0

    const val NUMBER_VARIATION_PASSWORD = 0x00000010

    const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x01000000
}
