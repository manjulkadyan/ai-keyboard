/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.mlkit.ModelUiState
import com.manjul.aikeyboard.core.voice.platform.PlatformRecognizerCapabilities
import com.manjul.aikeyboard.core.voice.whisper.ModelState
import com.manjul.aikeyboard.core.voice.whisper.WhisperModelSpec
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import com.manjul.aikeyboard.translateModelStore
import com.manjul.aikeyboard.whisperVoice
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.florisboard.lib.compose.stringRes

/** Keeps the view model (and its scope) across rotation. */
internal class OfflineLanguagesHolder(context: Context) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val whisper = context.whisperVoice().value

    // The gate is fixed per build: with no Whisper language (release) the Whisper model manager is never created.
    val whisperOffered = whisper.gate.anyEnabled
    val vm = OfflineLanguagesViewModel(
        store = context.translateModelStore().value,
        settings = context.featureSettingsStore().value,
        whisperStates = if (whisperOffered) whisper.models.states else null,
        whisperOffered = if (whisperOffered) whisper.models.offered() else emptyList(),
        onDeviceRecognizer = PlatformRecognizerCapabilities(context).isOnDeviceAvailable(),
        scope = scope,
    )

    fun downloadWhisper(spec: WhisperModelSpec, wifiOnly: Boolean) = whisper.models.download(spec, wifiOnly)

    fun deleteWhisper(spec: WhisperModelSpec) = whisper.models.delete(spec)

    override fun onCleared() {
        scope.cancel()
    }
}

/**
 * Offline languages (PRD FR-36; route `settings/offline-languages`, also opened from the keyboard's offline chip):
 * per language size, status, download and delete; an indeterminate indicator with "Stop" while downloading (ML Kit
 * gives no progress, RN-06); the Wi-Fi-only switch; the storage used; Whisper models and the system recognizer.
 */
@Composable
fun OfflineLanguagesScreen() = FlorisScreen {
    title = stringRes(R.string.offline_languages__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current.applicationContext
    val holder = viewModel<OfflineLanguagesHolder>(factory = viewModelFactory { initializer { OfflineLanguagesHolder(context) } })
    val vm = holder.vm
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val state by vm.state.collectAsState()

    content {
        OfflineLanguagesContent(
            state = state,
            onDownload = vm::download,
            onStop = vm::stop,
            onDelete = vm::delete,
            onWifiOnly = vm::setWifiOnly,
            onWhisperDownload = { holder.downloadWhisper(it, state.wifiOnly) },
            onWhisperDelete = holder::deleteWhisper,
            onRecognizerSettings = {
                try {
                    context.startActivity(OfflineLanguagesViewModel.recognizerSettingsIntent())
                } catch (_: ActivityNotFoundException) {
                    // Some builds have no voice input settings screen; the row then does nothing.
                }
            },
        )
    }
}

@Composable
internal fun OfflineLanguagesContent(
    state: OfflineLanguagesUiState,
    onDownload: (Lang) -> Unit,
    onStop: (Lang) -> Unit,
    onDelete: (Lang) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onWhisperDownload: (WhisperModelSpec) -> Unit,
    onWhisperDelete: (WhisperModelSpec) -> Unit,
    onRecognizerSettings: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text(stringRes(R.string.offline_languages__storage)) },
                supportingContent = {
                    Text(
                        stringRes(
                            R.string.offline_languages__storage_value,
                            "size" to Formatter.formatShortFileSize(context, state.storageBytes),
                        ),
                    )
                },
            )
            ListItem(
                modifier = Modifier.toggleable(value = state.wifiOnly, role = Role.Switch, onValueChange = onWifiOnly),
                headlineContent = { Text(stringRes(R.string.offline_languages__wifi_only)) },
                supportingContent = { Text(stringRes(R.string.offline_languages__wifi_only_summary)) },
                trailingContent = { Switch(checked = state.wifiOnly, onCheckedChange = null) },
            )
            Header(stringRes(R.string.offline_languages__translation))
        }
        items(state.languages, key = { it.lang.tag }) { row ->
            LanguageRow(row, onDownload, onStop, onDelete)
        }
        item {
            Header(stringRes(R.string.offline_languages__voice))
            ListItem(
                headlineContent = { Text(stringRes(R.string.offline_languages__recognizer)) },
                supportingContent = {
                    Text(
                        stringRes(
                            if (state.onDeviceRecognizer) {
                                R.string.offline_languages__recognizer_on_device
                            } else {
                                R.string.offline_languages__recognizer_none
                            },
                        ),
                    )
                },
                trailingContent = {
                    TextButton(onClick = onRecognizerSettings) {
                        Text(stringRes(R.string.offline_languages__recognizer_open))
                    }
                },
            )
        }
        items(state.whisper, key = { "whisper-" + it.first.name }) { (spec, modelState) ->
            WhisperRow(spec, modelState, onWhisperDownload, onWhisperDelete)
        }
    }
}

