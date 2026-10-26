/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.suggest

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FieldAutocorrectRulesTest {
    private fun info(type: Int, options: Int = 0) = EditorInfo().apply {
        inputType = type
        imeOptions = options
    }

    private fun text(variationOrFlag: Int) = info(InputType.TYPE_CLASS_TEXT or variationOrFlag)

    private val open = PolicyDecision.Unrestricted

    @Test fun `plain text allows autocorrect`() {
        assertTrue(FieldAutocorrectRules.allows(info(InputType.TYPE_CLASS_TEXT), open))
    }

    @Test fun `text with the auto-correct flag and a normal variation allows it`() {
        assertTrue(FieldAutocorrectRules.allows(text(InputType.TYPE_TEXT_FLAG_AUTO_CORRECT), open))
        assertTrue(FieldAutocorrectRules.allows(text(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE), open))
    }

    @Test fun `uri field`() = assertFalse(FieldAutocorrectRules.allows(text(InputType.TYPE_TEXT_VARIATION_URI), open))

    @Test fun `email field`() =
        assertFalse(FieldAutocorrectRules.allows(text(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS), open))

    @Test fun `web email field`() =
        assertFalse(FieldAutocorrectRules.allows(text(InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS), open))

    @Test fun `password field`() =
        assertFalse(FieldAutocorrectRules.allows(text(InputType.TYPE_TEXT_VARIATION_PASSWORD), open))

    @Test fun `visible password field`() =
        assertFalse(FieldAutocorrectRules.allows(text(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD), open))

    @Test fun `web password field`() =
        assertFalse(FieldAutocorrectRules.allows(text(InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD), open))

    @Test fun `number password field`() = assertFalse(
        FieldAutocorrectRules.allows(
            info(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD),
            open,
        ),
    )

    @Test fun `number field`() = assertFalse(FieldAutocorrectRules.allows(info(InputType.TYPE_CLASS_NUMBER), open))

    @Test fun `phone field`() = assertFalse(FieldAutocorrectRules.allows(info(InputType.TYPE_CLASS_PHONE), open))

    @Test fun `datetime field`() =
        assertFalse(FieldAutocorrectRules.allows(info(InputType.TYPE_CLASS_DATETIME), open))

    @Test fun `no suggestions flag`() =
        assertFalse(FieldAutocorrectRules.allows(text(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS), open))

    @Test fun `no personalized learning flag`() = assertFalse(
        FieldAutocorrectRules.allows(
            info(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING),
            open,
        ),
    )

    @Test fun `sensitive decision`() = assertFalse(
        FieldAutocorrectRules.allows(info(InputType.TYPE_CLASS_TEXT), open.copy(sensitive = true)),
    )

    @Test fun `raw input editor`() = assertFalse(FieldAutocorrectRules.allows(info(InputType.TYPE_NULL), open))

    @Test fun `no field`() = assertFalse(FieldAutocorrectRules.allows(null, open))
}
