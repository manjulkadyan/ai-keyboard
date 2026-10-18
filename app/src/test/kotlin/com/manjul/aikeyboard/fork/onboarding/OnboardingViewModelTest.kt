/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.onboarding

import app.cash.turbine.test
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.datastore.LanguagePair
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeIme(enabled: Boolean = false, selected: Boolean = false) : ImeStatusSource {
    override val enabled = MutableStateFlow(enabled)
    override val selected = MutableStateFlow(selected)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class OnboardingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = FeatureSettingsStore(RuntimeEnvironment.getApplication(), dispatcher)
    private val events = mutableListOf<AnalyticsEvent>()
    private val seeded = mutableListOf<List<String>>()
    private var now = 1_000_000L

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun vm(ime: FakeIme, locales: List<String> = emptyList()) = OnboardingViewModel(
        store = store,
        imeStatus = ime,
        analytics = { events += it },
        ensureSubtypes = { seeded += it },
        clock = { now },
        scope = scope,
        systemLocaleTags = locales,
    )

    private fun named(name: String) = events.filter { it.name == name }

    @Test
    fun freshStartIsWelcomeAndLogsStart() = runTest {
        val vm = vm(FakeIme())
        assertEquals(OnboardingStep.WELCOME, vm.state.value.step)
        assertTrue(vm.state.value.loaded)
        assertEquals(mapOf<String, Any>("source" to "launcher"), named("onboarding_start").single().params)
        assertEquals(now, store.flow.first().firstOpenAt)
    }

    @Test
    fun enabledAdvancesToSelectAndSelectedToLanguages() = runTest {
        val ime = FakeIme()
        val vm = vm(ime)
        vm.next()
        assertEquals(OnboardingStep.ENABLE, vm.state.value.step)
        vm.state.test {
            assertEquals(OnboardingStep.ENABLE, awaitItem().step)
            now += 12_000
            ime.enabled.value = true
            assertEquals(OnboardingStep.SELECT, awaitItem().step)
            now += 3_000
            ime.selected.value = true
            assertEquals(OnboardingStep.LANGUAGES, awaitItem().step)
        }
        assertEquals("5-15", named("ime_enabled").single().params["seconds_since_start_bucket"])
        assertEquals("15-30", named("ime_selected").single().params["seconds_since_start_bucket"])
    }

    @Test
    fun resumesAtSelectAfterProcessDeath() = runTest {
        val ime = FakeIme()
        val first = vm(ime)
        first.next()
        ime.enabled.value = true
        assertEquals(OnboardingStep.SELECT, first.state.value.step)
        store.flow.first { it.onboardingStep == OnboardingStep.SELECT.ordinal }
        scope.cancel()

        val scope2 = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val second = OnboardingViewModel(store, FakeIme(enabled = true), { events += it }, { }, { now }, scope2)
            assertEquals(OnboardingStep.SELECT, second.state.value.step)
            assertEquals(1, named("onboarding_start").size)
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun languagesChosenSeedsSubtypesAndLogs() = runTest {
        val ime = FakeIme(enabled = true, selected = true)
        val vm = vm(ime)
        vm.next()
        assertEquals(OnboardingStep.LANGUAGES, vm.state.value.step)
        vm.onLanguagesChosen(listOf("en-US", "ur-PK"), "en" to "ur")
        assertEquals(listOf(listOf("en-US", "ur-PK")), seeded)
        assertEquals(mapOf<String, Any>("count" to 2, "has_rtl" to true), named("languages_set").single().params)
        assertEquals(OnboardingStep.TRY_IT, vm.state.value.step)
        assertEquals(LanguagePair("en", "ur"), store.flow.first { it.translatePair != null }.translatePair)
    }

    @Test
    fun defaultSelectionFromSystemLocales() {
        assertEquals(listOf("ur-PK"), defaultLanguageSelection(listOf("ur_PK")))
        assertEquals(listOf("es-ES", "en-US"), defaultLanguageSelection(listOf("es_MX", "en_GB")))
        assertEquals(listOf("en-US"), defaultLanguageSelection(listOf("de_DE", "ja_JP")))
        assertEquals(listOf("en-US"), defaultLanguageSelection(emptyList()))
        assertEquals("en" to "ur", defaultTranslatePair(listOf("en-US", "ur-PK")))
        assertEquals("ur" to "en", defaultTranslatePair(listOf("ur-PK")))
        assertEquals("en" to "es", defaultTranslatePair(listOf("en-US")))
    }

    @Test
    fun skipFromTryItCompletes() = runTest {
        val vm = vm(FakeIme(enabled = true, selected = true))
        vm.next()
        vm.onLanguagesChosen(listOf("en-US"), "en" to "es")
        vm.skip()
        assertEquals(OnboardingStep.DONE, vm.state.value.step)
        assertEquals(mapOf<String, Any>("skipped" to true), named("onboarding_complete").single().params)
        assertTrue(store.flow.first { it.onboardingDone }.onboardingDone)
    }

    @Test
    fun skipBeforeKeyboardIsSetUpIsIgnored() = runTest {
        val vm = vm(FakeIme())
        vm.skip()
        assertEquals(OnboardingStep.WELCOME, vm.state.value.step)
        assertTrue(named("onboarding_complete").isEmpty())
    }

    @Test
    fun returningUserWithKeyboardReadyStartsDone() = runTest {
        store.update { it.copy(onboardingDone = true, firstOpenAt = 5L) }
        val vm = vm(FakeIme(enabled = true, selected = true))
        assertEquals(OnboardingStep.DONE, vm.state.value.step)
        assertTrue(named("onboarding_start").isEmpty())
    }

    @Test
    fun returningUserWhoDeselectedGoesBackToSelectThenDone() = runTest {
        store.update { it.copy(onboardingDone = true, firstOpenAt = 5L) }
        val ime = FakeIme(enabled = true, selected = false)
        val vm = vm(ime)
        assertEquals(OnboardingStep.SELECT, vm.state.value.step)
        ime.selected.value = true
        assertEquals(OnboardingStep.DONE, vm.state.value.step)
    }

    @Test
    fun reentryLogsNoFunnelEvents() = runTest {
        store.update { it.copy(onboardingDone = true, firstOpenAt = 5L) }
        val ime = FakeIme(enabled = false, selected = false)
        val vm = vm(ime)
        assertEquals(OnboardingStep.ENABLE, vm.state.value.step)
        ime.enabled.value = true
        assertEquals(OnboardingStep.SELECT, vm.state.value.step)
        ime.selected.value = true
        assertEquals(OnboardingStep.DONE, vm.state.value.step)
        assertEquals(emptyList(), events.filter {
            it.name in setOf("onboarding_step_view", "ime_enabled", "ime_selected", "onboarding_complete", "onboarding_start")
        })
    }

    @Test
    fun recordVoiceConsentPersistsEvenWhenCallerIsCancelled() = runTest {
        val job = scope.launch { recordVoiceConsent(store, 4242L) }
        job.cancel()
        assertEquals(4242L, store.flow.first { it.consentVoiceNoticeAt != 0L }.consentVoiceNoticeAt)
    }

    @Test
    fun micDenialLogsAndStaysOnTryIt() = runTest {
        val vm = vm(FakeIme(enabled = true, selected = true))
        vm.next()
        vm.onLanguagesChosen(listOf("en-US"), "en" to "es")
        vm.onMicPermissionResult(false)
        assertEquals(mapOf<String, Any>("granted" to false), named("voice_permission_result").single().params)
        assertEquals(OnboardingStep.TRY_IT, vm.state.value.step)
        assertEquals(false, vm.state.value.micGranted)
        vm.next()
        assertEquals(OnboardingStep.DONE, vm.state.value.step)
        assertFalse(named("onboarding_complete").single().params["skipped"] as Boolean)
    }

    @Test
    fun onboardingPackageHasNoAdsOrBillingImports() {
        val dir = File("src/main/kotlin/com/manjul/aikeyboard/fork/onboarding")
        val files = dir.listFiles { f -> f.extension == "kt" }.orEmpty()
        assertTrue(files.isNotEmpty(), "run from the :app module directory")
        files.forEach { f ->
            f.readLines().filter { it.startsWith("import ") }.forEach {
                assertFalse("core.ads" in it || "core.billing" in it || "gms.ads" in it, "${f.name}: $it")
            }
        }
    }
}