@Composable
private fun LanguageRow(
    row: TranslateLanguageRow,
    onDownload: (Lang) -> Unit,
    onStop: (Lang) -> Unit,
    onDelete: (Lang) -> Unit,
) {
    val context = LocalContext.current
    val locale = Locale.forLanguageTag(row.lang.tag)
    val name = locale.getDisplayName(Locale.getDefault())
    val size = Formatter.formatShortFileSize(context, row.bytes)
    val status = when {
        row.cloudOnly -> stringRes(R.string.offline_languages__cloud_only)
        row.state == ModelUiState.ABSENT -> stringRes(R.string.offline_languages__absent, "size" to size)
        row.state == ModelUiState.DOWNLOADING -> stringRes(R.string.offline_languages__downloading, "size" to size)
        row.state == ModelUiState.FAILED -> stringRes(R.string.offline_languages__failed, "size" to size)
        !row.deletable -> stringRes(R.string.offline_languages__ready_pivot, "size" to size)
        else -> stringRes(R.string.offline_languages__ready, "size" to size)
    }
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(status) },
        trailingContent = {
            if (row.cloudOnly) return@ListItem
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (row.state) {
                    ModelUiState.ABSENT, ModelUiState.FAILED -> {
                        val a11y = stringRes(R.string.offline_languages__download_a11y, "language" to name)
                        TextButton(onClick = { onDownload(row.lang) }, modifier = Modifier.semantics { contentDescription = a11y }) {
                            Text(stringRes(R.string.offline_languages__download))
                        }
                    }
                    ModelUiState.DOWNLOADING -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        val a11y = stringRes(R.string.offline_languages__stop_a11y, "language" to name)
                        TextButton(onClick = { onStop(row.lang) }, modifier = Modifier.semantics { contentDescription = a11y }) {
                            Text(stringRes(R.string.offline_languages__stop))
                        }
                    }
                    ModelUiState.READY -> {
                        val a11y = stringRes(R.string.offline_languages__delete_a11y, "language" to name)
                        TextButton(
                            onClick = { onDelete(row.lang) },
                            enabled = row.deletable,
                            modifier = Modifier.semantics { contentDescription = a11y },
                        ) {
                            Text(stringRes(R.string.offline_languages__delete))
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun WhisperRow(
    spec: WhisperModelSpec,
    state: ModelState,
    onDownload: (WhisperModelSpec) -> Unit,
    onDelete: (WhisperModelSpec) -> Unit,
) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, spec.bytes)
    val title = when (spec) {
        WhisperModelSpec.BASE -> R.string.offline_languages__whisper_base
        WhisperModelSpec.SMALL -> R.string.offline_languages__whisper_small
    }
    val status = when (state) {
        ModelState.ABSENT -> R.string.voice_settings__model_absent
        ModelState.DOWNLOADING -> R.string.voice_settings__model_downloading
        ModelState.READY -> R.string.voice_settings__model_ready
        ModelState.FAILED -> R.string.voice_settings__model_failed
    }
    ListItem(
        headlineContent = { Text(stringRes(title)) },
        supportingContent = { Text(stringRes(status, "size" to size)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (state) {
                    ModelState.ABSENT, ModelState.FAILED -> TextButton(onClick = { onDownload(spec) }) {
                        Text(stringRes(spec.downloadRes()))
                    }
                    ModelState.DOWNLOADING -> {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        TextButton(onClick = { onDelete(spec) }) { Text(stringRes(spec.cancelRes())) }
                    }
                    ModelState.READY -> TextButton(onClick = { onDelete(spec) }) { Text(stringRes(spec.deleteRes())) }
                }
            }
        },
    )
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

private fun WhisperModelSpec.downloadRes() = when (this) {
    WhisperModelSpec.BASE -> R.string.voice_settings__download_base
    WhisperModelSpec.SMALL -> R.string.voice_settings__download_small
}

private fun WhisperModelSpec.deleteRes() = when (this) {
    WhisperModelSpec.BASE -> R.string.voice_settings__delete_base
    WhisperModelSpec.SMALL -> R.string.voice_settings__delete_small
}

private fun WhisperModelSpec.cancelRes() = when (this) {
    WhisperModelSpec.BASE -> R.string.voice_settings__cancel_base
    WhisperModelSpec.SMALL -> R.string.voice_settings__cancel_small
}
