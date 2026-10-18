/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.common

import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** The coroutine dispatchers our code may use. Inject them; never reference [Dispatchers] directly. */
enum class AiKeyboardDispatcher {
    Main,
    Default,
    IO,
}

/** DI qualifier for an injected [CoroutineDispatcher], e.g. `@Dispatcher(AiKeyboardDispatcher.IO)`. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: AiKeyboardDispatcher)

/** Supplies dispatchers so tests can swap in a test dispatcher. */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher

    operator fun get(dispatcher: AiKeyboardDispatcher): CoroutineDispatcher = when (dispatcher) {
        AiKeyboardDispatcher.Main -> main
        AiKeyboardDispatcher.Default -> default
        AiKeyboardDispatcher.IO -> io
    }
}

/** Production [DispatcherProvider] backed by [Dispatchers]. */
object DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val io: CoroutineDispatcher get() = Dispatchers.IO
}
