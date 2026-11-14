/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.routes

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.manjul.aikeyboard.app.Deeplink
import com.manjul.aikeyboard.app.FlorisAppActivity
import com.manjul.aikeyboard.app.Routes
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RouteRegistryTest {
    /** Every `@Deeplink` destination in [Routes]: path -> its name as written in Routes.kt (`Settings.Voice`). */
    private val deeplinks: Map<String, String> by lazy {
        fun walk(cls: Class<*>, prefix: String): List<Pair<String, String>> = cls.declaredClasses.flatMap { nested ->
            val name = if (prefix.isEmpty()) nested.simpleName else "$prefix.${nested.simpleName}"
            val own = nested.getAnnotation(Deeplink::class.java)?.let { listOf(it.path to name) }.orEmpty()
            own + walk(nested, name)
        }
        walk(Routes::class.java, "").toMap()
    }

    private val routesSource: String by lazy {
        listOf(File("src/main/kotlin"), File("app/src/main/kotlin"))
            .map { File(it, "com/manjul/aikeyboard/app/Routes.kt") }
            .firstOrNull { it.isFile }
            ?.readText()
            ?: fail("Cannot locate Routes.kt")
    }

    @Test
    fun everyEntryMapsToADestinationRegisteredInTheNavGraph() {
        for ((name, path) in RouteRegistry.routes) {
            // A query (`paywall?trigger=x`) is a default-valued argument of the destination, not part of its path.
            val destination = deeplinks[path.substringBefore('?')]
            if (destination == null) fail("Route '$name' -> '$path' has no @Deeplink destination in Routes")
            assertTrue(
                "composableWithDeepLink($destination::class)" in routesSource,
                "Route '$name' -> $destination is not registered in the nav graph with its deep link",
            )
        }
    }

    @Test
    fun allowListIsExactlyThePlannedRoutes() {
        assertEquals(
            setOf(
                "home", "translator", "settings/home", "settings/voice", "settings/offline-languages",
                "settings/privacy/private-apps", "pro", "paywall?trigger=ai_quota", "paywall?trigger=premium_theme",
                "paywall?trigger=remove_ads", "paywall?trigger=cloud_quality", "paywall?trigger=premium_sticker",
                "paywall?trigger=auto_polish", "paywall?trigger=win_back", "themes",
                // S4-settings: the settings tree root and its sections
                "settings/all", "settings/languages", "settings/translate", "settings/ai", "settings/appearance",
                "settings/typing", "settings/privacy", "settings/subscription", "settings/about",
                // S5-dict: the Dictionary screen
                "dictionary",
                // S5-phrasebook: the Phrasebook screen
                "phrasebook",
            ),
            RouteRegistry.routes.keys,
        )
        assertTrue(RouteRegistry.routes.values.none { it.startsWith("devtools") || it.startsWith("ext") })
        // S4-paywall: T1 opens inside onboarding (O6) only.
        assertTrue("paywall?trigger=onboarding" !in RouteRegistry.routes.values)
    }

    @Test
    fun knownRoutesResolveByExtraAndByLink() {
        val voice = RouteRegistry.resolve("settings/voice", null)
        assertEquals("settings/voice", voice.path)
        assertFalse(voice.unknown)
        assertEquals(voice.path, RouteRegistry.resolve(null, Uri.parse("ui://florisboard/settings/voice")).path)
        assertEquals("settings/offline-languages", RouteRegistry.resolve(null, Uri.parse("app://settings/offline-languages")).path)
        assertEquals("translator", RouteRegistry.resolve(null, Uri.parse("app://translator?x=1#y")).path)
        assertEquals(RouteRegistry.Home, RouteRegistry.resolve("home", null))
        assertEquals(RouteRegistry.Home, RouteRegistry.resolve(null, null))
        // S4-paywall: `pro` (the quota card's "See Pro") is the ai_quota paywall; a bare `paywall` is unknown.
        assertEquals("paywall?trigger=ai_quota", RouteRegistry.resolve(null, Uri.parse("ui://florisboard/pro")).path)
        assertTrue(RouteRegistry.resolve("paywall", null).unknown)
    }

    @Test
    fun unknownValuesResolveToHome() {
        val unknown = listOf(
            "paywall", "paywall?trigger=x", "devtools", "ext", "settings", "settings/voice/../devtools", "SETTINGS/VOICE",
            "", " ", "x".repeat(10_000), "settings/voicex",
        )
        for (value in unknown) {
            val r = RouteRegistry.resolve(value, null)
            assertTrue(r.unknown && r.isHome, "'${value.take(20)}' -> $r")
        }
        val links = listOf(
            "ui://florisboard/devtools", "ui://other/settings/voice", "https://florisboard/settings/voice",
            "app://paywall?trigger=onboarding", "app://", "mailto:x@y", "ui:", "app:///",
        )
        for (link in links) assertTrue(RouteRegistry.resolve(null, Uri.parse(link)).unknown, link)
    }

    @Test
    fun unknownRoutesOpenHomeAndLogDeeplinkUnknownWithoutTheValue() {
        val events = mutableListOf<AnalyticsEvent>()
        val intent = Intent().putExtra(RouteRegistry.EXTRA_OPEN_SCREEN, "paywall?trigger=secret")
        val request = assertIs<AppIntent.OpenRoute>(AppIntents.classify(intent) { events += it })
        assertTrue(request.route.isHome && request.route.unknown)
        assertEquals(listOf("deeplink_unknown"), events.map { it.name })
        assertTrue(events.single().params.isEmpty())

        val link = Intent(Intent.ACTION_VIEW, Uri.parse("ui://florisboard/devtools")).addCategory(Intent.CATEGORY_BROWSABLE)
        assertTrue(assertIs<AppIntent.OpenRoute>(AppIntents.classify(link) { events += it }).route.unknown)
        assertEquals(2, events.size)
    }

    @Test
    fun knownRoutesAndPlainLaunchesLogNothing() {
        val events = mutableListOf<AnalyticsEvent>()
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("ui://florisboard/settings/offline-languages"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        assertEquals("settings/offline-languages", assertIs<AppIntent.OpenRoute>(AppIntents.classify(link) { events += it }).route.path)
        assertNull(AppIntents.classify(Intent(Intent.ACTION_MAIN)) { events += it })
        assertNull(AppIntents.classify(null) { events += it })
        assertTrue(events.isEmpty())
    }

    @Test
    fun translatorTextIsTakenOnlyForTheTranslatorRoute() {
        val toTranslator = Intent().putExtra(RouteRegistry.EXTRA_OPEN_SCREEN, "translator")
            .putExtra(RouteRegistry.EXTRA_TRANSLATOR_TEXT, "hola")
        assertEquals("hola", assertIs<AppIntent.OpenRoute>(AppIntents.classify(toTranslator) {}).translatorText)
        val toVoice = Intent().putExtra(RouteRegistry.EXTRA_OPEN_SCREEN, "settings/voice")
            .putExtra(RouteRegistry.EXTRA_TRANSLATOR_TEXT, "hola")
        assertNull(assertIs<AppIntent.OpenRoute>(AppIntents.classify(toVoice) {}).translatorText)
        val long = Intent().putExtra(RouteRegistry.EXTRA_OPEN_SCREEN, "translator")
            .putExtra(RouteRegistry.EXTRA_TRANSLATOR_TEXT, "a".repeat(9_000))
        assertEquals(5_000, assertIs<AppIntent.OpenRoute>(AppIntents.classify(long) {}).translatorText?.length)
        assertFalse(AppIntent.OpenRoute(RouteRegistry.Home, "secret").toString().contains("secret"))
    }

    @Test
    fun extensionImportKeepsWorkingAndAnEmptyClipIsIgnored() {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("content://downloads/ext.flex"))
        assertIs<AppIntent.ImportExtension>(AppIntents.classify(view) {})
        // A clip item without a URI (upstream read getItemAt(0).uri with !!).
        val textClip = Intent(Intent.ACTION_SEND).apply { clipData = ClipData.newPlainText("x", "t") }
        assertNull(AppIntents.classify(textClip) {})
        val share = Intent(Intent.ACTION_SEND).apply { clipData = ClipData.newRawUri("x", Uri.parse("content://a/b")) }
        assertIs<AppIntent.ImportExtension>(AppIntents.classify(share) {})
    }

    @Test
    fun handoffGivesTheTextOnce() {
        TranslatorHandoff.offer("hola")
        assertEquals("hola", TranslatorHandoff.take())
        assertNull(TranslatorHandoff.take())
    }

    // S5-dict: the Dictionary screen and the keyboard card's "More"
    @Test
    fun dictionaryResolvesAndTakesItsWordOnlyFromTheExtra() {
        val byExtra = RouteRegistry.resolve("dictionary", null)
        assertEquals("dictionary", byExtra.path)
        assertFalse(byExtra.unknown)
        assertEquals("dictionary", RouteRegistry.resolve(null, Uri.parse("app://dictionary")).path)

        val events = mutableListOf<AnalyticsEvent>()
        val withQuery = Intent().putExtra(RouteRegistry.EXTRA_OPEN_SCREEN, "dictionary?word=x")
        val request = assertIs<AppIntent.OpenRoute>(AppIntents.classify(withQuery) { events += it })
        assertTrue(request.route.isHome && request.route.unknown)
        assertNull(request.dictionaryWord)
        assertEquals(listOf("deeplink_unknown"), events.map { it.name })
        assertTrue(events.single().params.isEmpty())

        val more = Intent().putExtra(RouteRegistry.EXTRA_OPEN_SCREEN, "dictionary")
            .putExtra(RouteRegistry.EXTRA_DICTIONARY_WORD, "  run ")
        assertEquals("run", assertIs<AppIntent.OpenRoute>(AppIntents.classify(more) {}).dictionaryWord)
        val tooLong = Intent().putExtra(RouteRegistry.EXTRA_OPEN_SCREEN, "dictionary")
            .putExtra(RouteRegistry.EXTRA_DICTIONARY_WORD, "a".repeat(65))
        assertNull(assertIs<AppIntent.OpenRoute>(AppIntents.classify(tooLong) {}).dictionaryWord)
        val toTranslator = Intent().putExtra(RouteRegistry.EXTRA_OPEN_SCREEN, "translator")
            .putExtra(RouteRegistry.EXTRA_DICTIONARY_WORD, "run")
        assertNull(assertIs<AppIntent.OpenRoute>(AppIntents.classify(toTranslator) {}).dictionaryWord)
        assertFalse(AppIntent.OpenRoute(byExtra, dictionaryWord = "secret").toString().contains("secret"))

        DictionaryHandoff.offer("run")
        assertEquals("run", DictionaryHandoff.take())
        assertNull(DictionaryHandoff.take())
    }

    @Test
    fun phrasebookResolvesByExtraAndByLink() {
        val byExtra = RouteRegistry.resolve("phrasebook", null)
        assertEquals("phrasebook", byExtra.path)
        assertFalse(byExtra.unknown)
        assertEquals("phrasebook", RouteRegistry.resolve(null, Uri.parse("app://phrasebook")).path)
        assertEquals("phrasebook", RouteRegistry.resolve(null, Uri.parse("ui://florisboard/phrasebook?x=1")).path)
        assertTrue(RouteRegistry.resolve("phrasebook/greetings", null).unknown)
    }

    @Test
    fun theDictionaryIntentRoundTripsThroughTheRouteAllowList() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val intent = AppIntents.dictionaryIntent(app, "book")
        assertEquals(FlorisAppActivity::class.java.name, intent.component?.className)
        assertEquals(RouteRegistry.DICTIONARY, intent.getStringExtra(RouteRegistry.EXTRA_OPEN_SCREEN))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        val open = assertIs<AppIntent.OpenRoute>(AppIntents.classify(intent) {})
        assertEquals("dictionary", open.route.path)
        assertEquals("book", open.dictionaryWord)
    }
}
