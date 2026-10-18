/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.onboarding

import android.content.Context
import com.manjul.aikeyboard.lib.util.InputMethodUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/** Whether our IME is enabled in system settings and whether it is the selected one. */
interface ImeStatusSource {
    val enabled: Flow<Boolean>
    val selected: Flow<Boolean>
}

private const val POLL_MS = 500L

/**
 * Production [ImeStatusSource] over [InputMethodUtils]. Polls every [POLL_MS] while collected and re-checks
 * at once on [recheck] (called from the activity's onResume, when the user returns from system settings).
 */
class InputMethodImeStatus(context: Context) : ImeStatusSource {
    private val appContext = context.applicationContext
    private val trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun recheck() {
        trigger.tryEmit(Unit)
    }

    override val enabled: Flow<Boolean> = polled { InputMethodUtils.isFlorisboardEnabled(appContext) }
    override val selected: Flow<Boolean> = polled { InputMethodUtils.isFlorisboardSelected(appContext) }

    private fun polled(read: () -> Boolean): Flow<Boolean> = flow {
        while (true) {
            emit(read())
            withTimeoutOrNull(POLL_MS) { trigger.first() }
        }
    }.distinctUntilChanged()
}
