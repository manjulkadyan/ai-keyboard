/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import android.app.Application
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.ai.AiError
import com.manjul.aikeyboard.core.ai.FakeTokenSource
import com.manjul.aikeyboard.core.ai.kb.AccountApi
import com.manjul.aikeyboard.core.ai.kb.AccountClient
import com.manjul.aikeyboard.core.ai.kb.AccountResult
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeleteDataViewModelTest {
    private val logged = mutableListOf<AnalyticsEvent>()
    private val wipes = mutableListOf<Boolean>()
    private var refreshes = 0
    private val order = mutableListOf<String>()

    private class FakeAccount(var answer: AccountResult) : AccountApi {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        val ids = mutableListOf<String>()

        override suspend fun delete(clientReqId: String): AccountResult {
            calls++
            ids += clientReqId
            gate?.await()
            return answer
        }
    }

    private fun vm(account: AccountApi?, scope: CoroutineScope) = DeleteDataViewModel(
        account = { account },
        wipe = { models ->
            order += "wipe"
            wipes += models
            true
        },
        log = {
            order += "log"
            logged += it
        },
        refreshEntitlement = {
            order += "refresh"
            refreshes++
        },
        scope = scope,
    )

    @Test
    fun `a server 200 logs delete_data once, then wipes and refreshes the entitlement`() = runTest {
        val account = FakeAccount(AccountResult.Deleted)
        val vm = vm(account, this)
        vm.deleteEverything(includeModels = true)
        advanceUntilIdle()

        assertEquals(1, account.calls)
        assertTrue(account.ids.single().length in 1..64)
        assertEquals(listOf(EventCatalog.deleteData()), logged)
        assertEquals(listOf(true), wipes)
        assertEquals(1, refreshes)
        assertEquals(listOf("log", "wipe", "refresh"), order)
        assertEquals(DeleteDataState.Done(serverDeleted = true), vm.state.value)
    }

    @Test
    fun `offline, a 401 or a missing backend wipes nothing and shows the error`() = runTest {
        for (account in listOf(
            FakeAccount(AccountResult.Err(AiError.Offline)),
            FakeAccount(AccountResult.Err(AiError.Unavailable)),
            null,
        )) {
            val vm = vm(account, this)
            vm.deleteEverything(includeModels = false)
            advanceUntilIdle()
            assertEquals(DeleteDataState.Failed, vm.state.value)
        }
        assertEquals(emptyList(), wipes)
        assertEquals(emptyList(), logged)
        assertEquals(0, refreshes)
        val app = RuntimeEnvironment.getApplication()
        assertEquals(
            "Could not delete. Check your connection and try again.",
            app.getString(R.string.delete_data__error),
        )
    }

    @Test
    fun `a double tap sends one request`() = runTest {
        val account = FakeAccount(AccountResult.Deleted).apply { gate = CompletableDeferred() }
        val vm = vm(account, this)
        vm.deleteEverything(includeModels = false)
        vm.deleteEverything(includeModels = false)
        advanceUntilIdle()
        assertEquals(DeleteDataState.Working, vm.state.value)
        vm.deleteEverything(includeModels = false)
        vm.clearOnDevice(includeModels = false)
        account.gate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, account.calls)
        assertEquals(listOf(false), wipes)
        assertEquals(1, logged.size)
    }

    @Test
    fun `a retry after a failure sends again`() = runTest {
        val account = FakeAccount(AccountResult.Err(AiError.Offline))
        val vm = vm(account, this)
        vm.deleteEverything(includeModels = false)
        advanceUntilIdle()
        account.answer = AccountResult.Deleted
        vm.deleteEverything(includeModels = false)
        advanceUntilIdle()
        assertEquals(2, account.calls)
        assertEquals(listOf(false), wipes)
    }

    @Test
    fun `clear on device wipes without a request or delete_data`() = runTest {
        val account = FakeAccount(AccountResult.Deleted)
        val vm = vm(account, this)
        vm.clearOnDevice(includeModels = true)
        advanceUntilIdle()
        assertEquals(0, account.calls)
        assertEquals(listOf(true), wipes)
        assertEquals(emptyList(), logged)
        assertEquals(1, refreshes)
        assertEquals(DeleteDataState.Done(serverDeleted = false), vm.state.value)
    }

    @Test
    fun `logs during deleteEverything hold no bearer or id token`() {
        ShadowLog.clear()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // A real client against a closed port: the token is read and sent, the call fails offline.
            val client = AccountClient(OkHttpClient(), "http://127.0.0.1:1/", FakeTokenSource())
            val vm = vm(client, scope)
            vm.deleteEverything(includeModels = false)
            runBlocking {
                withTimeout(10_000) { vm.state.first { it == DeleteDataState.Failed } }
            }
            val text = ShadowLog.getLogs().joinToString("\n") { "${it.tag} ${it.msg} ${it.throwable}" } +
                vm.state.value.toString() + logged.toString()
            assertFalse("Bearer" in text)
            assertFalse("test-id-token" in text)
            assertFalse("test-app-check" in text)
            assertEquals(emptyList(), wipes)
        } finally {
            scope.cancel()
        }
    }
}
