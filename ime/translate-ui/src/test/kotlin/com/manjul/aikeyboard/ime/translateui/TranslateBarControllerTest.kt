/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.translateui

import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.RateSampler
import com.manjul.aikeyboard.core.datastore.BarMode
import com.manjul.aikeyboard.core.translate.CacheKey
import com.manjul.aikeyboard.core.translate.CloudTranslationEngine
import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.ModelSizes
import com.manjul.aikeyboard.core.translate.Origin
import com.manjul.aikeyboard.core.translate.RouterContext
import com.manjul.aikeyboard.core.translate.SourceResolver
import com.manjul.aikeyboard.core.translate.Support
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.TranslationCache
import com.manjul.aikeyboard.core.translate.TranslationEngineRouter
import com.manjul.aikeyboard.core.translate.bar.BarStatus
import com.manjul.aikeyboard.core.translate.bar.DebouncePolicy
import com.manjul.aikeyboard.core.translate.testing.FakeLanguageDetector
import com.manjul.aikeyboard.core.translate.testing.FakeTranslationEngine
import com.manjul.aikeyboard.ime.editing.testing.FakeEditor
import com.manjul.aikeyboard.ime.editing.testing.HostSafetyChecker
import com.manjul.aikeyboard.ime.editing.testing.SelectionUpdate
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitivityReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A controller over the real router with [FakeTranslationEngine] and a [FakeEditor] host, in virtual time. Shared by
 * the controller tests and [BarComposingSafetyPropertyTest].
 */
