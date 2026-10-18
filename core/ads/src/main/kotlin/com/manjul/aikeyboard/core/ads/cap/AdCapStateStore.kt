/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.cap

/**
 * Persistence for [AdCapState]. The DataStore-backed implementation arrives with `:core:datastore`
 * (TRD §3.2); it must use the multi-process safe factory (TRD §2.3).
 */
interface AdCapStateStore {
    suspend fun read(): AdCapState

    /** Atomically replaces the state with `transform(current)` and returns the new state. */
    suspend fun update(transform: (AdCapState) -> AdCapState): AdCapState
}
