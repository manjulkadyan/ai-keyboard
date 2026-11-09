/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.benchmark

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.EditText

/**
 * A plain text field for [ImeShowBenchmark] (S4 Task 13). It lives in the benchmark APK, so the target app's
 * process runs only the keyboard, as it does for a real user who never opens the app.
 */
class ImeHostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            contentDescription = FIELD_DESCRIPTION
        }
        setContentView(field)
        field.requestFocus()
    }

    companion object {
        const val FIELD_DESCRIPTION = "aikb_benchmark_field"
    }
}
