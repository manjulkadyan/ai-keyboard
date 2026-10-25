/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.routes

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.TranslateResult
import com.manjul.aikeyboard.fork.processtext.FakeSheetModels
import com.manjul.aikeyboard.fork.processtext.FakeSpeaker
import com.manjul.aikeyboard.fork.processtext.ProcessTextInput
import com.manjul.aikeyboard.fork.processtext.TranslateSheetViewModel
import com.manjul.aikeyboard.fork.processtext.TranslateTextActivity
import kotlin.random.Random
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PRD FR-32 AC1: 500 seeded random intents (random extras of random types, random `app://` / `ui://` URIs, oversized
 * strings) never crash `FlorisAppActivity`'s intent handling or `TranslateTextActivity`, never open a route outside
 * the allow-list, and never return a result from the Translate sheet without the user's Replace.
 *
 * `FlorisAppActivity` needs the full `FlorisApplication` (preference store, extension manager), which Robolectric
 * cannot start, so its intent handling is fuzzed through [AppIntents.classify] and [RouteRegistry], the only code
 * that reads the intent. `TranslateTextActivity` is launched with every intent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DeepLinkFuzzTest {
    private val app = RuntimeEnvironment.getApplication()
    private val store = FeatureSettingsStore(app, UnconfinedTestDispatcher())
    private val events = mutableListOf<AnalyticsEvent>()

    @Before
    fun setUp() {
        TranslateTextActivity.depsOverride = TranslateTextActivity.Deps(
            sheet = {
                TranslateSheetViewModel.Deps(
                    store = store,
                    translate = { req, _, _ -> TranslateResult.Ok(req.text, req.source ?: Lang.English, EngineId.MLKIT) },
                    detect = { _, fallback -> fallback },
                    models = FakeSheetModels(),
                    speaker = FakeSpeaker(),
                    history = {},
                    imeEnabled = flowOf(true),
                    analytics = { events += it },
                    clock = { 0L },
                )
            },
            installVoiceIntent = { Intent() },
            targets = { listOf(Lang.English) },
        )
    }

    @After
    fun tearDown() {
        TranslateTextActivity.depsOverride = null
    }

    @Test
    fun florisAppIntentHandlingSurvives500RandomIntents() {
        repeat(RUNS) { seed ->
            val intent = randomIntent(Random(seed))
            val logged = mutableListOf<AnalyticsEvent>()
            val request = runCatching { AppIntents.classify(intent) { logged += it } }
                .getOrElse { throw AssertionError("seed $seed crashed: $it", it) }
            if (request is AppIntent.OpenRoute) {
                assertTrue(request.route.path in RouteRegistry.routes.values, "seed $seed opened ${request.route}")
                assertEquals(request.route.unknown, logged.isNotEmpty(), "seed $seed")
            } else {
                assertTrue(logged.isEmpty(), "seed $seed")
            }
            for (event in logged) assertTrue(event.params.isEmpty(), "seed $seed: deeplink_unknown carries no value")
        }
    }

    @Test
    fun translateTextActivitySurvives500RandomIntents() {
        var opened = 0
        repeat(RUNS) { seed ->
            val r = Random(seed)
            val intent = randomIntent(r).setClass(app, TranslateTextActivity::class.java)
            // Every other intent has a valid shape under the random extras, so the sheet itself is fuzzed too.
            if (seed % 2 == 0) {
                val process = r.nextBoolean()
                intent.action = if (process) Intent.ACTION_PROCESS_TEXT else Intent.ACTION_SEND
                intent.setDataAndType(null, "text/plain")
                intent.putExtra(if (process) Intent.EXTRA_PROCESS_TEXT else Intent.EXTRA_TEXT, "a" + randomString(r, 6_000))
            }
            val valid = ProcessTextInput.parse(intent) != null
            if (valid) opened++
            val scenario = runCatching { ActivityScenario.launchActivityForResult<TranslateTextActivity>(intent) }
                .getOrElse { throw AssertionError("seed $seed crashed: $it", it) }
            shadowOf(Looper.getMainLooper()).idle()
            if (!valid) assertEquals(Lifecycle.State.DESTROYED, scenario.state, "seed $seed should finish")
            scenario.close()
            shadowOf(Looper.getMainLooper()).idle()
            // Nobody pressed Replace: never a result.
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode, "seed $seed")
        }
        assertTrue(opened >= RUNS / 2, "only $opened of $RUNS intents opened the sheet")
    }

    private fun randomIntent(r: Random): Intent {
        val intent = Intent()
        r.pick(ACTIONS)?.let(intent::setAction)
        val type = r.pick(TYPES)
        val data = if (r.nextInt(3) == 0) randomUri(r) else null
        when {
            data != null && type != null -> intent.setDataAndType(data, type)
            data != null -> intent.data = data
            type != null -> intent.type = type
        }
        repeat(r.nextInt(3)) { intent.addCategory(r.pick(CATEGORIES)) }
        repeat(r.nextInt(6)) { putRandomExtra(intent, r.pick(KEYS) ?: randomString(r, 12), r) }
        if (r.nextInt(5) == 0) {
            intent.clipData = if (r.nextBoolean()) ClipData.newRawUri("x", randomUri(r)) else ClipData.newPlainText("x", randomString(r, 40))
        }
        return intent
    }

    private fun putRandomExtra(intent: Intent, key: String, r: Random) {
        when (r.nextInt(12)) {
            0 -> intent.putExtra(key, randomString(r, 64))
            1 -> intent.putExtra(key, "x".repeat(r.nextInt(10_000, 200_000)))
            2 -> intent.putExtra(key, r.nextInt())
            3 -> intent.putExtra(key, r.nextLong())
            4 -> intent.putExtra(key, r.nextBoolean())
            5 -> intent.putExtra(key, Rect(r.nextInt(), r.nextInt(), r.nextInt(), r.nextInt()))
            6 -> intent.putExtra(key, Bundle().apply { putString("k", randomString(r, 8)) })
            7 -> intent.putExtra(key, r.nextBytes(r.nextInt(64)))
            8 -> intent.putExtra(key, arrayOf(randomString(r, 8), randomString(r, 8)))
            9 -> intent.putExtra(key, randomUri(r))
            10 -> intent.putExtra(key, r.pick(ROUTE_VALUES))
            else -> intent.putExtra(key, java.util.ArrayList(listOf(randomString(r, 4))))
        }
    }

    private fun randomUri(r: Random): Uri = Uri.parse(
        when (r.nextInt(6)) {
            0 -> "ui://florisboard/" + (r.pick(ROUTE_VALUES) ?: randomString(r, 20))
            1 -> "app://" + (r.pick(ROUTE_VALUES) ?: randomString(r, 20))
            2 -> "app://" + "a/".repeat(r.nextInt(1, 5_000))
            3 -> "content://" + randomString(r, 16) + "/" + randomString(r, 16)
            4 -> randomString(r, 30)
            else -> r.pick(listOf("ui:", "app:", "app:///", "ui://florisboard", "https://x.y/z", "file:///sdcard/a", "javascript:x"))
        },
    )

    private fun randomString(r: Random, max: Int): String {
        val chars = "abcz09/:?#&=%.-_ \u0000😀‮اردو"
        return String(CharArray(r.nextInt(max + 1)) { chars[r.nextInt(chars.length)] })
    }

    private fun <T> Random.pick(list: List<T>): T = list[nextInt(list.size)]

    private companion object {
        const val RUNS = 500
        val ACTIONS = listOf(
            Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_PROCESS_TEXT, Intent.ACTION_MAIN, Intent.ACTION_EDIT,
            null, "random.ACTION",
        )
        val TYPES = listOf("text/plain", "text/html", "image/*", "*/*", null, "", "TEXT/PLAIN")
        val CATEGORIES = listOf(Intent.CATEGORY_BROWSABLE, Intent.CATEGORY_DEFAULT, Intent.CATEGORY_LAUNCHER, "x.CAT")
        val KEYS = listOf(
            RouteRegistry.EXTRA_OPEN_SCREEN, RouteRegistry.EXTRA_TRANSLATOR_TEXT, Intent.EXTRA_TEXT, Intent.EXTRA_PROCESS_TEXT,
            Intent.EXTRA_PROCESS_TEXT_READONLY, Intent.EXTRA_STREAM, "android-support-nav:controller:deepLinkIds", null,
        )
        val ROUTE_VALUES = listOf(
            "home", "translator", "settings/home", "settings/voice", "settings/offline-languages",
            "settings/privacy/private-apps", "paywall", "paywall?trigger=x", "devtools", "ext/edit", "", "../", null,
        )
    }
}
