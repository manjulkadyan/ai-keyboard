/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.paywall

import androidx.annotation.StringRes
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.analytics.PaywallTriggerParam
import com.manjul.aikeyboard.fork.routes.RouteRegistry

/**
 * Why the paywall opened (PRD §10.4, T1-T8). [wire] is the `trigger` of the route and of `paywall_view`. [header]
 * follows the trigger (PRD FR-29). T1 [ONBOARDING] opens only inside onboarding (O6), so no link opens it
 * ([deepLinkable] false). T5 and T6 have no UI yet (plan decision 10).
 *
 * This file must not import `:core:billing`: `RouteRegistry` and the IME's `AiIntents` read it.
 */
enum class PaywallTrigger(val wire: String, @StringRes val header: Int, val deepLinkable: Boolean = true) {
    ONBOARDING("onboarding", R.string.paywall__header_onboarding, deepLinkable = false),
    AI_QUOTA("ai_quota", R.string.paywall__header_ai_quota),
    PREMIUM_THEME("premium_theme", R.string.paywall__header_premium_theme),
    REMOVE_ADS("remove_ads", R.string.paywall__header_remove_ads),
    CLOUD_QUALITY("cloud_quality", R.string.paywall__header_cloud_quality),
    PREMIUM_STICKER("premium_sticker", R.string.paywall__header_premium_sticker),
    AUTO_POLISH("auto_polish", R.string.paywall__header_auto_polish),
    WIN_BACK("win_back", R.string.paywall__header_win_back),
    ;

    val param: PaywallTriggerParam get() = PaywallTriggerParam.valueOf(name)

    companion object {
        /** Exact match on [wire]; null for anything else. */
        fun fromWire(wire: String?): PaywallTrigger? = entries.firstOrNull { it.wire == wire }
    }
}

/** The route of the paywall for [trigger]: `paywall?trigger=<wire>`. */
fun RouteRegistry.paywall(trigger: PaywallTrigger): String = paywallPath(trigger)

internal fun paywallPath(trigger: PaywallTrigger): String = "${RouteRegistry.PAYWALL}?trigger=${trigger.wire}"
