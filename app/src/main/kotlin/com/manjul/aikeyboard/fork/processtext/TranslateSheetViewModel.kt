/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.processtext

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.TranslateEngineParam
import com.manjul.aikeyboard.core.analytics.TranslateErrorReason
import com.manjul.aikeyboard.core.analytics.TranslateSurface
import com.manjul.aikeyboard.core.database.TranslationHistoryEntry
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.datastore.LanguagePair
import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.ModelSizes
import com.manjul.aikeyboard.core.translate.Origin
import com.manjul.aikeyboard.core.translate.RouterContext
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.mlkit.ModelUiState
import com.manjul.aikeyboard.core.tts.TtsAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the sheet says about the translation besides the text itself. */
sealed interface SheetStatus {
    data object Translating : SheetStatus

    /** The pair's language packs are missing; [bytes] is the approximate download. */
    data class NeedsDownload(val bytes: Long) : SheetStatus

    data object Downloading : SheetStatus

    /** The pair needs the cloud engine (Pro and opt-in). */
    data object CloudOnly : SheetStatus

    data object Unsupported : SheetStatus

    /** No network, timeout, quota or engine error: offer Retry. */
    data class Error(val kind: FailKind) : SheetStatus
}

data class TranslateSheetState(
    /** The pair in use: the detected source (the last source when detection is unsure) and the chosen target. */
    val pair: LangPair,
    val translation: String? = null,
    val engine: EngineId? = null,
    val status: SheetStatus? = SheetStatus.Translating,
    val showEnableKeyboard: Boolean = false,
    /** The device has no voice for the target language. */
    val speakUnavailable: Boolean = false,
) {
    override fun toString(): String = "TranslateSheetState(pair=$pair, " +
        "translationChars=${translation?.length}, engine=$engine, status=$status, card=$showEnableKeyboard)"
}

/** The language packs the sheet needs, over the app's `TranslateModelStore`. */
interface SheetModels {
    val states: StateFlow<Map<Lang, ModelUiState>>

    /** Re-reads what is on the device (the sheet translates on open anyway, so ML Kit is initialised either way). */
    suspend fun refresh()

    fun download(pair: LangPair)
}

/** Read-aloud over the app's `TtsManager`; [release] when the sheet goes away. */
interface SheetSpeaker {
    suspend fun availability(lang: Lang): TtsAvailability

    fun speak(text: String, lang: Lang)

    fun stop()

    fun release()
}

/** What Speak did; [MISSING_VOICE] makes the activity offer the system's voice install screen. */
enum class SpeakOutcome { SPOKEN, MISSING_VOICE, UNSUPPORTED, NOTHING }

/**
 * The Translate sheet (PRD FR-14): translates [input] once on open, detecting the source; the target is the shared
 * pair's target (decision 2), or its source when the text is already in the target language. Analytics carry
 * language tags and counts only; history (origin `process_text`) is written only on Copy, Replace or Open in app,
 * once, and only when history is on.
 */
