/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.policy

import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.ime.policy.FieldDescriptor

/** Maps the framework [EditorInfo] to the Android-free descriptor the policy evaluates. */
fun EditorInfo?.toFieldDescriptor(): FieldDescriptor {
    if (this == null) return FieldDescriptor(null, 0, 0, null, null)
    return FieldDescriptor(
        packageName = packageName,
        inputType = inputType,
        imeOptions = imeOptions,
        hintText = hintText?.toString(),
        label = label?.toString(),
    )
}
