/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.policy

/**
 * The hard gate that decides which keyboard features may run in the current input field
 * (TRD §4.1.2): learning, clipboard history, translate, voice, AI actions, cloud engines, analytics.
 *
 * Placeholder for Sprint 0. The real inputs (editor info, device lock, manual incognito, remote
 * flags), the decision fields and the implementation arrive in Sprint 1.
 */
interface SensitiveFieldPolicy {
    /** Whether the field described by [field] must be treated as sensitive. */
    fun isSensitive(field: FieldDescriptor): Boolean
}

/**
 * Minimal, Android-free description of an input field. Holds the raw `EditorInfo` values so this
 * module stays pure Kotlin. It will grow in Sprint 1.
 */
data class FieldDescriptor(
    val packageName: String?,
    val inputType: Int,
    val imeOptions: Int,
)
