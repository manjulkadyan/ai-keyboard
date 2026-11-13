/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.routes

import android.net.Uri
import com.manjul.aikeyboard.fork.paywall.PaywallTrigger
import com.manjul.aikeyboard.fork.paywall.paywallPath
import com.manjul.aikeyboard.fork.settings.SettingsSection

/**
 * The deep-link allow-list (PRD FR-32, TRD §4.3.4). `FlorisAppActivity` is exported, so the `open_screen` extra and
 * `ui://florisboard/…` / `app://…` links can come from any app: only the routes listed here open a screen, anything
 * else (unknown names, unknown paywall triggers, devtools, extension screens, malformed values) lands on Home and
 * logs `deeplink_unknown` (the value itself is never sent or logged).
 *
 * Routes are keyed by name and point at the `@Deeplink` path of their `Routes` destination; navigation goes through
 * the nav graph's deep links (`ui://florisboard/<path>`), so a route whose screen is not in the graph yet falls back
 * to Home instead of crashing. `RouteRegistryTest` checks every entry against `Routes.kt`.
 *
 * The paywall is `paywall?trigger=<wire>` for every link-able [PaywallTrigger] (S4-paywall). `pro`, which the AI
 * quota card opens, is an alias of `paywall?trigger=ai_quota` (plan decision 23). Before onboarding is done every
 * paywall route opens Home (PRD FR-32 AC3). `themes` opens the Themes screen (S4-themes). `settings/all` opens the
 * Settings tree and `settings/<slug>` each of its [SettingsSection]s (S4-settings, PRD FR-09). There is no
 * `conversation` or `camera` route: both features are out of scope for this app (docs/decisions/od-21).
 */
object RouteRegistry {
    /** The `FlorisAppActivity` extra naming a route (a key of [routes]). */
    const val EXTRA_OPEN_SCREEN = "open_screen"

    /** Text handed to the Translator by `open_screen=translator` (Open in app from the Translate sheet). */
    const val EXTRA_TRANSLATOR_TEXT = "com.manjul.aikeyboard.extra.TRANSLATOR_TEXT"

    const val HOME = "home"
    const val TRANSLATOR = "translator"
    const val PRO = "pro"
    const val PAYWALL = "paywall"
    const val THEMES = "themes"
    const val SETTINGS = SettingsSection.ROOT_ROUTE

    private const val HOME_PATH = "settings/home"
    private const val UI_SCHEME = "ui"
    private const val UI_HOST = "florisboard"
    private const val APP_SCHEME = "app"

    /** Longest value we try to match; anything longer is unknown without further parsing. */
    private const val MAX_ROUTE_CHARS = 128

    /** Route name -> `@Deeplink` path of its `Routes` destination; a paywall path also carries its trigger. */
    val routes: Map<String, String> = mapOf(
        HOME to HOME_PATH,
        TRANSLATOR to "translator",
        "settings/home" to HOME_PATH,
        "settings/voice" to "settings/voice",
        "settings/offline-languages" to "settings/offline-languages",
        "settings/privacy/private-apps" to "settings/privacy/private-apps",
        PRO to paywallPath(PaywallTrigger.AI_QUOTA), // S4-paywall: alias (plan decision 23)
        THEMES to "themes", // S4-themes: the Themes screen
        SETTINGS to SettingsSection.ROOT_ROUTE, // S4-settings: the Settings tree
    ) + SettingsSection.entries.associate { it.route to it.path } +
        PaywallTrigger.entries.filter { it.deepLinkable }.map(::paywallPath).associateWith { it }

    /** Where to go. [path] is a `@Deeplink` path; [unknown] means the value was not allow-listed (Home + event). */
    data class Resolved(val name: String, val path: String, val unknown: Boolean) {
        val isHome: Boolean get() = path == HOME_PATH
    }

    val Home = Resolved(HOME, HOME_PATH, unknown = false)
    private val Unknown = Resolved(HOME, HOME_PATH, unknown = true)

    /**
     * Resolves an `open_screen` value or, without one, a `ui://florisboard/<route>` or `app://<route>` link. Link
     * query strings and fragments are ignored, except the single `trigger` of a paywall link. Matching is exact (no
     * prefixes, no case folding). Before [onboardingDone] a paywall route resolves to Home. Never throws.
     */
    fun resolve(openScreen: String?, uri: Uri?, onboardingDone: Boolean = true): Resolved {
        val name = when {
            openScreen != null -> openScreen
            uri != null -> routeOf(uri) ?: return Unknown
            else -> return Home
        }
        val resolved = lookup(name)
        return if (!onboardingDone && resolved.path.startsWith(PAYWALL)) Home else resolved
    }

    private fun lookup(name: String): Resolved {
        if (name.length > MAX_ROUTE_CHARS) return Unknown
        val key = name.trim('/')
        val path = routes[key] ?: return Unknown
        return Resolved(key, path, unknown = false)
    }

    private fun routeOf(uri: Uri): String? = runCatching {
        if (uri.isOpaque) return@runCatching null
        val path = uri.path.orEmpty().trim('/')
        val route = when (uri.scheme) {
            UI_SCHEME -> path.takeIf { uri.host == UI_HOST }
            APP_SCHEME -> listOfNotNull(uri.host, path.ifEmpty { null }).joinToString("/")
            else -> null
        }
        if (route != PAYWALL) return@runCatching route
        // The one `trigger` query value picks the paywall route; lookup() checks it against the allow-list.
        val trigger = uri.getQueryParameters(TRIGGER).singleOrNull() ?: return@runCatching null
        "$PAYWALL?$TRIGGER=$trigger"
    }.getOrNull()

    private const val TRIGGER = "trigger"
}
