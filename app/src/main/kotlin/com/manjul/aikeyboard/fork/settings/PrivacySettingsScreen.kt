/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.LocalNavController
import com.manjul.aikeyboard.app.Routes
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.ConsentKind
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.fork.privacy.consentWithdrawnEvents
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import com.manjul.aikeyboard.lib.util.launchUrl
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/** The four consent classes of RN-09, by their PRD names. Each can be withdrawn here. */
internal val CONSENT_CLASSES: List<String> =
    listOf("consent_ai", "consent_cloud_translate", "consent_auto_polish", "consent_voice_notice")

internal fun consentRowTag(cls: String) = "consent_row:$cls"

internal fun consentWithdrawTag(cls: String) = "consent_withdraw:$cls"

internal const val CONSENT_WITHDRAW_CONFIRM_TAG = "consent_withdraw_confirm"

/** When the user agreed to [cls] (epoch ms); 0 = no consent on record. */
internal fun consentAt(settings: FeatureSettings, cls: String): Long = when (cls) {
    "consent_ai" -> settings.consentAiAt
    "consent_cloud_translate" -> settings.consentCloudTranslateAt
    "consent_auto_polish" -> settings.consentAutoPolishAt
    "consent_voice_notice" -> settings.consentVoiceNoticeAt
    else -> 0L
}

/**
 * [settings] with [cls] withdrawn: its time stamp goes to 0 (RN-09), and the feature it gates turns off. AI consent
 * also clears auto-polish, which needs it ([FeatureSettings.withAiConsentWithdrawn]).
 */
internal fun withConsentWithdrawn(settings: FeatureSettings, cls: String): FeatureSettings = when (cls) {
    "consent_ai" -> settings.withAiConsentWithdrawn()
    "consent_cloud_translate" -> settings.copy(consentCloudTranslateAt = 0L, cloudTranslateOptIn = false)
    "consent_auto_polish" -> settings.copy(consentAutoPolishAt = 0L, autoPolish = false)
    "consent_voice_notice" -> settings.copy(consentVoiceNoticeAt = 0L)
    else -> settings
}

/**
 * The `consent_withdrawn` events for withdrawing [cls] from [before]. The catalog has kinds for the AI consents
 * only, so cloud translate and the voice notice log nothing.
 */
internal fun consentWithdrawnEventsFor(before: FeatureSettings, cls: String): List<AnalyticsEvent> = when (cls) {
    "consent_ai" -> consentWithdrawnEvents(before)
    "consent_auto_polish" ->
        if (before.consentAutoPolishAt > 0L) {
            listOf(EventCatalog.consentWithdrawn(ConsentKind.AUTO_POLISH))
        } else {
            emptyList()
        }
    else -> emptyList()
}

/**
 * Settings → Privacy (PRD FR-09, FR-35, RN-09): private apps, the analytics opt-out, the four consent classes with
 * Withdraw, "Ad privacy options" when UMP requires it, Clear data and Delete my data (Task 12 hooks) and the data
 * policy. [adPrivacy] comes from `app.ads` through `Routes.kt`; this package never touches the ads stack.
 */
@Composable
fun PrivacySettingsScreen(adPrivacy: AdPrivacyOptions) = FlorisScreen {
    title = stringRes(R.string.privacy_settings__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val store = remember { context.featureSettingsStore().value }
    val scope = rememberCoroutineScope()
    val settings by store.flow.collectAsState(initial = store.current)

    content {
        PrivacySettingsContent(
            settings = settings,
            adPrivacy = adPrivacy,
            onUpdate = { transform -> scope.launch { store.update(transform) } },
            onEvents = { events -> events.forEach(context.appContext().value.analytics.value::log) },
            onPrivateApps = { navController.navigate(Routes.Settings.PrivateApps) },
            // TODO(S4 Task 12): real Clear data and Delete my data flows.
            onClearData = { comingSoon(context) },
            onDeleteData = { comingSoon(context) },
            onDataPolicy = { context.launchUrl(R.string.privacy_policy_url) },
        )
    }
}

@Composable
internal fun PrivacySettingsContent(
    settings: FeatureSettings,
    adPrivacy: AdPrivacyOptions,
    onUpdate: ((FeatureSettings) -> FeatureSettings) -> Unit,
    onEvents: (List<AnalyticsEvent>) -> Unit,
    onPrivateApps: () -> Unit,
    onClearData: () -> Unit,
    onDeleteData: () -> Unit,
    onDataPolicy: () -> Unit,
) {
    val context = LocalContext.current
    val adPrivacyRequired by adPrivacy.required.collectAsState()
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, onClick = onPrivateApps),
            headlineContent = { Text(stringRes(R.string.private_apps__title)) },
        )
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

        SettingsHeader(stringRes(R.string.privacy_settings__consents))
        for (cls in CONSENT_CLASSES) {
            val given = consentAt(settings, cls) > 0L
            ListItem(
                modifier = Modifier.testTag(consentRowTag(cls)),
                headlineContent = { Text(stringRes(consentLabel(cls))) },
                supportingContent = {
                    val label = if (given) R.string.privacy_settings__agreed else R.string.privacy_settings__not_agreed
                    Text(stringRes(label))
                },
                trailingContent = {
                    TextButton(
                        modifier = Modifier.testTag(consentWithdrawTag(cls)),
                        enabled = given,
                        onClick = { confirm = cls },
                    ) { Text(stringRes(R.string.privacy__withdraw)) }
                },
            )
        }

        if (adPrivacyRequired) {
            ListItem(
                modifier = Modifier.clickable(role = Role.Button) { context.findActivity()?.let(adPrivacy::show) },
                headlineContent = { Text(stringRes(R.string.privacy_settings__ad_privacy)) },
                supportingContent = { Text(stringRes(R.string.privacy_settings__ad_privacy_summary)) },
            )
        }

        SettingsHeader(stringRes(R.string.privacy_settings__your_data))
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, onClick = onClearData),
            headlineContent = { Text(stringRes(R.string.privacy_settings__clear_data)) },
            supportingContent = { Text(stringRes(R.string.privacy_settings__clear_data_summary)) },
        )
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, onClick = onDeleteData),
            headlineContent = { Text(stringRes(R.string.privacy_settings__delete_data)) },
            supportingContent = { Text(stringRes(R.string.privacy_settings__delete_data_summary)) },
        )
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, onClick = onDataPolicy),
            headlineContent = { Text(stringRes(R.string.privacy_settings__data_policy)) },
        )
    }

    val cls = confirm
    if (cls != null && consentAt(settings, cls) > 0L) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(stringRes(R.string.privacy_settings__withdraw_confirm)) },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag(CONSENT_WITHDRAW_CONFIRM_TAG),
                    onClick = {
                        confirm = null
                        onEvents(consentWithdrawnEventsFor(settings, cls))
                        onUpdate { withConsentWithdrawn(it, cls) }
                    },
                ) { Text(stringRes(R.string.privacy__withdraw)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringRes(R.string.privacy__cancel)) }
            },
        )
    }
}

private fun consentLabel(cls: String): Int = when (cls) {
    "consent_ai" -> R.string.privacy_settings__consent_ai
    "consent_cloud_translate" -> R.string.privacy_settings__consent_cloud_translate
    "consent_auto_polish" -> R.string.privacy_settings__consent_auto_polish
    else -> R.string.privacy_settings__consent_voice_notice
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun comingSoon(context: Context) {
    Toast.makeText(context, R.string.privacy_settings__coming_soon, Toast.LENGTH_SHORT).show()
}
