/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import com.manjul.aikeyboard.fork.routes.RouteRegistry
import com.manjul.aikeyboard.fork.paywall.PaywallTrigger
import com.manjul.aikeyboard.fork.paywall.paywallPath
import kotlin.test.Test
import kotlin.test.assertEquals

/** "See Pro" on the premium card opens the paywall with the premium_sticker trigger (S5 Task 6). */
class StickersImeSessionTest {
    @Test
    fun `the see pro deep link is the premium sticker paywall route`() {
        val path = paywallPath(PaywallTrigger.PREMIUM_STICKER)
        assertEquals("ui://florisboard/$path", StickersImeSession.PAYWALL_DEEPLINK)
        assertEquals(RouteRegistry.PAYWALL + "?trigger=premium_sticker", paywallPath(PaywallTrigger.PREMIUM_STICKER))
    }
}
