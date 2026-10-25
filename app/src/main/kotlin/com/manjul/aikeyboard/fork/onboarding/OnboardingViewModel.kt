/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.onboarding

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.OnboardingSource
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.datastore.LanguagePair
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.fork.subtypes.MVP_LANGUAGE_TAGS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.manjul.aikeyboard.core.analytics.OnboardingStep as StepEvent

/** [loaded] is false until the persisted step is read; the UI shows nothing before that. */
data class OnboardingUiState(
    val loaded: Boolean = false,
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val defaultLanguageTags: List<String> = listOf("en-US"),
    val micGranted: Boolean? = null,
)

private val RTL_LANGUAGES = setOf("ar", "ur", "he", "fa")

private fun languageOf(tag: String) = tag.substringBefore('-').substringBefore('_').lowercase()

/** MVP tags matching [systemTags] (exact locale first, then same language), in system order; "en-US" if none. */
fun defaultLanguageSelection(systemTags: List<String>): List<String> {
    val picked = systemTags.mapNotNull { raw ->
        val tag = raw.replace('_', '-')
        MVP_LANGUAGE_TAGS.firstOrNull { it.equals(tag, ignoreCase = true) }
            ?: MVP_LANGUAGE_TAGS.firstOrNull { languageOf(it) == languageOf(tag) }
    }.distinct()
    return picked.ifEmpty { listOf("en-US") }
}

/** Source = language of the first chosen tag, target = language of the second; "en" (or "es" for English) otherwise. */
fun defaultTranslatePair(tags: List<String>): Pair<String, String> {
    val source = languageOf(tags.firstOrNull() ?: "en-US")
    val target = tags.drop(1).map(::languageOf).firstOrNull { it != source } ?: if (source == "en") "es" else "en"
    return source to target
}

/**
 * The O4 "Download for offline" offer (plan decision 12): the bytes [pair] still needs and the user-chosen
 * download, served by the app's translation model store. Language tags only.
 */
interface OfflinePackOffer {
    fun missingBytes(pair: LangPair): Long

    fun download(pair: LangPair)
}

private fun Pair<String, String>.toLangPair() = LangPair(Lang.of(first), Lang.of(second))

/**
 * State machine behind O1-O5. Progress is persisted in [store] after every step so a killed process resumes
 * where it left off; ENABLE and SELECT advance by themselves when [imeStatus] reports the change. Events carry
 * enums, buckets and language tags only.
 */
