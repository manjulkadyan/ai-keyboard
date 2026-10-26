/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.ConsentKind
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AiConsentActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = RuntimeEnvironment.getApplication()
    private val store = FeatureSettingsStore(app)
    private val events = mutableListOf<AnalyticsEvent>()

    @Before
    fun setUp() {
        AiConsentActivity.depsOverride = AiConsentActivity.Deps(store = store, analytics = { events += it }, clock = { 777L })
    }

    @After
    fun tearDown() {
        AiConsentActivity.depsOverride = null
    }

    private fun launch(intent: Intent): ActivityScenario<AiConsentActivity> {
        val scenario = ActivityScenario.launch<AiConsentActivity>(intent)
        idle()
        return scenario
    }

    private fun launch(kind: ConsentKind) = launch(AiConsentActivity.intent(app, kind))

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun ActivityScenario<AiConsentActivity>.isFinishing(): Boolean {
        if (state == Lifecycle.State.DESTROYED) return true
        var finishing = false
        onActivity { finishing = it.isFinishing }
        return finishing
    }

    private fun settings() = runBlocking { store.flow.first() }

    private fun consentEvents() = events.filter { it.name == "consent_result" }.map { it.params["kind"] to it.params["agreed"] }

    @Test
    fun agreeSetsConsentAiAtAndFinishes() {
        val scenario = launch(ConsentKind.AI)
        for (heading in listOf("What is sent", "Who processes it", "What is kept", "How to withdraw")) {
            compose.onNodeWithText(heading).assertExists()
        }
        assertEquals(0L, settings().consentAiAt)

        compose.onNodeWithText("Agree").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            scenario.isFinishing()
        }

        assertEquals(777L, settings().consentAiAt)
        assertEquals(0L, settings().consentAutoPolishAt)
        assertFalse(settings().autoPolish)
        assertEquals(listOf<Pair<Any?, Any?>>("ai" to true), consentEvents())
        // It never opens anything else (and has no way to start an AI request).
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun agreeToAutoPolishTurnsItOn() {
        val scenario = launch(ConsentKind.AUTO_POLISH)
        compose.onNodeWithText("Agree").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            shadowOf(Looper.getMainLooper()).idle()
            scenario.isFinishing()
        }
        assertEquals(777L, settings().consentAutoPolishAt)
        assertTrue(settings().autoPolish)
        assertEquals(0L, settings().consentAiAt)
        assertEquals(listOf<Pair<Any?, Any?>>("auto_polish" to true), consentEvents())
    }

    @Test
    fun notNowLeavesConsentAtZero() {
        val scenario = launch(ConsentKind.AI)
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        idle()
        assertTrue(scenario.isFinishing())
        assertEquals(0L, settings().consentAiAt)
        assertEquals(listOf<Pair<Any?, Any?>>("ai" to false), consentEvents())
    }

    @Test
    fun backLeavesConsentAtZero() {
        val scenario = launch(ConsentKind.AI)
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        idle()
        assertTrue(scenario.isFinishing())
        assertEquals(0L, settings().consentAiAt)
        assertEquals(listOf<Pair<Any?, Any?>>("ai" to false), consentEvents())
    }

    @Test
    fun leavingTheScreenIsNotConsent() {
        val scenario = launch(ConsentKind.AI)
        scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(scenario.isFinishing())
        assertEquals(0L, settings().consentAiAt)
    }

    @Test
    fun aBadKindFinishesWithoutShowingAnything() {
        val bad = listOf(null, "", "ai", "VOICE", "AI;AUTO_POLISH", "x".repeat(5_000))
        for (value in bad) {
            val intent = Intent(app, AiConsentActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (value != null) intent.putExtra(AiConsentActivity.EXTRA_KIND, value)
            val scenario = launch(intent)
            assertTrue(scenario.isFinishing(), value?.take(10))
            compose.onNodeWithText("Agree").assertDoesNotExist()
        }
        assertTrue(events.isEmpty())
        assertEquals(0L, settings().consentAiAt)
        assertEquals(0L, settings().consentAutoPolishAt)
    }
}
