/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertSame

class DispatcherProviderTest {
    @Test
    fun `default provider maps to kotlinx dispatchers`() {
        assertSame(Dispatchers.Default, DefaultDispatcherProvider[AiKeyboardDispatcher.Default])
        assertSame(Dispatchers.IO, DefaultDispatcherProvider[AiKeyboardDispatcher.IO])
    }

    @Test
    fun `test provider can replace every dispatcher`() {
        val test = StandardTestDispatcher()
        val provider = object : DispatcherProvider {
            override val main: CoroutineDispatcher = test
            override val default: CoroutineDispatcher = test
            override val io: CoroutineDispatcher = test
        }
        AiKeyboardDispatcher.entries.forEach { assertSame(test, provider[it]) }
    }
}
