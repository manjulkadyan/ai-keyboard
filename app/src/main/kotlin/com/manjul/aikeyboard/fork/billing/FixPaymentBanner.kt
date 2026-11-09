/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.billing

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.ai.kb.SubState
import com.manjul.aikeyboard.core.billing.ProUiState
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.isProAt
import com.manjul.aikeyboard.featureSettingsStore
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.stringRes

const val FIX_PAYMENT_BANNER_TAG = "fix_payment_banner"

/**
 * The "Fix payment" banner (PRD FR-28 AC3). Shown only in Play's grace period and account hold. A tap calls
 * [onFix], which opens Play's subscription page. The state comes from kb-api's signed answer (ARCH-006).
 */
@Composable
fun FixPaymentBanner(state: ProUiState, onFix: () -> Unit) {
    val text = when (state.state) {
        SubState.GRACE -> R.string.billing__fix_payment_grace
        SubState.ON_HOLD -> R.string.billing__fix_payment_hold
        else -> return
    }
    FlorisErrorCard(
        modifier = Modifier.padding(8.dp).testTag(FIX_PAYMENT_BANNER_TAG),
        text = stringRes(text),
        onClick = onFix,
    )
}

/** The Settings home slot. It reads the Pro cache only, so it never starts Play Billing or calls kb-api. */
@Composable
fun HomeFixPaymentBanner() {
    val context = LocalContext.current
    val store = remember { context.featureSettingsStore().value }
    val settings by store.flow.collectAsState(initial = store.current)
    FixPaymentBanner(proUiStateOf(settings, System.currentTimeMillis())) { PlayLinks.openManageSubscription(context) }
}

internal fun proUiStateOf(s: FeatureSettings, nowMs: Long) = ProUiState(
    isPro = s.isProAt(nowMs),
    state = SubState.fromWire(s.proState) ?: SubState.NONE,
    expiresAtMs = s.proExpiresAt,
    activating = false,
)
