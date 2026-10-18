/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.policy

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Full input matrix for TRD 4.1.2. The expected values come from [expectedFor], an independent
 * table written from the spec using literal android.text.InputType / EditorInfo values.
 */
class SensitiveFieldPolicyMatrixTest {
    enum class Cls(val bits: Int) { TEXT(0x1), NUMBER(0x2), PHONE(0x3), DATETIME(0x4), NULL(0x0) }
    enum class Ctx { PLAIN, LOCKED, INCOGNITO, PRIVATE_APP, FORCED_PACKAGE }

    data class Case(
        val cls: Cls,
        val variation: Int,
        val noSuggestions: Boolean,
        val noLearningIme: Boolean,
        val ctx: Ctx,
    ) {
        val inputType: Int get() = cls.bits or variation or (if (noSuggestions) 0x80000 else 0)
        val imeOptions: Int get() = if (noLearningIme) 0x1000000 else 0
        override fun toString() = "$cls/var=0x${variation.toString(16)}/noSug=$noSuggestions/noLearn=$noLearningIme/$ctx"
    }

    private fun Case.field() = FieldDescriptor("com.example.app", inputType, imeOptions, null, null)

    private fun Case.context() = PolicyContext(
        deviceLocked = ctx == Ctx.LOCKED,
        incognitoManual = ctx == Ctx.INCOGNITO,
        privatePackages = if (ctx == Ctx.PRIVATE_APP) setOf("com.example.app") else setOf("com.other"),
        forceSensitivePackages = if (ctx == Ctx.FORCED_PACKAGE) setOf("com.example.app") else emptySet(),
    )

    private fun flags(
        learning: Boolean = true,
        history: Boolean = true,
        strip: Boolean = true,
        translate: Boolean = true,
        ai: Boolean = true,
    ) = PolicyDecision(
        sensitive = false,
        reasons = emptySet(),
        allowLearning = learning,
        allowSuggestionHistory = history,
        allowSuggestionStrip = strip,
        allowClipboardHistory = true,
        allowTranslate = translate,
        allowVoice = true,
        allowAi = ai,
        allowCloud = true,
        analyticsMinimal = false,
    )

    private fun expectedFor(c: Case): PolicyDecision {
        val reasons = buildSet {
            if (c.cls == Cls.TEXT && c.variation in setOf(0x80, 0x90, 0xe0)) add(SensitivityReason.PASSWORD)
            if (c.cls == Cls.NUMBER && c.variation == 0x10) add(SensitivityReason.NUMBER_PASSWORD)
            if (c.noLearningIme) add(SensitivityReason.NO_PERSONALIZED_LEARNING)
            when (c.ctx) {
                Ctx.LOCKED -> add(SensitivityReason.DEVICE_LOCKED)
                Ctx.INCOGNITO -> add(SensitivityReason.INCOGNITO_MANUAL)
                Ctx.PRIVATE_APP -> add(SensitivityReason.PRIVATE_APP)
                Ctx.FORCED_PACKAGE -> add(SensitivityReason.FORCED_PACKAGE)
                Ctx.PLAIN -> Unit
            }
        }
        if (reasons.isNotEmpty()) {
            return PolicyDecision(
                sensitive = true, reasons = reasons,
                allowLearning = false, allowSuggestionHistory = false, allowSuggestionStrip = false,
                allowClipboardHistory = false, allowTranslate = false, allowVoice = false,
                allowAi = false, allowCloud = false, analyticsMinimal = true,
            )
        }
        val strip = !(c.cls == Cls.TEXT && c.noSuggestions)
        return when {
            c.cls == Cls.PHONE || c.cls == Cls.DATETIME -> flags(translate = false, ai = false)
            c.cls == Cls.TEXT && c.variation in setOf(0x10) -> flags(strip = strip, translate = false, ai = false)
            c.cls == Cls.TEXT && c.variation in setOf(0x20, 0xd0) ->
                flags(learning = false, history = false, strip = strip, translate = false, ai = false)
            else -> flags(strip = strip)
        }
    }

    @Test
    fun `matrix has at least 400 cases`() {
        assertTrue(cases().size >= 400, "cases=${cases().size}")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `decision matches the TRD table`(c: Case) {
        assertEquals(expectedFor(c), SensitiveFieldPolicy.evaluate(c.field(), c.context()))
    }

    companion object {
        private val variations = mapOf(
            Cls.TEXT to listOf(0x00, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80, 0x90, 0xa0, 0xb0, 0xc0, 0xd0, 0xe0),
            Cls.NUMBER to listOf(0x00, 0x10),
            Cls.PHONE to listOf(0x00),
            Cls.DATETIME to listOf(0x00, 0x10, 0x20),
            Cls.NULL to listOf(0x00),
        )

        @JvmStatic
        fun cases(): List<Case> = buildList {
            for ((cls, vars) in variations) for (v in vars)
                for (noSug in listOf(false, true)) for (noLearn in listOf(false, true)) for (ctx in Ctx.values())
                    add(Case(cls, v, noSug, noLearn, ctx))
        }
    }
}
