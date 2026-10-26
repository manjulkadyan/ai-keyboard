/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AiEventsTest {
    private val aiEvents = listOf(
        EventCatalog.aiRequest(AiEngineParam.CLOUD),
        EventCatalog.aiRewriteDone(AiTone.FORMAL, chars = 300, latencyMs = 1_500, action = AiAction.REPLACE),
        EventCatalog.aiPolishDone(PolishMode.CLEAN_TRANSLATE, auto = true),
        EventCatalog.aiQuotaHit(AiFeature.POLISH),
        EventCatalog.aiError(AiErrorReason.TEXT_CHANGED),
        EventCatalog.aiOutputReported(AiTone.EMOJIFY),
        EventCatalog.consentResult(ConsentKind.AI, agreed = true),
        EventCatalog.consentWithdrawn(ConsentKind.AUTO_POLISH),
    )

    @Test fun factoriesUseRegisteredNamesAndOnlyRegisteredParams() {
        assertEquals(
            listOf(
                "ai_request", "ai_rewrite_done", "ai_polish_done", "ai_quota_hit", "ai_error",
                "ai_output_reported", "consent_result", "consent_withdrawn",
            ),
            aiEvents.map { it.name },
        )
        for (e in aiEvents) {
            val registry = EventCatalog.paramRegistry.getValue(e.name)
            assertEquals(registry, e.params.keys, e.name)
        }
    }

    @Test fun paramsCarryOnlyClosedValues() {
        assertEquals(mapOf("engine" to "cloud"), aiEvents[0].params)
        assertEquals(
            mapOf("tone" to "formal", "chars_bucket" to "201-1000", "latency_bucket" to "1000-2000", "action" to "replace"),
            aiEvents[1].params,
        )
        assertEquals(mapOf("mode" to "clean_translate", "auto" to true), aiEvents[2].params)
        assertEquals(mapOf("feature" to "polish"), aiEvents[3].params)
        assertEquals(mapOf("reason" to "text_changed"), aiEvents[4].params)
        assertEquals(mapOf("tone" to "emojify"), aiEvents[5].params)
        assertEquals(mapOf("kind" to "ai", "agreed" to true), aiEvents[6].params)
        assertEquals(mapOf("kind" to "auto_polish"), aiEvents[7].params)
    }

    @Test fun charsBucketFor300ContainsIt() {
        val bucket = EventCatalog.aiRewriteDone(AiTone.FIX, 300, 0, AiAction.INSERT).params["chars_bucket"]
        assertEquals("201-1000", bucket)
        assertEquals("201-1000", Buckets.chars(300))
    }

    @Test fun latencyBuckets() {
        assertEquals("0-500", Buckets.latency(-1))
        assertEquals("0-500", Buckets.latency(499))
        assertEquals("500-1000", Buckets.latency(500))
        assertEquals("1000-2000", Buckets.latency(1_999))
        assertEquals("2000-5000", Buckets.latency(2_000))
        assertEquals("5000-10000", Buckets.latency(9_999))
        assertEquals("10000+", Buckets.latency(10_000))
        assertEquals("10000+", Buckets.latency(Long.MAX_VALUE))
    }

    @Test fun enumValuesMatchPrd() {
        assertEquals(listOf("replace", "insert"), AiAction.entries.map { it.value })
        assertEquals(listOf("clean", "clean_translate"), PolishMode.entries.map { it.value })
        assertEquals(listOf("rewrite", "polish"), AiFeature.entries.map { it.value })
        assertEquals(
            listOf("offline", "timeout", "refused", "paused", "too_long", "unavailable", "rate_limited", "text_changed"),
            AiErrorReason.entries.map { it.value },
        )
        assertEquals(
            listOf("fix", "formal", "friendly", "shorter", "longer", "emojify", "translate", "polish"),
            AiTone.entries.map { it.value },
        )
        assertEquals(listOf("ai", "auto_polish"), ConsentKind.entries.map { it.value })
        assertEquals(listOf("cloud", "on_device"), AiEngineParam.entries.map { it.value })
    }

    @Test fun noFactoryTakesAString() {
        val names = setOf(
            "aiRequest", "aiRewriteDone", "aiPolishDone", "aiQuotaHit", "aiError", "aiOutputReported",
            "consentResult", "consentWithdrawn",
        )
        val methods = EventCatalog::class.java.declaredMethods.filter { it.name in names }
        assertEquals(names, methods.map { it.name }.toSet())
        for (m in methods) {
            assertTrue(m.parameterTypes.none { it == String::class.java || it == CharSequence::class.java }, m.name)
        }
    }

    @Test fun optOutStopsEveryAiEventThroughTheRelay() = runTest {
        val flushed = mutableListOf<AnalyticsEvent>()
        val sink = object : AnalyticsSink {
            override fun log(event: AnalyticsEvent) { flushed += event }
        }
        var optOut = true
        val relay = BatchingRelay(sink, { currentTime }, this, { optOut }, { null })
        aiEvents.forEach(relay::log)
        advanceTimeBy(61_000)
        relay.flushNow()
        assertEquals(emptyList(), flushed)

        optOut = false
        aiEvents.forEach(relay::log)
        optOut = true // opting out stops even the queued batch
        advanceTimeBy(61_000)
        relay.flushNow()
        assertEquals(emptyList(), flushed)

        optOut = false
        aiEvents.forEach(relay::log)
        advanceTimeBy(61_000)
        assertEquals(aiEvents, flushed)
    }
}
