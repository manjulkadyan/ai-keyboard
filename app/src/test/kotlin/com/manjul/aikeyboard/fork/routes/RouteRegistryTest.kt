/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.routes

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import com.manjul.aikeyboard.app.Deeplink
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
            val destination = deeplinks[path]
            if (destination == null && name == RouteRegistry.TRANSLATOR) {
                // Task 10 adds Routes.Translator (@Deeplink("translator")); until then the activity's graph check
                // sends this route to Home. Checked like every other entry as soon as it exists.
                continue
            }
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
            setOf("home", "translator", "settings/home", "settings/voice", "settings/offline-languages", "settings/privacy/private-apps"),
            RouteRegistry.routes.keys,
        )
        assertTrue(RouteRegistry.routes.values.none { it.startsWith("devtools") || it.startsWith("ext") || "paywall" in it })
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
}
