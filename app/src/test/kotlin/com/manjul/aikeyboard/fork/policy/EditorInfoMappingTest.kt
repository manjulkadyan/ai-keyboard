/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.policy

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.ime.policy.FieldDescriptor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EditorInfoMappingTest {
    @Test
    fun `null maps to the empty descriptor`() {
        assertEquals(FieldDescriptor(null, 0, 0, null, null), (null as EditorInfo?).toFieldDescriptor())
    }

    @Test
    fun `fields are copied`() {
        val info = EditorInfo().apply {
            packageName = "p.q"
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            hintText = "hint"
            label = "label"
        }
        assertEquals(
            FieldDescriptor("p.q", InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_DONE, "hint", "label"),
            info.toFieldDescriptor(),
        )
    }
}
