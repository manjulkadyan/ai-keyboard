/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Keyboard show and key commit budgets of TRD §6.4 (S4 Task 13), read from the trace sections that the app adds:
 *
 * | Test | Section | Budget (mid tier) |
 * |---|---|---|
 * | [coldShow] | `aikb_ime_show` after the app process was killed | <= 350 ms p50, <= 600 ms p90 |
 * | [warmShow] | `aikb_ime_show` with the keyboard process alive | <= 80 ms p90 |
 * | [keyCommit] | `aikb_key_commit` per key tap | <= 16 ms p90 |
 *
 * Macrobenchmark reports min, median and max over the iterations. For the p90 values, open the saved Perfetto
 * traces, or raise `iterations`. Run on a physical mid-tier phone (Pixel 6a class), see
 * `docs/sprint-4-device-checklist.md` G.1:
 *
 * `./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.manjul.aikeyboard.benchmark.ImeShowBenchmark`
 *
 * The text field is [ImeHostActivity] in this APK, so the keyboard is the only thing in the target process.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class ImeShowBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val hostPackage = InstrumentationRegistry.getInstrumentation().context.packageName

    @Test
    fun coldShow() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(TraceSectionMetric(IME_SHOW, TraceSectionMetric.Mode.First)),
        iterations = ITERATIONS,
        setupBlock = {
            selectKeyboard()
            closeHost()
            killProcess()
        },
    ) {
        openHostAndWaitForKeyboard()
    }

    @Test
    fun warmShow() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(TraceSectionMetric(IME_SHOW, TraceSectionMetric.Mode.First)),
        iterations = ITERATIONS,
        setupBlock = {
            selectKeyboard()
            openHostAndWaitForKeyboard()
            device.pressBack() // hides the keyboard, the process stays
            device.waitForIdle()
        },
    ) {
        device.findObject(By.desc(ImeHostActivity.FIELD_DESCRIPTION))?.click()
        device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), WAIT_MS)
        device.waitForIdle()
    }

    @Test
    fun keyCommit() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(
            TraceSectionMetric(KEY_COMMIT, TraceSectionMetric.Mode.Average),
            TraceSectionMetric(KEY_COMMIT, TraceSectionMetric.Mode.Max),
            TraceSectionMetric(KEY_COMMIT, TraceSectionMetric.Mode.Count),
        ),
        iterations = ITERATIONS,
        setupBlock = {
            selectKeyboard()
            openHostAndWaitForKeyboard()
        },
    ) {
        val keyboard = device.findObject(By.pkg(TARGET_PACKAGE)) ?: error("The keyboard is not shown")
        val bounds = keyboard.visibleBounds
        // Taps across the second key row from the bottom of the letter area. The exact letters do not matter.
        val y = bounds.top + bounds.height() * 3 / 5
        repeat(TAPS) { i ->
            val x = bounds.left + bounds.width() * (1 + 2 * (i % 9)) / 18
            device.click(x, y)
        }
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.selectKeyboard() {
        device.executeShellCommand("ime enable $IME_ID")
        device.executeShellCommand("ime set $IME_ID")
    }

    // The host runs in this benchmark process, so it cannot be force-stopped. Home closes its window.
    private fun MacrobenchmarkScope.closeHost() {
        pressHome()
    }

    private fun MacrobenchmarkScope.openHostAndWaitForKeyboard() {
        device.executeShellCommand("am start -W -n $hostPackage/${ImeHostActivity::class.java.name}")
        device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), WAIT_MS)
        device.waitForIdle()
    }

    private companion object {
        const val IME_SHOW = "aikb_ime_show"
        const val KEY_COMMIT = "aikb_key_commit"
        const val ITERATIONS = 10
        const val TAPS = 30
        const val WAIT_MS = 5_000L
    }
}
