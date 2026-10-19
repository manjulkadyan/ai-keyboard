/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.VoiceEnginePreference
import com.manjul.aikeyboard.core.voice.whisper.ModelState
import com.manjul.aikeyboard.core.voice.whisper.WhisperModelSpec
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import com.manjul.aikeyboard.whisperVoice
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

private const val SILENCE_MIN_MS = 2000
private const val SILENCE_MAX_MS = 5000
private const val SILENCE_STEP_MS = 500

/** The auto-stop slider value as stored: 2 to 5 seconds in half-second steps. */
internal fun silenceMsFromSlider(value: Float): Int =
    ((value / SILENCE_STEP_MS).roundToInt() * SILENCE_STEP_MS).coerceIn(SILENCE_MIN_MS, SILENCE_MAX_MS)

/** Voice settings (a Settings home entry): speech engine, punctuation, auto-stop and the offline models. */
@Composable
fun VoiceSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.voice_settings__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val store = remember { context.featureSettingsStore().value }
    val whisper = remember { context.whisperVoice().value }
    val scope = rememberCoroutineScope()
    val settings by store.flow.collectAsState(initial = store.current)
    val states by whisper.models.states.collectAsState()
    val offered = remember { whisper.models.offered() }

    content {
        VoiceSettingsContent(
            settings = settings,
            onUpdate = { transform -> scope.launch { store.update(transform) } },
            whisperOffered = whisper.gate.anyEnabled,
            models = states.filterKeys { it in offered },
            onDownload = { whisper.models.download(it, settings.whisperWifiOnly) },
            onDelete = { whisper.models.delete(it) },
        )
    }
}

/** The screen body; [models] lists the models this device can run. */
@Composable
internal fun VoiceSettingsContent(
    settings: FeatureSettings,
    onUpdate: ((FeatureSettings) -> FeatureSettings) -> Unit,
    whisperOffered: Boolean,
    models: Map<WhisperModelSpec, ModelState>,
    onDownload: (WhisperModelSpec) -> Unit,
    onDelete: (WhisperModelSpec) -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(stringRes(R.string.voice_settings__engine))
        Column(modifier = Modifier.selectableGroup()) {
            val options = buildList {
                add(Triple(VoiceEnginePreference.AUTO, R.string.voice_settings__engine_auto,
                    R.string.voice_settings__engine_auto_summary))
                add(Triple(VoiceEnginePreference.ON_DEVICE, R.string.voice_settings__engine_on_device,
                    R.string.voice_settings__engine_on_device_summary))
                if (whisperOffered) {
                    add(Triple(VoiceEnginePreference.WHISPER, R.string.voice_settings__engine_whisper,
                        R.string.voice_settings__engine_whisper_summary))
                }
            }
            for ((preference, title, summary) in options) {
                val selected = settings.voiceEnginePreference == preference
                ListItem(
                    modifier = Modifier.selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onUpdate { it.copy(voiceEnginePreference = preference) } },
                    ),
                    headlineContent = { Text(stringRes(title)) },
                    supportingContent = { Text(stringRes(summary)) },
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                )
            }
        }
        val noModel = models.values.none { it == ModelState.READY }
        if (whisperOffered && settings.voiceEnginePreference == VoiceEnginePreference.WHISPER && noModel) {
            Text(
                text = stringRes(R.string.voice_settings__needs_model),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        SwitchRow(
            title = stringRes(R.string.voice_settings__spoken_punctuation),
            summary = stringRes(R.string.voice_settings__spoken_punctuation_summary),
            checked = settings.spokenPunctuation,
            onChange = { on -> onUpdate { it.copy(spokenPunctuation = on) } },
        )

        val silence = settings.voiceSilenceMs.coerceIn(SILENCE_MIN_MS, SILENCE_MAX_MS)
        ListItem(
            headlineContent = { Text(stringRes(R.string.voice_settings__auto_stop)) },
            supportingContent = {
                Text(stringRes(R.string.voice_settings__auto_stop_value, "s" to formatSeconds(silence)))
            },
        )
        Slider(
            value = silence.toFloat(),
            onValueChange = { value ->
                val ms = silenceMsFromSlider(value)
                if (ms != settings.voiceSilenceMs) onUpdate { it.copy(voiceSilenceMs = ms) }
            },
            valueRange = SILENCE_MIN_MS.toFloat()..SILENCE_MAX_MS.toFloat(),
            steps = (SILENCE_MAX_MS - SILENCE_MIN_MS) / SILENCE_STEP_MS - 1,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (whisperOffered) {
            Header(stringRes(R.string.voice_settings__models))
            for ((spec, state) in models) {
                val size = Formatter.formatShortFileSize(context, spec.bytes)
                val status = when (state) {
                    ModelState.ABSENT -> R.string.voice_settings__model_absent
                    ModelState.DOWNLOADING -> R.string.voice_settings__model_downloading
                    ModelState.READY -> R.string.voice_settings__model_ready
                    ModelState.FAILED -> R.string.voice_settings__model_failed
                }
                ListItem(
                    headlineContent = { Text(stringRes(spec.titleRes())) },
                    supportingContent = { Text(stringRes(status, "size" to size)) },
                    trailingContent = {
                        Row {
                            when (state) {
                                ModelState.ABSENT, ModelState.FAILED -> TextButton(onClick = { onDownload(spec) }) {
                                    Text(stringRes(spec.downloadRes()))
                                }
                                ModelState.DOWNLOADING -> TextButton(onClick = { onDelete(spec) }) {
                                    Text(stringRes(spec.cancelRes()))
                                }
                                ModelState.READY -> TextButton(onClick = { onDelete(spec) }) {
                                    Text(stringRes(spec.deleteRes()))
                                }
                            }
                        }
                    },
                )
            }
            SwitchRow(
                title = stringRes(R.string.voice_settings__wifi_only),
                summary = null,
                checked = settings.whisperWifiOnly,
                onChange = { on -> onUpdate { it.copy(whisperWifiOnly = on) } },
            )
        }
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

/** 2000 -> "2", 2500 -> "2.5". */
private fun formatSeconds(ms: Int): String =
    if (ms % 1000 == 0) (ms / 1000).toString() else "%.1f".format(ms / 1000f)

private fun WhisperModelSpec.titleRes() = when (this) {
    WhisperModelSpec.BASE -> R.string.voice_settings__model_base
    WhisperModelSpec.SMALL -> R.string.voice_settings__model_small
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
