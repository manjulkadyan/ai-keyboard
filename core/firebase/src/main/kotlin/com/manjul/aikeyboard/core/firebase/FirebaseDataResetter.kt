/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.firebase

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.installations.FirebaseInstallations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * The Firebase part of Delete my data (S4 Task 12): deletes the Firebase installation id, resets the Analytics app
 * instance id and signs the anonymous Auth user out, so the next kb-api call gets a new uid. Without Firebase
 * options it does nothing. Each step runs even when an earlier one fails; failures are never logged, because
 * Firebase messages can carry request details.
 */
class FirebaseDataResetter(context: Context, private val bootstrap: FirebaseBootstrap) {
    private val appContext = context.applicationContext

    /** Returns false when a step failed. */
    suspend fun reset(): Boolean {
        if (!bootstrap.isConfigured) return true
        var ok = true
        ok = step { bootstrap.defaultApp()?.let { FirebaseInstallations.getInstance(it).delete().await() } } && ok
        ok = step {
            if (bootstrap.defaultApp() != null) FirebaseAnalytics.getInstance(appContext).resetAnalyticsData()
        } && ok
        ok = step { bootstrap.app()?.let { FirebaseAuth.getInstance(it).signOut() } } && ok
        return ok
    }

    private suspend fun step(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
