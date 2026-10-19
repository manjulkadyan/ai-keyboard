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

    private val LANG_TAG = Regex("^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8}){0,3}\$")

    /** Free strings never reach an event: anything that is not a BCP-47-shaped tag becomes "other". */
    private fun tag(s: String) = if (LANG_TAG.matches(s)) s else "other"

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
    const val PACK_DOWNLOAD_START = "pack_download_start"
    const val PACK_DOWNLOAD_DONE = "pack_download_done"

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
        PACK_DOWNLOAD_START to setOf("lang", "kind", "size_bucket"),
        PACK_DOWNLOAD_DONE to setOf("lang", "kind", "size_bucket", "result"),
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

    fun onboardingStart(source: OnboardingSource) = event(ONBOARDING_START, "source" to source.value)

    fun onboardingStepView(step: OnboardingStep) = event(ONBOARDING_STEP_VIEW, "step" to step.value)

    fun imeEnabled(secondsSinceStart: Long) =
        event(IME_ENABLED, "seconds_since_start_bucket" to Buckets.seconds(secondsSinceStart))

    fun imeSelected(secondsSinceStart: Long) =
        event(IME_SELECTED, "seconds_since_start_bucket" to Buckets.seconds(secondsSinceStart))

    fun languagesSet(count: Int, hasRtl: Boolean) = event(LANGUAGES_SET, "count" to count, "has_rtl" to hasRtl)

    fun onboardingComplete(skipped: Boolean) = event(ONBOARDING_COMPLETE, "skipped" to skipped)

    /** [subtype] is the subtype's primary language tag (e.g. "en-US"), never a display or layout name. */
    fun imeSessionStart(subtype: String, isSensitive: Boolean) =
        event(IME_SESSION_START, "subtype" to tag(subtype), "is_sensitive" to isSensitive)

    /** [lang] is a BCP-47 language tag. */
    fun firstDictation(lang: String) = event(FIRST_DICTATION, "lang" to tag(lang))

    fun voiceSessionStart(lang: String, engine: VoiceEngine) =
        event(VOICE_SESSION_START, "lang" to tag(lang), "engine" to engine.value)

    fun voiceSessionEnd(durationMs: Long, words: Int, endReason: EndReason, errorCode: Int?): AnalyticsEvent {
        val params = arrayListOf<Pair<String, Any>>(
            "duration_bucket" to Buckets.duration(durationMs),
            "words_bucket" to Buckets.words(words),
            "end_reason" to endReason.value,
        )
        if (errorCode != null) params += "error_code" to errorCode
        return event(VOICE_SESSION_END, *params.toTypedArray())
    }

    fun voicePermissionResult(granted: Boolean) = event(VOICE_PERMISSION_RESULT, "granted" to granted)

    fun voiceUnavailable(reason: UnavailableReason) = event(VOICE_UNAVAILABLE, "reason" to reason.value)

    fun privacyPrivateAppAdd() = event(PRIVACY_PRIVATE_APP_ADD)

    /** [lang] is the pack's language tag ("mul" for multilingual packs); [bytes] is only reported as a bucket. */
    fun packDownloadStart(lang: String, kind: PackKind, bytes: Long) =
        event(PACK_DOWNLOAD_START, "lang" to tag(lang), "kind" to kind.value, "size_bucket" to Buckets.megabytes(bytes))

    fun packDownloadDone(lang: String, kind: PackKind, bytes: Long, result: PackResult) = event(
        PACK_DOWNLOAD_DONE,
        "lang" to tag(lang),
        "kind" to kind.value,
        "size_bucket" to Buckets.megabytes(bytes),
        "result" to result.value,
    )
}
