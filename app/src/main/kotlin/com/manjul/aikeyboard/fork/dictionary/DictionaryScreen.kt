/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.dictionary

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.ads.DictionaryNativeAdSlot
import com.manjul.aikeyboard.core.content.dictionary.Attribution
import com.manjul.aikeyboard.core.content.dictionary.Definition
import com.manjul.aikeyboard.core.content.dictionary.LookupResult
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.tts.TtsAvailability
import com.manjul.aikeyboard.core.tts.TtsManager
import com.manjul.aikeyboard.dictionary
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.fork.routes.DictionaryHandoff
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import com.manjul.aikeyboard.ttsManager
import java.util.Locale
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

internal class DictionaryHolder(context: Context, saved: androidx.lifecycle.SavedStateHandle) : ViewModel() {
    private val graph = context.dictionary().value
    val store: FeatureSettingsStore = context.featureSettingsStore().value
    val speaker = DictionarySpeaker(context.ttsManager().value)
    val vm = DictionaryViewModel(
        saved = saved,
        lookup = { word, online -> graph.repository.lookup(word, allowNetwork = online) },
        connectivity = graph.connectivity(),
        scope = viewModelScope,
    )

    override fun onCleared() = speaker.release()
}

/**
 * The Dictionary screen (S-16, PRD FR-21; route `dictionary`, from Home, "Define" and the keyboard card's "More").
 * The offline pack first; kb-api only for a word the pack lacks, while online, with "Online dictionary" on and AI
 * consent given (plan decision 6, F1). The query survives connectivity changes (FR-21 AC2). The footer shows the
 * attribution and the licence link (FR-21 AC3); the one native ad sits below it, as the last item.
 */
@Composable
fun DictionaryLookupScreen() = FlorisScreen {
    title = stringRes(R.string.dictionary__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current.applicationContext
    val holder = viewModel<DictionaryHolder>(
        factory = viewModelFactory { initializer { DictionaryHolder(context, createSavedStateHandle()) } },
    )
    val vm = holder.vm
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // A word from the keyboard card's "More", taken once
        DictionaryHandoff.take()?.let(vm::open)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { holder.speaker.stop() }
    val state by vm.state.collectAsState()
    val settings by holder.store.flow.collectAsState(initial = holder.store.current)

    actions {
        var menu by remember { mutableStateOf(false) }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringRes(R.string.dictionary__more_cd))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(R.string.dictionary__online_setting)) },
                trailingIcon = { Checkbox(checked = settings.onlineDictionary, onCheckedChange = null) },
                onClick = {
                    val on = !settings.onlineDictionary
                    holder.viewModelScope.launch { holder.store.update { it.copy(onlineDictionary = on) } }
                },
            )
        }
    }

    content {
        val scope = holder.viewModelScope
        DictionaryContent(
            state = state,
            onQueryChange = vm::onQueryChange,
            onSubmit = vm::submit,
            onSpeak = { text ->
                scope.launch {
                    if (holder.speaker.speak(text) == TtsAvailability.MISSING_DATA) {
                        start(context, holder.speaker.installVoiceIntent())
                    }
                }
            },
            onOpenLicence = { url -> start(context, Intent(Intent.ACTION_VIEW, url.toUri())) },
        )
    }
}

private fun start(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No browser or TTS screen on this device: nothing to open.
    }
}

/** The screen body. Split out so tests can render it with a fake lookup. */
@Composable
internal fun DictionaryContent(
    state: DictionaryUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSpeak: (String) -> Unit,
    onOpenLicence: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringRes(R.string.dictionary__search_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSubmit, enabled = state.query.isNotBlank()) {
                Text(stringRes(R.string.dictionary__search))
            }
            if (!state.online) {
                Text(stringRes(R.string.dictionary__offline), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        state.result?.let { result ->
            DictionaryEntry(word = state.submitted, result = result, onSpeak = onSpeak)
            Footer(result.attribution, onOpenLicence)
        }
        // S5-dict: native_dictionary, below the fold and below the footer, as the last item
        DictionaryNativeAdSlot()
    }
}

/** The entry view: part of speech, definitions, examples, synonyms and a speak button. Shared with "Define". */
@Composable
internal fun DictionaryEntry(word: String, result: LookupResult, onSpeak: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = result.definitions.firstOrNull()?.headword ?: word,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (result.definitions.isNotEmpty()) {
                IconButton(onClick = { onSpeak(result.definitions.first().headword) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = stringRes(R.string.dictionary__speak_cd),
                    )
                }
            }
        }
        when {
            result.definitions.isNotEmpty() -> result.definitions.groupBy { it.pos }.forEach { (pos, senses) ->
                Text(pos, style = MaterialTheme.typography.titleSmall, fontStyle = FontStyle.Italic)
                senses.forEachIndexed { i, sense -> Sense(i + 1, sense) }
            }
            result.packMissing && result.offline -> Text(stringRes(R.string.dictionary__no_pack))
            else -> Text(stringRes(R.string.dictionary__not_found))
        }
    }
}

@Composable
private fun Sense(number: Int, sense: Definition) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$number. ${sense.definition}")
        sense.example?.let {
            Text(
                text = stringRes(R.string.dictionary__example, "example" to it),
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
            )
        }
        if (sense.synonyms.isNotEmpty()) {
            Text(
                text = stringRes(R.string.dictionary__synonyms, "synonyms" to sense.synonyms.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** FR-21 AC3: who wrote the text and under which licence, with the licence link. */
@Composable
internal fun Footer(attribution: Attribution?, onOpenLicence: (String) -> Unit) {
    attribution ?: return
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringRes(
                R.string.dictionary__attribution,
                "source" to attribution.source,
                "attribution" to attribution.attribution,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { onOpenLicence(attribution.licenceUrl) }) {
            Text(
                text = stringRes(R.string.dictionary__licence, "licence" to attribution.licenceName),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Holds the shared `TtsManager` from the first speak until the screen or sheet goes away. */
internal class DictionarySpeaker(private val tts: TtsManager) {
    private var acquired = false

    suspend fun speak(text: String): TtsAvailability {
        if (!acquired) {
            acquired = true
            tts.acquire()
        }
        val availability = runCatching { tts.availability(Locale.ENGLISH) }.getOrDefault(TtsAvailability.NOT_SUPPORTED)
        if (availability == TtsAvailability.AVAILABLE) tts.speak(text, Locale.ENGLISH)
        return availability
    }

    fun installVoiceIntent(): Intent = tts.installVoiceIntent()

    fun stop() {
        if (acquired) tts.stop()
    }

    fun release() {
        if (acquired) {
            acquired = false
            tts.release()
        }
    }
}
