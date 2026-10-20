/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.ime.ImeUiMode
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.text.key.KeyCode
import com.manjul.aikeyboard.ime.text.keyboard.TextKeyData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Important 1 of the final review: no route opens clipboard history where the policy denies it, and an open panel
 * closes when the field becomes more restrictive. FlorisImeService and KeyboardManager cannot run under Robolectric,
 * so this drives the gate the way they do: the key handler asks [ClipboardPanelGate.onClipboardKey], the service
 * installs [ClipboardPanelGate.closeOnRestriction] on the real policy controller and applies
 * [ClipboardPanelGate.onStartInputView] in onStartInputView.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ClipboardPanelGateTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = FeatureSettingsStore(RuntimeEnvironment.getApplication(), dispatcher)
    private val policy = ImePolicyController(store, keyguard = { false }, forcedPackages = { emptySet() }, scope = scope)
    private var mode = ImeUiMode.TEXT

    init {
        ClipboardPanelGate.closeOnRestriction(policy, { mode }) { mode = it }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun text() = EditorInfo().apply {
        packageName = "some.app"
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun password() = EditorInfo().apply {
        packageName = "some.app"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    /** What KeyboardManager does for a key event with [code]. */
    private fun press(code: Int) {
        assertEquals(KeyCode.IME_UI_MODE_CLIPBOARD, code, "route does not reach the clipboard key handler")
        mode = ClipboardPanelGate.onClipboardKey(mode, policy.decision.value)
    }

    /** What FlorisImeService.onStartInputView does with upstream's "hide on next text field" pref. */
    private fun startInputView(hideOnNextTextField: Boolean) {
        mode = ClipboardPanelGate.onStartInputView(mode, hideOnNextTextField, policy.decision.value)
    }

    /** The code of the Enter key's long-press popup entry that opens clipboard history. */
    private fun enterPopupClipboardCode(): Int {
        val file = listOf(File("src/main"), File("app/src/main"))
            .map { File(it, "assets/ime/keyboard/org.florisboard.localization/popupMappings/default.json") }
            .firstOrNull { it.isFile } ?: fail("popup mapping not found")
        val enter = Json.parseToJsonElement(file.readText()).jsonObject.getValue("all").jsonObject.getValue("~enter")
        return enter.jsonObject.getValue("relevant").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("label").jsonPrimitive.content == "ime_ui_mode_clipboard" }
            .getValue("code").jsonPrimitive.int
    }

    @Test fun `enter popup and swipe leave a password field in text mode`() {
        policy.onStartInput(password(), false)
        startInputView(hideOnNextTextField = true)

        press(enterPopupClipboardCode()) // -213
        assertEquals(ImeUiMode.TEXT, mode)
        press(TextKeyData.IME_UI_MODE_CLIPBOARD.code) // SwipeAction.SWITCH_TO_CLIPBOARD_CONTEXT
        assertEquals(ImeUiMode.TEXT, mode)
    }

    @Test fun `normal field still opens the panel`() {
        policy.onStartInput(text(), false)
        startInputView(hideOnNextTextField = true)
        press(enterPopupClipboardCode())
        assertEquals(ImeUiMode.CLIPBOARD, mode)
    }

    @Test fun `open panel closes on a restrictive restart`() {
        policy.onStartInput(text(), false)
        startInputView(hideOnNextTextField = false)
        press(TextKeyData.IME_UI_MODE_CLIPBOARD.code)
        assertEquals(ImeUiMode.CLIPBOARD, mode)

        policy.onStartInput(password(), restarting = true)
        assertEquals(ImeUiMode.TEXT, mode)
        startInputView(hideOnNextTextField = false)
        assertEquals(ImeUiMode.TEXT, mode)
    }

    @Test fun `start input view closes a panel the decision denies even without a restriction callback`() {
        policy.onStartInput(password(), false)
        mode = ImeUiMode.CLIPBOARD // e.g. a stale state; restriction listeners only fire on changes
        startInputView(hideOnNextTextField = false)
        assertEquals(ImeUiMode.TEXT, mode)
    }

    @Test fun `open panel closes when the app is made private after settings load`() {
        policy.onStartInput(text(), false)
        press(TextKeyData.IME_UI_MODE_CLIPBOARD.code)
        assertEquals(ImeUiMode.CLIPBOARD, mode)

        runBlocking { store.update { it.copy(privatePackages = setOf("some.app")) } }
        assertTrue(!policy.decision.value.allowClipboardHistory)
        assertEquals(ImeUiMode.TEXT, mode)
    }

    @Test fun `upstream keep-open pref still holds across a normal field change`() {
        policy.onStartInput(text(), false)
        press(TextKeyData.IME_UI_MODE_CLIPBOARD.code)
        // onFinishInput reports "no field" between the two fields; that alone must not close the panel.
        policy.onFinishInput()
        policy.onStartInput(text(), false)
        startInputView(hideOnNextTextField = false)
        assertEquals(ImeUiMode.CLIPBOARD, mode)
        startInputView(hideOnNextTextField = true)
        assertEquals(ImeUiMode.TEXT, mode)
    }

    @Test fun `media mode is left alone by restrictions`() {
        assertEquals(ImeUiMode.MEDIA, ClipboardPanelGate.afterRestriction(ImeUiMode.MEDIA, PolicyDecision.Locked))
        assertEquals(ImeUiMode.TEXT, ClipboardPanelGate.afterRestriction(ImeUiMode.CLIPBOARD, PolicyDecision.Locked))
    }
}
