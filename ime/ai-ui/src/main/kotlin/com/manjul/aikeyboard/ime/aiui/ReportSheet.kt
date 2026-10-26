/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Report an AI output (PRD FR-15 AC9, RN-08): one reason (radio buttons) and "Include the text", unticked by default.
 * The text goes with the report only when ticked.
 */
@Composable
fun ReportSheet(onSubmit: (ReportReason, Boolean) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var reason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    var includeText by rememberSaveable { mutableStateOf(false) }
    val color = LocalContentColor.current
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        Text(
            text = stringResource(R.string.ai_sheet__report_title),
            modifier = Modifier.padding(vertical = 4.dp),
            fontWeight = FontWeight.Medium,
        )
        Column(Modifier.selectableGroup()) {
            for (option in ReportReason.entries) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = reason == option, role = Role.RadioButton, onClick = { reason = option }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = reason == option,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = color, unselectedColor = color),
                    )
                    Text(stringResource(AiStrings.reportReason(option)), Modifier.padding(start = 8.dp))
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = includeText, role = Role.Checkbox, onValueChange = { includeText = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = includeText,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = color, uncheckedColor = color),
            )
            Text(stringResource(R.string.ai_sheet__report_include_text), Modifier.padding(start = 8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            SheetChip(stringResource(R.string.ai_sheet__report_cancel), onCancel)
            val chosen = reason
            SheetChip(
                label = stringResource(R.string.ai_sheet__report_send),
                onClick = if (chosen != null) ({ onSubmit(chosen, includeText) }) else null,
            )
        }
    }
}
