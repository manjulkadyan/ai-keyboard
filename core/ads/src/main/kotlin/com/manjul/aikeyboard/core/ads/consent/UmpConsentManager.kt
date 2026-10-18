/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.consent

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * [ConsentManager] backed by the Google User Messaging Platform SDK.
 *
 * Constructing this class does not touch UMP; the SDK is first used inside a call that receives an
 * Activity. The consent message itself (GDPR/TCF, US states) is configured in the AdMob
 * "Privacy & messaging" console; the SDK only renders what is configured there.
 *
 * @param debug Forces a geography and registers test devices. Pass it only from debug builds.
 */
class UmpConsentManager(
    private val debug: ConsentDebugConfig? = null,
) : ConsentManager {

    override fun gatherConsent(activity: Activity, onResult: (ConsentResult) -> Unit) {
        val info = consentInformation(activity)
        var delivered = false
        fun deliver(error: FormError?) {
            if (delivered) return
            delivered = true
            onResult(
                ConsentResult(
                    canRequestAds = info.canRequestAds(),
                    privacyOptionsRequired = info.isPrivacyOptionsRequired(),
                    error = error?.toConsentError(),
                ),
            )
        }

        info.requestConsentInfoUpdate(
            activity,
            requestParameters(activity),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError -> deliver(formError) }
            },
            { requestError -> deliver(requestError) },
        )
    }

    override fun canRequestAds(activity: Activity): Boolean = consentInformation(activity).canRequestAds()

    override fun isPrivacyOptionsRequired(activity: Activity): Boolean =
        consentInformation(activity).isPrivacyOptionsRequired()

    override fun showPrivacyOptionsForm(activity: Activity, onDismissed: (ConsentError?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError -> onDismissed(formError?.toConsentError()) }
    }

    private fun consentInformation(activity: Activity): ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity.applicationContext)

    private fun requestParameters(context: Context): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        if (debug != null) {
            val settings = ConsentDebugSettings.Builder(context)
                .setDebugGeography(debug.geography.umpValue)
            debug.testDeviceHashedIds.forEach { settings.addTestDeviceHashedId(it) }
            builder.setConsentDebugSettings(settings.build())
        }
        return builder.build()
    }

    private fun ConsentInformation.isPrivacyOptionsRequired(): Boolean =
        privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    private fun FormError.toConsentError() = ConsentError(errorCode, message)
}

/** Debug-only UMP overrides, so the consent form can be tested outside the EEA. */
data class ConsentDebugConfig(
    val geography: DebugGeography = DebugGeography.EEA,
    val testDeviceHashedIds: List<String> = emptyList(),
) {
    enum class DebugGeography(internal val umpValue: Int) {
        DISABLED(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED),
        EEA(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA),
        REGULATED_US_STATE(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE),
        OTHER(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER),
    }
}
