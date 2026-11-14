/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.database.TranslitMappingEntity
import com.manjul.aikeyboard.core.translit.TranslitLang
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import com.manjul.aikeyboard.translitMappings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes

/**
 * Roman typing (Beta), under Languages: the user's own Roman to native words for Urdu and Hindi. A language switch,
 * the list, an add dialog with two fields, edit on tap and a confirm before delete. The words are user content and
 * go nowhere but Room. The app context has no field decision, so writes are blocked only in incognito (the
 * repository's `canLearn`).
 */
@Composable
fun RomanTypingScreen() = FlorisScreen {
    title = stringRes(R.string.roman_typing__title)
    previewFieldVisible = true
    scrollable = false

    val context = LocalContext.current
    val repo = remember { context.translitMappings().value }
    val scope = rememberCoroutineScope()
    var lang by remember { mutableStateOf(TranslitLang.UR) }
    val rows by remember(lang) { repo.observe(lang).catch { emit(emptyList()) } }
        .collectAsState(initial = emptyList())

    content {
        RomanTypingContent(
            lang = lang,
            onLang = { lang = it },
            rows = rows,
            onSave = { roman, native, replaceId ->
                // Room off the main thread; a failure is not shown with its text and does not crash.
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            if (repo.save(lang, roman, native)) replaceId?.let { repo.delete(it) }
                        }
                    }
                }
            },
            onDelete = { id -> scope.launch { runCatching { withContext(Dispatchers.IO) { repo.delete(id) } } } },
        )
    }
}

/** What the add or edit dialog is for: a new word, or [row] being edited. */
private data class Editing(val row: TranslitMappingEntity?)

@Composable
internal fun RomanTypingContent(
    lang: TranslitLang,
    onLang: (TranslitLang) -> Unit,
    rows: List<TranslitMappingEntity>,
    /** [replaceId] is the row being edited when its Roman word changed; it goes once the new row is saved. */
    onSave: (roman: String, native: String, replaceId: Long?) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    var editing by remember { mutableStateOf<Editing?>(null) }
    var deleting by remember { mutableStateOf<TranslitMappingEntity?>(null) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            text = stringRes(R.string.roman_typing__summary),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
            for (l in TranslitLang.entries) {
                TextButton(onClick = { onLang(l) }) {
                    val name = stringRes(
                        if (l == TranslitLang.UR) R.string.roman_typing__language_ur else R.string.roman_typing__language_hi,
                    )
                    Text(if (l == lang) "● $name" else name)
                }
            }
        }
        ListItem(
            modifier = Modifier.clickable { editing = Editing(null) },
            headlineContent = { Text(stringRes(R.string.roman_typing__add)) },
        )
        if (rows.isEmpty()) {
            Text(
                text = stringRes(R.string.roman_typing__empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        for (row in rows) {
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { editing = Editing(row) },
                headlineContent = { Text(row.roman) },
                supportingContent = { Text(row.native) },
                trailingContent = {
                    TextButton(onClick = { deleting = row }) { Text(stringRes(R.string.roman_typing__delete)) }
                },
            )
        }
    }

    editing?.let { e ->
        MappingDialog(
            initial = e.row,
            onDismiss = { editing = null },
            onSave = { roman, native ->
                onSave(roman, native, e.row?.takeIf { it.roman != roman }?.id)
                editing = null
            },
        )
    }
    deleting?.let { row ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringRes(R.string.roman_typing__delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { deleting = null; onDelete(row.id) }) {
                    Text(stringRes(R.string.roman_typing__delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringRes(R.string.roman_typing__cancel)) }
            },
        )
    }
}

@Composable
private fun MappingDialog(
    initial: TranslitMappingEntity?,
    onDismiss: () -> Unit,
    onSave: (roman: String, native: String) -> Unit,
) {
    var roman by remember { mutableStateOf(initial?.roman.orEmpty()) }
    var native by remember { mutableStateOf(initial?.native.orEmpty()) }
    val key = roman.trim().lowercase(java.util.Locale.ROOT)
    val romanOk = CustomMappingsRepository.isValidRoman(key)
    val nativeOk = CustomMappingsRepository.isValidNative(native.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(if (initial == null) R.string.roman_typing__add else R.string.roman_typing__edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = roman,
                    onValueChange = { roman = it },
                    label = { Text(stringRes(R.string.roman_typing__roman_label)) },
                    singleLine = true,
                    isError = roman.isNotEmpty() && !romanOk,
                    supportingText = {
                        if (roman.isNotEmpty() && !romanOk) Text(stringRes(R.string.roman_typing__roman_error))
                    },
                )
                OutlinedTextField(
                    value = native,
                    onValueChange = { native = it },
                    label = { Text(stringRes(R.string.roman_typing__native_label)) },
                    singleLine = true,
                    isError = native.isNotEmpty() && !nativeOk,
                    supportingText = {
                        if (native.isNotEmpty() && !nativeOk) Text(stringRes(R.string.roman_typing__native_error))
                    },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = romanOk && nativeOk, onClick = { onSave(key, native.trim()) }) {
                Text(stringRes(R.string.roman_typing__save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(R.string.roman_typing__cancel)) } },
    )
}
