/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translate

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.ime.text.key.KeyCode
import com.manjul.aikeyboard.ime.translateui.BarUiState
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Keys, suggestions, glide words and paste edit only the bar buffer while the bar is open (TRD §4.1.3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BarKeyRoutingTest {
    private val app = RuntimeEnvironment.getApplication()

    private class Rig(scope: TestScope, app: android.app.Application) {
        val edit = EditText(app).apply {
            setText("host text")
            setSelection(4)
        }
        val ic = edit.onCreateInputConnection(EditorInfo())
        val store = FeatureSettingsStore(app, StandardTestDispatcher(scope.testScheduler))
        val policy = ImePolicyController(store, keyguard = { false }, forcedPackages = { emptySet() }, scope = scope.backgroundScope)
        val session = TranslateImeSession(
            context = app,
            policy = policy,
            store = store,
            translation = { error("translation core resolved") },
            models = { TranslateImeSessionTest.FakeModels() },
            analytics = {},
            connection = { ic },
            isRawInputEditor = { false },
            setOwnsComposing = {},
            scope = scope.backgroundScope,
            lowTier = { false },
            engine = { req, _, _ -> TranslateResult.Ok("t:${req.text}", Lang.English, EngineId.MLKIT) },
            cache = { null },
            openOfflineLanguages = {},
        )
        val input get() = (session.ui as? BarUiState.Open)?.input
        val host get() = edit.text.toString()
    }

    private fun run(block: suspend TestScope.(Rig) -> Unit) = runTest(StandardTestDispatcher()) {
        val r = Rig(this, app)
        r.policy.onStartInput(EditorInfo().apply { packageName = "some.app"; inputType = InputType.TYPE_CLASS_TEXT }, false)
        r.session.toggle()
        runCurrent()
        block(r)
    }

    private fun key(event: Int, code: Int, meta: Int = 0) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta)

    @Test fun lettersSpaceAndBackspaceEditTheBuffer() = run { r ->
        for (ch in "hi") assertTrue(BarKeyRouter.onKey(r.session, ch.code, ch.toString()))
        assertTrue(BarKeyRouter.onKey(r.session, KeyCode.SPACE, null))
        for (ch in "yo") BarKeyRouter.onKey(r.session, ch.code, ch.toString())
        assertTrue(BarKeyRouter.onKey(r.session, KeyCode.DELETE, null))
        runCurrent()
        assertEquals("hi y", r.input)
        assertTrue(BarKeyRouter.onKey(r.session, KeyCode.DELETE_WORD, null))
        runCurrent()
        assertEquals("hi ", r.input)
        assertEquals("host text", r.host)
    }

    @Test fun suggestionAndGlideWordsAreAppendedAsWords() = run { r ->
        BarKeyRouter.onKey(r.session, 'a'.code, "a")
        assertTrue(BarKeyRouter.onWord(r.session, "suggested"))
        assertTrue(BarKeyRouter.onWord(r.session, "glided"))
        runCurrent()
        assertEquals("a suggested glided", r.input)
        assertEquals("host text", r.host)
    }

    @Test fun pasteGoesToTheBufferAndMediaIsDropped() = run { r ->
        assertTrue(BarKeyRouter.onPaste(r.session, "pasted"))
        assertTrue(BarKeyRouter.onPaste(r.session, null))
        runCurrent()
        assertEquals("pasted", r.input)
        assertEquals("host text", r.host)
    }

    @Test fun hostEditingKeysAreSwallowedAndOthersPassThrough() = run { r ->
        for (code in listOf(KeyCode.FORWARD_DELETE, KeyCode.CLIPBOARD_CUT, KeyCode.CLIPBOARD_SELECT_ALL, KeyCode.UNDO)) {
            assertTrue(BarKeyRouter.onKey(r.session, code, null), "$code")
        }
        // Shift and the Translate chip still reach KeyboardManager.
        assertFalse(BarKeyRouter.onKey(r.session, KeyCode.SHIFT, null))
        assertFalse(BarKeyRouter.onKey(r.session, KeyCode.TOGGLE_TRANSLATE_BAR, null))
        assertEquals("host text", r.host)
    }

    @Test fun hardwareKeysEditTheBuffer() = run { r ->
        assertTrue(BarKeyRouter.onHardwareKey(r.session, KeyEvent.KEYCODE_H, key(0, KeyEvent.KEYCODE_H)))
        assertTrue(BarKeyRouter.onHardwareKey(r.session, KeyEvent.KEYCODE_SPACE, key(0, KeyEvent.KEYCODE_SPACE)))
        assertTrue(BarKeyRouter.onHardwareKey(r.session, KeyEvent.KEYCODE_I, key(0, KeyEvent.KEYCODE_I)))
        assertTrue(BarKeyRouter.onHardwareKey(r.session, KeyEvent.KEYCODE_DEL, key(0, KeyEvent.KEYCODE_DEL)))
        assertTrue(BarKeyRouter.onHardwareKey(r.session, KeyEvent.KEYCODE_FORWARD_DEL, key(0, KeyEvent.KEYCODE_FORWARD_DEL)))
        // Ctrl shortcuts are not text.
        assertFalse(
            BarKeyRouter.onHardwareKey(r.session, KeyEvent.KEYCODE_A, key(0, KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)),
        )
        runCurrent()
        assertEquals("h ", r.input)
        assertEquals("host text", r.host)
    }

    @Test fun afterCloseNothingIsRouted() = run { r ->
        r.session.close()
        runCurrent()
        assertFalse(BarKeyRouter.onKey(r.session, 'a'.code, "a"))
        assertFalse(BarKeyRouter.onKey(r.session, KeyCode.DELETE, null))
        assertFalse(BarKeyRouter.onWord(r.session, "word"))
        assertFalse(BarKeyRouter.onPaste(r.session, "x"))
        assertFalse(BarKeyRouter.onHardwareKey(r.session, KeyEvent.KEYCODE_A, key(0, KeyEvent.KEYCODE_A)))
        assertFalse(BarKeyRouter.onKey(null, 'a'.code, "a"))
    }
}
