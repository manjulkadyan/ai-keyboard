/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.gma

import android.app.Activity
import android.content.Context
import com.manjul.aikeyboard.core.ads.AdsInitializer
import com.manjul.aikeyboard.core.ads.AdsStatus
import com.manjul.aikeyboard.core.ads.consent.ConsentError
import com.manjul.aikeyboard.core.ads.consent.ConsentManager
import com.manjul.aikeyboard.core.ads.consent.ConsentResult
import com.manjul.aikeyboard.core.common.NonFatalReporter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** The thread name without the " @coroutine#N" suffix that the coroutines debug mode adds. */
private fun threadName(): String = Thread.currentThread().name.substringBefore(" @")

@OptIn(ExperimentalCoroutinesApi::class)
class GmaAdSdkTest {
    private val activity = Activity()
    private lateinit var ioExecutor: ExecutorService
    private lateinit var mainExecutor: ExecutorService
    private lateinit var io: CoroutineDispatcher

    private class RecordingReporter : NonFatalReporter {
        val areas = CopyOnWriteArrayList<String>()
        override fun report(area: String, error: Throwable) {
            areas += area
        }
    }

    private class FakeFacade(private val fail: Boolean = false) : MobileAdsFacade {
        val threads = CopyOnWriteArrayList<String>()
        override fun initialize(context: Context, onDone: () -> Unit) {
            threads += threadName()
            if (fail) error("gma boom")
            onDone()
        }
    }

    /** Consent that allows ad requests at once, as on a returning user outside the EEA. */
    private val allowAds = object : ConsentManager {
        override fun gatherConsent(activity: Activity, onResult: (ConsentResult) -> Unit) =
            onResult(ConsentResult(canRequestAds = true, privacyOptionsRequired = false))
        override fun canRequestAds(activity: Activity) = true
        override fun isPrivacyOptionsRequired(activity: Activity) = false
        override fun showPrivacyOptionsForm(activity: Activity, onDismissed: (ConsentError?) -> Unit) = Unit
    }

    @BeforeTest
    fun setUp() {
        ioExecutor = Executors.newSingleThreadExecutor { Thread(it, "test-io") }
        mainExecutor = Executors.newSingleThreadExecutor { Thread(it, "test-main") }
        io = ioExecutor.asCoroutineDispatcher()
        Dispatchers.setMain(mainExecutor.asCoroutineDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        ioExecutor.shutdownNow()
        mainExecutor.shutdownNow()
    }

    @Test
    fun `the facade runs once on io and onComplete runs once on main`() {
        val facade = FakeFacade()
        val sdk = GmaAdSdk(facade, io, RecordingReporter())
        val done = CountDownLatch(1)
        val completions = CopyOnWriteArrayList<String>()
        sdk.initialize(activity, onComplete = {
            completions += threadName()
            done.countDown()
        }, onFailure = { error("not expected") })
        assertTrue(done.await(5, TimeUnit.SECONDS))
        drain()
        assertEquals(listOf("test-io"), facade.threads)
        assertEquals(listOf("test-main"), completions)
    }

    @Test
    fun `a facade that calls back twice completes once`() {
        val twice = object : MobileAdsFacade {
            override fun initialize(context: Context, onDone: () -> Unit) {
                onDone()
                onDone()
            }
        }
        val completions = CopyOnWriteArrayList<String>()
        GmaAdSdk(twice, io, RecordingReporter()).initialize(activity, { completions += "done" }, { error("x") })
        drain()
        assertEquals(listOf("done"), completions)
    }

    @Test
    fun `a throwing facade reports one non-fatal and gives SDK_FAILED`() {
        val reporter = RecordingReporter()
        val facade = FakeFacade(fail = true)
        val initializer = AdsInitializer(allowAds, GmaAdSdk(facade, io, reporter))
        // AdsInitializer is main-thread only, as on the device.
        mainExecutor.submit { initializer.initialize(activity) }.get(5, TimeUnit.SECONDS)
        drain()
        assertEquals(1, facade.threads.size)
        assertEquals(1, reporter.areas.size)
        assertEquals(AdsStatus.Phase.SDK_FAILED, initializer.status.value.phase)
    }

    @Test
    fun `a throwing onComplete is reported and does not escape`() {
        val reporter = RecordingReporter()
        GmaAdSdk(FakeFacade(), io, reporter).initialize(activity, { error("callback boom") }, { error("x") })
        drain()
        assertEquals(1, reporter.areas.size)
    }

    /** Waits until both single-thread executors ran everything queued so far (io first, as it posts to main). */
    private fun drain() {
        repeat(2) {
            ioExecutor.submit {}.get(5, TimeUnit.SECONDS)
            mainExecutor.submit {}.get(5, TimeUnit.SECONDS)
        }
    }
}
