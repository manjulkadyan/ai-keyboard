/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.policy

import java.util.concurrent.atomic.AtomicInteger

/**
 * Counter that changes whenever the focused field changes or the keyboard must drop in-flight work
 * (TRD §11.2 #2.5). Async work captures [value] when it starts and drops its result unless [isCurrent].
 */
class FieldGeneration {
    private val counter = AtomicInteger(0)

    val value: Int get() = counter.get()

    fun bump() {
        counter.incrementAndGet()
    }

    fun isCurrent(token: Int): Boolean = counter.get() == token
}
