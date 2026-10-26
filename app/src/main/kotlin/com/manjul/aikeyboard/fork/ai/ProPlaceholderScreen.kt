/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes

/**
 * Placeholder for Pro, opened by "See Pro" on the AI quota card (`ui://florisboard/pro`). Billing arrives in a later
 * sprint: this screen sells nothing, shows no price and has no ads. The real paywall stays off the deep-link
 * allow-list.
 */
@Composable
fun ProPlaceholderScreen() = FlorisScreen {
    title = stringRes(R.string.pro__title)
    previewFieldVisible = false

    content {
        Text(
            text = stringRes(R.string.pro__placeholder_body),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
