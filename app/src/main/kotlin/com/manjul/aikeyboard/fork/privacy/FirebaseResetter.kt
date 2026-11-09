/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

import com.manjul.aikeyboard.core.firebase.FirebaseDataResetter

/**
 * Resets the Firebase identifiers on the device (Delete my data): `FirebaseInstallations.delete()`,
 * `FirebaseAnalytics.resetAnalyticsData()` and `FirebaseAuth.signOut()`. Never throws.
 */
interface FirebaseResetter {
    suspend fun reset()
}

/** The real [FirebaseResetter], on the app's Firebase bootstrap. */
internal class DefaultFirebaseResetter(private val delegate: FirebaseDataResetter) : FirebaseResetter {
    override suspend fun reset() {
        delegate.reset()
    }
}
