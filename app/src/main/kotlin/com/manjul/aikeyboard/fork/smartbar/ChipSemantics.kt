/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Accessibility semantics for a smartbar chip (DT-W.3). The chip itself is driven by raw pointer events, which TalkBack
 * cannot see, so it needs a name, a button role and an accessibility click action.
 */
fun Modifier.chipSemantics(
    description: String,
    enabled: Boolean,
    onClick: (() -> Unit)?,
): Modifier = semantics {
    contentDescription = description
    role = Role.Button
    if (!enabled) {
        disabled()
    } else if (onClick != null) {
        onClick(label = null) {
            onClick()
            true
        }
    }
}
