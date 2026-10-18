/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import android.content.Intent
import android.content.pm.PackageManager
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore

data class AppEntry(val packageName: String, val label: String)

/** Apps the user marked private: the policy treats every field in them as sensitive. */
class PrivateAppsRepository(
    private val store: FeatureSettingsStore,
    private val pm: PackageManager,
    private val log: (AnalyticsEvent) -> Unit = {},
) {
    val privatePackages get() = store.flow

    suspend fun add(pkg: String) {
        store.update { it.copy(privatePackages = it.privatePackages + pkg) }
        log(EventCatalog.privacyPrivateAppAdd())
    }

    suspend fun remove(pkg: String) {
        store.update { it.copy(privatePackages = it.privatePackages - pkg) }
    }

    /** Apps with a launcher entry, sorted by label. Needs the LAUNCHER `<queries>` entry on API 30+. */
    fun launchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Known banking apps that are installed. Suggestions only: never added without the user's action. */
    fun suggestions(): List<AppEntry> {
        val banking = BankingPackages.all
        if (banking.isEmpty()) return emptyList()
        return launchableApps().filter { it.packageName in banking }
    }
}
