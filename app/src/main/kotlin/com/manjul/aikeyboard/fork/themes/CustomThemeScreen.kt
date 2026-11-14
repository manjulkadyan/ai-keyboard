/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.FlorisPreferenceModel
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.core.theme.CustomTheme
import com.manjul.aikeyboard.core.theme.CustomTheme.Element
import com.manjul.aikeyboard.core.theme.SnyggGenerator
import com.manjul.aikeyboard.core.theme.ThemeSpec
import com.manjul.aikeyboard.extensionManager
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.fork.themes.color.HsvColorPicker
import com.manjul.aikeyboard.ime.theme.ThemeExtensionComponentImpl
import com.manjul.aikeyboard.ime.theme.ThemeManager
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import com.manjul.aikeyboard.lib.ext.ExtensionComponentName
import com.manjul.aikeyboard.themeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.SnyggStylesheet

/** Keeps the editor state across configuration changes. */
internal class CustomThemeHolder(context: Context) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs: FlorisPreferenceModel by FlorisPreferenceStore
    private val themeManager = context.themeManager().value
    private val installer = CustomThemeInstaller(
        workRoot = context.cacheDir,
        importExtension = context.extensionManager().value::import,
        prefs = prefs,
    )
    val vm = CustomThemeViewModel(
        settings = context.featureSettingsStore().value,
        scope = scope,
        preview = { spec -> themeManager.previewThemeInfo.value = spec?.let(::previewInfo) },
        install = { spec ->
            installer.install(spec)
            // The saved theme is now the active one, so the unsaved preview can go.
            themeManager.previewThemeInfo.value = null
        },
    )

    override fun onCleared() {
        vm.clearPreview()
        scope.cancel()
    }

    /**
     * An unsaved theme has no extension yet, so it cannot be previewed by id (`previewThemeId`). The upstream
     * `previewThemeInfo` takes the parsed stylesheet directly.
     */
    private fun previewInfo(spec: ThemeSpec): ThemeManager.ThemeInfo? {
        val stylesheet = SnyggStylesheet.fromJson(SnyggGenerator.stylesheet(spec, keyBorder = true)).getOrNull()
            ?: return null
        return ThemeManager.ThemeInfo.DEFAULT.copy(
            name = ExtensionComponentName(CustomTheme.EXTENSION_ID, CustomTheme.STYLESHEET_ID),
            config = ThemeExtensionComponentImpl(
                id = CustomTheme.STYLESHEET_ID,
                label = "",
                authors = emptyList(),
                isNightTheme = SnyggGenerator.isNight(spec),
            ),
            stylesheet = stylesheet,
        )
    }
}

private val Element.label: Int
    get() = when (this) {
        Element.KEYS -> R.string.custom_theme__element_keys
        Element.KEY_TEXT -> R.string.custom_theme__element_key_text
        Element.BACKGROUND -> R.string.custom_theme__element_background
        Element.ACCENT -> R.string.custom_theme__element_accent
    }

/**
 * The "Custom colours" theme editor (S5 Task 8, route `themes/custom`, opened from Themes only). The user picks
 * keys, key text, background and accent with the HSV picker and sees each change in the preview field. Save
 * writes and applies the theme. The theme is free.
 */
@Composable
fun CustomThemeScreen() = FlorisScreen {
    title = stringRes(R.string.custom_theme__title)
    previewFieldVisible = true

    val appContext = LocalContext.current.applicationContext
    val holder = viewModel<CustomThemeHolder>(
        key = "custom_theme",
        factory = viewModelFactory { initializer { CustomThemeHolder(appContext) } },
    )
    val vm = holder.vm

    content {
        val state by vm.state.collectAsState()
        DisposableEffect(Unit) {
            vm.showPreview()
            onDispose { vm.clearPreview() }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (element in Element.entries) {
                    FilterChip(
                        selected = element == state.selected,
                        onClick = { vm.select(element) },
                        label = { Text(stringRes(element.label)) },
                    )
                }
            }
            HsvColorPicker(
                color = state.color,
                recents = state.recents,
                onColorChange = vm::pick,
                eyedropper = null,
            )
            if (state.lowContrast) {
                Text(
                    stringRes(R.string.custom_theme__low_contrast),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = vm::done) {
                    Text(stringRes(R.string.custom_theme__done))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = state.saveStatus != SaveStatus.SAVING,
                    onClick = vm::save,
                ) { Text(stringRes(R.string.custom_theme__save)) }
            }
            val status = when (state.saveStatus) {
                SaveStatus.SAVED -> R.string.custom_theme__saved
                SaveStatus.FAILED -> R.string.custom_theme__failed
                else -> null
            }
            if (status != null) {
                Text(
                    stringRes(status),
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    color = if (state.saveStatus == SaveStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
