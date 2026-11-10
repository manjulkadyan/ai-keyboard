/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.rc

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.firebase.FirebaseConfig
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * I-1 (S4 final review, decision 17, TRD §4.14 "Consent"): with Firebase options set, analytics before consent goes
 * to the fallback sink only and never starts a FirebaseApp, as on an IME-only cold start. Firebase is not on the
 * `:app` compile classpath, so `FirebaseApp` is read through reflection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class FirebaseGraphConsentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val firebaseApp = Class.forName("com.google.firebase.FirebaseApp")

    private val configured = FirebaseConfig(
        projectId = "demo-project",
        appId = "1:123456789012:android:0123456789abcdef",
        apiKey = "AIzaSyTestOnlyNotARealKey0000000000000",
        projectNumber = "123456789012",
    )

    private class Recorder : AnalyticsSink {
        val events = mutableListOf<String>()
        override fun log(event: AnalyticsEvent) {
            events += event.name
        }
    }

    private fun apps(): List<*> = firebaseApp.getMethod("getApps", Context::class.java).invoke(null, context) as List<*>

    private fun clearApps() {
        firebaseApp.getMethod("clearInstancesForTest").invoke(null)
    }

    @Before
    fun setUp() = clearApps()

    @After
    fun tearDown() = clearApps()

    @Test
    fun `before consent no event reaches Firebase and no FirebaseApp starts`() {
        val fallback = Recorder()
        val graph = FirebaseGraph(context, optOut = { false }, analyticsAllowed = { false }, fallback, configured)
        assertTrue(graph.firebaseConfig.isConfigured)

        graph.analyticsSink.log(AnalyticsEvent("ime_session_start", mapOf("is_sensitive" to false)))
        graph.analyticsSink.setUserProperty("is_pro", "false")
        graph.onAnalyticsAllowedChanged(false)
        graph.onOptOutChanged(false)

        assertEquals(listOf("ime_session_start"), fallback.events)
        assertEquals(emptyList<Any?>(), apps())
        assertFalse(graph.isAnalyticsRunning())
    }

    @Test
    fun `an opt-out after the UMP result started Analytics turns collection off before any event`() {
        // A consented free user on a cold start: UMP sets Consent Mode, which starts Firebase Analytics with the
        // collection flag an earlier process persisted. The relay has sent nothing yet.
        var allowed = true
        val collection = mutableListOf<Boolean>()
        val graph = FirebaseGraph(
            context,
            optOut = { !allowed },
            analyticsAllowed = { allowed },
            fallbackSink = Recorder(),
            firebaseConfig = configured,
            onCollectionSet = { collection += it },
        )
        graph.setConsentMode(adStorage = true, adUserData = true, adPersonalization = true, analyticsStorage = true)
        assertTrue(graph.isAnalyticsRunning())

        allowed = false
        graph.onAnalyticsAllowedChanged(false)

        assertEquals(listOf(false), collection)
    }

    @Test
    fun `after consent the first event starts the default FirebaseApp`() {
        val fallback = Recorder()
        val graph = FirebaseGraph(context, optOut = { false }, analyticsAllowed = { true }, fallback, configured)

        graph.analyticsSink.log(AnalyticsEvent("ime_session_start", mapOf("is_sensitive" to false)))

        assertEquals(emptyList(), fallback.events)
        val names = apps().map { firebaseApp.getMethod("getName").invoke(it) }
        assertTrue("[DEFAULT]" in names, "Expected the default app, got $names")
    }
}
