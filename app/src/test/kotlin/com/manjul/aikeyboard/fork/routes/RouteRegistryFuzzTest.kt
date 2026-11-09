/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.routes

import android.content.Intent
import android.net.Uri
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.fork.paywall.PaywallTrigger
import com.manjul.aikeyboard.fork.paywall.paywall
import kotlin.random.Random
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * S4 plan Task 5: 10,000 seeded random route values, links and `open_screen` extras never throw and never resolve
 * outside the allow-list. The paywall resolves only with an allow-listed trigger, and never before onboarding ends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RouteRegistryFuzzTest {
    private val allowed = RouteRegistry.routes.values.toSet()

    @Test
    fun tenThousandRandomValuesStayInTheAllowList() {
        repeat(RUNS) { seed ->
            val r = Random(seed)
            val done = r.nextBoolean()
            val value = randomValue(r)
            val byExtra = runCatching { RouteRegistry.resolve(value, null, onboardingDone = done) }
                .getOrElse { throw AssertionError("seed $seed extra crashed: $it", it) }
            val uri = runCatching { Uri.parse(randomLink(r)) }.getOrNull()
            val byLink = runCatching { RouteRegistry.resolve(null, uri, onboardingDone = done) }
                .getOrElse { throw AssertionError("seed $seed link crashed: $it", it) }
            val intent = Intent().putExtra(RouteRegistry.EXTRA_OPEN_SCREEN, value)
            val events = mutableListOf<AnalyticsEvent>()
            val classified = runCatching { AppIntents.classify(intent, onboardingDone = done) { events += it } }
                .getOrElse { throw AssertionError("seed $seed classify crashed: $it", it) }
            for (route in listOf(byExtra, byLink, (classified as AppIntent.OpenRoute).route)) {
                assertTrue(route.path in allowed, "seed $seed: $route")
                if (route.unknown) assertTrue(route.isHome, "seed $seed")
                if (!done) assertFalse(route.path.startsWith(RouteRegistry.PAYWALL), "seed $seed: paywall before onboarding")
            }
            assertTrue(events.all { it.params.isEmpty() }, "seed $seed")
        }
    }

    @Test
    fun aBogusTriggerGivesHome() {
        for (value in listOf("paywall?trigger=bogus", "paywall", "paywall?trigger=", "paywall?trigger=AI_QUOTA")) {
            val r = RouteRegistry.resolve(value, null)
            assertTrue(r.isHome && r.unknown, value)
        }
        for (link in listOf("app://paywall?trigger=bogus", "ui://florisboard/paywall", "ui://florisboard/paywall?x=ai_quota")) {
            assertTrue(RouteRegistry.resolve(null, Uri.parse(link)).isHome, link)
        }
    }

    @Test
    fun proIsTheAiQuotaPaywall() {
        assertEquals("paywall?trigger=ai_quota", RouteRegistry.resolve(RouteRegistry.PRO, null).path)
        assertEquals("paywall?trigger=ai_quota", RouteRegistry.resolve(null, Uri.parse("ui://florisboard/pro")).path)
        assertEquals("paywall?trigger=ai_quota", RouteRegistry.paywall(PaywallTrigger.AI_QUOTA))
    }

    @Test
    fun allowListedTriggersResolveByExtraAndByLink() {
        for (t in PaywallTrigger.entries.filter { it.deepLinkable }) {
            val path = RouteRegistry.paywall(t)
            assertEquals(path, RouteRegistry.resolve(path, null).path)
            assertEquals(path, RouteRegistry.resolve(null, Uri.parse("ui://florisboard/$path")).path)
            assertEquals(path, RouteRegistry.resolve(null, Uri.parse("app://$path")).path)
        }
        // T1 is shown inside onboarding (O6) only; no link opens it.
        assertTrue(RouteRegistry.resolve(RouteRegistry.paywall(PaywallTrigger.ONBOARDING), null).unknown)
    }

    @Test
    fun anyPaywallBeforeOnboardingIsDoneGivesHome() {
        val values = PaywallTrigger.entries.map { RouteRegistry.paywall(it) } + RouteRegistry.PRO
        for (value in values) {
            val r = RouteRegistry.resolve(value, null, onboardingDone = false)
            assertTrue(r.isHome, value)
            val link = RouteRegistry.resolve(null, Uri.parse("ui://florisboard/$value"), onboardingDone = false)
            assertTrue(link.isHome, value)
        }
        // Other routes are not affected.
        assertEquals("settings/voice", RouteRegistry.resolve("settings/voice", null, onboardingDone = false).path)
        val events = mutableListOf<AnalyticsEvent>()
        val pro = Intent(Intent.ACTION_VIEW, Uri.parse("ui://florisboard/pro"))
        val request = AppIntents.classify(pro, onboardingDone = false) { events += it } as AppIntent.OpenRoute
        assertTrue(request.route.isHome)
    }

    private fun randomValue(r: Random): String? = when (r.nextInt(5)) {
        0 -> null
        1 -> r.pick(SEEDS)
        2 -> r.pick(SEEDS) + randomString(r, 8)
        3 -> "x".repeat(r.nextInt(0, 400))
        else -> randomString(r, 40)
    }

    private fun randomLink(r: Random): String = when (r.nextInt(6)) {
        0 -> "ui://florisboard/" + r.pick(SEEDS)
        1 -> "app://" + r.pick(SEEDS)
        2 -> "ui://florisboard/paywall?trigger=" + randomString(r, 16)
        3 -> "app://paywall?" + randomString(r, 30)
        4 -> randomString(r, 60)
        else -> r.pick(listOf("ui:", "app:", "app:///", "ui://florisboard", "ui://florisboard/paywall?trigger=a&trigger=b"))
    }

    private fun randomString(r: Random, max: Int): String {
        val chars = "abcz09/:?#&=%.-_ \u0000😀‮اردو"
        return String(CharArray(r.nextInt(max + 1)) { chars[r.nextInt(chars.length)] })
    }

    private fun <T> Random.pick(list: List<T>): T = list[nextInt(list.size)]

    private companion object {
        const val RUNS = 10_000
        val SEEDS = listOf(
            "home", "translator", "settings/voice", "pro", "paywall", "paywall?trigger=ai_quota",
            "paywall?trigger=win_back", "paywall?trigger=onboarding", "paywall?trigger=bogus", "devtools", "ext/edit", "",
        )
    }
}
