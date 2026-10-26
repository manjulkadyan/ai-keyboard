/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import com.manjul.aikeyboard.userWordRepository
import java.util.Locale
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/** The language name shown for an MVP tag, in the phone's language (e.g. "en" -> "English"). */
internal fun languageName(tag: String, displayLocale: Locale = Locale.getDefault()): String =
    Locale.forLanguageTag(tag).getDisplayName(displayLocale).replaceFirstChar { it.titlecase(displayLocale) }

/** [settings] with autocorrect turned [on] or off for [tag]. */
internal fun FeatureSettings.withAutocorrect(tag: String, on: Boolean): FeatureSettings =
    copy(autocorrectLocales = if (on) autocorrectLocales + tag else autocorrectLocales - tag)

/**
 * Typing settings (a Settings home entry, PRD FR-05): the suggestion strip, autocorrect per MVP language (opt-in,
 * plan decision 4) and "Clear learned words", which empties the on-device user dictionary after a confirm dialog.
 */
@Composable
fun TypingSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.typing_settings__title)
    previewFieldVisible = true
    scrollable = false

    val context = LocalContext.current
    val store = remember { context.featureSettingsStore().value }
    val scope = rememberCoroutineScope()
    val settings by store.flow.collectAsState(initial = store.current)

    content {
        TypingSettingsContent(
            settings = settings,
            onUpdate = { transform -> scope.launch { store.update(transform) } },
            onClearLearned = {
                scope.launch {
                    context.userWordRepository().value.clearAll()
                    Toast.makeText(context, R.string.typing_settings__cleared, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
internal fun TypingSettingsContent(
    settings: FeatureSettings,
    onUpdate: ((FeatureSettings) -> FeatureSettings) -> Unit,
    onClearLearned: () -> Unit,
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SwitchRow(
            title = stringRes(R.string.typing_settings__suggestions),
            summary = stringRes(R.string.typing_settings__suggestions_summary),
            checked = settings.suggestionsEnabled,
            onChange = { on -> onUpdate { it.copy(suggestionsEnabled = on) } },
        )

        Header(stringRes(R.string.typing_settings__autocorrect))
        Text(
            text = stringRes(R.string.typing_settings__autocorrect_summary),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        for (tag in FeatureSettings.MVP_LANGUAGES) {
            SwitchRow(
                title = languageName(tag),
                summary = null,
                checked = tag in settings.autocorrectLocales,
                onChange = { on -> onUpdate { it.withAutocorrect(tag, on) } },
            )
        }

        ListItem(
            modifier = Modifier.clickable { confirmClear = true },
            headlineContent = { Text(stringRes(R.string.typing_settings__clear_learned)) },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringRes(R.string.typing_settings__clear_learned_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearLearned()
                    },
                ) { Text(stringRes(R.string.typing_settings__clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringRes(R.string.typing_settings__cancel)) }
            },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}
