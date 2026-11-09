/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.app.ads

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.manjul.aikeyboard.app.Routes
import com.manjul.aikeyboard.core.ads.rules.NavTransition

/**
 * Reports each navigation step of the app to [AdsController]. Only a step from a feature screen (Translator,
 * History, a theme screen) back to Home is [NavTransition.FEATURE_TO_HOME], the one step that can show an
 * interstitial. It also tells the controller when onboarding (with its O6 paywall step) or the paywall is on
 * screen: no app-open ad there.
 */
@Composable
fun NavTransitionReporter(navController: NavController) {
    val activity = LocalActivity.current ?: return
    val controller = LocalAdsAppGraph.current?.controller ?: return
    DisposableEffect(navController, activity) {
        var previous: NavDestination? = null
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            controller.setSplashOrOnboarding(destination.blocksFullScreenAds())
            val from = previous
            previous = destination
            if (from != null && from.id != destination.id) {
                controller.onNavTransition(activity, classify(from, destination))
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}

/** Onboarding (`Setup.Screen`, which also hosts the O6 paywall step) and the paywall never show a full-screen ad. */
internal fun NavDestination.blocksFullScreenAds(): Boolean =
    hasRoute(Routes.Setup.Screen::class) || hasRoute(Routes.Paywall::class)

private fun NavDestination.isHome(): Boolean = hasRoute(Routes.Settings.Home::class)

private fun NavDestination.isFeature(): Boolean =
    hasRoute(Routes.Translator::class) ||
        hasRoute(Routes.TranslatorHistory::class) ||
        hasRoute(Routes.Settings.Theme::class) ||
        hasRoute(Routes.Settings.ThemeManager::class)

internal fun classify(from: NavDestination, to: NavDestination): NavTransition = when {
    from.isFeature() && to.isHome() -> NavTransition.FEATURE_TO_HOME
    from.isHome() && to.isFeature() -> NavTransition.HOME_TO_FEATURE
    else -> NavTransition.OTHER
}
