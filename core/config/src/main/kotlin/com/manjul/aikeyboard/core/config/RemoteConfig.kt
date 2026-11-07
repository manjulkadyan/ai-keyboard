/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.config

/**
 * Typed read access to remote-configurable values. Keys are [ConfigKeys], typed in [ConfigCatalog]. Reading an
 * unknown key, or a key as the wrong type, throws [IllegalArgumentException] (a programming error). The message
 * never holds a value.
 */
interface RemoteConfig {
    fun bool(key: String): Boolean

    fun long(key: String): Long

    /** A [Long] value is also readable as a double. */
    fun double(key: String): Double

    fun string(key: String): String
}

/** Every remote config key (Sprint 3 decision 5, Sprint 4 Task 1). Types and defaults are in [ConfigCatalog]. */
object ConfigKeys {
    /** Global AI kill switch on the client: false hides every AI entry point. */
    const val AI_ENABLED_GLOBAL = "ai_enabled_global"

    /** Minimum top-candidate score for autocorrect on space (PRD FR-05). */
    const val AUTOCORRECT_MIN_SCORE = "autocorrect_min_score"

    /** With no streamed text for this long, the AI sheet shows "Still working…". */
    const val AI_STILL_WORKING_MS = "ai_still_working_ms"

    /** No text delta for this long, before the first one or between two, ends the request with a timeout. */
    const val AI_TIMEOUT_MS = "ai_timeout_ms"

    /** Kill switch: comma-separated packages the policy always treats as sensitive. Read with [forcedPackages]. */
    const val POLICY_FORCE_SENSITIVE_PACKAGES = "policy_force_sensitive_packages"

    /** App versions below this version code must update (Task 8). */
    const val MIN_SUPPORTED_VERSION = "min_supported_version"

    /** Global ads switch: false shows no ad anywhere. */
    const val ADS_ENABLED_GLOBAL = "ads_enabled_global"

    /** The OD-05 A/B arm: false skips the soft paywall in onboarding (set in the console). */
    const val PAYWALL_ONBOARDING_ENABLED = "paywall_onboarding_enabled"

    const val AD_UNIT_APP_OPEN = "ad_unit_app_open"
    const val AD_UNIT_INTER_FEATURE_EXIT = "ad_unit_inter_feature_exit"
    const val AD_UNIT_BANNER_HOME = "ad_unit_banner_home"
    const val AD_UNIT_NATIVE_TRANSLATOR = "ad_unit_native_translator"
    const val AD_UNIT_NATIVE_HISTORY = "ad_unit_native_history"
    const val AD_UNIT_NATIVE_DICTIONARY = "ad_unit_native_dictionary"
    const val AD_UNIT_REWARDED_GAME_HINT = "ad_unit_rewarded_game_hint"

    /** AdMob ad unit ids. Empty means "no ad" for that placement. */
    val AD_UNITS: List<String> = listOf(
        AD_UNIT_APP_OPEN,
        AD_UNIT_INTER_FEATURE_EXIT,
        AD_UNIT_BANNER_HOME,
        AD_UNIT_NATIVE_TRANSLATOR,
        AD_UNIT_NATIVE_HISTORY,
        AD_UNIT_NATIVE_DICTIONARY,
        AD_UNIT_REWARDED_GAME_HINT,
    )

    /** The package set in a [POLICY_FORCE_SENSITIVE_PACKAGES] value: trimmed, blanks dropped. */
    fun forcedPackages(raw: String): Set<String> =
        raw.split(',').map { it.trim() }.filterTo(LinkedHashSet()) { it.isNotEmpty() }
}
