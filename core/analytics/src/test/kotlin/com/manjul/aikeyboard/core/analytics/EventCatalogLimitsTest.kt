/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventCatalogLimitsTest {
    private val all = listOf(
        EventCatalog.onboardingStart("launcher"),
        EventCatalog.onboardingStepView("try"),
        EventCatalog.imeEnabled("5-15"),
        EventCatalog.imeSelected("60-300"),
        EventCatalog.languagesSet(3, true),
        EventCatalog.onboardingComplete(false),
        EventCatalog.imeSessionStart("en-US", false),
        EventCatalog.firstDictation("ur"),
        EventCatalog.voiceSessionStart("hi", "whisper"),
        EventCatalog.voiceSessionEnd("5-15", "6-20", "error", 7),
        EventCatalog.voiceSessionEnd("5-15", "6-20", "user", null),
        EventCatalog.voicePermissionResult(true),
        EventCatalog.voiceUnavailable("no_recognizer"),
        EventCatalog.privacyPrivateAppAdd(),
    )

    @Test fun limitsHold() {
        val nameRe = Regex("^[a-z][a-z0-9_]{0,39}$")
        for (e in all) {
            assertTrue(nameRe.matches(e.name), e.name)
            assertTrue(e.params.size <= 25)
            val registry = EventCatalog.paramRegistry.getValue(e.name)
            for ((k, v) in e.params) {
                assertTrue(k.length <= 40, k)
                assertTrue(k in registry, "${e.name}.$k")
                if (v is String) assertTrue(v.length <= 100)
            }
        }
    }

    @Test fun longStringValuesAreClamped() {
        assertEquals(100, EventCatalog.firstDictation("x".repeat(500)).params["lang"].toString().length)
    }

    @Test fun nullErrorCodeIsOmitted() {
        assertFalse("error_code" in EventCatalog.voiceSessionEnd("0-5", "0", "user", null).params)
    }

    @Test fun namesMatchPrd() {
        val expected = setOf(
            "onboarding_start", "onboarding_step_view", "ime_enabled", "ime_selected", "languages_set",
            "onboarding_complete", "ime_session_start", "first_dictation", "voice_session_start",
            "voice_session_end", "voice_permission_result", "voice_unavailable", "privacy_private_app_add",
        )
        assertEquals(expected, EventCatalog.allNames.toSet())
        assertFalse("first_keystroke" in EventCatalog.allNames)
    }
}
