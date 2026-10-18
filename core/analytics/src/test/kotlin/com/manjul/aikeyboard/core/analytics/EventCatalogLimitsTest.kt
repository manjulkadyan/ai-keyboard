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
    private val big = "x".repeat(500)

    private val all = listOf(
        EventCatalog.onboardingStart(OnboardingSource.LAUNCHER),
        EventCatalog.onboardingStepView(OnboardingStep.TRY),
        EventCatalog.imeEnabled(Long.MAX_VALUE),
        EventCatalog.imeSelected(-5),
        EventCatalog.languagesSet(Int.MAX_VALUE, true),
        EventCatalog.onboardingComplete(false),
        EventCatalog.imeSessionStart(big, false),
        EventCatalog.imeSessionStart("en-US", true),
        EventCatalog.firstDictation(big),
        EventCatalog.voiceSessionStart(big, VoiceEngine.WHISPER),
        EventCatalog.voiceSessionEnd(Long.MAX_VALUE, Int.MAX_VALUE, EndReason.FIELD_CHANGE, Int.MAX_VALUE),
        EventCatalog.voiceSessionEnd(0, 0, EndReason.USER, null),
        EventCatalog.voicePermissionResult(true),
        EventCatalog.voiceUnavailable(UnavailableReason.OFFLINE_NO_MODEL),
        EventCatalog.privacyPrivateAppAdd(),
    )

    @Test fun everyRegistryKeyIsProducedByAFactoryCall() {
        for ((name, keys) in EventCatalog.paramRegistry) {
            val produced = all.filter { it.name == name }.flatMap { it.params.keys }.toSet()
            assertEquals(keys, produced, name)
        }
    }

    @Test fun nonLanguageStringsBecomeOther() {
        for (bad in listOf("com.bank.app", "hello world", "", big, "QWERTY layout", "en-US; DROP")) {
            assertEquals("other", EventCatalog.imeSessionStart(bad, false).params["subtype"], bad)
            assertEquals("other", EventCatalog.firstDictation(bad).params["lang"], bad)
            assertEquals("other", EventCatalog.voiceSessionStart(bad, VoiceEngine.WHISPER).params["lang"], bad)
        }
        assertEquals("ur", EventCatalog.firstDictation("ur").params["lang"])
        assertEquals("en-US", EventCatalog.imeSessionStart("en-US", false).params["subtype"])
        assertEquals("zh_Hans_CN", EventCatalog.firstDictation("zh_Hans_CN").params["lang"])
    }

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

    @Test fun nullErrorCodeIsOmitted() {
        assertFalse("error_code" in EventCatalog.voiceSessionEnd(0, 0, EndReason.USER, null).params)
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
