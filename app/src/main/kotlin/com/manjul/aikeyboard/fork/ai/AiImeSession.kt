/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import android.view.inputmethod.InputConnection
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.ConsentKind
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.fork.policy.FieldGeneration
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.fork.voice.ImeTextTarget
import com.manjul.aikeyboard.ime.aiui.AiActionController
import com.manjul.aikeyboard.ime.editing.SafeReplacer
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The ✦ AI rewrite for one IME service instance (PRD FR-15, TRD §4.4): wires [AiActionController] to the input
 * connection, the live policy decision and field generation, the settings store (consent, translate target) and the
 * app's [AiAppGraph]. Created in `FlorisImeService.onCreate`, main thread only. Mirrors `TranslateImeSession`.
 *
 * Privacy (PC-1, TRD §11.6):
 * - [chipAvailable] is false unless the backend is configured and the controller says AI is available
 *   (`allowAi && allowCloud`, not paused, not switched off). The chip is hidden then, and [open] does nothing.
 * - The controller re-checks the live decision and the field generation before every send and every host write.
 * - Nothing is captured or sent before `consentAiAt > 0`. Without it, [open] asks the app for the consent screen
 *   ([launchConsent]); only Agree there sets the consent. The IME window never shows the consent itself.
 * - Every restriction, a new field, hiding the keyboard and [onDestroy] cancel the stream, drop the field's LRU and
 *   the pending undo, and hide the sheet.
 * - Token prefetch runs at most once per keyboard session, and only once the user has agreed: before that the app
 *   makes no kb-api or Firebase traffic for AI at all.
 *
 * Captured text and AI output are user content: never logged.
 */
class AiImeSession(
    connection: () -> InputConnection?,
    private val decision: () -> PolicyDecision,
    private val generation: FieldGeneration,
    private val settings: FeatureSettingsStore,
    private val graph: AiAppGraph,
    private val launchConsent: (ConsentKind) -> Unit,
    @Suppress("unused") private val isPro: () -> Boolean, // the Pro gate; always false until billing (Task 14 uses it)
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    isRawInputEditor: () -> Boolean = { false },
    locale: () -> String = { DEFAULT_LOCALE },
    analytics: (AnalyticsEvent) -> Unit = {},
    copyToClipboard: (String) -> Unit = {},
    private val openPro: () -> Unit = {},
) {
    // The sheet never composes in the host field, so the editor's composing region is never ours.
    private val target = ImeTextTarget(connection, isRawInputEditor, setOwnsComposing = {})
    private val replacer = SafeReplacer(target, clock)

    val rewrite: AiActionController = AiActionController(
        engine = graph.engine,
        replacer = replacer,
        targetProvider = { target },
        decision = decision,
        generation = { generation.value },
        hasConsent = { settings.current.consentAiAt > 0L },
        requestConsent = { launchConsent(ConsentKind.AI) },
        locale = locale,
        translateTarget = { settings.current.translatePair?.target },
        lru = graph.lru,
        pause = graph.pause,
        config = graph.config,
        analytics = analytics,
        copyToClipboard = copyToClipboard,
        report = graph.report,
        scope = scope,
        clock = clock,
    )

    private val _chipAvailable = MutableStateFlow(false)

    /** Whether the ✦ chip shows (and is enabled): a configured backend and an available controller. */
    val chipAvailable: StateFlow<Boolean> = _chipAvailable.asStateFlow()

    private val jobs = mutableListOf<Job>()
    private var viewShown = false
    private var prefetched = false

    /**
     * Follows [policy]: every restriction calls [onRestricted], every decision re-reads availability. Settings and
     * the controller's own availability (pause, kill switch) are followed too.
     */
    fun bindTo(policy: ImePolicyController) {
        policy.addRestrictionListener { onRestricted() }
        jobs += scope.launch { policy.decision.collect { onDecision() } }
        jobs += scope.launch { rewrite.isAvailable.collect { refresh() } }
        jobs += scope.launch { settings.flow.collect { refresh() } }
    }

    /** The ✦ chip. Re-checks availability; the controller asks for consent or shows the tones. */
    fun open() {
        if (!graph.isConfigured) return
        rewrite.open()
        refresh()
    }

    /** "See Pro" on the quota card. */
    fun seePro() {
        rewrite.dismiss()
        openPro()
    }

    /** A new field (not a restart): nothing on the new connection is ours, and nothing of the old field survives. */
    fun onStartInput(restarting: Boolean) {
        if (restarting) return
        target.forgetOwnedRegion()
        rewrite.onFieldChanged()
        refresh()
    }

    fun onStartInputView() {
        viewShown = true
        refresh()
    }

    /** The decision changed (also on a settings change for the same field). */
    fun onDecision() {
        rewrite.refreshAvailability()
        refresh()
    }

    /** The field became more restrictive, or a field started or finished. */
    fun onRestricted() {
        rewrite.onRestricted()
        refresh()
    }

    /** Hiding the keyboard ends the sheet and the keyboard session (the next show may prefetch again). */
    fun onFinishInputView() {
        viewShown = false
        prefetched = false
        rewrite.onFieldChanged()
        refresh()
    }

    fun onDestroy() {
        rewrite.onFieldChanged()
        jobs.forEach { it.cancel() }
        jobs.clear()
        _chipAvailable.value = false
    }

    private fun refresh() {
        val available = graph.isConfigured && rewrite.isAvailable.value
        _chipAvailable.value = available
        if (available && viewShown && !prefetched && settings.current.consentAiAt > 0L) {
            prefetched = true
            graph.tokens.prefetch()
        }
    }

    private companion object {
        const val DEFAULT_LOCALE = "en"
    }
}
