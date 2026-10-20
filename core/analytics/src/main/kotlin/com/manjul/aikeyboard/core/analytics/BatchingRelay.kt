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
 * Batches events and flushes to [sink] at most once per 60 s (PRD FR-35 AC2). A batch flushes at
 * max(first event + 60 s, last flush + 60 s); later events join it. Opt-out drops everything; under a
 * minimal policy only `ime_session_start` with `is_sensitive=true` passes. The policy is applied when the event
 * is logged, against the decision current then, so a later field does not decide for an earlier one. Opt-out is
 * re-checked at flush. A null policy means "no IME field context" and is not minimal.
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
    private var lastFlushAt: Long? = null

    fun log(event: AnalyticsEvent) {
        if (optOut() || !passesPolicy(event)) return
        synchronized(lock) {
            pending += event
            if (flushJob == null) {
                val now = clock()
                val last = lastFlushAt
                val deadline = maxOf(now + FLUSH_INTERVAL_MS, if (last == null) 0L else last + FLUSH_INTERVAL_MS)
                flushJob = scope.launch {
                    while (true) {
                        val remaining = deadline - clock()
                        if (remaining <= 0) break
                        delay(remaining)
                    }
                    flush(manual = false)
                }
            }
        }
    }

    /** Sends the pending batch now (e.g. on background). The next batch still waits 60 s after this. */
    fun flushNow() = flush(manual = true)

    private fun flush(manual: Boolean) {
        val batch: List<AnalyticsEvent>
        synchronized(lock) {
            batch = ArrayList(pending)
            pending.clear()
            if (manual) flushJob?.cancel() // the scheduled job itself must not cancel the flush it is running
            flushJob = null
            if (batch.isNotEmpty()) lastFlushAt = clock()
        }
        if (optOut()) return
        batch.forEach(sink::log)
    }

    private fun passesPolicy(e: AnalyticsEvent) =
        policy()?.analyticsMinimal != true ||
            (e.name == EventCatalog.IME_SESSION_START && e.params["is_sensitive"] == true)

    private companion object {
        const val FLUSH_INTERVAL_MS = 60_000L
    }
}
