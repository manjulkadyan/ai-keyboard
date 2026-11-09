/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.LocalNavController
import com.manjul.aikeyboard.app.Routes
import com.manjul.aikeyboard.billing
import com.manjul.aikeyboard.core.ai.kb.SubState
import com.manjul.aikeyboard.core.billing.ProUiState
import com.manjul.aikeyboard.fork.billing.FixPaymentBanner
import com.manjul.aikeyboard.fork.billing.PlayLinks
import com.manjul.aikeyboard.fork.paywall.PaywallTrigger
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * Settings → Subscription (PRD FR-09, FR-28): the Pro state and renewal date from kb-api's signed answer, the
 * "Fix payment" banner, Manage in Google Play, Restore purchases and, for a free user, Remove ads (the paywall).
 * Restore goes through `EntitlementRepository.restore()`, which logs `restore_purchases{found}`.
 */
@Composable
fun SubscriptionSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.subscription_settings__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val repository = remember { context.billing().value.repository }
    val state by repository.state.collectAsState()

    content {
        SubscriptionSettingsContent(
            state = state,
            onManage = { PlayLinks.openManageSubscription(context) },
            onRestore = { repository.restore() },
            onRemoveAds = { navController.navigate(Routes.Paywall(PaywallTrigger.REMOVE_ADS.wire)) },
        )
    }
}

private enum class RestoreMessage { RESTORED, NOT_FOUND }

@Composable
internal fun SubscriptionSettingsContent(
    state: ProUiState,
    onManage: () -> Unit,
    onRestore: suspend () -> Boolean,
    onRemoveAds: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var restoring by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<RestoreMessage?>(null) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        FixPaymentBanner(state, onFix = onManage)
        ListItem(
            headlineContent = { Text(stringRes(stateLabel(state))) },
            supportingContent = renewalText(state)?.let { { Text(it) } },
        )
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, onClick = onManage),
            headlineContent = { Text(stringRes(R.string.subscription_settings__manage)) },
        )
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, enabled = !restoring) {
                restoring = true
                message = null
                scope.launch {
                    val found = try {
                        onRestore()
                    } finally {
                        restoring = false
                    }
                    message = if (found) RestoreMessage.RESTORED else RestoreMessage.NOT_FOUND
                }
            },
            headlineContent = { Text(stringRes(R.string.subscription_settings__restore)) },
            supportingContent = message?.let { m ->
                {
                    Text(
                        stringRes(
                            if (m == RestoreMessage.RESTORED) {
                                R.string.subscription_settings__restored
                            } else {
                                R.string.subscription_settings__not_found
                            },
                        ),
                    )
                }
            },
        )
        if (!state.isPro) {
            ListItem(
                modifier = Modifier.clickable(role = Role.Button, onClick = onRemoveAds),
                headlineContent = { Text(stringRes(R.string.ads__remove_ads_link)) },
            )
        }
    }
}

private fun stateLabel(state: ProUiState): Int = when {
    state.state == SubState.GRACE || state.state == SubState.ON_HOLD -> R.string.subscription_settings__state_payment
    state.isPro -> R.string.subscription_settings__state_pro
    state.state == SubState.PENDING -> R.string.subscription_settings__state_pending
    else -> R.string.subscription_settings__state_free
}

@Composable
private fun renewalText(state: ProUiState): String? {
    if (!state.isPro || state.expiresAtMs <= 0L) return null
    val date = remember(state.expiresAtMs) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(state.expiresAtMs))
    }
    return stringRes(R.string.subscription_settings__renews, "date" to date)
}
