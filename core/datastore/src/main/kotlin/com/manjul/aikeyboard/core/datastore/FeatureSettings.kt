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
    /**
     * The last kb-api entitlement verdict (S4 Task 2), written by `EntitlementRepository` only after its signature
     * checks out. kb-api is the Pro authority (ARCH-006); the IME reads Pro through [isProAt]. [proState] is the
     * kb-api `SubState` wire name. No purchase token or order id is ever stored here.
     */
    val proActive: Boolean = false,
    val proState: String = "none",
    val proExpiresAt: Long = 0L,
    val proIssuedAt: Long = 0L,
    val proSignature: String = "",
    /** When kb-api last answered (epoch ms); 0 = never. */
    val proCheckedAt: Long = 0L,
    /**
     * Paywall frequency caps (S4 Task 5, PRD §10.4). When the onboarding paywall (T1) showed (epoch ms, 0 = never),
     * and how often and when the Home win-back card (T8) showed. Read only by `PaywallFrequency`.
     */
    val paywallOnboardingShownAt: Long = 0L,
    val winBackShownCount: Int = 0,
    val winBackLastShownAt: Long = 0L,
    /** When the Play in-app update check last finished (S4 Task 8, epoch ms); 0 = never. Local only. */
    val updateCheckedAt: Long = 0L,
    /**
     * The keyboard click sound (S4 Task 10): `off`, `system` (the platform key click, upstream behaviour) or one of
     * the bundled sounds `soft`, `crisp`, `wood`, `pop`. An unknown value plays like `system`. Local only.
     */
    val keySound: String = "system",
    /**
     * The stylesheet id (`<preset>` or `<preset>_borderless`) a non-Pro user tried to apply from the Themes
     * screen. It is applied and cleared when Pro turns on; empty = none. Local only.
     */
    val pendingPremiumThemeId: String = "",
    /**
     * The tone the ✦ sheet lists first (S4 Task 11), a `RewriteMode` wire name from [AI_TONES]. Kept as a string so
     * this module does not depend on `:core:ai`; [FeatureSettingsStore] clamps unknown values to the default.
     */
    val aiDefaultTone: String = DEFAULT_AI_TONE,
    /** When the user agreed to cloud translation (`consent_cloud_translate`, RN-09); 0 = no consent. */
    val consentCloudTranslateAt: Long = 0L,
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

        /** The `RewriteMode` wire names a user can pick as the default tone. `translate` is not a tone. */
        val AI_TONES: List<String> = listOf("fix", "formal", "friendly", "shorter", "longer", "emojify")

        const val DEFAULT_AI_TONE: String = "fix"

        /** [value] as one of [AI_TONES] (case-insensitive), or [DEFAULT_AI_TONE]. */
        fun normalizeAiTone(value: String): String =
            value.lowercase(java.util.Locale.ROOT).takeIf { it in AI_TONES } ?: DEFAULT_AI_TONE

        /** The MVP keyboard languages (PRD FR-05). */
        val MVP_LANGUAGES: List<String> = listOf("en", "es", "fr", "pt", "id", "ar", "ur", "hi")

        val DEFAULT_AUTOCORRECT_LOCALES: Set<String> = setOf("en", "es", "fr", "pt", "id")

        /** The cached verdict stays valid this long after `expires_at` (TRD §4.12, plan decision 2). */
        const val PRO_CACHE_GRACE_MS: Long = 3L * 24 * 60 * 60 * 1000
    }
}

/** Pro at [nowMs] from the cached kb-api verdict: active, and not past `expires_at` plus the 3-day grace. */
fun FeatureSettings.isProAt(nowMs: Long): Boolean =
    proActive && nowMs < proExpiresAt + FeatureSettings.PRO_CACHE_GRACE_MS
