/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translator

import android.content.Intent
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.TranslateEngineParam
import com.manjul.aikeyboard.core.analytics.TranslateErrorReason
import com.manjul.aikeyboard.core.analytics.TranslateSurface
import com.manjul.aikeyboard.core.database.TranslationHistoryEntry
import com.manjul.aikeyboard.core.database.TranslationHistoryRepository
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.datastore.LanguagePair
import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.ModelSizes
import com.manjul.aikeyboard.core.translate.Origin
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.bar.BarStatus
import com.manjul.aikeyboard.core.translate.bar.DebouncePolicy
import com.manjul.aikeyboard.core.translate.bar.PipelineState
import com.manjul.aikeyboard.core.translate.bar.TranslatePipeline
import com.manjul.aikeyboard.core.tts.TtsAvailability
import com.manjul.aikeyboard.core.tts.TtsManager
import com.manjul.aikeyboard.core.tts.TtsState
import com.manjul.aikeyboard.fork.translate.TranslateModels
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One Translator request through the app's router. The text is user content, never logged. */
fun interface TranslatorCall {
    suspend fun translate(req: TranslateRequest, lastSource: Lang): TranslateResult
}

/** The parts of the shared `TtsManager` the screen uses; tests fake it. Spoken text is never logged. */
interface TranslatorTts {
    val state: StateFlow<TtsState>

    fun acquire()

    fun release()

    suspend fun availability(locale: Locale): TtsAvailability

    fun speak(text: String, locale: Locale)

    fun stop()

    fun installVoiceIntent(): Intent
}

fun TtsManager.asTranslatorTts(): TranslatorTts = object : TranslatorTts {
    private val m = this@asTranslatorTts
    override val state: StateFlow<TtsState> get() = m.state
    override fun acquire() = m.acquire()
    override fun release() = m.release()
    override suspend fun availability(locale: Locale) = m.availability(locale)
    override fun speak(text: String, locale: Locale) = m.speak(text, locale)
    override fun stop() = m.stop()
    override fun installVoiceIntent() = m.installVoiceIntent()
}

/** The fork settings the screen reads and writes; [FeatureSettingsStore] in the app, a flow in tests. */
interface TranslatorSettings {
    val flow: Flow<FeatureSettings>
    val current: FeatureSettings

    suspend fun update(transform: (FeatureSettings) -> FeatureSettings)
}

fun FeatureSettingsStore.asTranslatorSettings(): TranslatorSettings = object : TranslatorSettings {
    private val s = this@asTranslatorSettings
    override val flow: Flow<FeatureSettings> get() = s.flow
    override val current: FeatureSettings get() = s.current
    override suspend fun update(transform: (FeatureSettings) -> FeatureSettings) = s.update(transform)
}

/**
 * The Translator screen (PRD FR-12, TRD FR-05): auto-translate after 600 ms through the shared [TranslatePipeline]
 * (at most one request in flight, stale results dropped, failures keep the last good result), the shared pair (plan
 * decision 2), history with one row per edit session (decision 6), copy/share/speak/favourite and the platform
 * recognizer mic (decision 7). There is no ad or interstitial anywhere on this screen (FR-12 AC2): this package
 * depends on nothing from `core.ads`.
 *
 * An edit session ends on clear, swap, pair change and [onLeave]; the next result starts a new history row, and the
 * ended session logs its one `translate_done`. Input and output are user content: never logged, never in an event.
 *
 * All calls on [scope]'s thread (the main thread in the app).
 *
 * @param defaultPair the pair when the user never chose one: device language to English, English to Spanish for English.
 */
