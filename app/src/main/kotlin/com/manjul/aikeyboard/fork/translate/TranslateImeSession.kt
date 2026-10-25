/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translate

import android.content.Context
import android.content.Intent
import android.view.inputmethod.InputConnection
import androidx.core.net.toUri
import com.manjul.aikeyboard.app.FlorisAppActivity
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.RateSampler
import com.manjul.aikeyboard.core.datastore.BarMode
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.datastore.LanguagePair
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.Origin
import com.manjul.aikeyboard.core.translate.RouterContext
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.TranslationCache
import com.manjul.aikeyboard.core.translate.bar.DebouncePolicy
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.fork.voice.ImeTextTarget
import com.manjul.aikeyboard.ime.editing.TextTarget
import com.manjul.aikeyboard.ime.translateui.BarBufferTarget
import com.manjul.aikeyboard.ime.translateui.BarUiState
import com.manjul.aikeyboard.ime.translateui.CloseReason
import com.manjul.aikeyboard.ime.translateui.TranslateBarController
import com.manjul.aikeyboard.ime.translateui.TranslateCall
import com.manjul.aikeyboard.translation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Translate bar for one IME service instance: wires [TranslateBarController] to the policy controller, the
 * settings store, the input connection and the app's translation core. Created in `FlorisImeService.onCreate`, main
 * thread only. Mirrors `VoiceImeSession`.
 *
 * Process start (Review Focus 6): neither creating the session nor showing the keyboard touches [translation] or
 * ML Kit. The controller, and with it the translation core, is created on the first [toggle] in a field that allows
 * translation.
 *
 * Privacy (PC-1): [toggle] re-checks the live decision before anything is created; every policy restriction closes
 * the bar ([CloseReason.SENSITIVE] when translation is no longer allowed); the controller re-checks decision and
 * field generation before every request and write. Bar text is user content and is never logged.
 *
 * @param engine the translate call; defaults to the app's router. Tests pass a fake.
 * @param beforeToggle runs before the bar opens or closes from a key; the service cancels voice typing there, so a
 *   dictation never spans the switch between the host field and the bar.
 */
