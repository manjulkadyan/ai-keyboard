/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translator

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.LocalNavController
import com.manjul.aikeyboard.app.Routes
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.translate.RouterContext
import com.manjul.aikeyboard.core.translate.bar.BarStatus
import com.manjul.aikeyboard.core.voice.platform.PlatformRecognizerCapabilities
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.fork.onboarding.DisclosureEngineKind
import com.manjul.aikeyboard.fork.onboarding.VoiceDisclosureSheet
import com.manjul.aikeyboard.fork.routes.TranslatorHandoff
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import com.manjul.aikeyboard.translation
import com.manjul.aikeyboard.translationHistory
import com.manjul.aikeyboard.ttsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.florisboard.lib.compose.stringRes

/** Keeps the view model (and its scope, TTS hold and recognizer) across rotation. */
internal class TranslatorHolder(context: Context) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store: FeatureSettingsStore = context.featureSettingsStore().value
    private val translation = context.translation().value

    val vm = TranslatorViewModel(
        router = { req, lastSource ->
            translation.router.translate(
                req,
                // The app screen has no field policy; cloud still needs Pro and the opt-in (Pro is false until S4).
                RouterContext(
                    allowTranslate = true,
                    allowCloud = true,
                    pro = false,
                    cloudOptIn = store.current.cloudTranslateOptIn,
                    preferQuality = false,
                ),
                lastSource,
            )
        },
        models = translation.models,
        history = context.translationHistory().value,
        tts = context.ttsManager().value.asTranslatorTts(),
        settings = store.asTranslatorSettings(),
        analytics = context.appContext().value.analytics.value::log,
        clock = System::currentTimeMillis,
        speech = PlatformTranslatorMic(context, store, scope),
        scope = scope,
    )

    val onDeviceRecognizer = PlatformRecognizerCapabilities(context).isOnDeviceAvailable()

    override fun onCleared() {
        vm.destroy()
        scope.cancel()
    }
}

/**
 * The Translator screen (PRD FR-12; route `translator`, opened from the Home "Translate" entry): pickers with swap and
 * auto-detect, a 5,000-character input with counter, mic and paste, and the result with copy, share, speak and
 * favourite. No interstitial or ad on any action here (FR-12 AC2).
 */
@Composable
fun TranslatorScreen() = FlorisScreen {
    title = stringRes(R.string.translator__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current.applicationContext
    val navController = LocalNavController.current
    val holder = viewModel<TranslatorHolder>(factory = viewModelFactory { initializer { TranslatorHolder(context) } })
    val vm = holder.vm
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refresh()
        // Text from the Translate sheet's "Open in app" (Task 11), taken once
        TranslatorHandoff.take()?.let(vm::onInput)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onLeave() }
    val state by vm.state.collectAsState()

    actions {
        IconButton(onClick = { navController.navigate(Routes.TranslatorHistory) }) {
            Icon(Icons.Default.History, contentDescription = stringRes(R.string.translator__history))
        }
        var menu by remember { mutableStateOf(false) }
        IconButton(onClick = { menu = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringRes(R.string.translator__more_cd))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringRes(R.string.translator__save_history)) },
                trailingIcon = { Checkbox(checked = state.historyEnabled, onCheckedChange = null) },
                onClick = { vm.setHistoryEnabled(!state.historyEnabled) },
            )
        }
    }

    content {
        var disclosure by remember { mutableStateOf(false) }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.startMic()
        }
        val startMic = {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                vm.startMic()
            } else {
                permission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        TranslatorContent(
            state = state,
            vm = vm,
            onMic = {
                when {
                    state.mic == MicState.Listening -> vm.stopMic()
                    // Sprint 1 rule: the voice notice comes before the first microphone request.
                    holder.store.current.consentVoiceNoticeAt <= 0 -> disclosure = true
                    else -> startMic()
                }
            },
        )
        if (disclosure) {
            VoiceDisclosureSheet(
                engineKind = {
                    if (holder.onDeviceRecognizer) DisclosureEngineKind.ON_DEVICE else DisclosureEngineKind.SYSTEM_SERVICE
                },
                store = holder.store,
                clock = System::currentTimeMillis,
                onAgree = {
                    disclosure = false
                    startMic()
                },
                onDismiss = { disclosure = false },
            )
        }
    }
}