internal class BarRig(
    val scope: TestScope,
    val engine: FakeTranslationEngine,
    host: String = "Hi ",
    sampler: RateSampler = RateSampler(0.0),
) {
    val editor = FakeEditor(host)
    val checker = HostSafetyChecker(editor)
    var decision = PolicyDecision.Unrestricted
    var generation = 1
    var pair = EN_ES
    var mode = BarMode.PREVIEW
    var firstEver = true
    val events = mutableListOf<AnalyticsEvent>()
    val downloads = mutableListOf<LangPair>()
    var offlineOpened = 0
    val cache = TranslationCache()
    private val router =
        TranslationEngineRouter(engine, CloudTranslationEngine(), SourceResolver(FakeLanguageDetector()), cache)

    val controller = TranslateBarController(
        scope = scope.backgroundScope,
        router = { text, p, _ ->
            val ctx = RouterContext(
                allowTranslate = decision.allowTranslate,
                allowCloud = decision.allowCloud,
                pro = false,
                cloudOptIn = false,
                preferQuality = false,
            )
            router.translate(TranslateRequest(text, p.source, p.target, Origin.BAR), ctx, p.source)
        },
        decision = { decision },
        generation = { generation },
        target = editor,
        pair = { pair },
        mode = { mode },
        debounce = DebouncePolicy.Default,
        analytics = events::add,
        previewSampler = sampler,
        clock = { scope.testScheduler.currentTime },
        onDownload = downloads::add,
        onOpenOfflineLanguages = { offlineOpened++ },
        onPairChange = { pair = it },
        onModeChange = { mode = it },
        cache = cache,
        onFirstTranslate = { firstEver.also { firstEver = false } },
    )

    val open: BarUiState.Open get() = assertIs<BarUiState.Open>(controller.ui.value)

    /** Delivers every queued selection update to the controller, as `onUpdateSelection` would. */
    fun deliver() = editor.deliverAll(::forward)

    fun deliverOne() = editor.deliverNext(::forward)

    fun forward(u: SelectionUpdate) = controller.onSelectionUpdate(u.selStart, u.selEnd, u.candStart, u.candEnd)

    fun named(name: String) = events.filter { it.name == name }

    companion object {
        val EN_ES = LangPair(Lang("en"), Lang("es"))
        val PASSWORD = PolicyDecision.Unrestricted.copy(
            sensitive = true,
            reasons = setOf(SensitivityReason.PASSWORD),
            allowTranslate = false,
            allowCloud = false,
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TranslateBarControllerTest {
    private var support = Support.ON_DEVICE
    private var fail: FailKind? = null
    private var delayMs = 0L

    private fun engine() = FakeTranslationEngine(
        delays = { delayMs },
        supports = { support },
        results = { req ->
            val text = if (req.text == "hello" && req.target == Lang("es")) "hola" else "<${req.target}>${req.text}"
            fail?.let { TranslateResult.Failed(it) } ?: TranslateResult.Ok(text, req.source!!, EngineId.MLKIT)
        },
    )

    private fun TestScope.rig(host: String = "Hi ", sampler: RateSampler = RateSampler(0.0)) =
        BarRig(this, engine(), host, sampler)

    /** Opens the bar, types [text] and waits past the debounce and the engine. */
    private fun TestScope.typed(rig: BarRig, text: String = "hello") {
        rig.controller.open()
        runCurrent()
        rig.controller.buffer.type(text)
        runCurrent()
        advanceTimeBy(DEBOUNCE + delayMs + 1)
        runCurrent()
    }

    @Test fun `open in a password field stays hidden and never calls the engine`() = runTest {
        val rig = rig()
        rig.decision = BarRig.PASSWORD
        rig.controller.open()
        rig.controller.buffer.type("hello")
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(BarUiState.Hidden, rig.controller.ui.value)
        assertFalse(rig.controller.capturingInput)
        assertTrue(rig.engine.calls.isEmpty())
    }

    @Test fun `typing makes one call after the debounce and previews without touching the host`() = runTest {
        val rig = rig()
        rig.controller.open()
        runCurrent()
        assertTrue(rig.controller.capturingInput)
        for (c in "hello") {
            rig.controller.buffer.type(c.toString())
            runCurrent()
            advanceTimeBy(100)
        }
        // The last key went in at 400 ms: nothing before 400 + 450.
        advanceTimeBy(DEBOUNCE - 101)
        runCurrent()
        assertTrue(rig.engine.calls.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("hello"), rig.engine.calls.map { it.text })
        assertEquals("hola", rig.open.preview)
        assertEquals("hello", rig.open.input)
        assertEquals(BarStatus.Ok, rig.open.status)
        assertEquals("Hi ", rig.editor.text)
    }

    @Test fun `insert commits the preview once, clears the buffer and logs translate_done`() = runTest {
        val rig = rig()
        typed(rig)
        rig.controller.insert()
        runCurrent()
        assertEquals("Hi hola", rig.editor.text)
        assertEquals("", rig.controller.buffer.text.value)
        assertEquals("", rig.open.input)
        assertNull(rig.open.preview)
        assertTrue(rig.open.undoAvailable)
        val done = rig.named(EventCatalog.TRANSLATE_DONE).single()
        assertEquals(
            mapOf(
                "surface" to "bar", "engine" to "on_device", "src" to "en", "tgt" to "es",
                "chars_bucket" to "1-20", "offline" to true,
            ),
            done.params,
        )
        rig.controller.insert()
        runCurrent()
        assertEquals("Hi hola", rig.editor.text)
        assertEquals(1, rig.named(EventCatalog.TRANSLATE_DONE).size)
        rig.checker.assertSafe()
    }

    @Test fun `insert does nothing while the preview is stale or missing`() = runTest {
        val rig = rig()
        rig.controller.open()
        runCurrent()
        rig.controller.insert()
        rig.controller.buffer.type("hello")
        runCurrent()
        rig.controller.insert()
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()
        // A newer key makes the preview stale until its own translation arrives.
        rig.controller.buffer.type("x")
        runCurrent()
        rig.controller.insert()
        assertEquals("Hi ", rig.editor.text)
        assertTrue(rig.named(EventCatalog.TRANSLATE_DONE).isEmpty())
    }

    @Test fun `first_translate is logged once, only when the app says it is the first`() = runTest {
        val rig = rig()
        typed(rig)
        rig.controller.insert()
        runCurrent()
        typed(rig)
        rig.controller.insert()
        runCurrent()
        assertEquals(1, rig.named(EventCatalog.FIRST_TRANSLATE).size)
        assertEquals(mapOf("surface" to "bar"), rig.named(EventCatalog.FIRST_TRANSLATE).single().params)

        val later = rig(host = "")
        later.firstEver = false
        typed(later)
        later.controller.insert()
        runCurrent()
        assertTrue(later.named(EventCatalog.FIRST_TRANSLATE).isEmpty())
    }

    @Test fun `undo within 5 s removes our insert and restores the source`() = runTest {
        val rig = rig()
        typed(rig)
        rig.controller.insert()
        runCurrent()
        rig.deliver()
        advanceTimeBy(4_900)
        rig.controller.undo()
        runCurrent()
        assertEquals("Hi ", rig.editor.text)
        assertEquals("hello", rig.controller.buffer.text.value)
        assertFalse(rig.open.undoAvailable)
        rig.checker.assertSafe()
    }

    @Test fun `undo after 5 s does nothing and the chip goes away`() = runTest {
        val rig = rig()
        typed(rig)
        rig.controller.insert()
        runCurrent()
        advanceTimeBy(5_100)
        runCurrent()
        assertFalse(rig.open.undoAvailable)
        rig.controller.undo()
        assertEquals("Hi hola", rig.editor.text)
        assertEquals("", rig.controller.buffer.text.value)
    }

    @Test fun `undo does nothing after the host typed`() = runTest {
        val rig = rig()
        typed(rig)
        rig.controller.insert()
        runCurrent()
        rig.deliver()
        rig.editor.hostType("x")
        rig.deliver()
        assertFalse(rig.open.undoAvailable)
        rig.controller.undo()
        assertEquals("Hi holax", rig.editor.text)
        rig.checker.assertSafe()
    }

    @Test fun `undo does nothing when the host typed and its update is still on its way`() = runTest {
        val rig = rig()
        typed(rig)
        rig.controller.insert()
        runCurrent()
        rig.editor.hostType("x")
        rig.controller.undo()
        assertEquals("Hi holax", rig.editor.text)
        rig.checker.assertSafe()
    }

    @Test fun `a field turning sensitive mid-request hides the bar and drops the late result`() = runTest {
        delayMs = 1_000
        val rig = rig()
        rig.controller.open()
        runCurrent()
        rig.controller.buffer.type("hello")
        runCurrent()
        advanceTimeBy(DEBOUNCE + 500)
        runCurrent()
        assertEquals(1, rig.engine.inFlight)
        rig.cache.put(CacheKey(EngineId.MLKIT, Lang("en"), Lang("es"), "x"), "y")

        rig.decision = BarRig.PASSWORD
        rig.controller.onPolicyRestricted(BarRig.PASSWORD)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(BarUiState.Hidden, rig.controller.ui.value)
        assertFalse(rig.controller.capturingInput)
        assertEquals("", rig.controller.buffer.text.value)
        assertEquals(0, rig.cache.size)
        assertEquals("Hi ", rig.editor.text)

        // Back in a normal field: nothing of the old request shows up.
        rig.decision = PolicyDecision.Unrestricted
        rig.controller.open()
        runCurrent()
        assertNull(rig.open.preview)
        assertEquals("", rig.open.input)
        rig.controller.insert()
        assertEquals("Hi ", rig.editor.text)
    }

    @Test fun `a sensitive close in live mode removes our composing preview`() = runTest {
        val rig = rig()
        rig.mode = BarMode.LIVE
        typed(rig)
        assertEquals("Hi hola", rig.editor.text)
        rig.decision = BarRig.PASSWORD
        rig.controller.onPolicyRestricted(BarRig.PASSWORD)
        assertEquals("Hi ", rig.editor.text)
        assertEquals(BarUiState.Hidden, rig.controller.ui.value)
        rig.checker.assertSafe()
    }

    @Test fun `a late result after a generation bump is discarded`() = runTest {
        delayMs = 1_000
        val rig = rig()
        rig.controller.open()
        runCurrent()
        rig.controller.buffer.type("hello")
        runCurrent()
        advanceTimeBy(DEBOUNCE + 500)
        runCurrent()
        rig.generation++
        advanceTimeBy(1_000)
        runCurrent()
        assertNull(rig.open.preview)
        assertEquals("", rig.open.input)
        rig.controller.insert()
        assertEquals("Hi ", rig.editor.text)
    }

    @Test fun `a missing pack shows the download offer, keeps the preview and downloads only on tap`() = runTest {
        val rig = rig()
        typed(rig)
        support = Support.NEEDS_DOWNLOAD
        rig.controller.buffer.type(" there")
        runCurrent()
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()
        assertEquals("hola", rig.open.preview)
        val status = assertIs<BarStatus.NeedsDownload>(rig.open.status)
        assertEquals(BarRig.EN_ES, status.pair)
        assertEquals(ModelSizes.APPROX_MODEL_BYTES, status.bytes)
        assertTrue(rig.downloads.isEmpty())
        assertEquals(
            mapOf("surface" to "bar", "reason" to "no_pack"),
            rig.named(EventCatalog.TRANSLATE_ERROR).single().params,
        )
        rig.controller.download()
        assertEquals(listOf(BarRig.EN_ES), rig.downloads)
        // A stale preview is never inserted.
        rig.controller.insert()
        assertEquals("Hi ", rig.editor.text)
    }

    @Test fun `offline after a good preview keeps it and shows the offline chip`() = runTest {
        val rig = rig()
        typed(rig)
        fail = FailKind.OFFLINE
        rig.controller.buffer.type("!")
        runCurrent()
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()
        assertEquals("hola", rig.open.preview)
        assertEquals(BarStatus.Offline, rig.open.status)
        assertEquals(listOf("net"), rig.named(EventCatalog.TRANSLATE_ERROR).map { it.params["reason"] })
        rig.controller.openOfflineLanguages()
        assertEquals(1, rig.offlineOpened)
        rig.controller.insert()
        assertEquals("Hi ", rig.editor.text)

        fail = null
        rig.controller.retry()
        runCurrent()
        assertEquals("<es>hello!", rig.open.preview)
        assertEquals(BarStatus.Ok, rig.open.status)
    }

    @Test fun `translate_preview is sampled`() = runTest {
        val sampled = rig(sampler = RateSampler(0.01) { 0.005 })
        typed(sampled)
        assertEquals(
            mapOf("surface" to "bar", "engine" to "on_device", "src" to "en", "tgt" to "es"),
            sampled.named(EventCatalog.TRANSLATE_PREVIEW).single().params,
        )
        val unsampled = rig(sampler = RateSampler(0.01) { 0.02 })
        typed(unsampled)
        assertTrue(unsampled.named(EventCatalog.TRANSLATE_PREVIEW).isEmpty())
    }

    @Test fun `live mode composes previews in the host and insert commits them`() = runTest {
        val rig = rig()
        rig.mode = BarMode.LIVE
        typed(rig)
        assertEquals("Hi hola", rig.editor.text)
        assertEquals(3, rig.editor.composingStart)
        rig.deliver()
        rig.controller.buffer.type("!")
        runCurrent()
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()
        assertEquals("Hi <es>hello!", rig.editor.text)
        rig.controller.insert()
        runCurrent()
        assertEquals("Hi <es>hello!", rig.editor.text)
        assertEquals(-1, rig.editor.composingStart)
        assertEquals("", rig.controller.buffer.text.value)
        rig.checker.assertSafe()
    }

    @Test fun `live mode close removes our composing text`() = runTest {
        val rig = rig()
        rig.mode = BarMode.LIVE
        typed(rig)
        rig.deliver()
        rig.controller.close(CloseReason.USER)
        assertEquals("Hi ", rig.editor.text)
        assertEquals(BarUiState.Hidden, rig.controller.ui.value)
    }

    @Test fun `live mode close keeps the composing text once the host moved away from it`() = runTest {
        val rig = rig()
        rig.mode = BarMode.LIVE
        typed(rig)
        rig.deliver()
        rig.editor.hostMoveCursor(0)
        rig.deliver()
        rig.controller.close(CloseReason.USER)
        assertEquals("Hi hola", rig.editor.text)
        rig.checker.assertSafe()
    }

    @Test fun `switching to preview removes the live preview, switching back composes it`() = runTest {
        val rig = rig()
        rig.mode = BarMode.LIVE
        typed(rig)
        rig.controller.setMode(BarMode.PREVIEW)
        assertEquals("Hi ", rig.editor.text)
        assertEquals(BarMode.PREVIEW, rig.mode)
        assertEquals(BarMode.PREVIEW, rig.open.mode)
        rig.controller.setMode(BarMode.LIVE)
        assertEquals("Hi hola", rig.editor.text)
    }

    @Test fun `swap swaps the pair, keeps the buffer and sends a new request`() = runTest {
        val rig = rig()
        typed(rig)
        rig.controller.swapPair()
        runCurrent()
        val swapped = LangPair(Lang("es"), Lang("en"))
        assertEquals(swapped, rig.open.pair)
        assertEquals(swapped, rig.pair)
        assertEquals("hello", rig.open.input)
        assertEquals(2, rig.engine.calls.size)
        assertEquals(Lang("en"), rig.engine.calls.last().target)
        assertEquals("<en>hello", rig.open.preview)
    }

    @Test fun `a new field clears the buffer and keeps pair and mode`() = runTest {
        val rig = rig()
        typed(rig)
        rig.controller.swapPair()
        rig.controller.setMode(BarMode.LIVE)
        runCurrent()
        rig.generation++
        rig.controller.onFieldChanged()
        runCurrent()
        assertEquals("", rig.open.input)
        assertNull(rig.open.preview)
        assertEquals(LangPair(Lang("es"), Lang("en")), rig.open.pair)
        assertEquals(BarMode.LIVE, rig.open.mode)
    }

    @Test fun `hide keeps the buffer, a user close clears it`() = runTest {
        val rig = rig()
        typed(rig)
        rig.controller.close(CloseReason.HIDDEN)
        assertEquals(BarUiState.Hidden, rig.controller.ui.value)
        assertEquals("hello", rig.controller.buffer.text.value)
        rig.controller.open()
        runCurrent()
        assertEquals("hello", rig.open.input)
        rig.controller.close(CloseReason.USER)
        assertEquals("", rig.controller.buffer.text.value)
    }

    @Test fun `a result the router caches after the field turned sensitive is cleared again`() = runTest {
        delayMs = 1_000
        val rig = rig()
        rig.controller.open()
        runCurrent()
        rig.controller.buffer.type("hello")
        runCurrent()
        // Up to the instant the engine answers; the answer and the sensitive close land in the same turn.
        advanceTimeBy(DEBOUNCE + delayMs)
        assertEquals(1, rig.engine.inFlight)
        rig.decision = BarRig.PASSWORD
        rig.controller.onPolicyRestricted(BarRig.PASSWORD)
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(0, rig.engine.inFlight)
        assertEquals(0, rig.cache.size)
        assertEquals(BarUiState.Hidden, rig.controller.ui.value)
        assertEquals("Hi ", rig.editor.text)
    }

    @Test fun `enter before the debounce translates now and the next enter commits`() = runTest {
        val rig = rig()
        rig.controller.open()
        runCurrent()
        rig.controller.buffer.type("hello")
        runCurrent()
        rig.controller.insert()
        runCurrent()
        assertEquals("Hi ", rig.editor.text)
        assertEquals(listOf("hello"), rig.engine.calls.map { it.text })
        assertEquals("hola", rig.open.preview)
        // The debounced request for the same text is not sent again.
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()
        assertEquals(1, rig.engine.calls.size)
        rig.controller.insert()
        runCurrent()
        assertEquals("Hi hola", rig.editor.text)
        assertEquals(1, rig.named(EventCatalog.TRANSLATE_DONE).size)
    }

    @Test fun `enter while the same text is at the engine does not send it again`() = runTest {
        delayMs = 1_000
        val rig = rig()
        typed(rig)
        rig.controller.buffer.type("!")
        runCurrent()
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()
        assertTrue(rig.open.inFlight)
        rig.controller.insert()
        runCurrent()
        advanceTimeBy(delayMs)
        runCurrent()
        assertEquals(listOf("hello", "hello!"), rig.engine.calls.map { it.text })
        assertEquals("Hi ", rig.editor.text)
    }

    @Test fun `undo in live mode removes our insert, not the preview typed since`() = runTest {
        val rig = rig()
        rig.mode = BarMode.LIVE
        typed(rig)
        rig.deliver()
        rig.controller.insert()
        runCurrent()
        rig.deliver()
        rig.controller.buffer.type("hello")
        runCurrent()
        advanceTimeBy(DEBOUNCE + 1)
        runCurrent()
        rig.deliver()
        assertEquals("Hi holahola", rig.editor.text)
        assertTrue(rig.open.undoAvailable)
        rig.controller.undo()
        runCurrent()
        rig.deliver()
        assertEquals("Hi ", rig.editor.text)
        assertEquals("hello", rig.controller.buffer.text.value)
        assertEquals("hola", rig.open.preview)
        rig.controller.close(CloseReason.USER)
        assertEquals("Hi ", rig.editor.text)
        rig.checker.assertSafe()
    }

    @Test fun `every insert gets a new undo id`() = runTest {
        val rig = rig()
        typed(rig)
        val before = rig.open.undoId
        rig.controller.insert()
        runCurrent()
        rig.deliver()
        val first = rig.open.undoId
        typed(rig)
        rig.controller.insert()
        runCurrent()
        assertTrue(rig.open.undoAvailable)
        assertTrue(before < first && first < rig.open.undoId)
    }

    @Test fun `the bar's callbacks take the controller's methods directly`() = runTest {
        val rig = rig()
        typed(rig)
        val c = rig.controller
        val onSwap: () -> Unit = c::swapPair
        val onInsert: () -> Unit = c::insert
        val onUndo: () -> Unit = c::undo
        val onDownload: () -> Unit = c::download
        val onOpenOfflineLanguages: () -> Unit = c::openOfflineLanguages
        val onRetry: () -> Unit = c::retry
        val onClose: () -> Unit = c::close
        onOpenOfflineLanguages()
        assertEquals(1, rig.offlineOpened)
        onDownload()
        assertEquals(listOf(BarRig.EN_ES), rig.downloads)
        onSwap()
        onRetry()
        runCurrent()
        onInsert()
        runCurrent()
        assertEquals("Hi <en>hello", rig.editor.text)
        onUndo()
        assertEquals("Hi ", rig.editor.text)
        onClose()
        assertEquals(BarUiState.Hidden, c.ui.value)
        assertEquals("", c.buffer.text.value)
    }

    private companion object {
        const val DEBOUNCE = 450L
    }
}
