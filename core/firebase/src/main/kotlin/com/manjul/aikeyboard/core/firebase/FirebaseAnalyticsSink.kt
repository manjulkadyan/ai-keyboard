/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.firebase

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.common.NonFatalReporter

/**
 * Firebase Analytics on the default FirebaseApp (Sprint 4 decision 17). The SDK starts on the first call, not when
 * this object is created. Collection stays off (manifest default) until [setCollection] turns it on. Param values
 * are enums, buckets, language tags, numbers or booleans from [com.manjul.aikeyboard.core.analytics.EventCatalog],
 * never user content.
 */
class FirebaseAnalyticsSink(
    context: Context,
    private val bootstrap: FirebaseBootstrap,
    private val reporter: NonFatalReporter,
) : AnalyticsSink {
    private val appContext = context.applicationContext

    @Volatile private var instance: FirebaseAnalytics? = null

    /** True once this sink holds a `FirebaseAnalytics` instance. Reading it never starts Firebase. */
    val isStarted: Boolean get() = instance != null

    private fun analytics(): FirebaseAnalytics? {
        instance?.let { return it }
        bootstrap.defaultApp() ?: return null
        return synchronized(this) {
            instance ?: FirebaseAnalytics.getInstance(appContext).also { instance = it }
        }
    }

    override fun log(event: AnalyticsEvent) = guarded { analytics()?.logEvent(event.name, bundleOf(event.params)) }

    override fun setUserProperty(name: String, value: String?) = guarded { analytics()?.setUserProperty(name, value) }

    fun setCollection(enabled: Boolean) = guarded { analytics()?.setAnalyticsCollectionEnabled(enabled) }

    /** Consent mode v2. Task 6 calls this from the UMP result. */
    fun setConsent(adStorage: Boolean, adUserData: Boolean, adPersonalization: Boolean, analyticsStorage: Boolean) =
        guarded {
            analytics()?.setConsent(
                mapOf(
                    FirebaseAnalytics.ConsentType.AD_STORAGE to status(adStorage),
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to status(adUserData),
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to status(adPersonalization),
                    FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to status(analyticsStorage),
                ),
            )
        }

    private fun status(granted: Boolean) =
        if (granted) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            reporter.report("analytics", e)
        }
    }

    companion object {
        /** Firebase takes String, Long and Double params. Booleans and enums become strings. */
        internal fun bundleOf(params: Map<String, Any>): Bundle = Bundle().apply {
            for ((key, value) in params) {
                when (value) {
                    is Int -> putLong(key, value.toLong())
                    is Long -> putLong(key, value)
                    is Float -> putDouble(key, value.toDouble())
                    is Double -> putDouble(key, value)
                    else -> putString(key, value.toString())
                }
            }
        }
    }
}
