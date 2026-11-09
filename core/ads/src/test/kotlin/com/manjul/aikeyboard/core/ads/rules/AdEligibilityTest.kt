/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.rules

import com.manjul.aikeyboard.core.ads.cap.AdCapBlockReason
import com.manjul.aikeyboard.core.ads.cap.AdCapDecision
import com.manjul.aikeyboard.core.common.entry.EntrySource
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** S4 Task 7: each row flips one field of an allowed context; the flipped rule must block its formats. */
class AdEligibilityTest {
    enum class Kind { APP_OPEN, INTERSTITIAL, BANNER_OR_NATIVE }

    data class Row(
        val name: String,
        val context: AdContext = BASE,
        val transition: NavTransition = NavTransition.FEATURE_TO_HOME,
        val unitId: String? = UNIT,
        val blocks: Set<Kind>,
    ) {
        override fun toString() = name
    }

    @Test
    fun `the base row allows every format`() {
        assertTrue(AdEligibility.appOpen(BASE, UNIT))
        assertTrue(AdEligibility.interstitial(BASE, NavTransition.FEATURE_TO_HOME, UNIT))
        assertTrue(AdEligibility.bannerOrNative(BASE, UNIT))
    }

    @Test
    fun `app open is allowed on resume too`() {
        assertTrue(AdEligibility.appOpen(BASE.copy(entry = EntrySource.RESUME), UNIT))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    fun `a flipped field blocks`(row: Row) {
        if (Kind.APP_OPEN in row.blocks) assertFalse(AdEligibility.appOpen(row.context, row.unitId), "app open")
        if (Kind.INTERSTITIAL in row.blocks) {
            assertFalse(AdEligibility.interstitial(row.context, row.transition, row.unitId), "interstitial")
        }
        if (Kind.BANNER_OR_NATIVE in row.blocks) {
            assertFalse(AdEligibility.bannerOrNative(row.context, row.unitId), "banner or native")
        }
    }

    companion object {
        const val NOW = 1_800_000_000_000L
        const val UNIT = "ca-app-pub-3940256099942544/9257395921"

        val BASE = AdContext(
            isPro = false,
            adsReady = true,
            sessionIndex = 2,
            entry = EntrySource.LAUNCHER,
            onSplashOrOnboarding = false,
            returningFromExternalFlow = false,
            featureUsesSinceInterstitial = 2,
            lastInterstitialAtMs = NOW - 30_000,
            nowMs = NOW,
            cap = AdCapDecision.Allowed,
        )

        private val all = Kind.entries.toSet()
        private val blocked = AdCapDecision.Blocked(setOf(AdCapBlockReason.GLOBAL_INTERVAL), NOW + 1)

        @JvmStatic
        fun rows() = listOf(
            Row("pro", BASE.copy(isPro = true), blocks = all),
            Row("ads not ready", BASE.copy(adsReady = false), blocks = all),
            Row("session 1", BASE.copy(sessionIndex = 1), blocks = all),
            Row("empty unit id", unitId = "", blocks = all),
            Row("no unit id", unitId = null, blocks = all),
            Row("process text entry", BASE.copy(entry = EntrySource.PROCESS_TEXT), blocks = setOf(Kind.APP_OPEN, Kind.INTERSTITIAL)),
            Row("send entry", BASE.copy(entry = EntrySource.SEND), blocks = setOf(Kind.APP_OPEN, Kind.INTERSTITIAL)),
            Row("notification entry", BASE.copy(entry = EntrySource.NOTIFICATION), blocks = setOf(Kind.APP_OPEN, Kind.INTERSTITIAL)),
            Row("deep link entry", BASE.copy(entry = EntrySource.DEEP_LINK), blocks = setOf(Kind.APP_OPEN)),
            Row("splash or onboarding", BASE.copy(onSplashOrOnboarding = true), blocks = setOf(Kind.APP_OPEN)),
            Row("external flow return", BASE.copy(returningFromExternalFlow = true), blocks = setOf(Kind.APP_OPEN)),
            Row("interstitial 29 s ago", BASE.copy(lastInterstitialAtMs = NOW - 29_000), blocks = setOf(Kind.APP_OPEN)),
            Row("blocked cap", BASE.copy(cap = blocked), blocks = setOf(Kind.APP_OPEN, Kind.INTERSTITIAL)),
            Row("exit transition", transition = NavTransition.EXIT, blocks = setOf(Kind.INTERSTITIAL)),
            Row("other transition", transition = NavTransition.OTHER, blocks = setOf(Kind.INTERSTITIAL)),
            Row("home to feature", transition = NavTransition.HOME_TO_FEATURE, blocks = setOf(Kind.INTERSTITIAL)),
            Row("1 feature use", BASE.copy(featureUsesSinceInterstitial = 1), blocks = setOf(Kind.INTERSTITIAL)),
        )
    }
}