class OnboardingViewModel(
    private val store: FeatureSettingsStore,
    private val imeStatus: ImeStatusSource,
    private val analytics: (AnalyticsEvent) -> Unit,
    private val ensureSubtypes: (List<String>) -> Unit,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    systemLocaleTags: List<String> = emptyList(),
    private val source: OnboardingSource = OnboardingSource.LAUNCHER,
    private val offlinePacks: OfflinePackOffer? = null,
) {
    private val _state = MutableStateFlow(OnboardingUiState(defaultLanguageTags = defaultLanguageSelection(systemLocaleTags)))
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var startedAt = 0L
    /** Already onboarded once, but the IME got disabled or deselected: set up again, skipping the language step; no funnel events. */
    private var reentry = false
    private var lastEnabled = false
    private var lastSelected = false

    init {
        scope.launch {
            load()
            if (_state.value.step != OnboardingStep.DONE) {
                combine(imeStatus.enabled, imeStatus.selected) { enabled, selected -> enabled to selected }
                    .collect { (enabled, selected) -> onImeStatus(enabled, selected) }
            }
        }
    }

    private suspend fun load() {
        var settings = store.flow.first()
        if (settings.firstOpenAt == 0L) {
            store.update { it.copy(firstOpenAt = clock()) }
            analytics(EventCatalog.onboardingStart(source))
            settings = store.flow.first()
        }
        startedAt = settings.firstOpenAt
        val step = if (settings.onboardingDone) {
            val enabled = imeStatus.enabled.first()
            val selected = imeStatus.selected.first()
            reentry = !(enabled && selected)
            when {
                !enabled -> OnboardingStep.ENABLE
                !selected -> OnboardingStep.SELECT
                else -> OnboardingStep.DONE
            }
        } else {
            OnboardingStep.entries[settings.onboardingStep.coerceIn(0, OnboardingStep.DONE.ordinal)]
        }
        _state.update { it.copy(loaded = true, step = step) }
        logStepView(step)
    }

    private fun onImeStatus(enabled: Boolean, selected: Boolean) {
        lastEnabled = enabled
        lastSelected = selected
        if (_state.value.step == OnboardingStep.ENABLE && enabled) {
            if (!reentry) analytics(EventCatalog.imeEnabled(secondsSinceStart()))
            goTo(OnboardingStep.SELECT)
        }
        if (_state.value.step == OnboardingStep.SELECT && selected) {
            if (!reentry) analytics(EventCatalog.imeSelected(secondsSinceStart()))
            if (reentry) finish(skipped = false) else goTo(OnboardingStep.LANGUAGES)
        }
    }

    /** Advances O1 to O2 and finishes O5; ENABLE, SELECT and LANGUAGES move on by their own triggers. */
    fun next() {
        when (_state.value.step) {
            OnboardingStep.WELCOME -> {
                goTo(OnboardingStep.ENABLE)
                // The keyboard may already be enabled or selected: no new status emission will tell us.
                onImeStatus(lastEnabled, lastSelected)
            }
            OnboardingStep.TRY_IT -> finish(skipped = false)
            else -> Unit
        }
    }

    /** Leaves onboarding from the language or try-it step; earlier steps cannot be skipped (no keyboard yet). */
    fun skip() {
        if (_state.value.step == OnboardingStep.LANGUAGES || _state.value.step == OnboardingStep.TRY_IT) {
            finish(skipped = true)
        }
    }

    /** Size of the O4 offline offer for the pair [tags] would set; 0 when nothing is missing (no offer shown). */
    fun offlineOfferBytes(tags: List<String>): Long =
        offlinePacks?.missingBytes(defaultTranslatePair(tags).toLangPair()) ?: 0L

    /** [downloadOffline] is the O4 offer; it only enqueues the Wi-Fi-only job and never holds up the next step. */
    fun onLanguagesChosen(tags: List<String>, pair: Pair<String, String>, downloadOffline: Boolean = false) {
        if (_state.value.step != OnboardingStep.LANGUAGES || tags.isEmpty()) return
        ensureSubtypes(tags)
        scope.launch { store.update { it.copy(translatePair = LanguagePair(pair.first, pair.second)) } }
        if (downloadOffline) offlinePacks?.download(pair.toLangPair())
        analytics(EventCatalog.languagesSet(tags.size, tags.any { languageOf(it) in RTL_LANGUAGES }))
        goTo(OnboardingStep.TRY_IT)
    }

    /** A denial leaves the user on TRY_IT: typing keeps working, only voice is unavailable. */
    fun onMicPermissionResult(granted: Boolean) {
        analytics(EventCatalog.voicePermissionResult(granted))
        _state.update { it.copy(micGranted = granted) }
    }

    private fun goTo(step: OnboardingStep) {
        _state.update { it.copy(step = step) }
        scope.launch { store.update { it.copy(onboardingStep = step.ordinal) } }
        logStepView(step)
    }

    private fun finish(skipped: Boolean) {
        _state.update { it.copy(step = OnboardingStep.DONE) }
        scope.launch { store.update { it.copy(onboardingDone = true, onboardingStep = OnboardingStep.DONE.ordinal) } }
        if (!reentry) analytics(EventCatalog.onboardingComplete(skipped))
    }

    /** Funnel timing is measured from `firstOpenAt` (ruling R16), not from the current session. */
    private fun secondsSinceStart() = ((clock() - startedAt) / 1000).coerceAtLeast(0)

    private fun logStepView(step: OnboardingStep) {
        if (reentry) return // a user who finished onboarding once must not re-enter the funnel
        val event = when (step) {
            OnboardingStep.WELCOME -> StepEvent.ONE
            OnboardingStep.ENABLE -> StepEvent.TWO
            OnboardingStep.SELECT -> StepEvent.THREE
            OnboardingStep.TRY_IT -> StepEvent.TRY
            // LANGUAGES is reported through languages_set; the closed step set has no value for it.
            OnboardingStep.LANGUAGES, OnboardingStep.DONE -> return
        }
        analytics(EventCatalog.onboardingStepView(event))
    }
}
