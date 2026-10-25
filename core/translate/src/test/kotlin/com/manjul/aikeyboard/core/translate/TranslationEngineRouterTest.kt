/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import com.manjul.aikeyboard.core.translate.testing.FakeLanguageDetector
import com.manjul.aikeyboard.core.translate.testing.FakeTranslationEngine
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationEngineRouterTest {
    private val en = Lang("en")
    private val es = Lang("es")
    private val ur = Lang("ur")
    private val pair = LangPair(en, es)

    private val allowed = RouterContext(allowTranslate = true, allowCloud = true, pro = false, cloudOptIn = false, preferQuality = false)
    private val proCloud = allowed.copy(pro = true, cloudOptIn = true)

    private class Rig(
        local: Support = Support.ON_DEVICE,
        remote: Support = Support.NONE,
        detections: Map<String, List<Pair<String, Float>>> = emptyMap(),
        localResults: ((TranslateRequest) -> TranslateResult)? = null,
    ) {
        val onDevice = if (localResults != null) {
            FakeTranslationEngine(EngineId.MLKIT, supports = { local }, results = localResults)
        } else {
            FakeTranslationEngine(EngineId.MLKIT, supports = { local })
        }
        val cloud = FakeTranslationEngine(EngineId.CLOUD, supports = { remote })
        val detector = FakeLanguageDetector(detections)
        val cache = TranslationCache()
        val router = TranslationEngineRouter(onDevice, cloud, SourceResolver(detector), cache)
    }

    /** Independent statement of TRD §4.3.1 / FR-11 AC3, for the exhaustive table. */
    private fun expected(ctx: RouterContext, local: Support, remote: Support): Route {
        val cloudPermitted = ctx.pro && ctx.cloudOptIn && ctx.allowCloud
        val localUsable = local == Support.ON_DEVICE || local == Support.NEEDS_DOWNLOAD
        val cloudCan = remote != Support.NONE && remote != Support.NEEDS_DOWNLOAD
        return when {
            !ctx.allowTranslate -> Route.Refused
            !localUsable && !cloudCan -> Route.Unsupported
            !localUsable && cloudPermitted -> Route.Use(EngineId.CLOUD)
            !localUsable && ctx.pro && ctx.cloudOptIn -> Route.Refused
            !localUsable -> Route.CloudOnlyNeedsPro
            ctx.preferQuality && cloudPermitted && cloudCan -> Route.Use(EngineId.CLOUD)
            local == Support.ON_DEVICE -> Route.Use(EngineId.MLKIT)
            else -> Route.NeedsDownload(pair)
        }
    }

    @Test fun routeTableCoversEveryCombination() = runTest {
        val bools = listOf(false, true)
        var cases = 0
        for (allowTranslate in bools) for (allowCloud in bools) for (pro in bools) for (optIn in bools)
            for (preferQuality in bools) for (local in Support.entries) for (remote in Support.entries) {
                val ctx = RouterContext(allowTranslate, allowCloud, pro, optIn, preferQuality)
                val r = Rig(local, remote)
                val route = r.router.route(pair, ctx)
                assertEquals(expected(ctx, local, remote), route, "$ctx local=$local remote=$remote")
                if (route == Route.Use(EngineId.CLOUD)) {
                    assertTrue(pro && optIn && allowCloud && allowTranslate, "cloud chosen without permission: $ctx")
                }
                if (!allowTranslate) {
                    assertTrue(r.onDevice.supportQueries.isEmpty() && r.cloud.supportQueries.isEmpty())
                }
                cases++
            }
        assertEquals(2 * 2 * 2 * 2 * 2 * 4 * 4, cases)
    }

    @Test fun refusedFieldNeverTouchesDetectorCacheOrEngines() = runTest {
        val r = Rig(detections = mapOf("my password" to listOf("en" to 0.99f)))
        val refused = proCloud.copy(allowTranslate = false, preferQuality = true)
        val result = r.router.translate(TranslateRequest("my password", null, es, Origin.BAR), refused, en)
        assertEquals(TranslateResult.Failed(FailKind.POLICY), result)
        assertEquals(Route.Refused, r.router.route(pair, refused))
        assertTrue(r.onDevice.calls.isEmpty() && r.cloud.calls.isEmpty())
        assertTrue(r.onDevice.supportQueries.isEmpty() && r.cloud.supportQueries.isEmpty())
        assertEquals(0, r.detector.calls)
        assertEquals(0, r.cache.size)
    }

    @Test fun missingPackIsNeverSilentlySentToCloud() = runTest {
        val r = Rig(local = Support.NEEDS_DOWNLOAD, remote = Support.ON_DEVICE)
        assertEquals(Route.NeedsDownload(pair), r.router.route(pair, proCloud))
        val result = r.router.translate(TranslateRequest("hello", en, es, Origin.BAR), proCloud, en)
        assertEquals(TranslateResult.Failed(FailKind.NO_PACK), result)
        assertTrue(r.cloud.calls.isEmpty() && r.onDevice.calls.isEmpty())
    }

    @Test fun cloudOnlyPairWithoutProFailsAsCloudUnavailable() = runTest {
        val r = Rig(local = Support.NONE, remote = Support.CLOUD_ONLY)
        val result = r.router.translate(TranslateRequest("hello", en, Lang("ps"), Origin.BAR), allowed, en)
        assertEquals(TranslateResult.Failed(FailKind.CLOUD_UNAVAILABLE), result)
        assertTrue(r.cloud.calls.isEmpty())
    }

    @Test fun unsupportedPairFails() = runTest {
        val r = Rig(local = Support.NONE, remote = Support.NONE)
        val result = r.router.translate(TranslateRequest("hello", en, es, Origin.BAR), proCloud, en)
        assertEquals(TranslateResult.Failed(FailKind.UNSUPPORTED), result)
    }

    @Test fun permittedCloudOnlyPairUsesCloudEngine() = runTest {
        val r = Rig(local = Support.NONE, remote = Support.CLOUD_ONLY)
        r.router.translate(TranslateRequest("hello", en, Lang("ps"), Origin.BAR), proCloud, en)
        assertEquals(1, r.cloud.calls.size)
        assertTrue(r.onDevice.calls.isEmpty())
    }

    @Test fun sameSourceAndTargetReturnsTextWithoutEngine() = runTest {
        val r = Rig()
        val result = r.router.translate(TranslateRequest("hola", es, es, Origin.BAR), allowed, en)
        assertEquals(TranslateResult.Ok("hola", es, EngineId.MLKIT), result)
        assertTrue(r.onDevice.calls.isEmpty())
    }

    @Test fun cacheHitSkipsTheEngine() = runTest {
        val r = Rig()
        val req = TranslateRequest("hello", en, es, Origin.BAR)
        val first = r.router.translate(req, allowed, en)
        val second = r.router.translate(req, allowed, en)
        assertEquals(first, second)
        assertEquals(TranslateResult.Ok("<es>hello", en, EngineId.MLKIT), second)
        assertEquals(1, r.onDevice.calls.size)
    }

    @Test fun detectedSourceIsUsedAndUnsureDetectionKeepsLastSource() = runTest {
        val r = Rig(detections = mapOf("آپ کیسے ہیں" to listOf("ur" to 0.9f), "ok" to listOf("ur" to 0.3f)))
        r.router.translate(TranslateRequest("آپ کیسے ہیں", null, es, Origin.BAR), allowed, en)
        r.router.translate(TranslateRequest("ok", null, es, Origin.BAR), allowed, en)
        assertEquals(listOf(ur, en), r.onDevice.calls.map { it.source })
    }

    @Test fun longTextIsChunkedAndJoinedWithOriginalSeparators() = runTest {
        val r = Rig()
        val sentences = (1..5).map { i -> "Sentence $i " + "word ".repeat(70).trim() + "." }
        val text = sentences[0] + " " + sentences[1] + "\n\n" + sentences[2] + " " + sentences[3] + "\n\n" + sentences[4]
        assertTrue(text.length in 1_700..1_900, "length ${text.length}")
        val result = r.router.translate(TranslateRequest(text, en, es, Origin.TRANSLATOR), allowed, en)
        assertTrue(r.onDevice.calls.size >= 4)
        val expectedText = "<es>" + sentences[0] + " <es>" + sentences[1] + "\n\n<es>" + sentences[2] + " <es>" +
            sentences[3] + "\n\n<es>" + sentences[4]
        assertEquals(TranslateResult.Ok(expectedText, en, EngineId.MLKIT), result)
        assertTrue(r.onDevice.calls.all { it.text.length <= SentenceChunker.DEFAULT_MAX_CHARS })
        assertTrue(r.onDevice.calls.none { '\n' in it.text })
    }

    @Test fun failedChunkFailsTheWholeRequestAndIsNotCached() = runTest {
        val r = Rig(localResults = { req ->
            if (req.text.startsWith("Two")) TranslateResult.Failed(FailKind.TIMEOUT)
            else TranslateResult.Ok("x", en, EngineId.MLKIT)
        })
        val req = TranslateRequest("One. Two. Three.", en, es, Origin.BAR)
        assertEquals(TranslateResult.Failed(FailKind.TIMEOUT), r.router.translate(req, allowed, en))
        assertEquals(0, r.cache.size)
    }
}
