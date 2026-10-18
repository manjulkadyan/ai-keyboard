/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.manjul.aikeyboard.FlorisImeService
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.ime.policy.SensitivityReason
import com.manjul.aikeyboard.ime.smartbar.quickaction.QuickAction
import com.manjul.aikeyboard.ime.text.key.KeyCode
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val FailClosed: StateFlow<PolicyDecision> = MutableStateFlow(PolicyDecision.Locked)

/** The live policy decision for UI code. Without a running IME service it is [PolicyDecision.Locked]. */
@Composable
fun rememberPolicyDecision(): PolicyDecision {
    val flow = remember { FlorisImeService.policyControllerOrNull()?.decision ?: FailClosed }
    val decision by flow.collectAsState()
    return decision
}

/** True if [action] may be shown under [decision]. Chip-backed actions follow [ChipArrangement.allowed]. */
fun QuickAction.isAllowedBy(decision: PolicyDecision, clipboardHistoryOn: Boolean): Boolean {
    val code = (this as? QuickAction.InsertKey)?.data?.code ?: return true
    if (code == KeyCode.TOGGLE_PRIVATE_APP) return SensitivityReason.PRIVATE_APP !in decision.reasons
    val chip = ChipId.forKeyCode(code) ?: return true
    return ChipArrangement.allowed(chip, decision, clipboardHistoryOn)
}

/** Actions the current decision disallows are removed, not greyed out. */
@Composable
fun List<QuickAction>.policyFiltered(): List<QuickAction> {
    val decision = rememberPolicyDecision()
    val prefs by FlorisPreferenceStore
    val clipboardHistoryOn by prefs.clipboard.historyEnabled.observeAsState()
    val actions = this
    return remember(actions, decision, clipboardHistoryOn) {
        actions.filter { it.isAllowedBy(decision, clipboardHistoryOn) }
    }
}

@Composable
fun QuickAction?.policyFilteredOrNull(): QuickAction? {
    if (this == null) return null
    return listOf(this).policyFiltered().firstOrNull()
}
