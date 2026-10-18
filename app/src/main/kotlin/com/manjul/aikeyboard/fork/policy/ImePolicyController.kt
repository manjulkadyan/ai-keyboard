/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.policy

import android.view.inputmethod.EditorInfo
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.ime.policy.FieldDescriptor
import com.manjul.aikeyboard.ime.policy.PolicyContext
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitiveFieldPolicy
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Evaluates [SensitiveFieldPolicy] on every `onStartInput` and tells features to drop their state when a
 * field becomes more restrictive. Main thread only. [keyguard] is read on every evaluation, never cached.
 */
class ImePolicyController(
    private val store: FeatureSettingsStore,
    private val keyguard: () -> Boolean,
    private val forcedPackages: () -> Set<String>,
    scope: CoroutineScope,
) {
    private val _decision = MutableStateFlow(NoField)
    private val listeners = CopyOnWriteArrayList<(PolicyDecision) -> Unit>()

    /** The field of the active input session; null between sessions. Re-evaluated when settings change. */
    private var activeField: FieldDescriptor? = null

    val decision: StateFlow<PolicyDecision> = _decision
    val generation = FieldGeneration()

    init {
        // Settings load asynchronously: a private app or incognito value that arrives after the first field
        // must still lock it, so every emission re-evaluates the active field.
        scope.launch {
            store.flow.collect { settings ->
                val field = activeField ?: return@collect
                val next = evaluate(field, settings)
                val previous = _decision.value
                _decision.value = next
                if (next.isMoreRestrictiveThan(previous)) {
                    generation.bump()
                    notifyListeners(next)
                }
            }
        }
    }

    /** Called when a field starts (not a restart) or a restart is more restrictive than the previous decision. */
    fun addRestrictionListener(l: (PolicyDecision) -> Unit) {
        listeners.add(l)
    }

    fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        val field = info.toFieldDescriptor()
        activeField = field
        val next = evaluate(field, store.current)
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
        activeField = null
        generation.bump()
        _decision.value = NoField
        notifyListeners(NoField)
    }

    private fun evaluate(field: FieldDescriptor, settings: FeatureSettings): PolicyDecision =
        SensitiveFieldPolicy.evaluate(
            field,
            PolicyContext(
                deviceLocked = keyguard(),
                incognitoManual = settings.incognitoManual,
                privatePackages = settings.privatePackages,
                forceSensitivePackages = forcedPackages(),
            ),
        )

    private fun notifyListeners(decision: PolicyDecision) {
        listeners.forEach { it(decision) }
    }

    private companion object {
        /** Everything denied and no reasons: used before the first and after the last field. */
        val NoField: PolicyDecision = PolicyDecision.Locked.copy(reasons = emptySet())
    }
}
