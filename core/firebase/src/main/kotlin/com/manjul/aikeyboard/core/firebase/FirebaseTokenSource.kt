/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.TokenSource
import com.manjul.aikeyboard.core.ai.TokenState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * kb-api auth from Firebase (TRD §5.2): an App Check token and the ID token of an anonymous user, created on
 * first use. `NotConfigured` without Firebase options. Failures are mapped to [TokenState.Failed] and never
 * logged (Firebase messages can carry request details). [scope] runs [prefetch]; give it a background dispatcher.
 */
class FirebaseTokenSource(
    private val bootstrap: FirebaseBootstrap,
    private val scope: CoroutineScope,
) : TokenSource {
    override suspend fun headers(): TokenState {
        val app = try {
            bootstrap.app()
        } catch (e: IllegalStateException) {
            return TokenState.Failed(retryable = false)
        } catch (e: IllegalArgumentException) {
            return TokenState.Failed(retryable = false)
        } ?: return TokenState.NotConfigured
        return try {
            val appCheck = FirebaseAppCheck.getInstance(app).getAppCheckToken(false).await().token
            val auth = FirebaseAuth.getInstance(app)
            val user = auth.currentUser ?: auth.signInAnonymously().await().user
                ?: return TokenState.Failed(retryable = true)
            val idToken = user.getIdToken(false).await().token
            if (appCheck.isNullOrEmpty() || idToken.isNullOrEmpty()) {
                TokenState.Failed(retryable = true)
            } else {
                TokenState.Ready(mapOf(AuthHeaders.APP_CHECK to appCheck, AuthHeaders.AUTHORIZATION to "Bearer $idToken"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseNetworkException) {
            TokenState.Failed(retryable = true)
        } catch (e: Exception) {
            TokenState.Failed(retryable = false)
        }
    }

    override fun prefetch() {
        if (!bootstrap.isConfigured) return
        scope.launch { headers() }
    }
}
