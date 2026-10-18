/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads

import android.app.Activity
import com.manjul.aikeyboard.core.ads.consent.ConsentError
import com.manjul.aikeyboard.core.ads.consent.ConsentManager
import com.manjul.aikeyboard.core.ads.consent.ConsentResult
import kotlin.test.Test
import kotlin.test.assertEquals

class AdsInitializerTest {
    private val activity = Activity()

    private class FakeConsent : ConsentManager {
        var calls = 0
        var pending: ((ConsentResult) -> Unit)? = null

        override fun gatherConsent(activity: Activity, onResult: (ConsentResult) -> Unit) {
            calls++
            pending = onResult
        }

        fun resolve(result: ConsentResult) = pending!!.also { pending = null }(result)

        override fun canRequestAds(activity: Activity) = false
        override fun isPrivacyOptionsRequired(activity: Activity) = false
        override fun showPrivacyOptionsForm(activity: Activity, onDismissed: (ConsentError?) -> Unit) = Unit
    }

    private class FakeSdk(private val completeImmediately: Boolean = true, private val fail: Boolean = false) : AdSdk {
        var calls = 0
        var complete: (() -> Unit)? = null

        override fun initialize(activity: Activity, onComplete: () -> Unit) {
            calls++
            if (fail) error("sdk boom")
            if (completeImmediately) onComplete() else complete = onComplete
        }
    }

    private val consent = FakeConsent()

    @Test
    fun `nothing happens until initialize is called`() {
        val sdk = FakeSdk()
        val initializer = AdsInitializer(consent, sdk)
        assertEquals(AdsStatus(), initializer.status.value)
        assertEquals(0, consent.calls)
        assertEquals(0, sdk.calls)
    }

    @Test
    fun `sdk starts only after consent allows ad requests`() {
        val sdk = FakeSdk(completeImmediately = false)
        val initializer = AdsInitializer(consent, sdk)
        initializer.initialize(activity)
        assertEquals(AdsStatus.Phase.GATHERING_CONSENT, initializer.status.value.phase)
        assertEquals(0, sdk.calls)

        consent.resolve(ConsentResult(canRequestAds = true, privacyOptionsRequired = true))
        assertEquals(1, sdk.calls)
        assertEquals(AdsStatus.Phase.INITIALIZING_SDK, initializer.status.value.phase)
        assertEquals(true, initializer.status.value.privacyOptionsRequired)

        sdk.complete!!()
        assertEquals(AdsStatus.Phase.READY, initializer.status.value.phase)
    }

    @Test
    fun `no consent means no sdk`() {
        val sdk = FakeSdk()
        val initializer = AdsInitializer(consent, sdk)
        initializer.initialize(activity)
        val error = ConsentError(2, "offline")
        consent.resolve(ConsentResult(canRequestAds = false, privacyOptionsRequired = false, error = error))
        assertEquals(0, sdk.calls)
        assertEquals(AdsStatus(AdsStatus.Phase.CONSENT_NOT_GRANTED, false, error), initializer.status.value)
    }

    @Test
    fun `ad-free users never start the sdk`() {
        val sdk = FakeSdk()
        val initializer = AdsInitializer(consent, sdk, adsDisabled = { true })
        initializer.initialize(activity)
        consent.resolve(ConsentResult(canRequestAds = true, privacyOptionsRequired = false))
        assertEquals(0, sdk.calls)
        assertEquals(AdsStatus.Phase.ADS_DISABLED, initializer.status.value.phase)
    }

    @Test
    fun `consent is refreshed on every call but the sdk starts once`() {
        val sdk = FakeSdk()
        val initializer = AdsInitializer(consent, sdk)
        repeat(2) {
            initializer.initialize(activity)
            consent.resolve(ConsentResult(canRequestAds = true, privacyOptionsRequired = false))
        }
        assertEquals(2, consent.calls)
        assertEquals(1, sdk.calls)
        assertEquals(AdsStatus.Phase.READY, initializer.status.value.phase)
    }

    @Test
    fun `a call while consent is in flight is ignored`() {
        val initializer = AdsInitializer(consent, FakeSdk())
        initializer.initialize(activity)
        initializer.initialize(activity)
        assertEquals(1, consent.calls)
    }

    @Test
    fun `sdk failure is contained`() {
        val sdk = FakeSdk(fail = true)
        val initializer = AdsInitializer(consent, sdk)
        initializer.initialize(activity)
        consent.resolve(ConsentResult(canRequestAds = true, privacyOptionsRequired = false))
        assertEquals(AdsStatus.Phase.SDK_FAILED, initializer.status.value.phase)

        initializer.initialize(activity)
        consent.resolve(ConsentResult(canRequestAds = true, privacyOptionsRequired = false))
        assertEquals(1, sdk.calls)
        assertEquals(AdsStatus.Phase.SDK_FAILED, initializer.status.value.phase)
    }
}
