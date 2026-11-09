/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PrivacySettingsTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private class FakeAdPrivacy(required: Boolean, private val succeeds: Boolean = true) : AdPrivacyOptions {
        override val required = MutableStateFlow(required)
        var shown = 0
        override fun show(activity: Activity, onResult: (Boolean) -> Unit) {
            shown++
            onResult(succeeds)
        }
    }

    private var settings by mutableStateOf(FeatureSettings())
    private val events = mutableListOf<AnalyticsEvent>()
    private var clearData = 0
    private var deleteData = 0
    private var adPrivacyFailed = 0

    private fun show(ads: AdPrivacyOptions) = compose.setContent {
        ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
            PrivacySettingsContent(
                settings = settings,
                adPrivacy = ads,
                onUpdate = { settings = it(settings) },
                onEvents = { events += it },
                onPrivateApps = {},
                onClearData = { clearData++ },
                onDeleteData = { deleteData++ },
                onDataPolicy = {},
                onAdPrivacyFailed = { adPrivacyFailed++ },
            )
        }
    }

    @Test fun adPrivacyOptionsShowOnlyWhenRequired() {
        val ads = FakeAdPrivacy(required = false)
        show(ads)
        compose.onNodeWithText("Ad privacy options").assertDoesNotExist()
        ads.required.value = true
        compose.waitForIdle()
        compose.onNodeWithText("Ad privacy options").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, ads.shown)
        assertEquals(0, adPrivacyFailed)
    }

    @Test fun aFailedAdPrivacyFormIsSurfaced() {
        val ads = FakeAdPrivacy(required = true, succeeds = false)
        show(ads)
        compose.onNodeWithText("Ad privacy options").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, ads.shown)
        assertEquals(1, adPrivacyFailed)
    }

    @Test fun theFourConsentClassesEachWithdrawToZero() {
        settings = settings.copy(
            consentAiAt = 11L,
            consentCloudTranslateAt = 12L,
            consentAutoPolishAt = 13L,
            consentVoiceNoticeAt = 14L,
            autoPolish = true,
            cloudTranslateOptIn = true,
        )
        show(FakeAdPrivacy(required = false))
        for (cls in CONSENT_CLASSES) compose.onNodeWithTag(consentRowTag(cls)).performScrollTo()

        fun withdraw(cls: String) {
            compose.onNodeWithTag(consentWithdrawTag(cls)).performScrollTo().performClick()
            compose.onNodeWithTag(CONSENT_WITHDRAW_CONFIRM_TAG).performClick()
            compose.waitForIdle()
        }

        withdraw("consent_auto_polish")
        assertEquals(0L, settings.consentAutoPolishAt)
        assertFalse(settings.autoPolish)
        assertEquals(11L, settings.consentAiAt)

        withdraw("consent_cloud_translate")
        assertEquals(0L, settings.consentCloudTranslateAt)
        assertFalse(settings.cloudTranslateOptIn)

        withdraw("consent_voice_notice")
        assertEquals(0L, settings.consentVoiceNoticeAt)

        withdraw("consent_ai")
        assertEquals(0L, settings.consentAiAt)
        compose.onNodeWithTag(consentWithdrawTag("consent_ai")).performScrollTo().assertIsNotEnabled()

        assertEquals(
            listOf("auto_polish", "ai"),
            events.filter { it.name == "consent_withdrawn" }.map { it.params["kind"] },
        )
    }

    @Test fun clearAndDeleteDataAreHooksForLater() {
        show(FakeAdPrivacy(required = false))
        compose.onNodeWithText("Clear data").performScrollTo().performClick()
        compose.onNodeWithText("Delete my data").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(1, clearData)
        assertEquals(1, deleteData)
        compose.onNodeWithText("Share usage analytics").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(settings.analyticsOptOut)
    }
}
