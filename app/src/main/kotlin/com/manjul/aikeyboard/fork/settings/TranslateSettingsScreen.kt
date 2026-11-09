/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.LocalNavController
import com.manjul.aikeyboard.app.Routes
import com.manjul.aikeyboard.core.datastore.BarMode
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.datastore.LanguagePair
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.fork.processtext.displayName
import com.manjul.aikeyboard.fork.translator.TranslatorViewModel
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import java.util.Locale
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/**
 * Settings → Translate (PRD FR-09): the default pair, the bar's Preview or Live mode, Wi-Fi only downloads,
 * translation history and a link to Offline languages. There is no cloud tier row (OD-05: on-device only).
 */
@Composable
fun TranslateSettingsScreen() = FlorisScreen {
    title = stringRes(R.string.translate_settings__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val navController = LocalNavController.current
    val store = remember { context.featureSettingsStore().value }
    val scope = rememberCoroutineScope()
    val settings by store.flow.collectAsState(initial = store.current)

    content {
        TranslateSettingsContent(
            settings = settings,
            onUpdate = { transform -> scope.launch { store.update(transform) } },
            onOfflinePacks = { navController.navigate(Routes.Settings.OfflineLanguages) },
        )
    }
}

/** The pair shown and edited here: the saved one, else the Translator's default for the device language. */
internal fun effectivePair(settings: FeatureSettings, locale: Locale = Locale.getDefault()): LanguagePair =
    settings.translatePair
        ?: TranslatorViewModel.defaultPairFor(locale).let { LanguagePair(it.source.tag, it.target.tag) }

/** [pair] with one side set to [tag]. Picking the other side's language swaps the two. */
internal fun pairWith(pair: LanguagePair, tag: String, target: Boolean): LanguagePair = when {
    target && tag == pair.source -> LanguagePair(pair.target, pair.source)
    target -> pair.copy(target = tag)
    tag == pair.target -> LanguagePair(pair.target, pair.source)
    else -> pair.copy(source = tag)
}

private enum class PairSide { SOURCE, TARGET }

@Composable
internal fun TranslateSettingsContent(
    settings: FeatureSettings,
    onUpdate: ((FeatureSettings) -> FeatureSettings) -> Unit,
    onOfflinePacks: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf<PairSide?>(null) }
    val pair = effectivePair(settings)
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsHeader(stringRes(R.string.translate_settings__default_pair))
        ListItem(
            modifier = Modifier.clickable(role = Role.Button) { picking = PairSide.SOURCE },
            headlineContent = { Text(stringRes(R.string.translate_settings__from)) },
            supportingContent = { Text(displayName(Lang.of(pair.source))) },
        )
        ListItem(
            modifier = Modifier.clickable(role = Role.Button) { picking = PairSide.TARGET },
            headlineContent = { Text(stringRes(R.string.translate_settings__to)) },
            supportingContent = { Text(displayName(Lang.of(pair.target))) },
        )

        val live = settings.barMode == BarMode.LIVE
        SettingsSwitchRow(
            title = stringRes(R.string.translate_settings__live),
            summary = stringRes(R.string.translate_settings__live_summary),
            checked = live,
            onChange = { on ->
                val mode = if (on) BarMode.LIVE else BarMode.PREVIEW
                onUpdate { it.copy(barMode = mode, barLiveComposingSet = true) }
            },
        )
        SettingsSwitchRow(
            title = stringRes(R.string.translate_settings__wifi_only),
            summary = null,
            checked = settings.translateWifiOnly,
            // Same switch as on Offline languages: one Wi-Fi rule for translation packs and voice models.
            onChange = { on -> onUpdate { it.copy(translateWifiOnly = on, whisperWifiOnly = on) } },
        )
        SettingsSwitchRow(
            title = stringRes(R.string.translate_settings__history),
            summary = stringRes(R.string.translate_settings__history_summary),
            checked = settings.translateHistoryEnabled,
            onChange = { on -> onUpdate { it.copy(translateHistoryEnabled = on) } },
        )
        ListItem(
            modifier = Modifier.clickable(role = Role.Button, onClick = onOfflinePacks),
            headlineContent = { Text(stringRes(R.string.offline_languages__title)) },
            supportingContent = { Text(stringRes(R.string.translate_settings__offline_summary)) },
        )
    }

    val side = picking
    if (side != null) {
        val current = if (side == PairSide.TARGET) pair.target else pair.source
        AlertDialog(
            onDismissRequest = { picking = null },
            title = {
                val title =
                    if (side == PairSide.TARGET) R.string.translate_settings__to else R.string.translate_settings__from
                Text(stringRes(title))
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    for (tag in FeatureSettings.MVP_LANGUAGES) {
                        ListItem(
                            modifier = Modifier.selectable(
                                selected = tag == current,
                                role = Role.RadioButton,
                                onClick = {
                                    picking = null
                                    val next = pairWith(pair, tag, target = side == PairSide.TARGET)
                                    onUpdate { it.copy(translatePair = next) }
                                },
                            ),
                            headlineContent = { Text(displayName(Lang.of(tag))) },
                            leadingContent = { RadioButton(selected = tag == current, onClick = null) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = null }) { Text(stringRes(R.string.privacy__cancel)) }
            },
        )
    }
}

@Composable
internal fun SettingsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
internal fun SettingsSwitchRow(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}
