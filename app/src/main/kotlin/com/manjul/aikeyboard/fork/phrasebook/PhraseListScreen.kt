/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.phrasebook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.content.phrasebook.Phrase
import com.manjul.aikeyboard.core.content.phrasebook.PhrasebookRepository
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.fork.translator.displayName
import org.florisboard.lib.compose.stringRes

/**
 * The rows of one category. A tap speaks the shown text. Each row has a favourite star and a menu with "Send via
 * keyboard", "Copy" and, for the user's own phrase, "Edit" and "Delete". Without the target pack the rows show English
 * and the header offers "Download language" (FR-22 AC2). Split out so tests can render it with fake rows.
 */
@Composable
internal fun PhraseListContent(
    state: PhrasebookUiState,
    onSpeak: (Phrase) -> Unit,
    onFavourite: (Phrase) -> Unit,
    onShare: (Phrase) -> Unit,
    onCopy: (Phrase) -> Unit,
    onEdit: (Phrase) -> Unit,
    onDelete: (Phrase) -> Unit,
    onDownload: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (state.loading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        if (state.packMissing) {
            item {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val language = displayName(Lang.of(state.target))
                    Text(
                        text = stringRes(R.string.phrasebook__no_pack, "language" to language),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onDownload) { Text(stringRes(R.string.phrasebook__download)) }
                }
                HorizontalDivider()
            }
        }
        if (!state.loading && state.phrases.isEmpty()) {
            val empty = when (state.category) {
                PhrasebookRepository.FAVOURITES -> R.string.phrasebook__empty_favourites
                PhrasebookRepository.MY_PHRASES -> R.string.phrasebook__empty_own
                else -> null
            }
            if (empty != null) item { Text(stringRes(empty), modifier = Modifier.padding(16.dp)) }
        }
        items(state.phrases, key = { it.ref }) { phrase ->
            PhraseRow(phrase, onSpeak, onFavourite, onShare, onCopy, onEdit, onDelete)
            HorizontalDivider()
        }
    }
}

@Composable
private fun PhraseRow(
    phrase: Phrase,
    onSpeak: (Phrase) -> Unit,
    onFavourite: (Phrase) -> Unit,
    onShare: (Phrase) -> Unit,
    onCopy: (Phrase) -> Unit,
    onEdit: (Phrase) -> Unit,
    onDelete: (Phrase) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onSpeak(phrase) }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val translation = phrase.translation
            if (translation != null) {
                Text(translation, style = MaterialTheme.typography.bodyLarge)
                Text(phrase.text, style = MaterialTheme.typography.bodySmall)
                if (phrase.machine) {
                    Text(
                        text = stringRes(R.string.phrasebook__machine),
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                    )
                }
            } else {
                Text(phrase.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        IconButton(onClick = { onFavourite(phrase) }) {
            Icon(
                if (phrase.favourite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = stringRes(
                    if (phrase.favourite) R.string.phrasebook__unfavourite_cd else R.string.phrasebook__favourite_cd,
                ),
            )
        }
        var menu by remember { mutableStateOf(false) }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringRes(R.string.phrasebook__more_cd))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(R.string.phrasebook__send)) },
                onClick = { menu = false; onShare(phrase) },
            )
            DropdownMenuItem(
                text = { Text(stringRes(R.string.phrasebook__copy)) },
                onClick = { menu = false; onCopy(phrase) },
            )
            if (phrase.own) {
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.phrasebook__edit)) },
                    onClick = { menu = false; onEdit(phrase) },
                )
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.phrasebook__delete)) },
                    onClick = { menu = false; onDelete(phrase) },
                )
            }
        }
    }
}
