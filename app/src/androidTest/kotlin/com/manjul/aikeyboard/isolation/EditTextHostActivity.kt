/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.isolation

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/** A focused plain text field in another app (the test APK), so the keyboard is the only part of the app that runs. */
class EditTextHostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val field = EditText(this)
        setContentView(field)
        field.requestFocus()
        field.post { getSystemService(InputMethodManager::class.java).showSoftInput(field, 0) }
    }
}
