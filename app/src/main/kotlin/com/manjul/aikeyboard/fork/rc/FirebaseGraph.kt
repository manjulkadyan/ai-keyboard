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
 * - [analyticsSink] sends to Firebase Analytics only when Firebase is configured and the user did not opt out.
 * - [reporter] sends non-fatal errors to Crashlytics (class name and stack only).
 */
class FirebaseGraph(context: Context, private val optOut: () -> Boolean, fallbackSink: AnalyticsSink) {
    private val appContext = context.applicationContext

    val firebaseConfig = FirebaseConfig(
        projectId = BuildConfig.FIREBASE_PROJECT_ID,
        appId = BuildConfig.FIREBASE_APP_ID,
        apiKey = BuildConfig.FIREBASE_API_KEY,
        projectNumber = BuildConfig.FIREBASE_PROJECT_NUMBER,
    )
    val bootstrap = FirebaseBootstrap(appContext, firebaseConfig)
    val mirror = RemoteConfigMirror(appContext)
    val remoteConfig: RemoteConfig = MirroredRemoteConfig(mirror)

    private val crashlytics: CrashlyticsReporter? =
        if (firebaseConfig.isConfigured) CrashlyticsReporter(bootstrap) else null
    val reporter: NonFatalReporter = crashlytics ?: NoopNonFatalReporter

    private val firebaseSink: FirebaseAnalyticsSink? =
        if (firebaseConfig.isConfigured) FirebaseAnalyticsSink(appContext, bootstrap, reporter) else null
    val analyticsSink = AnalyticsSinkSelector(firebaseSink, fallbackSink, optOut, { firebaseSink?.setCollection(it) })

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

    /** Applies a change of the analytics opt-out to Analytics and Crashlytics collection. */
    fun onOptOutChanged(optedOut: Boolean) {
        analyticsSink.onOptOutChanged(optedOut)
        if (crashlyticsStarted.get()) crashlytics?.setCollection(!optedOut)
    }

    private fun startCrashlytics() {
        val reporter = crashlytics ?: return
        if (!crashlyticsStarted.compareAndSet(false, true)) return
        reporter.start()
        reporter.setCollection(!optOut())
    }
}

/**
 * Picks the analytics sink per event: Firebase when it exists and the user did not opt out, else [fallback]
 * (Logcat in debug, no-op in release). Collection is turned on lazily before the first Firebase event, and off at
 * once when the user opts out (the SDK keeps that setting across launches).
 */
class AnalyticsSinkSelector(
    private val firebase: AnalyticsSink?,
    private val fallback: AnalyticsSink,
    private val optOut: () -> Boolean,
    private val setCollection: (Boolean) -> Unit,
) : AnalyticsSink {
    private val lock = Any()
    private var collecting: Boolean? = null

    override fun log(event: AnalyticsEvent) {
        val sink = firebase
        if (sink == null || optOut()) {
            fallback.log(event)
            return
        }
        apply(true)
        sink.log(event)
    }

    override fun setUserProperty(name: String, value: String?) {
        val sink = firebase
        if (sink == null || optOut()) fallback.setUserProperty(name, value) else sink.setUserProperty(name, value)
    }

    fun onOptOutChanged(optedOut: Boolean) {
        if (firebase == null) return
        if (optedOut) apply(false) else if (synchronized(lock) { collecting == false }) apply(true)
    }

    private fun apply(enabled: Boolean) {
        val changed = synchronized(lock) {
            (collecting != enabled).also { collecting = enabled }
        }
        if (changed) setCollection(enabled)
    }
}
