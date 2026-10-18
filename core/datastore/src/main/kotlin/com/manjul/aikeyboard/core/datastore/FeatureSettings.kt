/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.datastore

import kotlinx.serialization.Serializable

enum class VoiceEnginePreference { AUTO, ON_DEVICE, WHISPER }

/** Source and target language tags of the translate feature. */
@Serializable
data class LanguagePair(val source: String, val target: String)

/** Fork-owned settings, persisted by [FeatureSettingsStore]. Unknown or missing JSON keys fall back to defaults. */
@Serializable
data class FeatureSettings(
    val incognitoManual: Boolean = false,
    val privatePackages: Set<String> = emptySet(),
    val smartbarPinned: List<String> = emptyList(),
    val smartbarOrder: List<String> = emptyList(),
    val voiceEnginePreference: VoiceEnginePreference = VoiceEnginePreference.AUTO,
    val voiceSilenceMs: Int = 2000,
    val spokenPunctuation: Boolean = true,
    val onboardingStep: Int = 0,
    val onboardingDone: Boolean = false,
    val firstOpenAt: Long = 0L,
    val translatePair: LanguagePair? = null,
    val consentVoiceNoticeAt: Long = 0L,
    val analyticsOptOut: Boolean = false,
)
