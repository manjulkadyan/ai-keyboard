/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.policy

/**
 * Android-free description of an input field. Holds the raw `EditorInfo` values so this module
 * stays pure Kotlin. [hintText] and [label] are only inspected for the card heuristic and are
 * never stored or logged.
 */
data class FieldDescriptor(
    val packageName: String?,
    val inputType: Int,
    val imeOptions: Int,
    val hintText: String?,
    val label: String?,
)
