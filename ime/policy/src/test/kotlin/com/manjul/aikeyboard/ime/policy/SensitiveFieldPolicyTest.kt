/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SensitiveFieldPolicyTest {
    @Test
    fun `policy contract can be implemented without Android`() {
        val field = FieldDescriptor(packageName = "com.example", inputType = 0x81, imeOptions = 0)
        val alwaysSensitive = object : SensitiveFieldPolicy {
            override fun isSensitive(field: FieldDescriptor) = true
        }
        assertTrue(alwaysSensitive.isSensitive(field))
        assertEquals(field, field.copy())
    }
}
