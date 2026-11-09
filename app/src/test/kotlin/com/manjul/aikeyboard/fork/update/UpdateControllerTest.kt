/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.update

import android.app.Activity
import androidx.activity.ComponentActivity
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.common.entry.EntrySource
import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.config.RemoteConfig
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wraps [FakeAppUpdateManager] to count update-info requests (a skipped check is visible) and to answer a declined
 * dialog the way Play does: the `startUpdateFlow` task ends with `RESULT_CANCELED`. The fake always answers
 * `RESULT_OK` there, whatever the user does.
 */
private class CountingManager(val fake: FakeAppUpdateManager) : AppUpdateManager by fake {
    var infoRequests = 0
    var declineNext = false

    override fun getAppUpdateInfo(): Task<AppUpdateInfo> {
        infoRequests++
        return fake.appUpdateInfo
    }

    override fun startUpdateFlow(info: AppUpdateInfo, activity: Activity, options: AppUpdateOptions): Task<Int> {
        val started = fake.startUpdateFlow(info, activity, options)
        if (!declineNext) return started
        fake.userRejectsUpdate()
        return Tasks.forResult(Activity.RESULT_CANCELED)
    }
}

private class FixedConfig(var minSupported: Long) : RemoteConfig {
    override fun bool(key: String) = false
    override fun long(key: String) = if (key == ConfigKeys.MIN_SUPPORTED_VERSION) minSupported else 0L
    override fun double(key: String) = 0.0
    override fun string(key: String) = ""
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UpdateControllerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val store = FeatureSettingsStore(context, UnconfinedTestDispatcher())
    private val fake = FakeAppUpdateManager(context)
    private val manager = CountingManager(fake)
    private val config = FixedConfig(0L)
    private val events = mutableListOf<AnalyticsEvent>()
    private var now = 1_000_000_000L
    private val activityController: ActivityController<ComponentActivity> =
        Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val activity: ComponentActivity = activityController.get()

    private fun controller(versionCode: Long = 10L) =
        UpdateController(manager, config, store, versionCode, { now }, { events.add(it) })

    private suspend fun onboarded() = store.update { it.copy(onboardingDone = true) }

