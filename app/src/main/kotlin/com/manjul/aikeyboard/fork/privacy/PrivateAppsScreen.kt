/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.stringRes

/** Settings > Privacy > Private apps: every field in a toggled app is treated as sensitive. */
@Composable
fun PrivateAppsScreen() = FlorisScreen {
    title = stringRes(R.string.private_apps__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val repository = remember { context.privateAppsRepository() }
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var suggestions by remember { mutableStateOf(emptyList<AppEntry>()) }
    LaunchedEffect(repository) {
        withContext(Dispatchers.IO) {
            apps = repository.launchableApps()
            suggestions = repository.suggestions()
        }
    }
    val settings by repository.privatePackages.collectAsState(initial = FeatureSettings())
    val privatePackages = settings.privatePackages

    content {
        @Composable
        fun AppRow(app: AppEntry) {
            val checked = app.packageName in privatePackages
            ListItem(
                modifier = Modifier.toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = { on ->
                        scope.launch {
                            if (on) repository.add(app.packageName) else repository.remove(app.packageName)
                        }
                    },
                ),
                headlineContent = { Text(app.label) },
                trailingContent = { Switch(checked = checked, onCheckedChange = null) },
            )
        }

        @Composable
        fun Header(text: String) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = stringRes(R.string.private_apps__summary),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (suggestions.isNotEmpty()) {
                item { Header(stringRes(R.string.private_apps__suggested)) }
                items(suggestions, key = { "s:" + it.packageName }) { AppRow(it) }
            }
            item { Header(stringRes(R.string.private_apps__all_apps)) }
            items(apps, key = { it.packageName }) { AppRow(it) }
        }
    }
}
