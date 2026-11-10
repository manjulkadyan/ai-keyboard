/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.isolation

import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TRD §11.1 1.1(c), S4 Task 6: a process that only runs the keyboard never loads the GMA SDK.
 *
 * The instrumentation starts the app process fresh and starts no app Activity, so this process is an IME-only cold
 * start. (`am force-stop` on the app would kill this test, because the instrumentation runs in the app process.)
 * The test enables and selects the keyboard, opens a text field in the test APK's own process and types for 30 s.
 * Then no GMA class is loaded in the app process and the package declares no GMA provider.
 *
 * I-1 (S4 final review): on a fresh install no Analytics consent is stored, so the same session must not start the
 * default FirebaseApp (Analytics, Crashlytics and Remote Config use it). `FirebaseApp` is read through reflection,
 * because Firebase is not on the `:app` compile classpath.
 */
@RunWith(AndroidJUnit4::class)
class ImeOnlyColdStartTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val imeId = "${app.packageName}/com.manjul.aikeyboard.FlorisImeService"
    private var previousIme: String = ""

    private fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return FileInputStream(pfd.fileDescriptor).use { it.readBytes().decodeToString() }
            .also { ParcelFileDescriptor.AutoCloseInputStream(pfd).close() }
    }

    private fun isLoaded(className: String): Boolean {
        val findLoadedClass = ClassLoader::class.java.getDeclaredMethod("findLoadedClass", String::class.java)
        findLoadedClass.isAccessible = true
        var loader: ClassLoader? = app.classLoader
        while (loader != null) {
            if (findLoadedClass.invoke(loader, className) != null) return true
            loader = loader.parent
        }
        return false
    }

    @Before
    fun setUp() {
        previousIme = shell("settings get secure default_input_method").trim()
        shell("ime enable $imeId")
        shell("ime set $imeId")
    }

    @After
    fun tearDown() {
        shell("input keyevent KEYCODE_HOME")
        if (previousIme.isNotEmpty() && previousIme != "null") shell("ime set $previousIme")
    }

    @Test
    fun typingWithOnlyTheKeyboardLoadsNoAdSdk() {
        assertTrue("Fresh process expected, GMA already loaded", GMA_CLASSES.none(::isLoaded))

        val host = "${instrumentation.context.packageName}/${EditTextHostActivity::class.java.name}"
        shell("am start -W -n $host")
        val current = shell("dumpsys input_method")
        assertTrue("The keyboard is not the current input method", current.contains("mCurMethodId=$imeId"))

        val end = SystemClock.uptimeMillis() + TYPING_MS
        while (SystemClock.uptimeMillis() < end) {
            shell("input text hello%sworld")
            shell("input keyevent KEYCODE_SPACE")
        }

        for (name in GMA_CLASSES) assertNull("$name was loaded in the app process", name.takeIf(::isLoaded))
        val providers = app.packageManager
            .getPackageInfo(app.packageName, PackageManager.GET_PROVIDERS)
            .providers.orEmpty()
            .map { it.name }
            .filter { it.startsWith("com.google.android.gms.ads.") || it.endsWith(".MobileAdsInitProvider") }
        assertEquals(emptyList<String>(), providers)
        assertTrue("The default FirebaseApp started before consent", !hasDefaultFirebaseApp())
    }

    private fun hasDefaultFirebaseApp(): Boolean {
        if (!isLoaded(FIREBASE_APP)) return false
        val firebaseApp = Class.forName(FIREBASE_APP, false, app.classLoader)
        val apps = firebaseApp.getMethod("getApps", Context::class.java).invoke(null, app) as List<*>
        return apps.any { firebaseApp.getMethod("getName").invoke(it) == "[DEFAULT]" }
    }

    private companion object {
        const val TYPING_MS = 30_000L
        const val FIREBASE_APP = "com.google.firebase.FirebaseApp"
        val GMA_CLASSES = listOf(
            "com.google.android.gms.ads.MobileAds",
            "com.manjul.aikeyboard.core.ads.gma.GoogleMobileAdsFacade",
            "com.manjul.aikeyboard.app.ads.AdsAppGraph",
        )
    }
}
