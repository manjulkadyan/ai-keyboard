/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.phrasebook

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.LocalNavController
import com.manjul.aikeyboard.core.content.phrasebook.Phrase
import com.manjul.aikeyboard.core.content.phrasebook.PhraseCategory
import com.manjul.aikeyboard.core.content.phrasebook.PhrasebookRepository
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.tts.TtsAvailability
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.fork.translator.displayName
import com.manjul.aikeyboard.fork.tts.ScreenSpeaker
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import com.manjul.aikeyboard.phrasebook
import com.manjul.aikeyboard.ttsManager
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

internal class PhrasebookHolder(
    context: Context,
    saved: SavedStateHandle,
    private val notSaved: () -> Unit,
) : ViewModel() {
    private val graph = context.phrasebook().value
    val speaker = ScreenSpeaker(context.ttsManager().value)
    var repository: PhrasebookRepository? = null
        private set
    var unavailable by mutableStateOf(false)
        private set

    val vm = PhrasebookViewModel(
        saved = saved,
        categories = {
            graph.refreshPacks()
            val repo = graph.repository()
            repository = repo
            unavailable = repo == null
            repo?.categories().orEmpty()
        },
        phrases = { category, target ->
            // The repository opens first (the categories call); until then there are no rows.
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            flow { emit(graph.repository()) }.flatMapLatest { it?.phrases(category, target) ?: emptyFlow() }
        },
        packReady = graph::packReady,
        settings = context.featureSettingsStore().value.current,
        scope = viewModelScope,
    )

    fun download() = graph.download(vm.state.value.target)

    /** Runs a write (add, edit, delete, star). A failure shows "Phrase not saved" and logs nothing (review I1). */
    fun edit(block: suspend PhrasebookRepository.() -> Unit) {
        viewModelScope.launch {
            val saved = phraseWrite { checkNotNull(graph.repository()) { "phrasebook unavailable" }.block() }
            if (!saved) notSaved()
        }
    }

    override fun onCleared() = speaker.release()
}

/**
 * The Phrasebook screen (S-17, PRD FR-22; route `phrasebook`, from Home). The category list first, with the virtual
 * "Favourites" and "My phrases" on top; a category shows its phrases in the chosen target language. Everything works
 * offline. No ad on this screen (decision 19).
 */
