/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.phrasebook

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.content.phrasebook.PhrasebookRepository
import org.florisboard.lib.compose.stringRes

/**
 * Adds a user phrase ([initial] empty) or edits one. Save is enabled for 1 to 300 characters after trimming, the
 * same rule as [PhrasebookRepository.addOwn]. The text stays in this dialog and the repository; it is never logged.
 */
@Composable
internal fun PhraseEditDialog(initial: String, editing: Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val max = PhrasebookRepository.MAX_CHARS
    val trimmed = text.trim().length
    val valid = trimmed in 1..max
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringRes(if (editing) R.string.phrasebook__edit_title else R.string.phrasebook__add_title))
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringRes(R.string.phrasebook__field_label)) },
                isError = trimmed > max,
                supportingText = {
                    if (trimmed > max) {
                        Text(stringRes(R.string.phrasebook__too_long, "max" to max))
                    } else {
                        Text(stringRes(R.string.phrasebook__field_count, "count" to trimmed, "max" to max))
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = valid) {
                Text(stringRes(R.string.phrasebook__save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.phrasebook__cancel)) }
        },
    )
}
