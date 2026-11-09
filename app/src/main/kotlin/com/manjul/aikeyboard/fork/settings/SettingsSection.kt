/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.settings

import androidx.annotation.StringRes
import com.manjul.aikeyboard.R

/**
 * The nine sections of the Settings tree (PRD FR-09). [slug] is the link name (`settings/<slug>` in
 * `RouteRegistry`), [path] is the `@Deeplink` path of the screen it opens. Some sections reuse an existing screen:
 * Languages is the upstream Localization screen, Typing is Suggestions and autocorrect, Appearance is Themes.
 */
enum class SettingsSection(val slug: String, val path: String, @StringRes val title: Int) {
    LANGUAGES("languages", "settings/localization", R.string.settings_tree__languages),
    VOICE("voice", "settings/voice", R.string.settings_tree__voice),
    TRANSLATE("translate", "settings/translate", R.string.settings_tree__translate),
    AI("ai", "settings/ai", R.string.settings_tree__ai),
    APPEARANCE("appearance", "themes", R.string.settings_tree__appearance),
    TYPING("typing", "settings/suggestions", R.string.settings_tree__typing),
    PRIVACY("privacy", "settings/privacy", R.string.settings_tree__privacy),
    SUBSCRIPTION("subscription", "settings/subscription", R.string.settings_tree__subscription),
    ABOUT("about", "settings/about", R.string.settings_tree__about),
    ;

    /** The `RouteRegistry` route name of this section. */
    val route: String get() = "$ROUTE_PREFIX$slug"

    /** The nav deep link of the screen this section opens. */
    val deepLink: String get() = "ui://florisboard/$path"

    companion object {
        const val ROUTE_PREFIX = "settings/"

        /** The Settings tree root (`Routes.Settings.All`). Not bare `settings`, which stays unknown. */
        const val ROOT_ROUTE = "settings/all"
    }
}
