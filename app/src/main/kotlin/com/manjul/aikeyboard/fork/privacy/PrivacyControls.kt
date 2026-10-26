/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.ConsentKind
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import org.florisboard.lib.compose.stringRes

/** `consent_withdrawn` events for the consents [before] had on record, logged when the user withdraws AI consent. */
internal fun consentWithdrawnEvents(before: FeatureSettings): List<AnalyticsEvent> = buildList {
    if (before.consentAiAt > 0L) add(EventCatalog.consentWithdrawn(ConsentKind.AI))
    if (before.consentAutoPolishAt > 0L) add(EventCatalog.consentWithdrawn(ConsentKind.AUTO_POLISH))
}

/**
 * Privacy rows above the private-app list (PRD FR-35, §10.6): "Share usage analytics" (the inverse of
 * [FeatureSettings.analyticsOptOut]; off stops every event at once) and "Withdraw AI consent", which asks first and
 * then clears `consent_ai`, `consent_auto_polish` and auto-polish. It is disabled while no AI consent is on record.
 */
internal fun LazyListScope.privacyControls(
    settings: FeatureSettings,
    onUpdate: ((FeatureSettings) -> FeatureSettings) -> Unit,
    onWithdrawAiConsent: () -> Unit,
) {
    item(key = "privacy:header") {
        Text(
            text = stringRes(R.string.privacy__data_header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    item(key = "privacy:analytics") {
        val share = !settings.analyticsOptOut
        ListItem(
            modifier = Modifier.toggleable(
                value = share,
                role = Role.Switch,
                onValueChange = { on -> onUpdate { it.copy(analyticsOptOut = !on) } },
            ),
            headlineContent = { Text(stringRes(R.string.privacy__share_analytics)) },
            supportingContent = { Text(stringRes(R.string.privacy__share_analytics_summary)) },
            trailingContent = { Switch(checked = share, onCheckedChange = null) },
        )
    }
    item(key = "privacy:ai_consent") {
        var confirm by rememberSaveable { mutableStateOf(false) }
        val given = settings.hasAnyAiConsent
        ListItem(
            modifier = Modifier.clickable(enabled = given) { confirm = true },
            headlineContent = { Text(stringRes(R.string.privacy__withdraw_ai)) },
            supportingContent = {
                Text(
                    stringRes(
                        if (given) R.string.privacy__withdraw_ai_summary else R.string.privacy__withdraw_ai_none,
                    ),
                )
            },
        )
        if (confirm && given) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                text = { Text(stringRes(R.string.privacy__withdraw_ai_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirm = false
                            onWithdrawAiConsent()
                        },
                    ) { Text(stringRes(R.string.privacy__withdraw)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirm = false }) { Text(stringRes(R.string.privacy__cancel)) }
                },
            )
        }
    }
}
