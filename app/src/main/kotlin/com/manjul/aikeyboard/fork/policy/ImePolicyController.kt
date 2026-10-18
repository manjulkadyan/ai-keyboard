/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.policy

import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.ime.policy.PolicyContext
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitiveFieldPolicy
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Evaluates [SensitiveFieldPolicy] on every `onStartInput` and tells features to drop their state when a
 * field becomes more restrictive. Main thread only. [keyguard] is read on every evaluation, never cached.
 */
class ImePolicyController(
    private val store: FeatureSettingsStore,
    private val keyguard: () -> Boolean,
    private val forcedPackages: () -> Set<String>,
) {
    private val _decision = MutableStateFlow(NoField)
    private val listeners = CopyOnWriteArrayList<(PolicyDecision) -> Unit>()

    val decision: StateFlow<PolicyDecision> = _decision
    val generation = FieldGeneration()

    /** Called when a field starts (not a restart) or a restart is more restrictive than the previous decision. */
    fun addRestrictionListener(l: (PolicyDecision) -> Unit) {
        listeners.add(l)
    }

    fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        val settings = store.current
        val next = SensitiveFieldPolicy.evaluate(
            info.toFieldDescriptor(),
            PolicyContext(
                deviceLocked = keyguard(),
                incognitoManual = settings.incognitoManual,
                privatePackages = settings.privatePackages,
                forceSensitivePackages = forcedPackages(),
            ),
        )
        val previous = _decision.value
        _decision.value = next
        if (!restarting || next.isMoreRestrictiveThan(previous)) {
            generation.bump()
            notifyListeners(next)
        }
    }

    fun onFinishInputView() {
        generation.bump()
    }

    /** No field is focused: nothing may run until the next [onStartInput]. */
    fun onFinishInput() {
        generation.bump()
        _decision.value = NoField
        notifyListeners(NoField)
    }

    private fun notifyListeners(decision: PolicyDecision) {
        listeners.forEach { it(decision) }
    }

    private companion object {
        /** Everything denied and no reasons: used before the first and after the last field. */
        val NoField: PolicyDecision = PolicyDecision.Locked.copy(reasons = emptySet())
    }
}
