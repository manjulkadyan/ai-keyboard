/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translate

import android.text.InputType
import android.text.Selection
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import com.manjul.aikeyboard.core.datastore.BarMode
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.datastore.LanguagePair
import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.LangPair
import com.manjul.aikeyboard.core.translate.RouterContext
import com.manjul.aikeyboard.core.translate.TranslateRequest
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.core.translate.mlkit.MlKitBootstrap
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.ime.text.key.KeyCode
import com.manjul.aikeyboard.ime.translateui.BarUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Translate bar in the keyboard, end to end. FlorisImeService cannot run under Robolectric (native library), so
 * this drives [TranslateImeSession] the way the service does: the real policy controller gets the lifecycle calls,
 * keys go through [BarKeyRouter] as KeyboardManager sends them, and a real EditText input connection is the host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TranslateImeSessionTest {
    private val app = RuntimeEnvironment.getApplication()

    class FakeModels : TranslateModels {
        var refreshes = 0
        val downloads = mutableListOf<LangPair>()
        override fun downloaded(): Set<Lang> = setOf(Lang.English, Lang.of("es"))
        override suspend fun refresh() { refreshes++ }
        override fun download(pair: LangPair) { downloads += pair }
    }

    inner class Rig(
        private val scope: TestScope,
        initial: String = "",
        val engine: (suspend (TranslateRequest, RouterContext, Lang) -> TranslateResult)? = null,
        val fullCore: Boolean = false,
        val lowTier: Boolean = false,
    ) {
        val edit = EditText(app).apply {
            setText(initial)
            setSelection(initial.length)
        }
        val commits = mutableListOf<String>()
        var deletes = 0
        val ic: InputConnection = object : InputConnectionWrapper(edit.onCreateInputConnection(EditorInfo()), false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                commits += text.toString()
                return super.commitText(text, newCursorPosition)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                deletes++
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }
        val requests = mutableListOf<TranslateRequest>()
        var translationResolved = 0
        val models = FakeModels()
        val store = FeatureSettingsStore(app, StandardTestDispatcher(scope.testScheduler))
        val policy = ImePolicyController(store, keyguard = { false }, forcedPackages = { emptySet() }, scope = scope.backgroundScope)
        var toggles = 0
        val session = if (fullCore) {
            // The app's defaults for models, engine and cache, all resolved through `translation`.
            TranslateImeSession(
                context = app,
                policy = policy,
                store = store,
                translation = { translationResolved++; error("translation core resolved") },
                analytics = {},
                connection = { ic },
                isRawInputEditor = { false },
                setOwnsComposing = {},
                scope = scope.backgroundScope,
                lowTier = { false },
            )
        } else {
            TranslateImeSession(
                context = app,
                policy = policy,
                store = store,
                translation = { translationResolved++; error("translation core resolved") },
                models = { models },
                analytics = {},
                connection = { ic },
                isRawInputEditor = { false },
                setOwnsComposing = {},
                scope = scope.backgroundScope,
                clock = { scope.testScheduler.currentTime },
                lowTier = { lowTier },
                engine = { req, ctx, last ->
                    requests += req
                    (engine ?: ::spanish)(req, ctx, last)
                },
                cache = { null },
                openOfflineLanguages = {},
                beforeToggle = { toggles++ },
            )
        }
        val text get() = edit.text.toString()
        val open get() = session.ui as? BarUiState.Open

        /** What the framework would pass to onUpdateSelection for the current field state. */
        fun report() {
            val e = edit.text
            session.onSelectionUpdate(
                Selection.getSelectionStart(e),
                Selection.getSelectionEnd(e),
                BaseInputConnection.getComposingSpanStart(e),
                BaseInputConnection.getComposingSpanEnd(e),
            )
        }

        /** Soft keys, as KeyboardManager.onInputKeyUp routes them. */
        fun type(s: String) {
            for (ch in s) {
                val consumed = if (ch == ' ') {
                    BarKeyRouter.onKey(session, KeyCode.SPACE, null)
                } else {
                    BarKeyRouter.onKey(session, ch.code, ch.toString())
                }
                assertTrue(consumed, "key not routed to the bar")
            }
        }

        fun startField(info: EditorInfo, restarting: Boolean = false) {
            session.onStartInput(restarting)
            policy.onStartInput(info, restarting)
            if (!restarting) session.onStartInputView()
        }

        fun hide() {
            policy.onFinishInputView()
            session.onFinishInputView()
        }

        fun finishField() {
            session.onFinishInput()
            policy.onFinishInput()
        }
    }

    private suspend fun spanish(req: TranslateRequest, ctx: RouterContext, last: Lang): TranslateResult =
        TranslateResult.Ok(if (req.text.trim() == "hello") "hola" else "es:${req.text}", Lang.English, EngineId.MLKIT)

    private fun textField() = EditorInfo().apply {
        packageName = "some.app"
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun passwordField() = EditorInfo().apply {
        packageName = "some.app"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun run(block: suspend TestScope.() -> Unit) = runTest(StandardTestDispatcher()) { block() }

    private suspend fun TestScope.pair(r: Rig, mode: BarMode = BarMode.PREVIEW) {
        r.store.update {
            it.copy(translatePair = LanguagePair("en", "es"), barMode = mode, barLiveComposingSet = true)
        }
        runCurrent()
    }

    private fun TestScope.debounce() {
        advanceTimeBy(DEBOUNCE_MS + 1)
        runCurrent()
    }

    @Test fun typeHelloPreviewAndEnterInsertsTheTranslationNotTheSource() = run {
        val r = Rig(this, initial = "Hi ")
        pair(r)
        r.startField(textField())
        r.session.toggle()
        runCurrent()
        assertTrue(r.session.capturingInput)
        assertEquals(1, r.models.refreshes)

        r.type("hello")
        runCurrent()
        assertEquals("Hi ", r.text, "typing goes to the bar, not the host")
        assertEquals("hello", r.open?.input)
        debounce()
        assertEquals("hola", r.open?.preview)
        assertEquals("Hi ", r.text, "preview mode writes nothing before insert")

        assertTrue(BarKeyRouter.onKey(r.session, KeyCode.ENTER, null))
        runCurrent()
        assertEquals("Hi hola", r.text)
        assertEquals(listOf("hola"), r.commits)
        assertEquals(0, r.deletes)
        assertEquals("", r.open?.input)
        assertTrue(r.requests.all { it.origin == com.manjul.aikeyboard.core.translate.Origin.BAR })
    }

    @Test fun reviewFocus1TheHostIsNeverDeletedFromInPreviewMode() = run { hostNeverDeletedFrom(BarMode.PREVIEW) }

    @Test fun reviewFocus1TheHostIsNeverDeletedFromInLiveMode() = run { hostNeverDeletedFrom(BarMode.LIVE) }

    /** Random typing, previews and close: the host is never deleted from and ends exactly as it started. */
    private suspend fun TestScope.hostNeverDeletedFrom(mode: BarMode) {
        val rnd = Random(8)
        run {
            val host = "Dear Ana, the meeting is at 10. "
            val r = Rig(this, initial = host)
            pair(r, mode)
            r.startField(textField())
            r.session.toggle()
            runCurrent()
            repeat(40) {
                when (rnd.nextInt(5)) {
                    0 -> BarKeyRouter.onKey(r.session, KeyCode.DELETE, null)
                    1 -> BarKeyRouter.onKey(r.session, KeyCode.DELETE_WORD, null)
                    2 -> BarKeyRouter.onWord(r.session, "word")
                    3 -> BarKeyRouter.onKey(r.session, KeyCode.FORWARD_DELETE, null)
                    else -> r.type(listOf("a", "b", "c", " ", "x").random(rnd))
                }
                runCurrent()
                if (rnd.nextBoolean()) debounce()
                r.report()
            }
            debounce()
            r.report()
            assertTrue(r.text.startsWith(host), "$mode: host text changed")
            r.session.close()
            runCurrent()
            r.report()
            assertEquals(host, r.text, "$mode: host text not restored exactly")
            assertEquals(0, r.deletes, "$mode: deleteSurroundingText on the host")
            assertTrue(r.commits.isEmpty(), "$mode: committed without insert")
            assertFalse(r.session.capturingInput)
            assertFalse(BarKeyRouter.onKey(r.session, 'q'.code, "q"), "$mode: keys reach the host again after close")
        }
    }

    @Test fun reviewFocus2PasswordRestartMidRequestClosesTheBarAndDropsTheLateResult() = run {
        val held = CompletableDeferred<TranslateResult>()
        val r = Rig(this, initial = "abc ", engine = { _, _, _ -> held.await() })
        pair(r)
        r.startField(textField())
        r.session.toggle()
        runCurrent()
        r.type("hello")
        debounce()
        assertEquals(1, r.requests.size)
        assertTrue(r.open?.inFlight == true)
        // Enter while the request is out: a pending commit that must not survive the restart.
        BarKeyRouter.onKey(r.session, KeyCode.ENTER, null)
        runCurrent()

        r.startField(passwordField(), restarting = true)
        runCurrent()
        assertFalse(r.session.capturingInput)
        assertEquals(BarUiState.Hidden, r.session.ui)
        assertFalse(r.policy.decision.value.allowTranslate)

        held.complete(TranslateResult.Ok("hola", Lang.English, EngineId.MLKIT))
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals("abc ", r.text)
        assertTrue(r.commits.isEmpty())
        assertEquals(0, r.deletes)
        // The chip is gone and a stale toggle does nothing.
        r.session.toggle()
        runCurrent()
        assertFalse(r.session.capturingInput)
        assertFalse(BarKeyRouter.onKey(r.session, 'q'.code, "q"))
    }

    @Test fun hideAndShowWithin60sKeepsTheBufferPastTheTimeout() = run {
        val r = Rig(this)
        pair(r)
        r.startField(textField())
        r.session.toggle()
        runCurrent()
        r.type("hello")
        runCurrent()

        r.hide()
        runCurrent()
        assertFalse(r.session.capturingInput, "hiding closes the bar")
        advanceTimeBy(30_000)
        r.session.onStartInputView()
        runCurrent()
        // Past 60 s since the hide: the keyboard is back, so the timeout must not clear the text.
        advanceTimeBy(40_000)
        runCurrent()

        r.session.toggle()
        runCurrent()
        assertEquals("hello", r.open?.input)
        assertEquals(LangPair(Lang.English, Lang.of("es")), r.open?.pair)
        assertEquals(BarMode.PREVIEW, r.open?.mode)
        assertEquals("", r.text)
    }

    @Test fun hiddenForMoreThan60sClearsTheBufferButKeepsPairAndMode() = run {
        val r = Rig(this)
        pair(r, BarMode.LIVE)
        r.startField(textField())
        r.session.toggle()
        runCurrent()
        r.type("hello")
        runCurrent()
        r.hide()
        advanceTimeBy(61_000)
        runCurrent()
        r.session.onStartInputView()
        r.session.toggle()
        runCurrent()
        assertEquals("", r.open?.input)
        assertEquals(BarMode.LIVE, r.open?.mode)
        assertEquals(LangPair(Lang.English, Lang.of("es")), r.open?.pair)
    }

    @Test fun aNewFieldClearsTheBuffer() = run {
        val r = Rig(this)
        pair(r)
        r.startField(textField())
        r.session.toggle()
        runCurrent()
        r.type("hello")
        runCurrent()
        r.hide()
        r.finishField()
        r.startField(textField())
        runCurrent()
        r.session.toggle()
        runCurrent()
        assertEquals("", r.open?.input)
    }

    @Test fun voiceGoesToTheBarWhileItIsOpen() = run {
        val r = Rig(this, initial = "abc")
        pair(r)
        r.startField(textField())
        assertNull(r.session.voiceTarget(), "closed bar: voice goes to the host")
        r.session.toggle()
        runCurrent()
        val target = r.session.voiceTarget()!!
        target.setComposing("hel")
        target.commit("hello", removeSpaceBefore = false)
        runCurrent()
        assertEquals("hello", r.open?.input)
        assertEquals("abc", r.text)
        r.session.close()
        assertNull(r.session.voiceTarget())
    }

    @Test fun toggleCancelsVoiceFirst() = run {
        val r = Rig(this)
        r.startField(textField())
        r.session.toggle()
        runCurrent()
        assertEquals(1, r.toggles)
        r.session.toggle()
        assertEquals(2, r.toggles)
        assertFalse(r.session.capturingInput)
    }

    @Test fun lowTierStartsInPreviewUntilTheUserPicksAMode() = run {
        val r = Rig(this, lowTier = true)
        r.store.update { it.copy(barMode = BarMode.LIVE, barLiveComposingSet = false) }
        runCurrent()
        r.startField(textField())
        r.session.toggle()
        runCurrent()
        assertEquals(BarMode.PREVIEW, r.open?.mode)
        r.session.close()
        r.store.update { it.copy(barMode = BarMode.LIVE, barLiveComposingSet = true) }
        runCurrent()
        r.session.toggle()
        runCurrent()
        assertEquals(BarMode.LIVE, r.open?.mode)
    }

    @Test fun defaultPairIsTheSubtypeLanguageOrEnglishToSpanish() = run {
        val r = Rig(this)
        r.startField(textField())
        r.session.toggle()
        runCurrent()
        assertEquals(LangPair(Lang.English, Lang.of("es")), r.open?.pair)
    }

    @Test fun reviewFocus6ShowingTheKeyboardNeverTouchesTranslationOrMlKit() = run {
        val r = Rig(this, initial = "hello", fullCore = true)
        repeat(3) {
            r.startField(textField())
            r.report()
            r.hide()
            r.finishField()
        }
        // A password field's toggle is refused before anything is created.
        r.startField(passwordField())
        r.session.toggle()
        runCurrent()
        r.session.destroy()
        assertEquals(0, r.translationResolved)
        assertNull(r.session.controllerOrNull.value)
        assertFalse(mlKitInitialized(), "ML Kit initialised by showing the keyboard")
    }

    private fun mlKitInitialized(): Boolean {
        val f = MlKitBootstrap::class.java.getDeclaredField("initialized")
        f.isAccessible = true
        return f.getBoolean(MlKitBootstrap)
    }

    private companion object {
        /** DebouncePolicy.Default. */
        const val DEBOUNCE_MS = 450L
    }
}
