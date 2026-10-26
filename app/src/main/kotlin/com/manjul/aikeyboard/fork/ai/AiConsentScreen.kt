/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.analytics.ConsentKind
import org.florisboard.lib.compose.stringRes

/**
 * The prominent disclosure for [kind] (PRD §10.6, TRD §11.6): what is sent, who processes it, what is kept and how
 * to withdraw, with an affirmative Agree and Not now. Shown in the app, full screen, never in the keyboard window.
 */
@Composable
fun AiConsentScreen(kind: ConsentKind, enabled: Boolean, onAgree: () -> Unit, onNotNow: () -> Unit) {
    val autoPolish = kind == ConsentKind.AUTO_POLISH
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(
                text = stringRes(if (autoPolish) R.string.ai_consent__title_auto_polish else R.string.ai_consent__title_ai),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringRes(if (autoPolish) R.string.ai_consent__intro_auto_polish else R.string.ai_consent__intro_ai),
                style = MaterialTheme.typography.bodyLarge,
            )
            Section(
                R.string.ai_consent__sent_header,
                if (autoPolish) R.string.ai_consent__sent_auto_polish else R.string.ai_consent__sent_ai,
            )
            Section(R.string.ai_consent__processor_header, R.string.ai_consent__processor_body)
            Section(R.string.ai_consent__kept_header, R.string.ai_consent__kept_body)
            Section(
                R.string.ai_consent__withdraw_header,
                if (autoPolish) R.string.ai_consent__withdraw_auto_polish else R.string.ai_consent__withdraw_body,
            )
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onNotNow, enabled = enabled) { Text(stringRes(R.string.ai_consent__not_now)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = onAgree, enabled = enabled) { Text(stringRes(R.string.ai_consent__agree)) }
            }
        }
    }
}

@Composable
private fun Section(@StringRes header: Int, @StringRes body: Int) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = stringRes(header),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(4.dp))
    Text(text = stringRes(body), style = MaterialTheme.typography.bodyMedium)
}