class TranslateSheetViewModel(
    private val input: ProcessTextInput,
    private val deps: Deps,
    private val scope: CoroutineScope,
) {
    class Deps(
        val store: FeatureSettingsStore,
        /** The router's translate (TRD §4.3.1). */
        val translate: suspend (TranslateRequest, RouterContext, Lang) -> TranslateResult,
        /** The detected source of a text, or the given fallback when unsure. */
        val detect: suspend (text: String, fallback: Lang) -> Lang,
        val models: SheetModels,
        val speaker: SheetSpeaker,
        val history: suspend (TranslationHistoryEntry) -> Unit,
        val imeEnabled: Flow<Boolean>,
        val analytics: (AnalyticsEvent) -> Unit,
        val clock: () -> Long,
        val isPro: () -> Boolean = { false },
    )

    private val _state = MutableStateFlow(TranslateSheetState(pair = storedPair()))
    val state: StateFlow<TranslateSheetState> = _state.asStateFlow()

    private var translateJob: Job? = null
    private var historySaved = false
    private var firstTranslateChecked = false

    /** The target the user picked in the sheet; null until then. */
    private var pickedTarget: Lang? = null

    init {
        scope.launch { checkEnableKeyboardCard() }
        scope.launch {
            try {
                deps.models.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The download offer then counts every model; the router still asks ML Kit itself.
            }
        }
        scope.launch {
            // After the user's Download: translate once the packs are ready, offer Download again if one failed.
            deps.models.states.collect { states ->
                val s = _state.value
                if (s.status != SheetStatus.Downloading) return@collect
                val missing = missing(s.pair, states)
                when {
                    missing.isEmpty() -> translate()
                    missing.any { states[it] == ModelUiState.FAILED } ->
                        _state.update { it.copy(status = SheetStatus.NeedsDownload(ModelSizes.bytes(missing))) }
                }
            }
        }
        translate()
    }

    /** `process_text_open`, once per opened sheet (not again after rotation). */
    fun onOpened() = deps.analytics(EventCatalog.processTextOpen(input.action, editable = input.canReplace))

    fun translate() {
        translateJob?.cancel()
        _state.update { it.copy(status = SheetStatus.Translating, translation = null, engine = null) }
        translateJob = scope.launch { runTranslate() }
    }

    /** The user picked another target; the shared pair follows (decision 2). */
    fun pickTarget(target: Lang) {
        if (target == _state.value.pair.target) return
        pickedTarget = target
        historySaved = false
        val source = _state.value.pair.source
        scope.launch { deps.store.update { it.copy(translatePair = LanguagePair(source.tag, target.tag)) } }
        translate()
    }

    fun download() {
        val pair = _state.value.pair
        _state.update { it.copy(status = SheetStatus.Downloading) }
        deps.models.download(pair)
    }

    fun dismissEnableKeyboard() = _state.update { it.copy(showEnableKeyboard = false) }

    /** Copy: the activity puts the translation on the clipboard. */
    fun onCopy(): String? = settled()?.also { saveHistory() }

    /** Replace (editable PROCESS_TEXT only): the translation to return, or null when there is none to return. */
    fun onReplace(): String? {
        if (!input.canReplace) return null
        val text = settled() ?: return null
        saveHistory()
        deps.analytics(EventCatalog.processTextReplace())
        return text
    }

    /** Open in app: the text the Translator opens with. */
    fun onOpenInApp(): String {
        if (settled() != null) saveHistory()
        return input.text
    }

    suspend fun speak(): SpeakOutcome {
        val s = _state.value
        val text = settled() ?: return SpeakOutcome.NOTHING
        return when (runCatching { deps.speaker.availability(s.pair.target) }.getOrDefault(TtsAvailability.NOT_SUPPORTED)) {
            TtsAvailability.AVAILABLE -> {
                deps.speaker.speak(text, s.pair.target)
                SpeakOutcome.SPOKEN
            }
            TtsAvailability.MISSING_DATA -> SpeakOutcome.MISSING_VOICE
            TtsAvailability.NOT_SUPPORTED -> {
                _state.update { it.copy(speakUnavailable = true) }
                SpeakOutcome.UNSUPPORTED
            }
        }
    }

    fun clear() {
        translateJob?.cancel()
        deps.speaker.stop()
        deps.speaker.release()
    }

    private fun settled(): String? = _state.value.takeIf { it.status == null }?.translation

    private suspend fun runTranslate() {
        val settings = deps.store.flow.first()
        val stored = storedPair(settings.translatePair)
        val last = settings.lastSourceLang?.let(Lang::of) ?: stored.source
        val source = try {
            deps.detect(input.text, last)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            last
        }
        val wanted = pickedTarget ?: stored.target
        val target = if (source == wanted && pickedTarget == null) stored.source.takeIf { it != source } ?: wanted else wanted
        val pair = LangPair(source, target)
        _state.update { it.copy(pair = pair) }
        val ctx = RouterContext(
            allowTranslate = true,
            allowCloud = true,
            pro = deps.isPro(),
            cloudOptIn = settings.cloudTranslateOptIn,
            preferQuality = false,
        )
        val result = try {
            deps.translate(TranslateRequest(input.text, source, target, Origin.PROCESS_TEXT), ctx, last)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Message dropped: an engine error may quote the text.
            TranslateResult.Failed(FailKind.ENGINE)
        }
        when (result) {
            is TranslateResult.Ok -> onTranslated(result, pair)
            is TranslateResult.Failed -> onFailed(result.kind, pair)
        }
    }

    private suspend fun onTranslated(result: TranslateResult.Ok, pair: LangPair) {
        _state.update { it.copy(translation = result.text, engine = result.engine, status = null) }
        deps.analytics(
            EventCatalog.translateDone(
                TranslateSurface.PROCESS_TEXT,
                if (result.engine == EngineId.CLOUD) TranslateEngineParam.CLOUD else TranslateEngineParam.ON_DEVICE,
                src = result.source.tag,
                tgt = pair.target.tag,
                chars = input.text.length,
                offline = result.engine == EngineId.MLKIT,
            ),
        )
        deps.store.update { it.copy(lastSourceLang = result.source.tag) }
        if (!firstTranslateChecked) {
            firstTranslateChecked = true
            var first = false
            deps.store.update { s ->
                if (s.firstTranslateAt > 0) s else s.copy(firstTranslateAt = deps.clock()).also { first = true }
            }
            if (first) deps.analytics(EventCatalog.firstTranslate(TranslateSurface.PROCESS_TEXT))
        }
    }

    private fun onFailed(kind: FailKind, pair: LangPair) {
        val status = when (kind) {
            FailKind.NO_PACK -> SheetStatus.NeedsDownload(ModelSizes.bytes(missing(pair, deps.models.states.value)))
            FailKind.CLOUD_UNAVAILABLE -> SheetStatus.CloudOnly
            FailKind.UNSUPPORTED -> SheetStatus.Unsupported
            else -> SheetStatus.Error(kind)
        }
        _state.update { it.copy(status = status) }
        if (kind != FailKind.POLICY) {
            val reason = TranslateErrorReason.entries.firstOrNull { it.value == kind.analytics } ?: TranslateErrorReason.OTHER
            deps.analytics(EventCatalog.translateError(TranslateSurface.PROCESS_TEXT, reason))
        }
    }

    private fun saveHistory() {
        if (historySaved) return
        val s = _state.value
        val text = s.translation ?: return
        val engine = s.engine ?: return
        historySaved = true
        scope.launch {
            if (!deps.store.flow.first().translateHistoryEnabled) return@launch
            runCatching {
                deps.history(
                    TranslationHistoryEntry(
                        sourceText = input.text,
                        targetText = text,
                        sourceLang = s.pair.source.tag,
                        targetLang = s.pair.target.tag,
                        engine = engine.name.lowercase(),
                        origin = HISTORY_ORIGIN,
                    ),
                )
            }
        }
    }

    /** FR-14 AC3: the card shows when the keyboard is not enabled, at most once per 7 days. */
    private suspend fun checkEnableKeyboardCard() {
        val enabled = runCatching { deps.imeEnabled.first() }.getOrDefault(true)
        if (enabled) return
        val now = deps.clock()
        var show = false
        deps.store.update { s ->
            if (s.enableKeyboardCardShownAt > 0 && now - s.enableKeyboardCardShownAt < CARD_INTERVAL_MS) {
                s
            } else {
                show = true
                s.copy(enableKeyboardCardShownAt = now)
            }
        }
        if (show) _state.update { it.copy(showEnableKeyboard = true) }
    }

    private fun storedPair(): LangPair = storedPair(deps.store.current.translatePair)

    private fun missing(pair: LangPair, states: Map<Lang, ModelUiState>): Set<Lang> =
        ModelSizes.missingFor(pair, states.filterValues { it == ModelUiState.READY }.keys)

    companion object {
        const val HISTORY_ORIGIN = "process_text"
        const val CARD_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

        /** The stored pair, or the default other language to English. */
        fun storedPair(pair: LanguagePair?): LangPair =
            pair?.let { LangPair(Lang.of(it.source), Lang.of(it.target)) } ?: LangPair(Lang.of("es"), Lang.English)
    }
}