    /** Pumps the main looper (Play Task callbacks run there) until [job] ends. */
    private suspend fun pump(job: Job) {
        var spins = 0
        while (!job.isCompleted && spins++ < 200) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            yield()
        }
    }

    /** Starts a resume and pumps until the update dialog is up (or the resume ended). */
    private suspend fun TestScope.resumeUntilPrompt(c: UpdateController, entry: EntrySource = EntrySource.LAUNCHER): Job {
        val before = manager.infoRequests
        val job = launch(start = CoroutineStart.UNDISPATCHED) { c.onHomeResumed(activity, entry) }
        var spins = 0
        // Wait for a new info request first: a dialog flag from an earlier prompt can still be set.
        while (!job.isCompleted && spins++ < 200 &&
            !(manager.infoRequests > before && (fake.isConfirmationDialogVisible || fake.isImmediateFlowVisible))
        ) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            yield()
        }
        return job
    }

    @Test
    fun updateWithMinSupportedZeroIsFlexible() = runTest {
        onboarded()
        fake.setUpdateAvailable(11)
        val job = resumeUntilPrompt(controller())
        assertTrue(fake.isConfirmationDialogVisible)
        assertFalse(fake.isImmediateFlowVisible)
        fake.userAcceptsUpdate()
        pump(job)
        assertEquals(AppUpdateType.FLEXIBLE, fake.typeForUpdateInProgress)
    }

    @Test
    fun versionBelowMinSupportedIsImmediate() = runTest {
        onboarded()
        config.minSupported = 11L
        fake.setUpdateAvailable(11)
        val job = resumeUntilPrompt(controller(versionCode = 10L))
        assertTrue(fake.isImmediateFlowVisible)
        fake.userAcceptsUpdate()
        pump(job)
        assertEquals(AppUpdateType.IMMEDIATE, fake.typeForUpdateInProgress)
    }

    @Test
    fun versionAtMinSupportedStaysFlexible() = runTest {
        onboarded()
        config.minSupported = 10L
        fake.setUpdateAvailable(11)
        val job = resumeUntilPrompt(controller(versionCode = 10L))
        assertTrue(fake.isConfirmationDialogVisible)
        pump(job)
    }

    @Test
    fun secondResumeWithin24HoursDoesNotCheck() = runTest {
        onboarded()
        fake.setUpdateAvailable(11)
        val c = controller()
        var job = resumeUntilPrompt(c)
        fake.userRejectsUpdate()
        pump(job)
        assertEquals(1, manager.infoRequests)
        now += 23 * 3_600_000L
        job = launch(start = CoroutineStart.UNDISPATCHED) { c.onHomeResumed(activity, EntrySource.RESUME) }
        pump(job)
        assertEquals(1, manager.infoRequests)
        now += 2 * 3_600_000L
        job = launch(start = CoroutineStart.UNDISPATCHED) { c.onHomeResumed(activity, EntrySource.RESUME) }
        pump(job)
        assertEquals(2, manager.infoRequests)
    }

    @Test
    fun declinedImmediateChecksAgainOnNextResumeAndNeverFinishes() = runTest {
        onboarded()
        config.minSupported = 11L
        fake.setUpdateAvailable(11)
        manager.declineNext = true
        val c = controller(versionCode = 10L)
        var job = resumeUntilPrompt(c)
        pump(job)
        assertFalse(activity.isFinishing)
        assertEquals(1, manager.infoRequests)
        // Straight away, with no 24 h wait: the next resume asks again.
        job = resumeUntilPrompt(c, EntrySource.RESUME)
        pump(job)
        assertEquals(2, manager.infoRequests)
        assertFalse(activity.isFinishing)
        assertEquals(
            listOf("immediate" to "declined", "immediate" to "declined"),
            events.map { it.params["type"] to it.params["result"] },
        )
    }

    @Test
    fun flexibleDownloadSetsReadyToRestartAndRestartCompletes() = runTest {
        onboarded()
        fake.setUpdateAvailable(11)
        val c = controller()
        val job = resumeUntilPrompt(c)
        fake.userAcceptsUpdate()
        pump(job)
        assertFalse(c.readyToRestart.value)
        fake.downloadStarts()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(c.readyToRestart.value)
        fake.downloadCompletes()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(c.readyToRestart.value)
        c.restart()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(fake.isInstallSplashScreenVisible)
    }

    @Test
    fun onboardingResumeDoesNotCheck() = runTest {
        fake.setUpdateAvailable(11)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { controller().onHomeResumed(activity, EntrySource.LAUNCHER) }
        pump(job)
        assertEquals(0, manager.infoRequests)
        assertEquals(0L, store.flow.first().updateCheckedAt)
    }

    @Test
    fun processTextAndSendEntriesDoNotCheck() = runTest {
        onboarded()
        fake.setUpdateAvailable(11)
        for (entry in listOf(EntrySource.PROCESS_TEXT, EntrySource.SEND)) {
            val job = launch(start = CoroutineStart.UNDISPATCHED) { controller().onHomeResumed(activity, entry) }
            pump(job)
        }
        assertEquals(0, manager.infoRequests)
        assertTrue(events.isEmpty())
    }

    @Test
    fun eachPromptLogsUpdatePromptOnce() = runTest {
        onboarded()
        fake.setUpdateAvailable(11)
        val job = resumeUntilPrompt(controller())
        fake.userAcceptsUpdate()
        pump(job)
        assertEquals(1, events.size)
        assertEquals("update_prompt", events[0].name)
        assertEquals(mapOf("type" to "flexible", "result" to "accepted"), events[0].params)
    }

    @Test
    fun noUpdateLogsNothingButStampsTheCheck() = runTest {
        onboarded()
        fake.setUpdateNotAvailable()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { controller().onHomeResumed(activity, EntrySource.LAUNCHER) }
        pump(job)
        assertTrue(events.isEmpty())
        assertEquals(now, store.flow.first().updateCheckedAt)
    }
}
