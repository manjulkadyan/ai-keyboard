/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.billing
import com.manjul.aikeyboard.core.billing.Plan
import com.manjul.aikeyboard.core.billing.ProOffer
import com.manjul.aikeyboard.lib.util.launchUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.florisboard.lib.compose.stringRes

const val PAYWALL_CLOSE_TAG = "paywall_close"
const val PAYWALL_CTA_TAG = "paywall_cta"

/** How long "You are Pro" stays on screen before the paywall closes. */
private const val PRO_CLOSE_DELAY_MS = 1_500L

/** Plan decision 10: only the shipped benefits. */
private val BENEFITS = listOf(
    R.string.paywall__benefit_no_ads,
    R.string.paywall__benefit_ai_actions,
    R.string.paywall__benefit_premium_themes,
    R.string.paywall__benefit_auto_polish,
)

data class PaywallActions(
    val onClose: () -> Unit,
    val onSelect: (Plan) -> Unit,
    val onPurchase: () -> Unit,
    val onRestore: () -> Unit,
    val onRetry: () -> Unit,
    val onTerms: () -> Unit,
    val onPrivacy: () -> Unit,
)

/** Keeps the view model across rotation, so a configuration change does not log `paywall_view` again. */
internal class PaywallHolder(context: Context, trigger: PaywallTrigger) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val billing = context.billing().value
    val repository = billing.repository
    val vm = PaywallViewModel(
        trigger = trigger,
        catalog = { billing.gateway.catalog() },
        refresh = { repository.refresh(it) },
        restore = { repository.restore() },
        pro = repository.state,
        strings = ResourcePaywallStrings(context),
        analytics = context.appContext().value.analytics.value::log,
        clock = System::currentTimeMillis,
        scope = scope,
    )

    override fun onCleared() {
        scope.cancel()
    }
}

private class ResourcePaywallStrings(private val context: Context) : PaywallStrings {
    override fun trial(days: Int, yearlyPrice: String) =
        context.resources.getQuantityString(R.plurals.paywall__trial_terms, days, days, yearlyPrice)

    override fun yearly(price: String) = context.getString(R.string.paywall__yearly_terms, price)

    override fun monthly(price: String) = context.getString(R.string.paywall__monthly_terms, price)
}

/**
 * The paywall route content (S-03). [onClosed] leaves the screen: the close button, Back, and "You are Pro"
 * after a purchase or a restore all end here. App process only (`ImeAdIsolationTest`).
 */
@Composable
fun PaywallRoute(trigger: PaywallTrigger, onClosed: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val holder = viewModel<PaywallHolder>(
        key = "paywall:${trigger.wire}",
        factory = viewModelFactory { initializer { PaywallHolder(appContext, trigger) } },
    )
    val vm = holder.vm
    val ui by vm.ui.collectAsState()
    val closed by rememberUpdatedState(onClosed)
    LaunchedEffect(vm) { vm.open() }
    LaunchedEffect(ui.becamePro) {
        if (ui.becamePro) {
            delay(PRO_CLOSE_DELAY_MS)
            vm.close()
            closed()
        }
    }
    DisposableEffect(vm) { onDispose { vm.close() } }
    val activity = remember(context) { context.findActivity() }
    PaywallScreen(
        ui = ui,
        actions = PaywallActions(
            onClose = {
                vm.close()
                closed()
            },
            onSelect = vm::select,
            onPurchase = { vm.purchase { offer -> activity?.let { holder.repository.purchase(it, offer) } } },
            onRestore = vm::restore,
            onRetry = vm::retry,
            onTerms = { context.launchUrl(R.string.terms_url) },
            onPrivacy = { context.launchUrl(R.string.privacy_policy_url) },
        ),
    )
}

/** S-03 (PRD FR-29). The close button is the first element and needs no data, so it shows on the first frame. */
@Composable
fun PaywallScreen(ui: PaywallUi, actions: PaywallActions) {
    BackHandler(onBack = actions.onClose)
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = actions.onClose, modifier = Modifier.testTag(PAYWALL_CLOSE_TAG)) {
                Icon(Icons.Default.Close, contentDescription = stringRes(R.string.paywall__close))
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringRes(ui.header), style = MaterialTheme.typography.headlineSmall)
            for (benefit in BENEFITS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringRes(benefit), modifier = Modifier.padding(start = 12.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            when {
                ui.isPro -> Text(stringRes(R.string.paywall__you_are_pro), style = MaterialTheme.typography.titleMedium)
                ui.activating ->
                    Text(stringRes(R.string.paywall__activating), style = MaterialTheme.typography.titleMedium)
                ui.pricesUnavailable -> {
                    Text(stringRes(R.string.paywall__prices_unavailable), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = actions.onRetry) { Text(stringRes(R.string.paywall__try_again)) }
                }
                else -> {
                    PlanOption(
                        label = stringRes(R.string.paywall__plan_annual),
                        offer = ui.annual,
                        badge = ui.savingsPercent?.takeIf { it > 0 }
                            ?.let { stringRes(R.string.paywall__save_percent).format(it) },
                        selected = ui.selected == Plan.ANNUAL,
                        onSelect = { actions.onSelect(Plan.ANNUAL) },
                    )
                    PlanOption(
                        label = stringRes(R.string.paywall__plan_monthly),
                        offer = ui.monthly,
                        badge = null,
                        selected = ui.selected == Plan.MONTHLY,
                        onSelect = { actions.onSelect(Plan.MONTHLY) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        // The footer never scrolls away: the terms sit right above the CTA in plain text (FR-29 AC2).
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ui.trialTerms?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = actions.onPurchase,
                enabled = ui.ctaEnabled,
                modifier = Modifier.fillMaxWidth().testTag(PAYWALL_CTA_TAG),
            ) {
                val trial = ui.selected == Plan.ANNUAL && ui.annual?.trialDays != null
                Text(stringRes(if (trial) R.string.paywall__cta_trial else R.string.paywall__cta))
            }
            if (ui.restoreNotFound) {
                Text(stringRes(R.string.paywall__restore_not_found), style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = actions.onRestore, enabled = !ui.restoring) {
                    Text(stringRes(R.string.paywall__restore))
                }
                TextButton(onClick = actions.onTerms) { Text(stringRes(R.string.paywall__terms)) }
                TextButton(onClick = actions.onPrivacy) { Text(stringRes(R.string.paywall__privacy)) }
            }
        }
    }
}

@Composable
private fun PlanOption(label: String, offer: ProOffer?, badge: String?, selected: Boolean, onSelect: () -> Unit) {
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    OutlinedCard(
        border = border,
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, enabled = offer != null, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                // Play's formatted price only; never a hard-coded amount (FR-29 AC3).
                offer?.let { Text(it.formattedPrice, style = MaterialTheme.typography.bodyMedium) }
            }
            badge?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
