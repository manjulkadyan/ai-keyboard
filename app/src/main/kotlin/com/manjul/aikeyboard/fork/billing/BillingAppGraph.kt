/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.billing

import android.app.Application
import androidx.work.WorkerFactory
import com.manjul.aikeyboard.BuildConfig
import com.manjul.aikeyboard.aiAppGraph
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.core.ai.kb.EntitlementClient
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.billing.BillingGateway
import com.manjul.aikeyboard.core.billing.EntitlementRepository
import com.manjul.aikeyboard.core.billing.EntitlementSignature
import com.manjul.aikeyboard.core.billing.PlayBillingGateway
import com.manjul.aikeyboard.core.billing.RefreshReason
import com.manjul.aikeyboard.core.billing.VerifyPurchaseWorkerFactory
import com.manjul.aikeyboard.core.billing.WorkManagerVerifyScheduler
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.network.HttpClientFactory
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.firebaseGraph
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Play Billing and the kb-api entitlement for the app process (S4 plan Task 4). [FlorisApplication] holds one
 * lazily. Nothing here runs in the IME: the IME reads Pro through [proGate] only (`ImeAdIsolationTest`).
 * Nothing is built until first use. App start and resume reach kb-api only when [mayAutoRefresh] allows it.
 */
class BillingAppGraph(private val app: Application) {
    private val settings by lazy { app.featureSettingsStore().value }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val coldStarted = AtomicBoolean(false)
    private val resumeGate = ResumeAfterColdStart(EntitlementRepository.RESUME_THROTTLE_MS)

    val gateway: BillingGateway by lazy { PlayBillingGateway(app) }

    val repository: EntitlementRepository by lazy {
        // EntitlementClient adds the auth headers itself, so the client has no auth interceptor.
        val tokens = app.aiAppGraph().value.tokens
        EntitlementRepository(
            gateway = gateway,
            client = EntitlementClient(HttpClientFactory.create(), BuildConfig.AI_BASE_URL, tokens),
            signature = EntitlementSignature(BuildConfig.ENTITLEMENT_PUBLIC_KEY, BuildConfig.DEBUG),
            settings = settings,
            analytics = RelaySink(app),
            clock = System::currentTimeMillis,
            scope = scope,
            nonFatal = app.firebaseGraph().value.reporter,
            retryScheduler = WorkManagerVerifyScheduler(app),
        )
    }

    /** The app's first Activity start in this process. Later calls (a rotation) do nothing. */
    fun onAppStart() {
        if (!mayAutoRefresh(settings.current) || !coldStarted.compareAndSet(false, true)) return
        resumeGate.coldStarted(System.currentTimeMillis())
        scope.launch { repository.refresh(RefreshReason.COLD_START) }
    }

    /** An app Activity resumed. The repository throttles it to once per 5 min. */
    fun onAppResume() {
        if (!mayAutoRefresh(settings.current) || !resumeGate.mayResume(System.currentTimeMillis())) return
        scope.launch { repository.refresh(RefreshReason.RESUME) }
    }

    /** Events go through the batching relay (opt-out, policy). User properties go to the selected sink. */
    private class RelaySink(private val app: Application) : AnalyticsSink {
        override fun log(event: AnalyticsEvent) = app.appContext().value.analytics.value.log(event)

        override fun setUserProperty(name: String, value: String?) =
            app.firebaseGraph().value.analyticsSink.setUserProperty(name, value)
    }

    companion object {
        /**
         * Start and resume call kb-api only after AI consent or an earlier entitlement answer (a purchase or a
         * restore). A fresh install sends nothing before the user acts. The paywall and restore call it directly.
         */
        fun mayAutoRefresh(s: FeatureSettings): Boolean = s.consentAiAt > 0L || s.proCheckedAt > 0L

        /** The [androidx.work.Configuration] factory for `VerifyPurchaseWorker`. */
        fun workerFactory(graph: () -> BillingAppGraph): WorkerFactory =
            VerifyPurchaseWorkerFactory { graph().repository }
    }
}

/** Skips the resume refresh that follows a cold start (`onCreate` then `onResume`), so start sends one call. */
internal class ResumeAfterColdStart(private val throttleMs: Long) {
    @Volatile private var coldStartAt: Long? = null

    fun coldStarted(nowMs: Long) {
        coldStartAt = nowMs
    }

    fun mayResume(nowMs: Long): Boolean {
        val start = coldStartAt ?: return true
        return nowMs - start >= throttleMs
    }
}
