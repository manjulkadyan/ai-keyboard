/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck

/**
 * Firebase options from the Gradle properties `aikb.firebase.{projectId,appId,apiKey,projectNumber}` (via
 * `:app` BuildConfig). All empty by default; Firebase is used only when every value is set (decision 6).
 * These are public client identifiers, not secrets; [toString] still prints only whether they are set.
 */
data class FirebaseConfig(
    val projectId: String,
    val appId: String,
    val apiKey: String,
    val projectNumber: String,
) {
    val isConfigured: Boolean
        get() = listOf(projectId, appId, apiKey, projectNumber).all { it.isNotBlank() }

    override fun toString(): String = "FirebaseConfig(configured=$isConfigured)"
}

/**
 * Initialises the named FirebaseApp [APP_NAME] by hand on first use (TRD §2.3: nothing Firebase runs at process
 * start; `FirebaseInitProvider` is removed from the merged manifest) and installs the App Check provider: the
 * debug provider in debug builds, Play Integrity otherwise ([AppCheckProviders]).
 *
 * The App Check token TTL is set in the Firebase console, not here (24 h, see backend `deploy/README.md`).
 */
class FirebaseBootstrap(context: Context, private val config: FirebaseConfig) {
    private val appContext = context.applicationContext

    @Volatile private var app: FirebaseApp? = null

    val isConfigured: Boolean get() = config.isConfigured

    /** The initialised app, or null when [config] is incomplete. Safe to call from any thread. */
    fun app(): FirebaseApp? {
        if (!config.isConfigured) return null
        app?.let { return it }
        return synchronized(this) {
            app ?: initialize().also { app = it }
        }
    }

    private fun initialize(): FirebaseApp {
        val existing = FirebaseApp.getApps(appContext).firstOrNull { it.name == APP_NAME }
        val firebaseApp = existing ?: FirebaseApp.initializeApp(
            appContext,
            FirebaseOptions.Builder()
                .setProjectId(config.projectId)
                .setApplicationId(config.appId)
                .setApiKey(config.apiKey)
                .setGcmSenderId(config.projectNumber)
                .build(),
            APP_NAME,
        )
        FirebaseAppCheck.getInstance(firebaseApp).installAppCheckProviderFactory(AppCheckProviders.factory())
        return firebaseApp
    }

    companion object {
        const val APP_NAME = "aikb"
    }
}
