/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.processtext

import android.app.Activity
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.translate.EngineId
import com.manjul.aikeyboard.core.translate.Lang
import com.manjul.aikeyboard.core.translate.TranslateResult
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TranslateTextActivityTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = RuntimeEnvironment.getApplication()
    private val store = FeatureSettingsStore(app, UnconfinedTestDispatcher())
    private val events = mutableListOf<AnalyticsEvent>()
    private val imeEnabled = MutableStateFlow(true)
    private var now = 1_000_000_000L
    private var translations = 0

    @Before
    fun setUp() {
        TranslateTextActivity.depsOverride = TranslateTextActivity.Deps(
            sheet = {
                TranslateSheetViewModel.Deps(
                    store = store,
                    // No network: a fake engine stands in for ML Kit.
                    translate = { req, _, _ ->
                        translations++
                        TranslateResult.Ok("T(${req.text})", req.source ?: Lang("es"), EngineId.MLKIT)
                    },
                    detect = { _, _ -> Lang("es") },
                    models = FakeSheetModels(),
                    speaker = FakeSpeaker(),
                    history = {},
                    imeEnabled = imeEnabled,
                    analytics = { events += it },
                    clock = { now },
                )
            },
            installVoiceIntent = { Intent("test.INSTALL_VOICE") },
            targets = { listOf(Lang.English, Lang("fr")) },
        )
    }

    @After
    fun tearDown() {
        TranslateTextActivity.depsOverride = null
    }

    private fun processText(text: String, readOnly: Boolean = false) =
        Intent(app, TranslateTextActivity::class.java)
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, readOnly)

    private fun send(text: String) =
        Intent(app, TranslateTextActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)

    private fun launch(intent: Intent): ActivityScenario<TranslateTextActivity> {
        val scenario = ActivityScenario.launchActivityForResult<TranslateTextActivity>(intent)
        idle()
        return scenario
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun opens() = events.filter { it.name == "process_text_open" }

    @Test
    fun editableSelectionReplacesWithTheTranslation() {
        val scenario = launch(processText("hola"))
        compose.onNodeWithText("T(hola)").assertExists()
        compose.onNodeWithText("Replace").performClick()
        idle()
        val result = scenario.result
        assertEquals(Activity.RESULT_OK, result.resultCode)
        assertEquals("T(hola)", result.resultData.getStringExtra(Intent.EXTRA_PROCESS_TEXT))
        assertEquals(1, events.count { it.name == "process_text_replace" })
        assertEquals(listOf<Any?>("process_text", true), opens().single().let { listOf(it.params["action"], it.params["editable"]) })
    }

    @Test
    fun readOnlySelectionHasNoReplaceAndReturnsNothing() {
        val scenario = launch(processText("hola", readOnly = true))
        compose.onNodeWithText("T(hola)").assertExists()
        compose.onNodeWithText("Replace").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        idle()
        val result = scenario.result
        assertEquals(Activity.RESULT_CANCELED, result.resultCode)
        assertNull(result.resultData?.getStringExtra(Intent.EXTRA_PROCESS_TEXT))
        assertEquals(false, opens().single().params["editable"])
    }

    @Test
    fun copyDoesNotReturnAResult() {
        val scenario = launch(processText("hola", readOnly = true))
        compose.onNodeWithText("Copy").performClick()
        idle()
        scenario.onActivity { it.finish() }
        idle()
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }

    @Test
    fun shareHasNoReplace() {
        launch(send("hello"))
        compose.onNodeWithText("T(hello)").assertExists()
        compose.onNodeWithText("Replace").assertDoesNotExist()
        assertEquals("send", opens().single().params["action"])
    }

    @Test
    fun invalidIntentFinishesWithNoUiAndNoEvents() {
        val invalid = listOf(
            Intent(app, TranslateTextActivity::class.java),
            Intent(app, TranslateTextActivity::class.java).setAction(Intent.ACTION_VIEW),
            Intent(app, TranslateTextActivity::class.java).setAction(Intent.ACTION_SEND).setType("image/png"),
            Intent(app, TranslateTextActivity::class.java).setAction(Intent.ACTION_PROCESS_TEXT)
                .putExtra(Intent.EXTRA_PROCESS_TEXT, android.graphics.Rect()),
        )
        for (intent in invalid) {
            val scenario = ActivityScenario.launchActivityForResult<TranslateTextActivity>(intent)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
        assertTrue(events.isEmpty())
        assertEquals(0, translations)
    }

    @Test
    fun openIsLoggedOnceAcrossRotation() {
        val scenario = launch(processText("hola"))
        scenario.recreate()
        idle()
        assertEquals(1, opens().size)
        assertEquals(1, translations)
    }

    @Test
    fun enableKeyboardCardShowsWhenNotEnabledAndNotAgainWithin7Days() {
        imeEnabled.value = false
        val first = launch(processText("hola"))
        compose.onNodeWithText("Enable keyboard").assertExists()
        first.close()
        now += 6L * 24 * 60 * 60 * 1000
        launch(processText("hola"))
        compose.onNodeWithText("Enable keyboard").assertDoesNotExist()
    }

    @Test
    fun enableKeyboardCardHiddenWhenTheKeyboardIsEnabled() {
        launch(processText("hola"))
        compose.onNodeWithText("Enable keyboard").assertDoesNotExist()
    }

    @Test
    fun openInAppGoesToTheTranslatorRoute() {
        launch(processText("hola"))
        compose.onNodeWithText("Open in app").performClick()
        idle()
        val started = shadowOf(app).nextStartedActivity
        assertEquals("translator", started.getStringExtra("open_screen"))
        assertEquals("hola", started.getStringExtra("com.manjul.aikeyboard.extra.TRANSLATOR_TEXT"))
    }

    @Test
    fun theSheetNeverReferencesAds() {
        // Review focus: no ads, no interstitial, no :core:ads in the Translate sheet.
        val dir = listOf(File("src/main/kotlin"), File("app/src/main/kotlin"))
            .map { File(it, "com/manjul/aikeyboard/fork/processtext") }
            .first { it.isDirectory }
        val sources = dir.listFiles().orEmpty().filter { it.extension == "kt" }
        assertTrue(sources.size >= 4)
        for (file in sources) {
            val text = file.readText()
            assertTrue("core.ads" !in text && "Interstitial" !in text, "${file.name} references ads")
        }
    }
}