@Composable
fun PhrasebookScreen() = FlorisScreen {
    title = stringRes(R.string.phrasebook__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current.applicationContext
    val holder = viewModel<PhrasebookHolder>(
        factory = viewModelFactory {
            initializer {
                PhrasebookHolder(context, createSavedStateHandle()) { toast(context, R.string.phrasebook__not_saved) }
            }
        },
    )
    val vm = holder.vm
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { holder.speaker.stop() }
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<Phrase?>(null) }
    var adding by remember { mutableStateOf(false) }

    navigationIcon {
        val navController = LocalNavController.current
        IconButton(onClick = { if (!vm.closeCategory()) navController.popBackStack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        }
    }

    actions {
        var menu by remember { mutableStateOf(false) }
        TextButton(onClick = { menu = true }) {
            Icon(Icons.Default.Translate, contentDescription = stringRes(R.string.phrasebook__target_cd))
            Text(
                text = stringRes(R.string.phrasebook__target_label, "language" to displayName(Lang.of(state.target))),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            for (tag in PhrasebookViewModel.TARGETS) {
                DropdownMenuItem(
                    text = { Text(displayName(Lang.of(tag))) },
                    onClick = {
                        menu = false
                        vm.setTarget(tag)
                    },
                )
            }
        }
    }

    floatingActionButton {
        if (state.category == PhrasebookRepository.MY_PHRASES) {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringRes(R.string.phrasebook__add)) },
            )
        }
    }

    content {
        BackHandler(enabled = state.category != null) { vm.closeCategory() }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                holder.unavailable -> Text(stringRes(R.string.phrasebook__unavailable), Modifier.padding(16.dp))
                state.category == null -> CategoryList(state.categories, onOpen = vm::openCategory)
                else -> PhraseListContent(
                    state = state,
                    onSpeak = { phrase ->
                        holder.viewModelScope.launch {
                            val tag = if (phrase.translation != null) state.target else PhrasebookRepository.SOURCE
                            val text = PhrasebookViewModel.shownText(phrase)
                            when (holder.speaker.speak(text, Locale.forLanguageTag(tag))) {
                                TtsAvailability.MISSING_DATA -> start(context, holder.speaker.installVoiceIntent())
                                TtsAvailability.AVAILABLE -> Unit
                                else -> toast(context, R.string.phrasebook__no_voice)
                            }
                        }
                    },
                    onFavourite = { phrase -> holder.edit { toggleFavourite(phrase.ref) } },
                    onShare = { phrase -> start(context, vm.shareIntent(phrase)) },
                    onCopy = { phrase -> copy(context, PhrasebookViewModel.shownText(phrase)) },
                    onEdit = { editing = it },
                    onDelete = { phrase -> ownId(phrase)?.let { id -> holder.edit { deleteOwn(id) } } },
                    onDownload = holder::download,
                )
            }
        }
        if (adding) {
            PhraseEditDialog(
                initial = "",
                editing = false,
                onSave = { text ->
                    adding = false
                    holder.edit { addOwn(text) }
                },
                onDismiss = { adding = false },
            )
        }
        editing?.let { phrase ->
            PhraseEditDialog(
                initial = phrase.text,
                editing = true,
                onSave = { text ->
                    editing = null
                    ownId(phrase)?.let { id -> holder.edit { editOwn(id, text) } }
                },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun CategoryList(categories: List<PhraseCategory>, onOpen: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(categories, key = { it.key }) { category ->
            Column(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(category.key) }.padding(16.dp),
            ) {
                Text(categoryName(category.key))
            }
            HorizontalDivider()
        }
    }
}

/** The name of a category, keyed by its `category_key`. An unknown key shows the key. */
@Composable
internal fun categoryName(key: String): String = when (key) {
    PhrasebookRepository.FAVOURITES -> stringRes(R.string.phrasebook__category_favourites)
    PhrasebookRepository.MY_PHRASES -> stringRes(R.string.phrasebook__category_my_phrases)
    "greetings" -> stringRes(R.string.phrasebook__category_greetings)
    "basics" -> stringRes(R.string.phrasebook__category_basics)
    "directions" -> stringRes(R.string.phrasebook__category_directions)
    "transport" -> stringRes(R.string.phrasebook__category_transport)
    "hotel" -> stringRes(R.string.phrasebook__category_hotel)
    "food" -> stringRes(R.string.phrasebook__category_food)
    "shopping" -> stringRes(R.string.phrasebook__category_shopping)
    "emergency" -> stringRes(R.string.phrasebook__category_emergency)
    "health" -> stringRes(R.string.phrasebook__category_health)
    "numbers_time" -> stringRes(R.string.phrasebook__category_numbers_time)
    "small_talk" -> stringRes(R.string.phrasebook__category_small_talk)
    "phone_internet" -> stringRes(R.string.phrasebook__category_phone_internet)
    else -> key
}

private fun ownId(phrase: Phrase): Long? = if (phrase.own) phrase.ref.removePrefix("u:").toLongOrNull() else null

private fun start(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No app takes the share or the TTS screen: nothing to open.
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.phrasebook__title), text))
    toast(context, R.string.phrasebook__copied)
}

private fun toast(context: Context, @StringRes text: Int) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

/**
 * Runs one phrasebook write and reports whether it worked. A missing phrase, a bad ref or a Room error gives false;
 * the error is dropped without a log, because its message can name the phrase. Cancellation still propagates.
 */
internal suspend fun phraseWrite(block: suspend () -> Unit): Boolean = try {
    block()
    true
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    false
}
