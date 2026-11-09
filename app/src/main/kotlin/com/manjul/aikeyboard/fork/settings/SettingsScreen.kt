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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.core.net.toUri
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.LocalNavController
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes

/** The Settings tree root (PRD FR-09): one row per [SettingsSection], in the PRD order. */
@Composable
fun SettingsScreen() = FlorisScreen {
    title = stringRes(R.string.settings_tree__title)
    previewFieldVisible = false
    scrollable = false

    val navController = LocalNavController.current

    content {
        SettingsContent(onOpen = { section -> navController.navigate(section.deepLink.toUri()) })
    }
}

@Composable
internal fun SettingsContent(onOpen: (SettingsSection) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        for (section in SettingsSection.entries) {
            ListItem(
                modifier = Modifier.clickable(role = Role.Button) { onOpen(section) },
                headlineContent = { Text(stringRes(section.title)) },
            )
        }
    }
}
