/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.fork.billing.PlayLinks
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.stringRes

internal const val DELETE_EVERYTHING_TAG = "delete_data:everything"
internal const val CLEAR_ON_DEVICE_TAG = "delete_data:device"
internal const val DELETE_CONFIRM_TAG = "delete_data:confirm"

private enum class Pending { EVERYTHING, DEVICE }

/**
 * Settings → Privacy → Delete my data (S4 Task 12), opened by both Clear data and Delete my data. [clearAdState]
 * comes from `app.ads` through `Routes.kt`; this package never touches the ads stack.
 */
@Composable
fun DeleteDataScreen(clearAdState: suspend () -> Unit) = FlorisScreen {
    title = stringRes(R.string.delete_data__title)
    previewFieldVisible = false
    scrollable = false

    val context = LocalContext.current
    val holder = viewModel<DeleteDataScreenModel>(
        factory = viewModelFactory { initializer { DeleteDataScreenModel(context, clearAdState) } },
    )
    val vm = holder.controller
    val state by vm.state.collectAsState()

    content {
        var includeModels by rememberSaveable { mutableStateOf(false) }
        var pending by rememberSaveable { mutableStateOf<Pending?>(null) }
        val working = state == DeleteDataState.Working

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringRes(R.string.delete_data__body))
            ListItem(
                modifier = Modifier.toggleable(
                    value = includeModels,
                    enabled = !working,
                    role = Role.Checkbox,
                    onValueChange = { includeModels = it },
                ),
                headlineContent = { Text(stringRes(R.string.delete_data__include_models)) },
                leadingContent = { Checkbox(checked = includeModels, onCheckedChange = null, enabled = !working) },
            )
            Button(
                modifier = Modifier.fillMaxWidth().testTag(DELETE_EVERYTHING_TAG),
                enabled = !working,
                onClick = { pending = Pending.EVERYTHING },
            ) { Text(stringRes(R.string.delete_data__delete_everything)) }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(CLEAR_ON_DEVICE_TAG),
                enabled = !working,
                onClick = { pending = Pending.DEVICE },
            ) { Text(stringRes(R.string.delete_data__clear_on_device)) }

            val status = when (val s = state) {
                DeleteDataState.Idle -> null
                DeleteDataState.Working -> R.string.delete_data__working
                DeleteDataState.Failed -> R.string.delete_data__error
                is DeleteDataState.Done ->
                    if (s.serverDeleted) R.string.delete_data__done_server else R.string.delete_data__done_device
            }
            if (status != null) {
                val color = if (state == DeleteDataState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                Text(stringRes(status), color = color, modifier = Modifier.padding(top = 16.dp))
            }

            Text(stringRes(R.string.delete_data__subscription_note), modifier = Modifier.padding(top = 24.dp))
            ListItem(
                modifier = Modifier.clickable(role = Role.Button) { PlayLinks.openManageSubscription(context) },
                headlineContent = {
                    Text(
                        stringRes(R.string.delete_data__manage_subscription),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
            )
        }

        val action = pending
        if (action != null) {
            val text = if (action == Pending.EVERYTHING) {
                R.string.delete_data__confirm_everything
            } else {
                R.string.delete_data__confirm_device
            }
            AlertDialog(
                onDismissRequest = { pending = null },
                text = { Text(stringRes(text)) },
                confirmButton = {
                    TextButton(
                        modifier = Modifier.testTag(DELETE_CONFIRM_TAG),
                        onClick = {
                            pending = null
                            if (action == Pending.EVERYTHING) {
                                vm.deleteEverything(includeModels)
                            } else {
                                vm.clearOnDevice(includeModels)
                            }
                        },
                    ) { Text(stringRes(R.string.delete_data__confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pending = null }) { Text(stringRes(R.string.delete_data__cancel)) }
                },
            )
        }
    }
}
