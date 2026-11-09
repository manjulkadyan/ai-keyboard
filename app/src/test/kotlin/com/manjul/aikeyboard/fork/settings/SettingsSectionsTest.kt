/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.fork.routes.RouteRegistry
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
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
class SettingsSectionsTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test fun thereAreNineSectionsWithTheirSlugs() {
        assertEquals(
            listOf("languages", "voice", "translate", "ai", "appearance", "typing", "privacy", "subscription", "about"),
            SettingsSection.entries.map { it.slug },
        )
    }

    @Test fun everySectionResolvesThroughTheRouteRegistry() {
        for (section in SettingsSection.entries) {
            val byExtra = RouteRegistry.resolve("settings/${section.slug}", null)
            assertFalse(byExtra.unknown, section.slug)
            assertEquals(section.path, byExtra.path, section.slug)
            val byLink = RouteRegistry.resolve(null, Uri.parse("ui://florisboard/settings/${section.slug}"))
            assertEquals(section.path, byLink.path, section.slug)
        }
        assertEquals("themes", SettingsSection.APPEARANCE.path)
        assertEquals("settings/localization", SettingsSection.LANGUAGES.path)
        assertEquals("settings/suggestions", SettingsSection.TYPING.path)
        assertEquals("settings/all", RouteRegistry.resolve(RouteRegistry.SETTINGS, null).path)
    }

    @Test fun anUnknownSlugGivesHome() {
        for (value in listOf("settings/billing", "settings/Privacy", "settings/privacy/x", "settings/")) {
            val r = RouteRegistry.resolve(value, null)
            assertTrue(r.unknown && r.isHome, value)
        }
    }

    @Test fun theSettingsScreenListsEverySectionAndOpensIt() {
        val opened = mutableListOf<SettingsSection>()
        compose.setContent {
            ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
                SettingsContent(onOpen = { opened += it })
            }
        }
        val titles = listOf("Languages", "Voice", "Translate", "AI", "Appearance", "Typing", "Privacy", "Subscription")
        for (title in titles) {
            compose.onNodeWithText(title).performScrollTo()
        }
        compose.onNodeWithText("Subscription").performScrollTo().performClick()
        compose.onNodeWithText("About").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(SettingsSection.SUBSCRIPTION, SettingsSection.ABOUT), opened)
    }
}