/** The screen body; [vm] receives the actions. Split out so tests can render it with fakes behind the view model. */
@Composable
internal fun TranslatorContent(state: TranslatorUiState, vm: TranslatorViewModel, onMic: () -> Unit) {
    val context = LocalContext.current
    var picker by remember { mutableStateOf<PickerSide?>(null) }
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PairRow(state, onSource = { picker = PickerSide.SOURCE }, onTarget = { picker = PickerSide.TARGET }, onSwap = vm::swap)
        val detected = state.detected
        if (state.autoDetect && detected != null) {
            Text(
                text = stringRes(R.string.translator__detected, "language" to displayName(detected)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        InputField(state, vm::onInput)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMic) {
                if (state.mic == MicState.Listening) {
                    Icon(Icons.Default.Stop, contentDescription = stringRes(R.string.translator__mic_stop_cd))
                } else {
                    Icon(Icons.Default.Mic, contentDescription = stringRes(R.string.translator__mic_cd))
                }
            }
            TextButton(onClick = { clipboardText(context)?.let(vm::paste) }) {
                Text(stringRes(R.string.translator__paste))
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringRes(
                    R.string.translator__counter,
                    "count" to state.count,
                    "max" to TranslatorUiState.MAX_CHARS,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (state.atLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        micMessage(state.mic)?.let { Text(stringRes(it), style = MaterialTheme.typography.bodySmall) }
        ResultCard(state, vm)
    }
    when (picker) {
        PickerSide.SOURCE -> LanguagePickerSheet(
            title = stringRes(R.string.translator__picker_source),
            selected = state.pair.source,
            onPick = {
                picker = null
                vm.pickSource(it)
            },
            onDismiss = { picker = null },
            onDetect = {
                picker = null
                vm.setAutoDetect()
            },
            detectSelected = state.autoDetect,
        )
        PickerSide.TARGET -> LanguagePickerSheet(
            title = stringRes(R.string.translator__picker_target),
            selected = state.pair.target,
            onPick = {
                picker = null
                vm.pickTarget(it)
            },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}

private enum class PickerSide { SOURCE, TARGET }

@Composable
private fun PairRow(state: TranslatorUiState, onSource: () -> Unit, onTarget: () -> Unit, onSwap: () -> Unit) {
    val source = if (state.autoDetect) stringRes(R.string.translator__detect) else displayName(state.pair.source)
    val target = displayName(state.pair.target)
    val sourceCd = stringRes(R.string.translator__source_cd, "language" to source)
    val targetCd = stringRes(R.string.translator__target_cd, "language" to target)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSource, modifier = Modifier.weight(1f).semantics { contentDescription = sourceCd }) {
            Text(source)
        }
        IconButton(onClick = onSwap) {
            Icon(Icons.Default.SwapHoriz, contentDescription = stringRes(R.string.translator__swap_cd))
        }
        TextButton(onClick = onTarget, modifier = Modifier.weight(1f).semantics { contentDescription = targetCd }) {
            Text(target)
        }
    }
}

@Composable
private fun InputField(state: TranslatorUiState, onInput: (String) -> Unit) {
    OutlinedTextField(
        value = state.input,
        onValueChange = onInput,
        modifier = Modifier.fillMaxWidth(),
        minLines = 4,
        maxLines = 10,
        // Urdu, Arabic and other RTL input lines up right-aligned, Latin text left-aligned.
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
        placeholder = { Text(stringRes(R.string.translator__input_hint)) },
        trailingIcon = {
            if (state.input.isNotEmpty()) {
                IconButton(onClick = { onInput("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringRes(R.string.translator__clear_cd))
                }
            }
        },
    )
}

@Composable
private fun ResultCard(state: TranslatorUiState, vm: TranslatorViewModel) {
    val context = LocalContext.current
    val output = state.output
    if (output == null && state.status == BarStatus.Idle && !state.inFlight) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.inFlight) {
                val cd = stringRes(R.string.translator__translating)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().semantics { contentDescription = cd })
            }
            if (output != null) {
                val resultCd = stringRes(R.string.translator__result_cd)
                SelectionContainer {
                    Text(
                        text = output,
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                        modifier = Modifier.fillMaxWidth().semantics {
                            contentDescription = resultCd
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
            }
            StatusLine(state.status, onRetry = vm::retry, onDownload = vm::download)
            if (output != null) {
                Actions(state, vm, output, context)
            }
        }
    }
}

@Composable
private fun StatusLine(status: BarStatus, onRetry: () -> Unit, onDownload: () -> Unit) {
    val message = TranslatorErrors.message(status) ?: return
    val context = LocalContext.current
    Column {
        Text(stringRes(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        when {
            status is BarStatus.NeedsDownload -> TextButton(onClick = onDownload) {
                Text(
                    stringRes(
                        R.string.translator__download,
                        "source" to displayName(status.pair.source),
                        "target" to displayName(status.pair.target),
                        "size" to Formatter.formatShortFileSize(context, status.bytes),
                    ),
                )
            }
            TranslatorErrors.retryable(status) -> TextButton(onClick = onRetry) {
                Text(stringRes(R.string.translator__retry))
            }
        }
    }
}

@Composable
private fun Actions(state: TranslatorUiState, vm: TranslatorViewModel, output: String, context: Context) {
    val copied = stringRes(R.string.translator__copied)
    val chooserTitle = stringRes(R.string.translator__share_chooser)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            vm.copy()?.let { copyToClipboard(context, it) }
            // Android 13+ shows its own confirmation for clipboard writes.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Default.ContentCopy, contentDescription = stringRes(R.string.translator__copy))
        }
        IconButton(onClick = {
            val send = vm.shareIntent() ?: return@IconButton
            startSafely(context, Intent.createChooser(send, chooserTitle))
        }) {
            Icon(Icons.Default.Share, contentDescription = stringRes(R.string.translator__share))
        }
        val canSpeak = state.speak == SpeakAvailability.AVAILABLE || state.speak == SpeakAvailability.UNKNOWN
        if (state.speaking) {
            IconButton(onClick = vm::stopSpeaking) {
                Icon(Icons.Default.Stop, contentDescription = stringRes(R.string.translator__stop_speaking))
            }
        } else {
            IconButton(onClick = vm::speak, enabled = canSpeak) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringRes(R.string.translator__speak))
            }
        }
        IconButton(onClick = vm::toggleFavorite) {
            if (state.favorite) {
                Icon(Icons.Default.Star, contentDescription = stringRes(R.string.translator__favorite_remove))
            } else {
                Icon(Icons.Default.StarBorder, contentDescription = stringRes(R.string.translator__favorite_add))
            }
        }
    }
    val language = displayName(state.pair.target)
    when {
        state.speak == SpeakAvailability.MISSING_DATA || state.ttsInstallOffered -> Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringRes(R.string.translator__voice_missing, "language" to language),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { startSafely(context, vm.installVoiceIntent()) }) {
                Text(stringRes(R.string.translator__install_voice))
            }
        }
        state.speak == SpeakAvailability.NOT_SUPPORTED -> Text(
            stringRes(R.string.translator__voice_unsupported, "language" to language),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun micMessage(mic: MicState): Int? = when (mic) {
    MicState.Unavailable -> R.string.translator__mic_unavailable
    MicState.NoPermission -> R.string.translator__mic_no_permission
    MicState.Error -> R.string.translator__mic_error
    else -> null
}

private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotEmpty() }
}

internal fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.translator__result_cd), text))
}

private fun startSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No app handles it (no TTS installer, no share target); the tap does nothing.
    }
}
