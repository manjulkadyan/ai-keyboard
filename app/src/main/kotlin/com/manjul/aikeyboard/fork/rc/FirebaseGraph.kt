/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.rc

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.manjul.aikeyboard.BuildConfig
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.common.NonFatalReporter
import com.manjul.aikeyboard.core.common.NoopNonFatalReporter
import com.manjul.aikeyboard.core.config.RemoteConfig
import com.manjul.aikeyboard.core.datastore.MirroredRemoteConfig
import com.manjul.aikeyboard.core.datastore.RemoteConfigMirror
import com.manjul.aikeyboard.core.firebase.CrashlyticsReporter
import com.manjul.aikeyboard.core.firebase.FirebaseAnalyticsSink
import com.manjul.aikeyboard.core.firebase.FirebaseBootstrap
import com.manjul.aikeyboard.core.firebase.FirebaseConfig
import com.manjul.aikeyboard.core.firebase.FirebaseRemoteConfigSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

/**
 * The app-wide Firebase pieces (S4 Task 1, decision 17). One per process (`FlorisApplication.firebaseGraph`).
 * Creating it does not start Firebase: each SDK starts on its first use, and only when Firebase options are set.
 *
 * - [remoteConfig] reads the IME mirror. The IME and the app both read it; only [onAppActivityCreated] writes it.
 * - [analyticsSink] sends to Firebase Analytics only when Firebase is configured and [analyticsAllowed] is true:
 *   the UMP result granted `analytics_storage` and the user did not opt out (`FeatureSettings.analyticsAllowed`).
 *   Before that it never touches FirebaseApp, so an IME-only cold start before consent starts no Firebase SDK.
 * - [reporter] sends non-fatal errors to Crashlytics (class name and stack only).
 */
class FirebaseGraph(
    context: Context,
    private val optOut: () -> Boolean,
    private val analyticsAllowed: () -> Boolean,
    fallbackSink: AnalyticsSink,
    val firebaseConfig: FirebaseConfig = buildConfigFirebase(),
    /** Sees each Analytics collection change after it went to the SDK. For tests; the default does nothing. */
    private val onCollectionSet: (Boolean) -> Unit = {},
) {
    private val appContext = context.applicationContext

    val bootstrap = FirebaseBootstrap(appContext, firebaseConfig)
    val mirror = RemoteConfigMirror(appContext)
    val remoteConfig: RemoteConfig = MirroredRemoteConfig(mirror)

    private val crashlytics: CrashlyticsReporter? =
        if (firebaseConfig.isConfigured) CrashlyticsReporter(bootstrap) else null
    val reporter: NonFatalReporter = crashlytics ?: NoopNonFatalReporter

    private val firebaseSink: FirebaseAnalyticsSink? =
        if (firebaseConfig.isConfigured) FirebaseAnalyticsSink(appContext, bootstrap, reporter) else null
    val analyticsSink = AnalyticsSinkSelector(
        firebase = firebaseSink,
        fallback = fallbackSink,
        allowed = analyticsAllowed,
        firebaseRunning = ::isAnalyticsRunning,
        setCollection = {
            firebaseSink?.setCollection(it)
            onCollectionSet(it)
        },
    )

    private val rcSource by lazy {
        FirebaseRemoteConfigSource(bootstrap, mirror, BuildConfig.DEBUG, R.xml.remote_config_defaults, reporter)
    }
    private val coldStartDone = AtomicBoolean(false)
    private val crashlyticsStarted = AtomicBoolean(false)

    /**
     * Called from the app's main Activity `onCreate` (never from the IME). Once per process it activates the
     * fetched config, writes the mirror and starts Crashlytics; after the first frame it fetches for the next cold
     * start. While [owner] is started it listens for kill-switch updates.
     */
    fun onAppActivityCreated(owner: LifecycleOwner, firstFrame: (Runnable) -> Unit) {
        if (!firebaseConfig.isConfigured) return
        if (coldStartDone.compareAndSet(false, true)) {
            owner.lifecycleScope.launch {
                rcSource.activateAndMirror()
                startCrashlytics()
                // Remote Config started the default app; turn off a collection flag an earlier process persisted.
                syncAnalyticsCollection()
            }
            firstFrame(Runnable { rcSource.fetchInBackground() })
        }
        owner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                private var listener: AutoCloseable? = null

                override fun onStart(owner: LifecycleOwner) {
                    listener = rcSource.listenForKillSwitches()
                }

                override fun onStop(owner: LifecycleOwner) {
                    listener?.close()
                    listener = null
                }
            },
        )
    }

    /**
     * Consent Mode v2 signals from the UMP result (S4 Task 6, TRD §4.11), set by the app's ads stack on each cold
     * start that gathers consent. Does nothing without Firebase options.
     */
    fun setConsentMode(adStorage: Boolean, adUserData: Boolean, adPersonalization: Boolean, analyticsStorage: Boolean) {
        firebaseSink?.setConsent(adStorage, adUserData, adPersonalization, analyticsStorage)
    }

    /** Applies a change of the analytics opt-out to Crashlytics collection. */
    fun onOptOutChanged(optedOut: Boolean) {
        if (crashlyticsStarted.get()) crashlytics?.setCollection(!optedOut)
    }

    /**
     * App process only (an app Activity start or a UMP result), where the default app may already run: while
     * [analyticsAllowed] is false, turns Analytics collection off, also a flag that an earlier process persisted.
     */
    fun syncAnalyticsCollection() {
        if (firebaseConfig.isConfigured) analyticsSink.syncWhenDisallowed()
    }

    /** Applies a change of [analyticsAllowed] (Analytics consent and the opt-out) to Analytics collection. */
    fun onAnalyticsAllowedChanged(allowed: Boolean) {
        analyticsSink.onAllowedChanged(allowed)
    }

    /**
     * True when Firebase already runs in this process: the sink holds a `FirebaseAnalytics` instance (for example
     * after [setConsentMode]) or the default app exists (Remote Config). Reading this never starts Firebase.
     */
    internal fun isAnalyticsRunning(): Boolean = firebaseSink?.isStarted == true || bootstrap.isDefaultAppRunning()

    private fun startCrashlytics() {
        val reporter = crashlytics ?: return
        if (!crashlyticsStarted.compareAndSet(false, true)) return
        reporter.start()
        reporter.setCollection(!optOut())
    }
}

