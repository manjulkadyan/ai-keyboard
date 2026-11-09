/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 * Modified by AI Keyboard contributors, 2026. FORK(S4-perf): shared target package and IME id, TRD §6.4 budget
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.manjul.aikeyboard.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run this benchmark from Studio to see startup measurements, and captured system traces
 * for investigating your app's performance from a cold state.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class ColdStartupBenchmark : AbstractStartupBenchmark(StartupMode.COLD)

/**
 * Run this benchmark from Studio to see startup measurements, and captured system traces
 * for investigating your app's performance from a warm state.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class WarmStartupBenchmark : AbstractStartupBenchmark(StartupMode.WARM)

/**
 * Run this benchmark from Studio to see startup measurements, and captured system traces
 * for investigating your app's performance from a hot state.
 */
@RunWith(AndroidJUnit4ClassRunner::class)
class HotStartupBenchmark : AbstractStartupBenchmark(StartupMode.HOT)

// FORK(S4-perf): the app package of the `benchmark` build type and our IME id, shared with ImeShowBenchmark
internal const val TARGET_PACKAGE = "com.manjul.aikeyboard"
internal const val IME_ID = "$TARGET_PACKAGE/.FlorisImeService"

/**
 * TRD §6.4 budget (mid tier): app cold start to first frame <= 700 ms p50 (`ColdStartupBenchmark`, FORK(S4-perf)).
 *
 * Base class for benchmarks with different startup modes.
 * Enables app startups from various states of baseline profile or [CompilationMode]s.
 *
 * Original source of this test: https://github.com/android/nowinandroid/blob/b4a2f35ed23b2cf40fe90311bdac2688d9cb69e2/benchmark/src/main/java/com/google/samples/apps/nowinandroid/startup/StartupBenchmark.kt
 */
abstract class AbstractStartupBenchmark(private val startupMode: StartupMode) {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfileDisabled() = startup(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Disable, warmupIterations = 1)
    )

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    @Test
    fun startupFullCompilation() = startup(CompilationMode.Full())

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE, // FORK(S4-perf)
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = startupMode,
        setupBlock = {
            pressHome()
            device.executeShellCommand("ime enable $IME_ID") // FORK(S4-perf)
            device.executeShellCommand("ime set $IME_ID")
        }
    ) {
        startActivityAndWait()
        device.waitForIdle(5000)
    }
}
