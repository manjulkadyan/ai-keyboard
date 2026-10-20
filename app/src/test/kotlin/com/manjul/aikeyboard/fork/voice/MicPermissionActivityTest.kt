/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.voice.EngineKind
import com.manjul.aikeyboard.core.voice.platform.EngineChoice
import com.manjul.aikeyboard.fork.onboarding.DisclosureEngineKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MicPermissionActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = RuntimeEnvironment.getApplication()
    private val store = FeatureSettingsStore(app)
    private val events = mutableListOf<AnalyticsEvent>()

    @Before
    fun setUp() {
        MicPermissionActivity.depsOverride = MicPermissionActivity.Deps(
            store = store,
            analytics = { events += it },
            engineKind = { DisclosureEngineKind.ON_DEVICE },
            clock = { 777L },
        )
    }

    @After
    fun tearDown() {
        MicPermissionActivity.depsOverride = null
    }

    private fun launch(): ActivityScenario<MicPermissionActivity> {
        val scenario = ActivityScenario.launch<MicPermissionActivity>(MicPermissionActivity.intent(app, "en-US"))
        idle()
        return scenario
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun ActivityScenario<MicPermissionActivity>.requestedPermissions(): Array<String>? {
        var result: Array<String>? = null
        onActivity { result = shadowOf(it).lastRequestedPermission?.requestedPermissions }
        return result
    }

    /** Activities started besides the system permission dialog (which Robolectric records as a started activity). */
    private fun otherStartedActivities(): List<Intent> {
        val all = generateSequence { shadowOf(app).nextStartedActivity }.toList()
        return all.filter { it.action != ACTION_REQUEST_PERMISSIONS }
    }

    private fun ActivityScenario<MicPermissionActivity>.answer(granted: Boolean) {
        onActivity { activity ->
            val request = checkNotNull(shadowOf(activity).lastRequestedPermission)
            if (granted) shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
            val result = if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
            @Suppress("DEPRECATION")
            activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(result))
        }
        idle()
    }

    private fun ActivityScenario<MicPermissionActivity>.isFinishing(): Boolean {
        if (state == androidx.lifecycle.Lifecycle.State.DESTROYED) return true
        var finishing = false
        onActivity { finishing = it.isFinishing }
        return finishing
    }

    @Test
    fun noticeComesFirstAndTheRequestFollowsAgree() {
        val scenario = launch()
        compose.onNodeWithText("Before you speak").assertExists()
        compose.onNodeWithText("Voice typing uses the on-device speech recognition. Your voice stays on this phone.")
            .assertExists()
        assertNull(scenario.requestedPermissions())

        compose.onNodeWithText("Agree").performClick()
        // The consent write finishes before the request is made.
        compose.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            scenario.requestedPermissions() != null
        }

        assertTrue(scenario.requestedPermissions()!!.contentEquals(arrayOf(Manifest.permission.RECORD_AUDIO)))
        assertEquals(777L, runBlocking { store.flow.first().consentVoiceNoticeAt })
    }

    @Test
    fun noticeAgreedBeforeSkipsTheSheet() {
        runBlocking { store.update { it.copy(consentVoiceNoticeAt = 5L) } }
        val scenario = launch()
        compose.onNodeWithText("Before you speak").assertDoesNotExist()
        assertTrue(scenario.requestedPermissions()!!.contentEquals(arrayOf(Manifest.permission.RECORD_AUDIO)))
    }

    @Test
    fun notNowOnTheNoticeFinishesWithoutAsking() {
        val scenario = launch()
        compose.onNodeWithText("Not now").performClick()
        idle()
        assertNull(scenario.requestedPermissions())
        assertTrue(scenario.isFinishing())
    }

    @Test
    fun grantFinishesLogsAndDoesNotStartDictation() {
        runBlocking { store.update { it.copy(consentVoiceNoticeAt = 5L) } }
        val scenario = launch()
        scenario.answer(granted = true)
        assertTrue(scenario.isFinishing())
        assertEquals(listOf(true), events.filter { it.name == "voice_permission_result" }.map { it.params["granted"] })
        // Nothing else is opened, and the activity has no way to reach the keyboard's voice controller.
        assertTrue(otherStartedActivities().isEmpty())
        assertEquals(1, events.size)
    }

    @Test
    fun denialShowsARationaleWithAnAppSettingsLink() {
        runBlocking { store.update { it.copy(consentVoiceNoticeAt = 5L) } }
        val scenario = launch()
        scenario.answer(granted = false)
        assertEquals(listOf(false), events.filter { it.name == "voice_permission_result" }.map { it.params["granted"] })
        compose.onNodeWithText("Microphone is off").assertExists()

        compose.onNodeWithText("Open app settings").performClick()
        idle()

        val started = otherStartedActivities().single()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, started.action)
        assertEquals("package:${app.packageName}", started.dataString)
        assertTrue(scenario.isFinishing())
    }

    @Test
    fun rationaleSurvivesRecreation() {
        runBlocking { store.update { it.copy(consentVoiceNoticeAt = 5L) } }
        val scenario = launch()
        scenario.answer(granted = false)
        compose.onNodeWithText("Microphone is off").assertExists()

        scenario.recreate()
        idle()

        compose.onNodeWithText("Microphone is off").assertExists()
        assertEquals(1, events.count { it.name == "voice_permission_result" })
    }

    @Test
    fun alreadyGrantedFinishesAtOnce() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val scenario = launch()
        assertTrue(scenario.isFinishing())
        assertTrue(events.isEmpty())
        // Not even the permission dialog.
        assertTrue(generateSequence { shadowOf(app).nextStartedActivity }.none())
    }

    @Test
    fun engineChoiceMapsToTheNotice() {
        assertEquals(DisclosureEngineKind.ON_DEVICE, EngineChoice.Use(EngineKind.SYSTEM_ON_DEVICE).toDisclosureKind())
        assertEquals(DisclosureEngineKind.SYSTEM_SERVICE, EngineChoice.Use(EngineKind.SYSTEM_DEFAULT).toDisclosureKind())
        assertEquals(DisclosureEngineKind.WHISPER, EngineChoice.Use(EngineKind.WHISPER).toDisclosureKind())
        assertEquals(
            DisclosureEngineKind.SYSTEM_SERVICE,
            EngineChoice.Unavailable("no_service", offerSystemDownload = false, offerWhisperDownload = false).toDisclosureKind(),
        )
        // On-device only and Offline (Whisper) never fall back to the server recognizer.
        assertEquals(
            DisclosureEngineKind.ON_DEVICE,
            EngineChoice.Unavailable("offline_no_model", false, offerWhisperDownload = true, localOnly = true)
                .toDisclosureKind(),
        )
    }

    private companion object {
        /** PackageManager.ACTION_REQUEST_PERMISSIONS, which is hidden API. */
        const val ACTION_REQUEST_PERMISSIONS = "android.content.pm.action.REQUEST_PERMISSIONS"
    }
}
