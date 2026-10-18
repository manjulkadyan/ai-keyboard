/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.onboarding

import android.content.Context
import com.manjul.aikeyboard.lib.util.InputMethodUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withTimeoutOrNull

/** Whether our IME is enabled in system settings and whether it is the selected one. */
interface ImeStatusSource {
    val enabled: Flow<Boolean>
    val selected: Flow<Boolean>
}

private const val POLL_MS = 500L

/**
 * Production [ImeStatusSource] over [InputMethodUtils]. One poll every [POLL_MS] feeds both flows (shared in
 * [scope] while collected); [recheck] re-reads at once (called from onResume, when the user returns from
 * system settings).
 */
class InputMethodImeStatus(context: Context, scope: CoroutineScope) : ImeStatusSource {
    private val appContext = context.applicationContext
    private val trigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun recheck() {
        trigger.tryEmit(Unit)
    }

    private val status: Flow<Pair<Boolean, Boolean>> = flow {
        while (true) {
            emit(InputMethodUtils.isFlorisboardEnabled(appContext) to InputMethodUtils.isFlorisboardSelected(appContext))
            withTimeoutOrNull(POLL_MS) { trigger.first() }
        }
    }.distinctUntilChanged().shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    override val enabled: Flow<Boolean> = status.map { it.first }.distinctUntilChanged()
    override val selected: Flow<Boolean> = status.map { it.second }.distinctUntilChanged()
}
