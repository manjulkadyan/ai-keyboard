/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.dictionary

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.AppTheme
import com.manjul.aikeyboard.app.apptheme.FlorisAppTheme
import com.manjul.aikeyboard.core.analytics.ProcessTextAction
import com.manjul.aikeyboard.core.content.dictionary.LookupResult
import com.manjul.aikeyboard.core.tts.TtsAvailability
import com.manjul.aikeyboard.dictionary
import com.manjul.aikeyboard.fork.processtext.ProcessTextInput
import com.manjul.aikeyboard.fork.routes.AppIntents
import com.manjul.aikeyboard.fork.tts.ScreenSpeaker
import com.manjul.aikeyboard.fork.voice.isOnline
import com.manjul.aikeyboard.ttsManager
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes

/** What the "Define" sheet shows. */
sealed interface DefineState {
    /** The selection is not one word of at most 64 chars. */
    data object NotOneWord : DefineState

    data object Loading : DefineState

    data class Done(val word: String, val result: LookupResult) : DefineState {
        override fun toString(): String = "Done(result=$result)"
    }
}

/**
 * The "Define" text-selection action (S5-dict, PRD FR-21). Exported, so the intent is untrusted: only `PROCESS_TEXT`
 * `text/plain` with text opens the sheet. It accepts one word of at most [AppIntents.MAX_DICTIONARY_WORD_CHARS]
 * chars after trim, else it shows "Select one word". It is an app action, so it may use kb-api (plan decision 6)
 * under the repository gates. It never offers Replace and always returns `RESULT_CANCELED`. No ads.
 */
class DefineTextActivity : ComponentActivity() {
    @VisibleForTesting
    internal class Deps(
        val lookup: suspend (word: String) -> LookupResult,
        val speak: suspend (text: String) -> TtsAvailability,
        val stopSpeaking: () -> Unit,
        val installVoiceIntent: () -> Intent,
    )

    internal class Holder(deps: Deps, word: String?) : ViewModel() {
        private val _state = MutableStateFlow(if (word == null) DefineState.NotOneWord else DefineState.Loading)
        val state: StateFlow<DefineState> = _state.asStateFlow()
        private val stop = deps.stopSpeaking

        init {
            if (word != null) {
                viewModelScope.launch { _state.value = DefineState.Done(word, deps.lookup(word)) }
            }
        }

        override fun onCleared() = stop()
    }

    private lateinit var deps: Deps

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // "Define" never returns text to the host app.
        setResult(RESULT_CANCELED)
        val input = ProcessTextInput.parse(intent)?.takeIf { it.action == ProcessTextAction.PROCESS_TEXT }
            ?: run {
                finish()
                return
            }
        val word = oneWord(input.text)
        deps = depsOverride ?: defaultDeps(this)
        val factory = viewModelFactory { initializer { Holder(deps, word) } }
        val holder = ViewModelProvider(this, factory)[Holder::class.java]
        enableEdgeToEdge()
        setContent {
            ProvideLocalizedResources(this, appName = R.string.app_name) {
                FlorisAppTheme(AppTheme.AUTO) {
                    val state by holder.state.collectAsState()
                    DefineSheet(
                        state = state,
                        onDismiss = ::finish,
                        onSpeak = ::speak,
                        onOpenInApp = { w ->
                            start(AppIntents.dictionaryIntent(this, w))
                            finish()
                        },
                        onOpenLicence = { url -> start(Intent(Intent.ACTION_VIEW, url.toUri())) },
                    )
                }
            }
        }
    }

    private fun speak(text: String) {
        lifecycleScope.launch {
            if (deps.speak(text) == TtsAvailability.MISSING_DATA) start(deps.installVoiceIntent())
        }
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // No browser or TTS screen on this device: nothing to open.
        }
    }

    companion object {
        @VisibleForTesting
        internal var depsOverride: Deps? = null

        /** The trimmed selection when it is one word of at most 64 chars, else null. */
        fun oneWord(text: String): String? {
            val word = text.trim()
            if (word.isEmpty() || word.length > AppIntents.MAX_DICTIONARY_WORD_CHARS) return null
            return word.takeIf { w -> w.none { it.isWhitespace() } }
        }

        private fun defaultDeps(context: Context): Deps {
            val app = context.applicationContext
            val speaker = ScreenSpeaker(app.ttsManager().value)
            return Deps(
                lookup = { word -> app.dictionary().value.repository.lookup(word, allowNetwork = isOnline(app)) },
                speak = { text -> speaker.speak(text, Locale.ENGLISH) },
                stopSpeaking = speaker::release,
                installVoiceIntent = speaker::installVoiceIntent,
            )
        }
    }
}

@Composable
internal fun DefineSheet(
    state: DefineState,
    onDismiss: () -> Unit,
    onSpeak: (String) -> Unit,
    onOpenInApp: (String) -> Unit,
    onOpenLicence: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                // Swallow taps so they do not reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state) {
                    DefineState.NotOneWord -> Text(stringRes(R.string.dictionary__select_one_word))
                    DefineState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    is DefineState.Done -> {
                        DictionaryEntry(word = state.word, result = state.result, onSpeak = onSpeak)
                        Footer(state.result.attribution, onOpenLicence)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (state is DefineState.Done) {
                        TextButton(onClick = { onOpenInApp(state.word) }) {
                            Text(stringRes(R.string.dictionary__open_in_app))
                        }
                    }
                    TextButton(onClick = onDismiss) { Text(stringRes(R.string.dictionary__card_close)) }
                }
            }
        }
    }
}
