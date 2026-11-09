/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.core.net.toUri
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.FlorisPreferenceModel
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.app.LocalNavController
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.core.datastore.isProAt
import com.manjul.aikeyboard.core.theme.SnyggGenerator
import com.manjul.aikeyboard.core.theme.ThemePreset
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import com.manjul.aikeyboard.themeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import org.florisboard.lib.compose.stringRes

/** Keeps the view model, and so the Pro observer for a pending premium theme, while the paywall is on top. */
internal class ThemesHolder(context: Context) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs: FlorisPreferenceModel by FlorisPreferenceStore
    private val store = context.featureSettingsStore().value
    val vm = ThemesViewModel(
        prefs = prefs,
        settings = store,
        isPro = store.flow.map { it.isProAt(System.currentTimeMillis()) },
        analytics = context.appContext().value.analytics.value::log,
        previewThemeId = context.themeManager().value.previewThemeId,
        scope = scope,
    )

    override fun onCleared() {
        vm.clearPreview()
        scope.cancel()
    }
}

/**
 * The Themes screen (S4 Task 10, route `themes`): the presets of this device, a key border switch and the key
 * sound. Tapping a preset previews it in the upstream preview field below the screen; Apply sets it for day and
 * night. A premium preset without Pro opens the paywall and is applied when Pro turns on.
 */
@Composable
fun ThemesScreen() = FlorisScreen {
    title = stringRes(R.string.themes__title)
    previewFieldVisible = true

    val context = LocalContext.current
    val navController = LocalNavController.current
    val appContext = context.applicationContext
    val holder = viewModel<ThemesHolder>(
        key = "themes",
        factory = viewModelFactory { initializer { ThemesHolder(appContext) } },
    )
    val vm = holder.vm

    content {
        val prefs by FlorisPreferenceStore
        val store = context.featureSettingsStore().value
        val settings by store.flow.collectAsState(initial = store.current)
        val dayTheme by prefs.theme.dayThemeId.asFlow().collectAsState()
        val active = dayTheme.takeIf { it.extensionId == SnyggGenerator.EXTENSION_ID }?.componentId
        var keyBorder by rememberSaveable { mutableStateOf(active?.endsWith(SnyggGenerator.BORDERLESS_SUFFIX) != true) }
        var selected by rememberSaveable {
            mutableStateOf(active?.removeSuffix(SnyggGenerator.BORDERLESS_SUFFIX) ?: vm.presets.first().id)
        }
        val isPro = settings.isProAt(System.currentTimeMillis())

        DisposableEffect(selected, keyBorder) {
            vm.select(selected, keyBorder)
            onDispose { vm.clearPreview() }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in vm.presets.chunked(2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (preset in row) {
                        PresetCard(
                            preset = preset,
                            selected = preset.id == selected,
                            locked = preset.premium && !isPro,
                            modifier = Modifier.weight(1f),
                            onClick = { selected = preset.id },
                        )
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        ListItem(
            modifier = Modifier.toggleable(value = keyBorder, role = Role.Switch) { keyBorder = it },
            headlineContent = { Text(stringRes(R.string.themes__key_border)) },
            trailingContent = { Switch(checked = keyBorder, onCheckedChange = null) },
        )
        Button(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onClick = {
                when (val result = vm.apply(selected, keyBorder)) {
                    ApplyResult.Applied -> Unit
                    is ApplyResult.NeedsPro -> navController.navigate("ui://florisboard/${result.route}".toUri())
                }
            },
        ) { Text(stringRes(R.string.themes__apply)) }
        Text(
            stringRes(R.string.themes__key_sound),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp),
        )
        val current = KeySound.fromWire(settings.keySound)
        Column(Modifier.selectableGroup()) {
            for (sound in KeySound.entries) {
                ListItem(
                    modifier = Modifier.selectable(selected = sound == current, role = Role.RadioButton) {
                        vm.setKeySound(sound)
                    },
                    headlineContent = { Text(stringRes(sound.label)) },
                    leadingContent = { RadioButton(selected = sound == current, onClick = null) },
                )
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: ThemePreset,
    selected: Boolean,
    locked: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val border = if (selected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    OutlinedCard(
        border = border,
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).background(
                    Color(preset.spec.colors.keyboardBackground.toInt()),
                    RoundedCornerShape(6.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(14.dp).background(Color(preset.spec.colors.key.toInt()), RoundedCornerShape(3.dp)))
            }
            Text(
                SnyggGenerator.label(preset.id),
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (locked) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = stringRes(R.string.themes__premium),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
