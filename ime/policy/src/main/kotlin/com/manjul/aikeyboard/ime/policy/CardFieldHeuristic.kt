/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.policy

/** Soft signal for payment-like numeric fields, matched on the field's hint and label. */
internal object CardFieldHeuristic {
    /** English only for now. Add other UI locales by appending to this list. */
    private val patterns = listOf("card", "cvv", "cvc", "iban", "pin", "otp", "one[- ]?time")

    private val regex = Regex("\\b(${patterns.joinToString("|")})\\b", RegexOption.IGNORE_CASE)

    fun matches(field: FieldDescriptor): Boolean {
        if ((field.inputType and InputTypeBits.MASK_CLASS) != InputTypeBits.CLASS_NUMBER) return false
        return field.hintText?.let(regex::containsMatchIn) == true ||
            field.label?.let(regex::containsMatchIn) == true
    }
}
