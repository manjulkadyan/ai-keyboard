/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translator

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.database.TranslationHistoryEntity
import com.manjul.aikeyboard.core.database.TranslationHistoryRepository
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import com.manjul.aikeyboard.translationHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/** Search, favourites filter and row actions over [TranslationHistoryRepository]. Rows hold user text: never logged. */
class HistoryViewModel(private val history: TranslationHistoryRepository, private val scope: CoroutineScope) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<TranslationHistoryEntity>> =
        combine(_query, _favoritesOnly) { q, fav -> q to fav }
            .flatMapLatest { (q, fav) -> history.page(q, fav) }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun setQuery(query: String) {
        _query.value = query
    }

    fun setFavoritesOnly(on: Boolean) {
        _favoritesOnly.value = on
    }

    fun toggleFavorite(row: TranslationHistoryEntity) {
        scope.launch { history.setFavorite(row.id, !row.isFavorite) }
    }

    fun delete(row: TranslationHistoryEntity) {
        scope.launch { history.delete(row.id) }
    }

    fun clearAll(keepFavorites: Boolean) {
        scope.launch { history.clearAll(keepFavorites) }
    }
}

internal class HistoryHolder(context: Context) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val vm = HistoryViewModel(context.translationHistory().value, scope)

    override fun onCleared() {
        scope.cancel()
    }
}

/** Translator history (route `translator/history`): search, favourites, swipe to delete and clear all. */
@Composable
fun HistoryScreen() = FlorisScreen {
    title = stringRes(R.string.history__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current.applicationContext
    val holder = viewModel<HistoryHolder>(factory = viewModelFactory { initializer { HistoryHolder(context) } })
    var confirm by remember { mutableStateOf(false) }

    actions {
        IconButton(onClick = { confirm = true }) {
            Icon(Icons.Default.DeleteSweep, contentDescription = stringRes(R.string.history__clear_all))
        }
    }

    content {
        HistoryContent(holder.vm, confirmClear = confirm, onConfirmClear = { confirm = it })
    }
}

/** The screen body; [confirmClear] shows the clear-all dialog. Split out so tests can drive it with a fake DAO. */
@Composable
internal fun HistoryContent(vm: HistoryViewModel, confirmClear: Boolean, onConfirmClear: (Boolean) -> Unit) {
    val query by vm.query.collectAsState()
    val favoritesOnly by vm.favoritesOnly.collectAsState()
    val items by vm.items.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                singleLine = true,
                label = { Text(stringRes(R.string.history__search)) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = favoritesOnly,
                onClick = { vm.setFavoritesOnly(!favoritesOnly) },
                label = { Text(stringRes(R.string.history__favorites_only)) },
            )
        }
        if (items.isEmpty()) {
            Text(
                text = stringRes(R.string.history__empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items, key = { it.id }) { row ->
                HistoryRow(row, onFavorite = { vm.toggleFavorite(row) }, onDelete = { vm.delete(row) })
            }
        }
    }
    if (confirmClear) {
        ClearAllDialog(
            onConfirm = { keep ->
                onConfirmClear(false)
                vm.clearAll(keep)
            },
            onDismiss = { onConfirmClear(false) },
        )
    }
}

@Composable
private fun HistoryRow(row: TranslationHistoryEntity, onFavorite: () -> Unit, onDelete: () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val deleteLabel = stringRes(R.string.history__delete_cd)
    SwipeToDismissBox(
        state = state,
        backgroundContent = {},
        onDismiss = { onDelete() },
        modifier = Modifier.semantics {
            // Swiping has no TalkBack equivalent, so delete is also a custom action.
            customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
        },
    ) {
        ListItem(
            overlineContent = {
                Text("${displayName(Lang(row.sourceLang))} → ${displayName(Lang(row.targetLang))}")
            },
            headlineContent = {
                Text(
                    row.sourceText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                )
            },
            supportingContent = {
                Text(
                    row.targetText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                )
            },
            trailingContent = {
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (row.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringRes(
                            if (row.isFavorite) R.string.translator__favorite_remove else R.string.translator__favorite_add,
                        ),
                    )
                }
            },
        )
    }
}

@Composable
private fun ClearAllDialog(onConfirm: (keepFavorites: Boolean) -> Unit, onDismiss: () -> Unit) {
    var keep by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.history__clear_title)) },
        text = {
            Column {
                Text(stringRes(R.string.history__clear_body))
                Row(
                    modifier = Modifier.toggleable(value = keep, role = Role.Checkbox, onValueChange = { keep = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = keep, onCheckedChange = null)
                    Text(stringRes(R.string.history__keep_favorites))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(keep) }) { Text(stringRes(R.string.history__clear_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.history__cancel)) }
        },
    )
}
