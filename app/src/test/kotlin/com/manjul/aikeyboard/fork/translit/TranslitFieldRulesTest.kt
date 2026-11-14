/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.fork.suggest.SuggestContext
import com.manjul.aikeyboard.ime.policy.FieldDescriptor
import com.manjul.aikeyboard.ime.policy.PolicyContext
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitiveFieldPolicy
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TranslitFieldRulesTest {
    private val open = PolicyDecision.Unrestricted
    private val text = InputType.TYPE_CLASS_TEXT

    private fun allows(type: Int, options: Int = 0, decision: PolicyDecision = open) =
        TranslitFieldRules.allowsSpaceCommit(type, options, decision)

    @Test fun `plain text allows`() = assertTrue(allows(text))

    @Test fun `the no personalized learning flag blocks through the real policy`() {
        val options = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        val decision = SensitiveFieldPolicy.evaluate(
            FieldDescriptor(null, text, options, null, null),
            PolicyContext(false, false, emptySet(), emptySet()),
        )
        // The rule ignores imeOptions itself; the policy marks the field sensitive and the decision blocks it.
        assertFalse(allows(text, options, decision))
        val context = SuggestContext()
        context.update(EditorInfo().apply { inputType = text; imeOptions = options }, decision)
        assertFalse(context.fieldAllowsTranslitCommit)
        assertFalse(context.allowSuggestions)
    }

    @Test fun `password variations block`() {
        assertFalse(allows(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(allows(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertFalse(allows(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
    }

    @Test fun `uri and email block`() {
        assertFalse(allows(text or InputType.TYPE_TEXT_VARIATION_URI))
        assertFalse(allows(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(allows(text or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
    }

    @Test fun `number phone and TYPE_NULL block`() {
        assertFalse(allows(InputType.TYPE_CLASS_NUMBER))
        assertFalse(allows(InputType.TYPE_CLASS_PHONE))
        assertFalse(allows(InputType.TYPE_NULL))
    }

    @Test fun `no suggestions flag blocks`() = assertFalse(allows(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))

    @Test fun `a sensitive decision blocks`() = assertFalse(allows(text, decision = open.copy(sensitive = true)))

    @Test fun `a decision without suggestions blocks`() =
        assertFalse(allows(text, decision = open.copy(allowSuggestionStrip = false)))
}
