/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.aiAppGraph
import com.manjul.aikeyboard.app.LocalNavController
import com.manjul.aikeyboard.app.Routes
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.core.ai.kb.QuotaResult
import com.manjul.aikeyboard.core.ai.kb.QuotaStatus
import com.manjul.aikeyboard.core.analytics.ConsentKind
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.fork.ai.AiConsentActivity
import com.manjul.aikeyboard.fork.paywall.PaywallTrigger
import com.manjul.aikeyboard.fork.privacy.consentWithdrawnEvents
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/** What turning auto-polish [on] does (plan decision 13). */
internal enum class AutoPolishChange { NONE, LAUNCH_CONSENT, TURN_ON, TURN_OFF }

/**
 * Auto-polish needs Pro ([isPro], always false until billing) and its own consent: the first enable opens the
 * `consent_auto_polish` screen, whose Agree turns it on. Turning it off needs nothing.
 */
internal fun autoPolishChange(settings: FeatureSettings, on: Boolean, isPro: Boolean): AutoPolishChange = when {
    !on -> if (settings.autoPolish) AutoPolishChange.TURN_OFF else AutoPolishChange.NONE
    !isPro -> AutoPolishChange.NONE
    settings.consentAutoPolishAt <= 0L -> AutoPolishChange.LAUNCH_CONSENT
    else -> AutoPolishChange.TURN_ON
}

/**
 * AI settings (Sprint 3 Task 14, Settings → AI in S4): what is left of today's AI quota, the default ✦ tone, the
 * ✦ Polish output script, "Auto-polish every dictation" (Pro; a free user goes to the paywall) and "Withdraw AI
 * consent" (same as on the Private apps screen). The quota is asked of kb-api only after `consentAiAt` is set.
 */
