/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.Origin
import com.manjul.aikeyboard.core.translate.Support
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.TranslatorPool
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MlKitTranslationEngineTest {
    private val en = Lang("en")
    private val ur = Lang("ur")
    private val hi = Lang("hi")

    private class FakeHandle(val pair: LangPair, val behaviour: suspend (String) -> String) : TranslatorHandle {
        var closed = false
        override suspend fun translate(text: String): String = behaviour(text)
        override fun close() {
            closed = true
        }
    }

    private class Rig(
        scope: TestScope,
        var downloaded: Set<Lang>,
        behaviour: suspend (String) -> String = { "[$it]" },
    ) {
        val opened = mutableListOf<FakeHandle>()
        val pool = TranslatorPool<LangPair, TranslatorHandle>(scope = scope.backgroundScope) { pair ->
            FakeHandle(pair, behaviour).also { opened += it }
        }
        val engine = MlKitTranslationEngine(
            presence = { downloaded },
            pool = pool,
            io = StandardTestDispatcher(scope.testScheduler),
        )
    }

    private fun req(source: Lang, target: Lang) = TranslateRequest("hello", source, target, Origin.BAR)

    @Test fun missingModelNeedsDownloadAndOpensNoTranslator() = runTest {
        val r = Rig(this, downloaded = emptySet())
        assertEquals(Support.NEEDS_DOWNLOAD, r.engine.supports(LangPair(en, ur)))
        assertEquals(TranslateResult.Failed(FailKind.NO_PACK), r.engine.translate(req(en, ur)))
        assertTrue(r.opened.isEmpty())
    }

    @Test fun presentModelTranslates() = runTest {
        val r = Rig(this, downloaded = setOf(en, ur))
        assertEquals(Support.ON_DEVICE, r.engine.supports(LangPair(en, ur)))
        assertEquals(TranslateResult.Ok("[hello]", en, EngineId.MLKIT), r.engine.translate(req(en, ur)))
        assertEquals(listOf(LangPair(en, ur)), r.opened.map { it.pair })
    }

    @Test fun nonEnglishPairNeedsTheEnglishPivot() = runTest {
        val r = Rig(this, downloaded = setOf(ur, hi))
        assertEquals(Support.NEEDS_DOWNLOAD, r.engine.supports(LangPair(ur, hi)))
        r.downloaded = setOf(ur, hi, en)
        assertEquals(Support.ON_DEVICE, r.engine.supports(LangPair(ur, hi)))
    }

    @Test fun unsupportedLanguageIsNone() = runTest {
        val r = Rig(this, downloaded = setOf(en))
        assertEquals(Support.NONE, r.engine.supports(LangPair(en, Lang("ps"))))
        assertEquals(TranslateResult.Failed(FailKind.UNSUPPORTED), r.engine.translate(req(en, Lang("ps"))))
    }

    @Test fun slowTranslationTimesOut() = runTest {
        val r = Rig(this, downloaded = setOf(en, ur), behaviour = { delay(10_000); it })
        assertEquals(TranslateResult.Failed(FailKind.TIMEOUT), r.engine.translate(req(en, ur)))
    }

    @Test fun engineErrorIsMappedAndItsMessageDropped() = runTest {
        // MlKitException codes are covered by MlKitErrorsTest (its constructor needs Robolectric).
        val r = Rig(this, downloaded = setOf(en, ur), behaviour = { throw IllegalStateException("secret") })
        val result = r.engine.translate(req(en, ur))
        assertEquals(TranslateResult.Failed(FailKind.ENGINE), result)
        assertFalse("secret" in result.toString())
    }

    @Test fun capKeepsTheFirst200CharsWithoutSplittingASurrogatePair() {
        assertEquals("abc", MlKitLanguageDetector.cap("abc"))
        assertEquals(200, MlKitLanguageDetector.cap("a".repeat(300)).length)
        val emojiAtEdge = "a".repeat(199) + "😀" + "b"
        assertEquals("a".repeat(199), MlKitLanguageDetector.cap(emojiAtEdge))
    }
}
