/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.isolation

import android.content.Context
import android.content.pm.PackageManager
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
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
 *
 * S5 exit: a loaded class is read from a heap dump of this process, never through `ClassLoader.findLoadedClass`.
 * On ART that call falls back to `BaseDexClassLoader` lookup and defines the class from the dex when it is not loaded
 * yet, so asking "is MobileAds loaded?" loaded MobileAds and the first assertion always failed.
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

    /** The names of all classes loaded in this process, from the LOAD_CLASS records of a heap dump. */
    private fun loadedClasses(): Set<String> {
        val dump = File(app.cacheDir, "cold-start.hprof")
        try {
            Debug.dumpHprofData(dump.path)
            return DataInputStream(BufferedInputStream(FileInputStream(dump), 1 shl 16)).use(::readLoadClassNames)
        } finally {
            dump.delete()
        }
    }

    private fun readLoadClassNames(input: DataInputStream): Set<String> {
        while (input.readByte() != 0.toByte()) Unit // "JAVA PROFILE 1.0.x" and its NUL
        val idSize = input.readInt()
        input.readLong() // timestamp
        fun readId(): Long = if (idSize == 4) input.readInt().toLong() and 0xFFFFFFFFL else input.readLong()
        val strings = HashMap<Long, String>()
        val nameIds = ArrayList<Long>()
        while (true) {
            val tag = try {
                input.readUnsignedByte()
            } catch (_: EOFException) {
                break
            }
            input.readInt() // time
            val length = input.readInt().toLong() and 0xFFFFFFFFL
            when (tag) {
                HPROF_STRING -> {
                    val id = readId()
                    val bytes = ByteArray((length - idSize).toInt())
                    input.readFully(bytes)
                    strings[id] = bytes.decodeToString()
                }
                HPROF_LOAD_CLASS -> {
                    input.readInt() // class serial
                    readId() // class object
                    input.readInt() // stack trace serial
                    nameIds += readId()
                }
                else -> {
                    var left = length
                    while (left > 0) left -= input.skip(left).also { if (it <= 0) throw EOFException() }
                }
            }
        }
        return nameIds.mapNotNullTo(HashSet()) { strings[it]?.replace('/', '.') }
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
        val atStart = loadedClasses()
        // The heap dump must list loaded classes, else the checks below pass on an empty set.
        assertTrue("The heap dump lists no loaded classes", APP_CLASS in atStart)
        val loadedAtStart = GMA_CLASSES.filter(atStart::contains)
        assertEquals("Fresh process expected, GMA already loaded", emptyList<String>(), loadedAtStart)

        val host = "${instrumentation.context.packageName}/${EditTextHostActivity::class.java.name}"
        shell("am start -W -n $host")
        val current = shell("dumpsys input_method")
        // API 34 prints mCurMethodId, API 37 prints mCurImeId
        val isCurrent = current.contains("mCurMethodId=$imeId") || current.contains("mCurImeId=$imeId")
        assertTrue("The keyboard is not the current input method", isCurrent)

        val end = SystemClock.uptimeMillis() + TYPING_MS
        while (SystemClock.uptimeMillis() < end) {
            shell("input text hello%sworld")
            shell("input keyevent KEYCODE_SPACE")
        }

        val afterTyping = loadedClasses()
        for (name in GMA_CLASSES) assertNull("$name was loaded in the app process", name.takeIf(afterTyping::contains))
        val providers = app.packageManager
            .getPackageInfo(app.packageName, PackageManager.GET_PROVIDERS)
            .providers.orEmpty()
            .map { it.name }
            .filter { it.startsWith("com.google.android.gms.ads.") || it.endsWith(".MobileAdsInitProvider") }
        assertEquals(emptyList<String>(), providers)
        assertTrue("The default FirebaseApp started before consent", !hasDefaultFirebaseApp())
    }

    private fun hasDefaultFirebaseApp(): Boolean {
        if (FIREBASE_APP !in loadedClasses()) return false
        val firebaseApp = Class.forName(FIREBASE_APP, false, app.classLoader)
        val apps = firebaseApp.getMethod("getApps", Context::class.java).invoke(null, app) as List<*>
        return apps.any { firebaseApp.getMethod("getName").invoke(it) == "[DEFAULT]" }
    }

    private companion object {
        const val TYPING_MS = 30_000L
        const val HPROF_STRING = 0x01
        const val HPROF_LOAD_CLASS = 0x02
        const val APP_CLASS = "com.manjul.aikeyboard.FlorisApplication"
        const val FIREBASE_APP = "com.google.firebase.FirebaseApp"
        val GMA_CLASSES = listOf(
            "com.google.android.gms.ads.MobileAds",
            "com.manjul.aikeyboard.core.ads.gma.GoogleMobileAdsFacade",
            "com.manjul.aikeyboard.app.ads.AdsAppGraph",
        )
    }
}