@Composable
fun AiSettingsScreen(isPro: () -> Boolean = { false }) = FlorisScreen {
    title = stringRes(R.string.ai_settings__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val store = remember { context.featureSettingsStore().value }
    val scope = rememberCoroutineScope()
    val settings by store.flow.collectAsState(initial = store.current)
    val navController = LocalNavController.current
    var quota by remember { mutableStateOf<QuotaStatus?>(null) }
    val consented = settings.consentAiAt > 0L
    LaunchedEffect(consented) {
        // Nothing goes to kb-api before AI consent (PRD §10.6). Only the auth headers are sent.
        quota = if (!consented) {
            null
        } else {
            // The graph's lazy client shares AiAppGraph's OkHttp client. Leaving the screen cancels the call.
            (context.aiAppGraph().value.quota.value?.status() as? QuotaResult.Ok)?.status
        }
    }

    content {
        AiSettingsContent(
            settings = settings,
            isPro = isPro(),
            quota = quota,
            onAutoPolishPaywall = { navController.navigate(Routes.Paywall(PaywallTrigger.AUTO_POLISH.wire)) },
            onUpdate = { transform -> scope.launch { store.update(transform) } },
            onAutoPolish = { on ->
                when (autoPolishChange(store.current, on, isPro())) {
                    AutoPolishChange.NONE -> Unit
                    AutoPolishChange.LAUNCH_CONSENT ->
                        context.startActivity(AiConsentActivity.intent(context, ConsentKind.AUTO_POLISH))
                    AutoPolishChange.TURN_ON -> scope.launch { store.update { it.copy(autoPolish = true) } }
                    AutoPolishChange.TURN_OFF -> scope.launch { store.update { it.copy(autoPolish = false) } }
                }
            },
            onWithdrawAiConsent = {
                scope.launch {
                    var before = FeatureSettings()
                    store.update { before = it; it.withAiConsentWithdrawn() }
                    consentWithdrawnEvents(before).forEach(context.appContext().value.analytics.value::log)
                }
            },
        )
    }
}

@Composable
internal fun AiSettingsContent(
    settings: FeatureSettings,
    isPro: Boolean,
    onUpdate: ((FeatureSettings) -> FeatureSettings) -> Unit,
    onAutoPolish: (Boolean) -> Unit,
    onWithdrawAiConsent: () -> Unit,
    quota: QuotaStatus? = null,
    onAutoPolishPaywall: () -> Unit = {},
) {
    var confirmWithdraw by rememberSaveable { mutableStateOf(false) }
    val script = FeatureSettings.normalizeOutputScript(settings.polishOutputScript)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (quota != null) {
            ListItem(
                headlineContent = {
                    val left = "left" to quota.aiLeftToday
                    val total = quotaTotal(quota)
                    Text(
                        if (total != null) {
                            stringRes(R.string.ai_settings__quota, left, "total" to total)
                        } else {
                            stringRes(R.string.ai_settings__quota_left, left)
                        },
                    )
                },
            )
        }

        Text(
            text = stringRes(R.string.ai_settings__output_script),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            text = stringRes(R.string.ai_settings__output_script_summary),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        for (option in FeatureSettings.OUTPUT_SCRIPTS) {
            val selected = option == script
            ListItem(
                modifier = Modifier.selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onUpdate { it.copy(polishOutputScript = option) } },
                ),
                headlineContent = { Text(stringRes(scriptLabel(option))) },
                leadingContent = { RadioButton(selected = selected, onClick = null) },
            )
        }

        val autoOn = settings.autoPolish && settings.consentAutoPolishAt > 0L
        ListItem(
            modifier = Modifier.toggleable(
                value = autoOn,
                role = Role.Switch,
                // A free user turning it on goes to the paywall (auto_polish trigger); turning off always works.
                onValueChange = { on -> if (on && !isPro) onAutoPolishPaywall() else onAutoPolish(on) },
            ),
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringRes(R.string.ai_settings__auto_polish))
                    Text(
                        text = stringRes(R.string.ai_settings__pro),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            },
            supportingContent = {
                Text(
                    stringRes(
                        if (isPro) R.string.ai_settings__auto_polish_summary else R.string.ai_settings__auto_polish_pro_only,
                    ),
                )
            },
            trailingContent = { Switch(checked = autoOn, onCheckedChange = null) },
        )

        Text(
            text = stringRes(R.string.ai_settings__default_tone),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val tone = FeatureSettings.normalizeAiTone(settings.aiDefaultTone)
        for (option in FeatureSettings.AI_TONES) {
            val selected = option == tone
            ListItem(
                modifier = Modifier.selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onUpdate { it.copy(aiDefaultTone = option) } },
                ),
                headlineContent = { Text(stringRes(toneLabel(option))) },
                leadingContent = { RadioButton(selected = selected, onClick = null) },
            )
        }

        val given = settings.hasAnyAiConsent
        ListItem(
            modifier = Modifier.clickable(enabled = given) { confirmWithdraw = true },
            headlineContent = { Text(stringRes(R.string.privacy__withdraw_ai)) },
            supportingContent = {
                Text(stringRes(if (given) R.string.privacy__withdraw_ai_summary else R.string.privacy__withdraw_ai_none))
            },
        )
    }

    if (confirmWithdraw && settings.hasAnyAiConsent) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            text = { Text(stringRes(R.string.privacy__withdraw_ai_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmWithdraw = false
                        onWithdrawAiConsent()
                    },
                ) { Text(stringRes(R.string.privacy__withdraw)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmWithdraw = false }) { Text(stringRes(R.string.privacy__cancel)) }
            },
        )
    }
}

/**
 * The total for the quota row: the server's `ai_daily_limit`, never less than what is left (so the row never reads
 * "15 of 10"). Null when the server sent no limit: the row then shows only what is left.
 */
internal fun quotaTotal(quota: QuotaStatus): Int? = quota.dailyTotal?.let { maxOf(it, quota.aiLeftToday) }

private fun toneLabel(wire: String): Int = when (wire) {
    "formal" -> R.string.ai_settings__tone_formal
    "friendly" -> R.string.ai_settings__tone_friendly
    "shorter" -> R.string.ai_settings__tone_shorter
    "longer" -> R.string.ai_settings__tone_longer
    "emojify" -> R.string.ai_settings__tone_emojify
    else -> R.string.ai_settings__tone_fix
}

private fun scriptLabel(wire: String): Int = when (wire) {
    "ROMAN" -> R.string.ai_settings__script_roman
    "NATIVE" -> R.string.ai_settings__script_native
    else -> R.string.ai_settings__script_same
}
