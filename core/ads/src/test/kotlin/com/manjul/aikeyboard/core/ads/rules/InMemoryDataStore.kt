/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.rules

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A [DataStore] in memory: the multi-process factory needs Android, so JVM tests use this. */
class InMemoryDataStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<T> = state

    val value: T get() = state.value

    override suspend fun updateData(transform: suspend (t: T) -> T): T = mutex.withLock {
        transform(state.value).also { state.value = it }
    }
}