/** The Firebase options from the `aikb.firebase.*` Gradle properties (empty by default). */
private fun buildConfigFirebase() = FirebaseConfig(
    projectId = BuildConfig.FIREBASE_PROJECT_ID,
    appId = BuildConfig.FIREBASE_APP_ID,
    apiKey = BuildConfig.FIREBASE_API_KEY,
    projectNumber = BuildConfig.FIREBASE_PROJECT_NUMBER,
)

/**
 * Picks the analytics sink per event: Firebase when it exists and [allowed] is true (Analytics consent is known and
 * the user did not opt out), else [fallback] (Logcat in debug, no-op in release). While [allowed] is false the
 * Firebase sink gets no call at all, so FirebaseApp is never started for analytics before consent. Collection is
 * turned on lazily before the first Firebase event, and off at once when [allowed] turns false while Firebase runs
 * ([firebaseRunning]; the SDK keeps that setting across launches).
 *
 * One lock covers the [allowed] check in [log], every collection change and [onAllowedChanged], so an opt-out can
 * never land between the check and turning collection on.
 */
class AnalyticsSinkSelector(
    private val firebase: AnalyticsSink?,
    private val fallback: AnalyticsSink,
    private val allowed: () -> Boolean,
    private val firebaseRunning: () -> Boolean,
    private val setCollection: (Boolean) -> Unit,
) : AnalyticsSink {
    private val lock = Any()
    private var collecting: Boolean? = null

    override fun log(event: AnalyticsEvent) {
        val sink = firebase?.takeIf { synchronized(lock) { allowed().also { ok -> if (ok) apply(true) } } }
        (sink ?: fallback).log(event)
    }

    override fun setUserProperty(name: String, value: String?) {
        val sink = firebase
        if (sink == null || !allowed()) fallback.setUserProperty(name, value) else sink.setUserProperty(name, value)
    }

    /**
     * On false, turns collection off whenever Firebase already runs: this process turned it on, or the UMP result
     * (Consent Mode) or Remote Config started it, which keeps a flag that an earlier process persisted. On true,
     * turns it back on only when it was turned off here. Never starts Firebase only to turn it off.
     */
    fun onAllowedChanged(allowed: Boolean) {
        if (firebase == null) return
        synchronized(lock) {
            if (!allowed && (collecting == true || firebaseRunning())) {
                apply(false)
            } else if (allowed && collecting == false) {
                apply(true)
            }
        }
    }

    /**
     * For the app Activity start, where Remote Config already started the default app: while [allowed] is false,
     * turns off a collection flag that an earlier process persisted.
     */
    fun syncWhenDisallowed() {
        if (firebase == null) return
        synchronized(lock) { if (!allowed()) apply(false) }
    }

    private fun apply(enabled: Boolean) = synchronized(lock) {
        if (collecting != enabled) {
            collecting = enabled
            setCollection(enabled)
        }
    }
}
