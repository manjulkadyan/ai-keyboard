/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import android.content.Context
import com.manjul.aikeyboard.FlorisApplication

fun Context.privateAppsRepository(): PrivateAppsRepository {
    val app = applicationContext as FlorisApplication
    return PrivateAppsRepository(
        store = app.featureSettingsStore.value,
        pm = packageManager,
        log = { app.analytics.value.log(it) },
    )
}

/** Adds the app of the focused editor to the private apps. The package name is never logged. */
suspend fun Context.makeAppPrivate(packageName: String?) {
    if (packageName.isNullOrBlank()) return
    privateAppsRepository().add(packageName)
}
