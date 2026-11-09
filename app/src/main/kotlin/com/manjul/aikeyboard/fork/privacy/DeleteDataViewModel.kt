/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import android.content.Context
import androidx.lifecycle.ViewModel
import com.manjul.aikeyboard.aiAppGraph
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.billing
import com.manjul.aikeyboard.core.ai.kb.AccountApi
import com.manjul.aikeyboard.core.ai.kb.AccountClient
import com.manjul.aikeyboard.core.ai.kb.AccountResult
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.billing.RefreshReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DeleteDataState {
    data object Idle : DeleteDataState
    data object Working : DeleteDataState
    data object Failed : DeleteDataState
    data class Done(val serverDeleted: Boolean) : DeleteDataState
}

/**
 * Delete my data (S4 Task 12), driven by [scope]. [DeleteDataScreenModel] keeps it on the screen's lifecycle.
 * [deleteEverything] asks kb-api to delete the account first and wipes the device only after a confirmed delete,
 * so a failure leaves everything as it was and the user can retry. [clearOnDevice] wipes the device without a
 * request. A second tap while one runs is ignored. The request goes out even without AI consent: deletion is a
 * right, not an AI feature. No token, id or user content reaches the state or a log.
 */
class DeleteDataViewModel(
    private val account: () -> AccountApi?,
    private val wipe: suspend (includeModels: Boolean) -> Boolean,
    private val log: (AnalyticsEvent) -> Unit,
    private val refreshEntitlement: suspend () -> Unit,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<DeleteDataState>(DeleteDataState.Idle)
    val state: StateFlow<DeleteDataState> = _state.asStateFlow()

    fun deleteEverything(includeModels: Boolean) {
        if (!begin()) return
        scope.launch {
            val client = account()
            val result = client?.delete(AccountClient.newRequestId())
            if (result != AccountResult.Deleted) {
                _state.value = DeleteDataState.Failed
                return@launch
            }
            // Logged before the wipe: the wipe resets the Analytics app instance id.
            log(EventCatalog.deleteData())
            finish(includeModels, serverDeleted = true)
        }
    }

    fun clearOnDevice(includeModels: Boolean) {
        if (!begin()) return
        scope.launch { finish(includeModels, serverDeleted = false) }
    }

    private fun begin(): Boolean {
        val now = _state.value
        if (now == DeleteDataState.Working) return false
        return _state.compareAndSet(now, DeleteDataState.Working)
    }

    private suspend fun finish(includeModels: Boolean, serverDeleted: Boolean) {
        wipe(includeModels)
        runCatching { refreshEntitlement() }
        _state.value = DeleteDataState.Done(serverDeleted)
    }
}

/**
 * Holds a [DeleteDataViewModel] across configuration changes and builds the production dependencies. The work runs
 * on a scope that outlives the screen: once kb-api confirmed the delete, leaving the screen must not stop the wipe.
 */
class DeleteDataScreenModel(context: Context, clearAdState: suspend () -> Unit) : ViewModel() {
    private val app = context.applicationContext
    private val wiper by lazy { DataWiper.create(app, clearAdState) }

    val controller = DeleteDataViewModel(
        account = { app.aiAppGraph().value.account.value },
        wipe = { wiper.wipeLocal(it) },
        // Flushed at once: the wipe then resets the Analytics app instance id, and a queued event would be lost.
        log = { event -> app.appContext().value.analytics.value.let { it.log(event); it.flushNow() } },
        refreshEntitlement = { app.billing().value.repository.refresh(RefreshReason.COLD_START) },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    )
}
