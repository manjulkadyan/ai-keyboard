/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.update

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.core.common.entry.EntrySource
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.firebaseGraph
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

/** The process-wide controller, so "ready to restart" survives leaving and re-entering Home. */
private object UpdateControllerHolder {
    @Volatile
    private var instance: UpdateController? = null

    fun get(context: Context): UpdateController = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(app: Context): UpdateController {
        val versionCode = PackageInfoCompat.getLongVersionCode(app.packageManager.getPackageInfo(app.packageName, 0))
        return UpdateController(
            manager = AppUpdateManagerFactory.create(app),
            config = app.firebaseGraph().value.remoteConfig,
            settings = app.featureSettingsStore().value,
            versionCode = versionCode,
            clock = System::currentTimeMillis,
            analytics = app.appContext().value.analytics.value::log,
        )
    }
}

/**
 * Home's update glue (S4 Task 8): asks [UpdateController] on every Home resume and shows the "Restart" snackbar once
 * a flexible download is ready. [currentEntry] is read on each resume (HomeScreen passes the ad entry tracker's value,
 * so this package never imports the ads code).
 */
@Composable
fun HomeUpdatePrompt(currentEntry: () -> EntrySource) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity ?: return
    val controller = remember { UpdateControllerHolder.get(context) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { controller.onHomeResumed(activity, currentEntry()) }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    UpdateSnackbar(controller)
}

/** "Update downloaded. Restart" with a Restart action, shown while [UpdateController.readyToRestart] is true. */
@Composable
fun UpdateSnackbar(controller: UpdateController) {
    val ready by controller.readyToRestart.collectAsState()
    if (!ready) return
    Snackbar(
        modifier = Modifier.padding(8.dp),
        action = {
            TextButton(onClick = controller::restart) { Text(stringRes(R.string.update__restart_action)) }
        },
        dismissAction = {
            TextButton(onClick = controller::dismissRestart) { Text(stringRes(R.string.update__later_action)) }
        },
    ) {
        Text(stringRes(R.string.update__downloaded_message))
    }
}