class TranslateImeSession(
    private val context: Context,
    private val policy: ImePolicyController,
    private val store: FeatureSettingsStore,
    translation: () -> TranslationCore = { context.translation().value },
    private val models: () -> TranslateModels = { translation().models },
    private val analytics: (AnalyticsEvent) -> Unit,
    connection: () -> InputConnection?,
    isRawInputEditor: () -> Boolean,
    setOwnsComposing: (Boolean) -> Unit,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val subtypeLang: () -> String = { "en" },
    private val lowTier: () -> Boolean = { LowTierDevice.isLowTier(context) },
    private val isPro: () -> Boolean = { false },
    private val engine: suspend (TranslateRequest, RouterContext, Lang) -> TranslateResult =
        { req, ctx, last -> translation().router.translate(req, ctx, last) },
    private val cache: () -> TranslationCache? = { translation().cache },
    private val openOfflineLanguages: () -> Unit = { context.startActivity(offlineLanguagesIntent(context)) },
    private val beforeToggle: () -> Unit = {},
) {
    private val target = ImeTextTarget(connection, isRawInputEditor, setOwnsComposing)

    private val _controller = MutableStateFlow<TranslateBarController?>(null)

    /** The bar controller once the user first opened the bar; null before (nothing translation-related exists). */
    val controllerOrNull: StateFlow<TranslateBarController?> = _controller.asStateFlow()

    /** Creates the controller (and resolves the translation core). Only [toggle] does this. */
    val controller: TranslateBarController
        get() = _controller.value ?: createController().also { _controller.value = it }

    private var voiceTarget: BarBufferTarget? = null

    init {
        policy.addRestrictionListener { d ->
            // onFinishInput reports "no field"; onFinishInput itself has closed the bar by then.
            if (d === ImePolicyController.NoField) return@addRestrictionListener
            val c = _controller.value ?: return@addRestrictionListener
            if (!d.allowTranslate) c.onPolicyRestricted(d) else c.onFieldChanged()
        }
    }

    /** True while the bar is open and keys go to its buffer instead of the host. */
    val capturingInput: Boolean get() = _controller.value?.capturingInput == true

    val ui: BarUiState get() = _controller.value?.ui?.value ?: BarUiState.Hidden

    /** The Translate chip. Opens only when the live decision allows translation (a stale button does nothing). */
    fun toggle() {
        if (capturingInput) {
            beforeToggle()
            controller.close()
            return
        }
        if (!policy.decision.value.allowTranslate) return
        beforeToggle()
        val c = controller
        c.open()
        if (c.capturingInput) scope.launch { runCatching { models().refresh() } }
    }

    /** Close button, back gesture. */
    fun close() {
        val c = _controller.value ?: return
        if (!c.capturingInput) return
        beforeToggle()
        c.close()
    }

    /** Enter or ✓. */
    fun insert() {
        _controller.value?.insert()
    }

    /** Where voice output goes while the bar is open; null when it should go to the host. */
    fun voiceTarget(): TextTarget? = if (capturingInput) voiceTarget else null

    /** A new field's connection holds no region of ours. Call before the policy controller's onStartInput. */
    fun onStartInput(restarting: Boolean) {
        if (!restarting) target.forgetOwnedRegion()
    }

    /** The keyboard is shown again: within 60 s of a hide, the kept bar text stays. */
    fun onStartInputView() {
        _controller.value?.onKeyboardShown()
    }

    /** Hiding the keyboard closes the bar; pair, mode and (for 60 s) the text are kept. */
    fun onFinishInputView() {
        _controller.value?.close(CloseReason.HIDDEN)
    }

    /**
     * The field goes away. Call before the policy controller's onFinishInput (which reports "no field"), so the
     * decision is still the field's: a field that may not be translated also drops the cache.
     */
    fun onFinishInput() {
        val c = _controller.value ?: return
        c.close(if (policy.decision.value.allowTranslate) CloseReason.FIELD_CHANGE else CloseReason.SENSITIVE)
    }

    fun onSelectionUpdate(newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        _controller.value?.onSelectionUpdate(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    fun destroy() {
        _controller.value?.close(CloseReason.FIELD_CHANGE)
    }

    private fun createController(): TranslateBarController {
        val models = models()
        val c = TranslateBarController(
            scope = scope,
            router = TranslateCall { text, pair, _ ->
                val d = policy.decision.value
                engine(
                    TranslateRequest(text, pair.source, pair.target, Origin.BAR),
                    RouterContext(
                        allowTranslate = d.allowTranslate,
                        allowCloud = d.allowCloud,
                        pro = isPro(),
                        cloudOptIn = store.current.cloudTranslateOptIn,
                        preferQuality = false,
                    ),
                    pair.source,
                )
            },
            decision = { policy.decision.value },
            generation = { policy.generation.value },
            target = target,
            pair = ::currentPair,
            mode = ::currentMode,
            debounce = if (lowTier()) DebouncePolicy.LowTier else DebouncePolicy.Default,
            analytics = analytics,
            previewSampler = RateSampler(PREVIEW_SAMPLE_RATE),
            clock = clock,
            onDownload = models::download,
            onOpenOfflineLanguages = openOfflineLanguages,
            onPairChange = { p ->
                scope.launch { store.update { it.copy(translatePair = LanguagePair(p.source.tag, p.target.tag)) } }
            },
            onModeChange = { m -> scope.launch { store.update { it.copy(barMode = m, barLiveComposingSet = true) } } },
            downloaded = models::downloaded,
            cache = cache(),
            onFirstTranslate = ::markFirstTranslate,
        )
        voiceTarget = BarBufferTarget(c.buffer)
        return c
    }

    /** The stored pair; without one, the subtype language to English, or English to Spanish for English. */
    private fun currentPair(): LangPair {
        store.current.translatePair?.let { return LangPair(Lang.of(it.source), Lang.of(it.target)) }
        val sub = Lang.of(subtypeLang())
        return if (sub == Lang.English || sub.tag.isEmpty()) LangPair(Lang.English, Lang.of("es")) else LangPair(sub, Lang.English)
    }

    /** Plan decision 5: low-tier devices start in Preview until the user picks a mode. */
    private fun currentMode(): BarMode {
        val s = store.current
        return if (!s.barLiveComposingSet && lowTier()) BarMode.PREVIEW else s.barMode
    }

    private fun markFirstTranslate(): Boolean {
        if (store.current.firstTranslateAt > 0) return false
        val now = clock()
        scope.launch { store.update { if (it.firstTranslateAt > 0) it else it.copy(firstTranslateAt = now) } }
        return true
    }

    companion object {
        /** PRD FR-13 AC7: `translate_preview` is sampled at 1 %. */
        const val PREVIEW_SAMPLE_RATE = 0.01

        private const val OFFLINE_LANGUAGES_DEEPLINK = "ui://florisboard/settings/offline-languages"

        /** Opens Offline languages from the bar's offline chip; only ever from the user's tap. */
        fun offlineLanguagesIntent(context: Context): Intent =
            Intent(Intent.ACTION_VIEW, OFFLINE_LANGUAGES_DEEPLINK.toUri(), context, FlorisAppActivity::class.java)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
    }
}
