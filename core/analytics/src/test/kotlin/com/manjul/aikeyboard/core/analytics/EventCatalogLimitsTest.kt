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
        EventCatalog.packDownloadStart(big, PackKind.WHISPER, Long.MAX_VALUE),
        EventCatalog.packDownloadDone("mul", PackKind.WHISPER, -1, PackResult.HASH_MISMATCH),
        EventCatalog.packDownloadStart("ur", PackKind.TRANSLATE, 30_000_000),
        EventCatalog.translateDone(TranslateSurface.BAR, TranslateEngineParam.ON_DEVICE, big, "ur", Int.MAX_VALUE, true),
        EventCatalog.translateDone(TranslateSurface.PROCESS_TEXT, TranslateEngineParam.CLOUD, "en", big, -1, false),
        EventCatalog.firstTranslate(TranslateSurface.TRANSLATOR),
        EventCatalog.translateError(TranslateSurface.BAR, TranslateErrorReason.OTHER),
        EventCatalog.translatePreview(TranslateSurface.BAR, TranslateEngineParam.ON_DEVICE, big, big),
        EventCatalog.processTextOpen(ProcessTextAction.SEND, false),
        EventCatalog.processTextReplace(),
        EventCatalog.deeplinkUnknown(),
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
            assertEquals("other", EventCatalog.packDownloadStart(bad, PackKind.WHISPER, 0).params["lang"], bad)
            val done = EventCatalog.translateDone(
                TranslateSurface.BAR, TranslateEngineParam.ON_DEVICE, bad, bad, 10, false,
            )
            assertEquals("other", done.params["src"], bad)
            assertEquals("other", done.params["tgt"], bad)
            val preview = EventCatalog.translatePreview(TranslateSurface.BAR, TranslateEngineParam.ON_DEVICE, bad, bad)
            assertEquals("other", preview.params["src"], bad)
            assertEquals("other", preview.params["tgt"], bad)
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

    @Test fun packDownloadsCarryOnlyClosedValues() {
        val done = EventCatalog.packDownloadDone("mul", PackKind.WHISPER, 57_000_000, PackResult.OK)
        assertEquals(
            mapOf("lang" to "mul", "kind" to "whisper", "size_bucket" to "50-100", "result" to "ok"),
            done.params,
        )
    }

    @Test fun translateDoneCarriesOnlyClosedValues() {
        val done = EventCatalog.translateDone(
            TranslateSurface.BAR, TranslateEngineParam.ON_DEVICE, "ur", "en", 42, true,
        )
        assertEquals(
            mapOf(
                "surface" to "bar", "engine" to "on_device", "src" to "ur", "tgt" to "en",
                "chars_bucket" to "21-50", "offline" to true,
            ),
            done.params,
        )
        // No text-like param: only these keys exist, and none of them can hold free text.
        assertEquals(setOf("surface", "engine", "src", "tgt", "chars_bucket", "offline"), done.params.keys)
        assertEquals(
            "other",
            EventCatalog.translateDone(
                TranslateSurface.BAR, TranslateEngineParam.ON_DEVICE, "hello world", "en", 1, false,
            ).params["src"],
        )
    }

    @Test fun translateEnumsMatchPrd() {
        assertEquals(listOf("bar", "translator", "process_text"), TranslateSurface.entries.map { it.value })
        assertEquals(listOf("on_device", "cloud"), TranslateEngineParam.entries.map { it.value })
        assertEquals(
            listOf("no_pack", "timeout", "net", "quota", "other"),
            TranslateErrorReason.entries.map { it.value },
        )
        assertEquals(listOf("process_text", "send"), ProcessTextAction.entries.map { it.value })
        assertEquals(
            mapOf("surface" to "translator", "reason" to "no_pack"),
            EventCatalog.translateError(TranslateSurface.TRANSLATOR, TranslateErrorReason.NO_PACK).params,
        )
        assertEquals(
            mapOf("action" to "process_text", "editable" to true),
            EventCatalog.processTextOpen(ProcessTextAction.PROCESS_TEXT, true).params,
        )
        assertEquals("translate", EventCatalog.packDownloadStart("ur", PackKind.TRANSLATE, 0).params["kind"])
    }

    @Test fun namesMatchPrd() {
        val expected = setOf(
            "onboarding_start", "onboarding_step_view", "ime_enabled", "ime_selected", "languages_set",
            "onboarding_complete", "ime_session_start", "first_dictation", "voice_session_start",
            "voice_session_end", "voice_permission_result", "voice_unavailable", "privacy_private_app_add",
            "pack_download_start", "pack_download_done", "translate_done", "first_translate", "translate_error",
            "translate_preview", "process_text_open", "process_text_replace", "deeplink_unknown",
        )
        assertEquals(expected, EventCatalog.allNames.toSet())
        assertFalse("first_keystroke" in EventCatalog.allNames)
    }
}
