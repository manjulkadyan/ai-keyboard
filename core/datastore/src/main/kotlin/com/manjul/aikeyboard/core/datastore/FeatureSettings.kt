/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.datastore

import kotlinx.serialization.Serializable

enum class VoiceEnginePreference { AUTO, ON_DEVICE, WHISPER }

/** Translate bar output: a preview the user inserts, or live composing into the field (OD-17). */
enum class BarMode { PREVIEW, LIVE }

/** Source and target language tags of the translate feature. */
@Serializable
data class LanguagePair(val source: String, val target: String)

/** Fork-owned settings, persisted by [FeatureSettingsStore]. Unknown or missing JSON keys fall back to defaults. */
@Serializable
data class FeatureSettings(
    val incognitoManual: Boolean = false,
    val privatePackages: Set<String> = emptySet(),
    val voiceEnginePreference: VoiceEnginePreference = VoiceEnginePreference.AUTO,
    val voiceSilenceMs: Int = 2000,
    val spokenPunctuation: Boolean = true,
    val onboardingStep: Int = 0,
    val onboardingDone: Boolean = false,
    val firstOpenAt: Long = 0L,
    val translatePair: LanguagePair? = null,
    val consentVoiceNoticeAt: Long = 0L,
    val analyticsOptOut: Boolean = false,
    /** When the first dictation with words was logged (first_dictation is sent once per install); 0 = never. */
    val firstDictationAt: Long = 0L,
    /** Whisper model downloads wait for an unmetered network (Wi-Fi). */
    val whisperWifiOnly: Boolean = true,
    val barMode: BarMode = BarMode.PREVIEW,
    /** The user picked [barMode] themselves; while false the low-RAM default (plan decision 5) applies. */
    val barLiveComposingSet: Boolean = false,
    /** Translation model downloads wait for an unmetered network (Wi-Fi). */
    val translateWifiOnly: Boolean = true,
    val cloudTranslateOptIn: Boolean = false,
    val translateHistoryEnabled: Boolean = true,
    /** When first_translate was logged (sent once per install); 0 = never. */
    val firstTranslateAt: Long = 0L,
    /** Last resolved source language tag, kept when language ID is unsure (PRD FR-11 AC2). */
    val lastSourceLang: String? = null,
    val enableKeyboardCardShownAt: Long = 0L,
    /** Language tags whose model download the user stopped; an entry is removed when its job finishes. */
    val translateStopped: Set<String> = emptySet(),
    /** On-device word suggestions in the keyboard strip (PRD FR-05). */
    val suggestionsEnabled: Boolean = true,
    /**
     * Languages (MVP tags, see [MVP_LANGUAGES]) where autocorrect may replace a word on space. Opt-in per language;
     * ur, hi and ar start off (plan decision 4). [FeatureSettingsStore.update] drops any other tag.
     */
    val autocorrectLocales: Set<String> = DEFAULT_AUTOCORRECT_LOCALES,
    /** When the user tapped "Agree" on the `consent_ai` screen (PRD §10.6); 0 = no consent, nothing is sent. */
    val consentAiAt: Long = 0L,
    /** When the user tapped "Agree" on the `consent_auto_polish` screen (PRD FR-16 AC5); 0 = no consent. */
    val consentAutoPolishAt: Long = 0L,
    /** Polish every dictation without a tap (Pro, needs [consentAutoPolishAt]); off by default. */
    val autoPolish: Boolean = false,
    /**
     * Polish output script as its kb-api wire name, one of [OUTPUT_SCRIPTS] (plan decision 12). Kept as a string so
     * this module does not depend on `:core:ai`; [FeatureSettingsStore] clamps unknown values to the default.
     */
    val polishOutputScript: String = DEFAULT_OUTPUT_SCRIPT,
) {
    /** True while any AI consent is on record, so Settings can offer to withdraw it. */
    val hasAnyAiConsent: Boolean get() = consentAiAt > 0L || consentAutoPolishAt > 0L

    /** These settings with every AI consent withdrawn: both consents cleared and auto-polish off (PRD §10.6). */
    fun withAiConsentWithdrawn(): FeatureSettings = copy(consentAiAt = 0L, consentAutoPolishAt = 0L, autoPolish = false)

    companion object {
        /** `OutputScript` wire names in `:core:ai` (kb-api `PolishRequest.script`). */
        val OUTPUT_SCRIPTS: List<String> = listOf("MATCH_INPUT", "ROMAN", "NATIVE")

        const val DEFAULT_OUTPUT_SCRIPT: String = "MATCH_INPUT"

        /** [value] as one of [OUTPUT_SCRIPTS] (case-insensitive), or [DEFAULT_OUTPUT_SCRIPT]. */
        fun normalizeOutputScript(value: String): String =
            value.uppercase(java.util.Locale.ROOT).takeIf { it in OUTPUT_SCRIPTS } ?: DEFAULT_OUTPUT_SCRIPT

        /** The MVP keyboard languages (PRD FR-05). */
        val MVP_LANGUAGES: List<String> = listOf("en", "es", "fr", "pt", "id", "ar", "ur", "hi")

        val DEFAULT_AUTOCORRECT_LOCALES: Set<String> = setOf("en", "es", "fr", "pt", "id")
    }
}