class TranslatorViewModel(
    private val router: TranslatorCall,
    private val models: TranslateModels,
    private val history: TranslationHistoryRepository,
    private val tts: TranslatorTts,
    private val settings: TranslatorSettings,
    private val analytics: (AnalyticsEvent) -> Unit,
    private val clock: () -> Long,
    private val speech: TranslatorMic,
    private val scope: CoroutineScope,
    private val defaultPair: () -> LangPair = { defaultPairFor(Locale.getDefault()) },
    debounce: DebouncePolicy = DebouncePolicy.Translator,
) {
    /** A successful result and what produced it. [text] is the request text (user content). */
    private class Meta(val seq: Long, val text: String, val source: Lang, val target: Lang, val engine: EngineId) {
        override fun toString(): String = "Meta(seq=$seq, $source->$target, engine=$engine)"
    }

    /** One edit session: at most one history row and one `translate_done`. */
    private class Session {
        var id: Long? = null
        var last: Meta? = null
        var output: String? = null
        var favorite = false
        var voice = false
        var doneLogged = false
    }

    private val _state = MutableStateFlow(TranslatorUiState(pair = pairFrom(settings.current)))
    val state: StateFlow<TranslatorUiState> = _state.asStateFlow()

    private var session = Session()
    private val saveLock = Mutex()

    @Volatile private var lastMeta: Meta? = null

    /** The pair of the latest request, with the fallback source in auto mode; sizes the download offer. */
    @Volatile private var lastRequestPair: LangPair = _state.value.pair
    private var handledSeq = -1L
    private var errorSeq = -1L
    private var firstTranslateChecked = false
    private var ttsAcquired = false
    private var micPrefix = ""

    private val pipeline = TranslatePipeline(
        scope = scope,
        debounce = debounce,
        needsDownload = {
            val pair = lastRequestPair
            runCatching { models.refresh() }
            BarStatus.NeedsDownload(pair, ModelSizes.bytes(ModelSizes.missingFor(pair, models.downloaded())))
        },
        translate = ::translate,
    )

    init {
        scope.launch {
            // The cached settings are defaults until the store's first load; the pair comes from the real value.
            val loaded = settings.flow.first()
            if (_state.value.input.isEmpty()) _state.update { it.copy(pair = pairFrom(loaded)) }
        }
        scope.launch {
            settings.flow.map { it.translateHistoryEnabled }.distinctUntilChanged().collect { on ->
                _state.update { it.copy(historyEnabled = on) }
            }
        }
        scope.launch { pipeline.state.collect(::onPipeline) }
        scope.launch { tts.state.collect { t -> _state.update { it.copy(speaking = t is TtsState.Speaking) } } }
        scope.launch { speech.state.collect { m -> _state.update { it.copy(mic = m) } } }
    }

    /** The user typed or edited. Over 5,000 characters is cut to the limit (the counter shows it is full). */
    fun onInput(text: String) {
        if (_state.value.mic == MicState.Listening) speech.stop()
        setInput(text)
    }

    /** Appends [text] (the clipboard) to the input, within the limit. */
    fun paste(text: String) {
        if (text.isEmpty()) return
        onInput(_state.value.input + text)
    }

    /** Swaps the languages; the translation becomes the new input. In auto mode the detected language is used. */
    fun swap() {
        val s = _state.value
        val source = if (s.autoDetect) s.detected ?: s.pair.source else s.pair.source
        val pair = LangPair(s.pair.target, source)
        val input = s.output?.takeIf { it.isNotBlank() } ?: s.input
        finishSession()
        tts.stop()
        _state.update {
            it.copy(pair = pair, autoDetect = false, detected = null, input = clamp(input), ttsInstallOffered = false)
        }
        persistPair(pair)
        restart()
    }

    fun setPair(pair: LangPair) {
        val s = _state.value
        if (pair == s.pair && !s.autoDetect) return
        finishSession()
        tts.stop()
        _state.update { it.copy(pair = pair, autoDetect = false, detected = null, ttsInstallOffered = false) }
        persistPair(pair)
        restart()
    }

    /** The source picker. Picking the target language swaps the pair instead of translating into itself. */
    fun pickSource(lang: Lang) {
        val pair = _state.value.pair
        if (lang == pair.target) swap() else setPair(LangPair(lang, pair.target))
    }

    fun pickTarget(lang: Lang) {
        val s = _state.value
        if (lang == s.pair.source && !s.autoDetect) swap() else setTarget(lang)
    }

    /** "Detect language" in the source picker: the router detects the source; the pair's source is the fallback. */
    fun setAutoDetect() {
        if (_state.value.autoDetect) return
        finishSession()
        _state.update { it.copy(autoDetect = true, detected = null) }
        restart()
    }

    fun retry() = pipeline.retry()

    /** The download offer's tap; the app's WorkManager job downloads what the pair is missing. Never automatic. */
    fun download() {
        val status = _state.value.status as? BarStatus.NeedsDownload ?: return
        models.download(status.pair)
    }

    /** The screen resumed: re-read the pair (the keyboard bar may have changed it) and the downloaded models. */
    fun refresh() {
        scope.launch {
            val loaded = settings.flow.first()
            val pair = pairFrom(loaded)
            val s = _state.value
            if (pair != s.pair && !s.autoDetect) {
                setPair(pair)
                return@launch
            }
            runCatching { models.refresh() }
            val status = _state.value.status
            if (status is BarStatus.NeedsDownload && ModelSizes.missingFor(status.pair, models.downloaded()).isEmpty()) {
                pipeline.retry()
            }
        }
    }

    /** Stars or unstars the current translation. Starring saves it even when history is off; unstarring it then removes it. */
    fun toggleFavorite() {
        val s = session
        if (s.last == null) return
        s.favorite = !s.favorite
        _state.update { it.copy(favorite = s.favorite) }
        scope.launch {
            saveLock.withLock {
                val id = s.id ?: if (s.favorite) history.upsertSession(null, entryOf(s) ?: return@withLock) else null
                s.id = id ?: return@withLock
                if (!s.favorite && !settings.current.translateHistoryEnabled) {
                    // History is off: the row only existed because it was starred.
                    history.delete(id)
                    s.id = null
                } else {
                    history.setFavorite(id, s.favorite)
                }
            }
        }
    }

    fun setHistoryEnabled(on: Boolean) {
        _state.update { it.copy(historyEnabled = on) }
        scope.launch { settings.update { it.copy(translateHistoryEnabled = on) } }
    }

    /** The translation for the clipboard, or null when there is none. */
    fun copy(): String? = _state.value.output?.takeIf { it.isNotEmpty() }

    /** `ACTION_SEND text/plain` with the translation (the screen wraps it in a chooser); null when there is none. */
    fun shareIntent(): Intent? = copy()?.let {
        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, it)
    }

    /** Speaks the translation (chunked by `TtsManager`). A missing voice offers "Install voice" instead. */
    fun speak() {
        val text = copy() ?: return
        val target = _state.value.pair.target
        scope.launch {
            val availability = checkVoice(target)
            when (availability) {
                SpeakAvailability.AVAILABLE -> {
                    if (_state.value.mic == MicState.Listening) speech.stop()
                    tts.speak(text, localeOf(target))
                }
                SpeakAvailability.MISSING_DATA -> _state.update { it.copy(ttsInstallOffered = true) }
                else -> Unit
            }
        }
    }

    fun stopSpeaking() = tts.stop()

    fun installVoiceIntent(): Intent = tts.installVoiceIntent()

    fun dismissInstallVoice() = _state.update { it.copy(ttsInstallOffered = false) }

    /** Dictates into the input in the source language; what was typed before stays in front. */
    fun startMic() {
        if (_state.value.mic == MicState.Listening) return
        tts.stop()
        micPrefix = _state.value.input
        session.voice = true
        speech.start(_state.value.pair.source.tag) { text, final ->
            val joined = when {
                text.isEmpty() -> micPrefix
                micPrefix.isEmpty() || micPrefix.last().isWhitespace() -> micPrefix + text
                else -> "$micPrefix $text"
            }
            if (final) micPrefix = clamp(joined)
            setInput(joined)
        }
    }

    fun stopMic() = speech.stop()

    /** Leaving the screen: stops speech and the mic and ends the edit session. */
    fun onLeave() {
        tts.stop()
        speech.stop()
        finishSession()
    }

    /** The screen's view model is cleared: also releases the shared TTS engine and the recognizer. */
    fun destroy() {
        onLeave()
        speech.destroy()
        if (ttsAcquired) {
            ttsAcquired = false
            tts.release()
        }
    }

    private fun setInput(text: String) {
        val t = clamp(text)
        if (t.isBlank() && _state.value.input.isNotBlank()) finishSession()
        _state.update { it.copy(input = t, ttsInstallOffered = false) }
        pipeline.onInput(t)
    }

    private fun setTarget(lang: Lang) {
        val s = _state.value
        val pair = LangPair(s.pair.source, lang)
        if (pair == s.pair) return
        finishSession()
        tts.stop()
        _state.update { it.copy(pair = pair, ttsInstallOffered = false) }
        persistPair(pair)
        restart()
    }

    /** Clears the shown result and translates the current input again now (new pair or mode). */
    private fun restart() {
        val input = _state.value.input
        pipeline.reset()
        if (input.isNotBlank()) {
            pipeline.onInput(input)
            pipeline.retry()
        }
    }

    private suspend fun translate(text: String, seq: Long): TranslateResult {
        val s = _state.value
        val lastSource = if (s.autoDetect) settings.current.lastSourceLang?.let(Lang::of) ?: s.pair.source else s.pair.source
        val source = if (s.autoDetect) null else s.pair.source
        lastRequestPair = LangPair(source ?: lastSource, s.pair.target)
        val result = router.translate(TranslateRequest(text, source, s.pair.target, Origin.TRANSLATOR), lastSource)
        if (result is TranslateResult.Ok) lastMeta = Meta(seq, text, result.source, s.pair.target, result.engine)
        return result
    }

    private fun onPipeline(p: PipelineState) {
        val meta = lastMeta?.takeIf { p.status == BarStatus.Ok && p.output != null && it.seq == p.seq }
        _state.update {
            it.copy(
                output = p.output,
                status = p.status,
                inFlight = p.inFlight,
                detected = if (p.output == null) null else meta?.source ?: it.detected,
            )
        }
        if (meta != null && meta.seq != handledSeq) {
            handledSeq = meta.seq
            onSettled(meta, p.output!!)
        }
        val reason = reasonOf(p.status)
        if (reason != null && !p.inFlight && p.seq != errorSeq) {
            errorSeq = p.seq
            analytics(EventCatalog.translateError(TranslateSurface.TRANSLATOR, reason))
        }
    }

    private fun onSettled(meta: Meta, output: String) {
        val s = session
        s.last = meta
        s.output = output
        markFirstTranslate()
        if (_state.value.autoDetect && settings.current.lastSourceLang != meta.source.tag) {
            scope.launch { settings.update { it.copy(lastSourceLang = meta.source.tag) } }
        }
        if (settings.current.translateHistoryEnabled || s.favorite) save(s)
        scope.launch { checkVoice(meta.target) }
    }

    private fun save(s: Session) {
        scope.launch {
            saveLock.withLock {
                val entry = entryOf(s) ?: return@withLock
                s.id = history.upsertSession(s.id, entry)
            }
        }
    }

    private fun entryOf(s: Session): TranslationHistoryEntry? {
        val meta = s.last ?: return null
        val output = s.output ?: return null
        return TranslationHistoryEntry(
            sourceText = meta.text,
            targetText = output,
            sourceLang = meta.source.tag,
            targetLang = meta.target.tag,
            engine = if (meta.engine == EngineId.CLOUD) "cloud" else "mlkit",
            origin = if (s.voice) "voice" else "text",
        )
    }

    /** Ends the edit session: its one `translate_done` (for its last result), and the next result starts a new row. */
    private fun finishSession() {
        val s = session
        val meta = s.last
        if (meta != null && !s.doneLogged) {
            s.doneLogged = true
            analytics(
                EventCatalog.translateDone(
                    TranslateSurface.TRANSLATOR,
                    if (meta.engine == EngineId.CLOUD) TranslateEngineParam.CLOUD else TranslateEngineParam.ON_DEVICE,
                    src = meta.source.tag,
                    tgt = meta.target.tag,
                    chars = meta.text.length,
                    // Translated without network: the on-device engine (same rule as the bar).
                    offline = meta.engine == EngineId.MLKIT,
                ),
            )
        }
        session = Session()
        _state.update { it.copy(favorite = false) }
    }

    private fun markFirstTranslate() {
        if (firstTranslateChecked) return
        firstTranslateChecked = true
        if (settings.current.firstTranslateAt > 0) return
        val now = clock()
        scope.launch {
            var first = false
            settings.update {
                first = it.firstTranslateAt <= 0
                if (first) it.copy(firstTranslateAt = now) else it
            }
            if (first) analytics(EventCatalog.firstTranslate(TranslateSurface.TRANSLATOR))
        }
    }

    private suspend fun checkVoice(target: Lang): SpeakAvailability {
        if (!ttsAcquired) {
            ttsAcquired = true
            tts.acquire()
        }
        val availability = when (tts.availability(localeOf(target))) {
            TtsAvailability.AVAILABLE -> SpeakAvailability.AVAILABLE
            TtsAvailability.MISSING_DATA -> SpeakAvailability.MISSING_DATA
            TtsAvailability.NOT_SUPPORTED -> SpeakAvailability.NOT_SUPPORTED
        }
        _state.update { if (it.pair.target == target) it.copy(speak = availability) else it }
        return availability
    }

    private fun persistPair(pair: LangPair) {
        scope.launch { settings.update { it.copy(translatePair = LanguagePair(pair.source.tag, pair.target.tag)) } }
    }

    private fun pairFrom(s: FeatureSettings): LangPair =
        s.translatePair?.let { LangPair(Lang.of(it.source), Lang.of(it.target)) } ?: defaultPair()

    companion object {
        /** Cuts [text] to [TranslatorUiState.MAX_CHARS] without splitting a surrogate pair. */
        fun clamp(text: String): String {
            if (text.length <= TranslatorUiState.MAX_CHARS) return text
            val end = if (text[TranslatorUiState.MAX_CHARS - 1].isHighSurrogate()) {
                TranslatorUiState.MAX_CHARS - 1
            } else {
                TranslatorUiState.MAX_CHARS
            }
            return text.substring(0, end)
        }

        fun defaultPairFor(locale: Locale): LangPair {
            val device = Lang.of(locale.language)
            return if (device == Lang.English || device.tag.isEmpty()) {
                LangPair(Lang.English, Lang("es"))
            } else {
                LangPair(device, Lang.English)
            }
        }

        fun localeOf(lang: Lang): Locale = Locale.forLanguageTag(lang.tag)

        private fun reasonOf(status: BarStatus): TranslateErrorReason? = when (status) {
            BarStatus.Idle, BarStatus.Ok -> null
            is BarStatus.NeedsDownload -> TranslateErrorReason.NO_PACK
            BarStatus.Offline -> TranslateErrorReason.NET
            BarStatus.CloudOnlyPro -> TranslateErrorReason.OTHER
            is BarStatus.Error -> reasonOf(status.kind)
        }

        private fun reasonOf(kind: FailKind): TranslateErrorReason =
            TranslateErrorReason.entries.firstOrNull { it.value == kind.analytics } ?: TranslateErrorReason.OTHER
    }
}
