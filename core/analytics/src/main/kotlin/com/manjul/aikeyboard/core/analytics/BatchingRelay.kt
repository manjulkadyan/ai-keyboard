/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

import com.manjul.aikeyboard.ime.policy.PolicyDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Batches events and flushes to [sink] at most once per 60 s (PRD FR-35 AC2). The first event of a batch
 * schedules the flush at its time + 60 s; later events join that batch. Opt-out drops everything; under a
 * minimal policy only `ime_session_start` with `is_sensitive=true` passes.
 */
class BatchingRelay(
    private val sink: AnalyticsSink,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val optOut: () -> Boolean,
    private val policy: () -> PolicyDecision?,
) {
    private val lock = Any()
    private val pending = ArrayList<AnalyticsEvent>()
    private var flushJob: Job? = null

    fun log(event: AnalyticsEvent) {
        if (optOut()) return
        if (policy()?.analyticsMinimal == true && !isSensitiveSessionStart(event)) return
        synchronized(lock) {
            pending += event
            if (flushJob == null) {
                val deadline = clock() + FLUSH_INTERVAL_MS
                flushJob = scope.launch {
                    while (true) {
                        val remaining = deadline - clock()
                        if (remaining <= 0) break
                        delay(remaining)
                    }
                    flushNow()
                }
            }
        }
    }

    /** Sends the pending batch now (e.g. on background). Re-checks opt-out. */
    fun flushNow() {
        val batch: List<AnalyticsEvent>
        synchronized(lock) {
            batch = ArrayList(pending)
            pending.clear()
            flushJob?.cancel()
            flushJob = null
        }
        if (optOut()) return
        batch.forEach(sink::log)
    }

    private fun isSensitiveSessionStart(e: AnalyticsEvent) =
        e.name == EventCatalog.IME_SESSION_START && e.params["is_sensitive"] == true

    private companion object {
        const val FLUSH_INTERVAL_MS = 60_000L
    }
}
