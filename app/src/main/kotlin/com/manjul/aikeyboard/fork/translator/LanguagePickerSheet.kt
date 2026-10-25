/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.mlkit.MlKitLanguages
import com.manjul.aikeyboard.fork.settings.OfflineLanguagesViewModel
import java.util.Locale
import org.florisboard.lib.compose.stringRes

/** A language's name in the user's locale (`Locale.getDisplayName`, never our strings). */
fun displayName(lang: Lang, locale: Locale = Locale.getDefault()): String =
    Locale.forLanguageTag(lang.tag).getDisplayName(locale).ifEmpty { lang.tag }

/** The source or target picker. Language tags only; the search text is a language name filter, not user content. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
    title: String,
    selected: Lang?,
    onPick: (Lang) -> Unit,
    onDismiss: () -> Unit,
    /** Shows "Detect language" first (source picker only); [detectSelected] marks it chosen. */
    onDetect: (() -> Unit)? = null,
    detectSelected: Boolean = false,
) {
    val locale = Locale.getDefault()
    val languages = remember(locale) { OfflineLanguagesViewModel.languageOrder(MlKitLanguages.all, locale) }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(query, languages) {
        val q = query.trim()
        if (q.isEmpty()) languages else languages.filter { displayName(it, locale).contains(q, ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringRes(R.string.translator__picker_search)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
            if (onDetect != null && query.isBlank()) {
                item {
                    ListItem(
                        modifier = Modifier.clickable(role = Role.Button, onClick = onDetect),
                        leadingContent = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        headlineContent = { Text(stringRes(R.string.translator__detect)) },
                        trailingContent = { if (detectSelected) Icon(Icons.Default.Check, contentDescription = null) },
                    )
                }
            }
            items(shown, key = { it.tag }) { lang ->
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { onPick(lang) },
                    headlineContent = { Text(displayName(lang, locale)) },
                    trailingContent = {
                        if (!detectSelected && lang == selected) Icon(Icons.Default.Check, contentDescription = null)
                    },
                )
            }
        }
    }
}
