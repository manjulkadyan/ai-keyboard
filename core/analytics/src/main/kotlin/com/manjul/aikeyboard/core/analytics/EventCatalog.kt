/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

/**
 * PRD §12 events used so far. Factories take enums, buckets, language tags and booleans only, so no
 * event can carry text, package names or keystroke counts.
 */
object EventCatalog {
    const val MAX_NAME = 40
    const val MAX_PARAMS = 25
    const val MAX_KEY = 40
    const val MAX_VALUE = 100

    const val ONBOARDING_START = "onboarding_start"
    const val ONBOARDING_STEP_VIEW = "onboarding_step_view"
    const val IME_ENABLED = "ime_enabled"
    const val IME_SELECTED = "ime_selected"
    const val LANGUAGES_SET = "languages_set"
    const val ONBOARDING_COMPLETE = "onboarding_complete"
    const val IME_SESSION_START = "ime_session_start"
    const val FIRST_DICTATION = "first_dictation"
    const val VOICE_SESSION_START = "voice_session_start"
    const val VOICE_SESSION_END = "voice_session_end"
    const val VOICE_PERMISSION_RESULT = "voice_permission_result"
    const val VOICE_UNAVAILABLE = "voice_unavailable"
    const val PRIVACY_PRIVATE_APP_ADD = "privacy_private_app_add"

    val paramRegistry: Map<String, Set<String>> = mapOf(
        ONBOARDING_START to setOf("source"),
        ONBOARDING_STEP_VIEW to setOf("step"),
        IME_ENABLED to setOf("seconds_since_start_bucket"),
        IME_SELECTED to setOf("seconds_since_start_bucket"),
        LANGUAGES_SET to setOf("count", "has_rtl"),
        ONBOARDING_COMPLETE to setOf("skipped"),
        IME_SESSION_START to setOf("subtype", "is_sensitive"),
        FIRST_DICTATION to setOf("lang"),
        VOICE_SESSION_START to setOf("lang", "engine"),
        VOICE_SESSION_END to setOf("duration_bucket", "words_bucket", "end_reason", "error_code"),
        VOICE_PERMISSION_RESULT to setOf("granted"),
        VOICE_UNAVAILABLE to setOf("reason"),
        PRIVACY_PRIVATE_APP_ADD to emptySet(),
    )

    val allNames: List<String> = paramRegistry.keys.toList()

    private fun event(name: String, vararg params: Pair<String, Any>): AnalyticsEvent {
        val allowed = paramRegistry.getValue(name)
        val map = LinkedHashMap<String, Any>()
        for ((k, v) in params) {
            require(k in allowed && k.length <= MAX_KEY) { "param not registered for $name" }
            map[k] = if (v is String) v.take(MAX_VALUE) else v
        }
        return AnalyticsEvent(name, map)
    }

    fun onboardingStart(source: String) = event(ONBOARDING_START, "source" to source)

    /** [step] is "1", "2", "3" or "try". */
    fun onboardingStepView(step: String) = event(ONBOARDING_STEP_VIEW, "step" to step)

    fun imeEnabled(secondsBucket: String) = event(IME_ENABLED, "seconds_since_start_bucket" to secondsBucket)

    fun imeSelected(secondsBucket: String) = event(IME_SELECTED, "seconds_since_start_bucket" to secondsBucket)

    fun languagesSet(count: Int, hasRtl: Boolean) = event(LANGUAGES_SET, "count" to count, "has_rtl" to hasRtl)

    fun onboardingComplete(skipped: Boolean) = event(ONBOARDING_COMPLETE, "skipped" to skipped)

    fun imeSessionStart(subtype: String, isSensitive: Boolean) =
        event(IME_SESSION_START, "subtype" to subtype, "is_sensitive" to isSensitive)

    fun firstDictation(lang: String) = event(FIRST_DICTATION, "lang" to lang)

    /** [engine] is system_on_device, system_default or whisper. */
    fun voiceSessionStart(lang: String, engine: String) =
        event(VOICE_SESSION_START, "lang" to lang, "engine" to engine)

    /** [endReason] is user, silence, error, cap or field_change. */
    fun voiceSessionEnd(durationBucket: String, wordsBucket: String, endReason: String, errorCode: Int?): AnalyticsEvent {
        val base = arrayListOf<Pair<String, Any>>(
            "duration_bucket" to durationBucket,
            "words_bucket" to wordsBucket,
            "end_reason" to endReason,
        )
        if (errorCode != null) base += "error_code" to errorCode
        return event(VOICE_SESSION_END, *base.toTypedArray())
    }

    fun voicePermissionResult(granted: Boolean) = event(VOICE_PERMISSION_RESULT, "granted" to granted)

    fun voiceUnavailable(reason: String) = event(VOICE_UNAVAILABLE, "reason" to reason)

    fun privacyPrivateAppAdd() = event(PRIVACY_PRIVATE_APP_ADD)
}
