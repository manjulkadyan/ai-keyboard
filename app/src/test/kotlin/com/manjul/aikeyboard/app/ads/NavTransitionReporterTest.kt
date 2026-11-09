/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import android.app.Application
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavigatorProvider
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import com.manjul.aikeyboard.app.Routes
import com.manjul.aikeyboard.core.ads.rules.NavTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavTransitionReporterTest {
    private val graph: NavGraph = run {
        val provider = NavigatorProvider().apply {
            addNavigator(ComposeNavigator())
            addNavigator(NavGraphNavigator(this))
        }
        NavGraphBuilder(provider, Routes.Settings.Home::class, null, emptyMap()).apply {
            composable<Routes.Settings.Home> {}
            composable<Routes.Translator> {}
            composable<Routes.TranslatorHistory> {}
            composable<Routes.Settings.Theme> {}
            composable<Routes.Settings.ThemeManager> {}
            composable<Routes.Settings.Keyboard> {}
            composable<Routes.Setup.Screen> {}
            composable<Routes.Paywall> {}
        }.build()
    }

    private inline fun <reified T : Any> node(): NavDestination = requireNotNull(graph.findNode<T>())

    @Test
    fun `classify table`() {
        val home = node<Routes.Settings.Home>()
        val features = listOf(
            node<Routes.Translator>(),
            node<Routes.TranslatorHistory>(),
            node<Routes.Settings.Theme>(),
            node<Routes.Settings.ThemeManager>(),
        )
        val other = node<Routes.Settings.Keyboard>()
        val paywall = node<Routes.Paywall>()
        for (feature in features) {
            assertEquals(feature.route, NavTransition.FEATURE_TO_HOME, classify(feature, home))
            assertEquals(feature.route, NavTransition.HOME_TO_FEATURE, classify(home, feature))
            assertEquals(feature.route, NavTransition.OTHER, classify(feature, other))
            assertEquals(feature.route, NavTransition.OTHER, classify(feature, paywall))
        }
        assertEquals(NavTransition.OTHER, classify(features[0], features[1]))
        assertEquals(NavTransition.OTHER, classify(other, home))
        assertEquals(NavTransition.OTHER, classify(paywall, home))
        assertEquals(NavTransition.OTHER, classify(home, other))
    }

    @Test
    fun `onboarding and the paywall block full-screen ads`() {
        assertTrue(node<Routes.Setup.Screen>().blocksFullScreenAds())
        assertTrue(node<Routes.Paywall>().blocksFullScreenAds())
        assertFalse(node<Routes.Settings.Home>().blocksFullScreenAds())
        assertFalse(node<Routes.Translator>().blocksFullScreenAds())
    }
}
