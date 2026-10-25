/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExportedComponentRulesTest {
    private fun manifest(applicationBody: String, debuggable: Boolean = false) = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.manjul.aikeyboard">
            <application ${if (debuggable) "android:debuggable=\"true\"" else ""}>
                $applicationBody
            </application>
        </manifest>
    """.trimIndent()

    private fun violations(applicationBody: String, debuggable: Boolean = false) =
        ExportedComponentRules.findViolations(manifest(applicationBody, debuggable).byteInputStream())

    private val profileReceiver = """
        <receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" android:exported="true"
            android:permission="android.permission.DUMP" />
    """

    private val appComponents = """
        <service android:name="com.manjul.aikeyboard.FlorisImeService" android:exported="true"
            android:permission="android.permission.BIND_INPUT_METHOD" />
        <service android:name="com.manjul.aikeyboard.FlorisSpellCheckerService" android:exported="true"
            android:permission="android.permission.BIND_TEXT_SERVICE" />
        <activity android:name="com.manjul.aikeyboard.app.FlorisAppActivity" android:exported="true" />
        <activity-alias android:name=".SettingsLauncherAlias" android:exported="true" />
        <activity android:name="com.manjul.aikeyboard.lib.crashutility.CrashDialogActivity" />
        <provider android:name="androidx.core.content.FileProvider" android:exported="false" />
        <service android:name="androidx.room.MultiInstanceInvalidationService" android:exported="false" />
    """

    @Test
    fun `allow-listed components pass in a release manifest`() {
        assertEquals(emptyList<String>(), violations(appComponents + profileReceiver))
    }

    @Test
    fun `debug-only preview activity passes in a debuggable manifest`() {
        val body = appComponents + profileReceiver +
            """<activity android:name="androidx.compose.ui.tooling.PreviewActivity" android:exported="true" />"""
        assertEquals(emptyList<String>(), violations(body, debuggable = true))
    }

    @Test
    fun `preview activity fails in a non-debuggable manifest`() {
        val result = violations("""<activity android:name="androidx.compose.ui.tooling.PreviewActivity" android:exported="true" />""")
        assertEquals(1, result.size)
        assertTrue(result.single().contains("debuggable builds only"))
    }

    @Test
    fun `unlisted exported library component fails`() {
        val result = violations(
            appComponents + """<receiver android:name="com.example.sdk.TrackingReceiver" android:exported="true" />""",
        )
        assertEquals(listOf("receiver com.example.sdk.TrackingReceiver is exported but not on the allow-list"), result)
    }

    @Test
    fun `intent filter without explicit exported counts as exported`() {
        val result = violations(
            """
            <activity android:name="com.example.sdk.DeepLinkActivity">
                <intent-filter><action android:name="android.intent.action.VIEW" /></intent-filter>
            </activity>
            """,
        )
        assertEquals(1, result.size)
        assertTrue(result.single().contains("DeepLinkActivity"))
    }

    @Test
    fun `exported provider fails`() {
        val result = violations("""<provider android:name="com.example.sdk.DataProvider" android:exported="true" />""")
        assertEquals(1, result.size)
    }

    @Test
    fun `allow-listed component without its permission fails`() {
        val result = violations(
            """<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" android:exported="true" />""",
        )
        assertEquals(1, result.size)
        assertTrue(result.single().contains("android.permission.DUMP"))
    }

    @Test
    fun `work manager components pass only with their system permissions`() {
        val guarded = """
            <service android:name="androidx.work.impl.background.systemjob.SystemJobService" android:exported="true"
                android:permission="android.permission.BIND_JOB_SERVICE" />
            <receiver android:name="androidx.work.impl.diagnostics.DiagnosticsReceiver" android:exported="true"
                android:permission="android.permission.DUMP" />
        """
        assertEquals(emptyList<String>(), violations(appComponents + guarded))
        val open = """
            <service android:name="androidx.work.impl.background.systemjob.SystemJobService" android:exported="true" />
            <receiver android:name="androidx.work.impl.diagnostics.DiagnosticsReceiver" android:exported="true" />
        """
        assertEquals(2, violations(open).size)
    }

    private fun translateActivity(filters: String) = """
        <activity android:name="com.manjul.aikeyboard.fork.processtext.TranslateTextActivity" android:exported="true">
            $filters
        </activity>
    """

    private val processTextFilter = """
        <intent-filter>
            <action android:name="android.intent.action.PROCESS_TEXT" />
            <category android:name="android.intent.category.DEFAULT" />
            <data android:mimeType="text/plain" />
        </intent-filter>
    """

    private val sendFilter = """
        <intent-filter>
            <action android:name="android.intent.action.SEND" />
            <category android:name="android.intent.category.DEFAULT" />
            <data android:mimeType="text/plain" />
        </intent-filter>
    """

    @Test
    fun `translate activity passes with exactly the process text and send filters`() {
        assertEquals(emptyList<String>(), violations(appComponents + translateActivity(processTextFilter + sendFilter)))
    }

    @Test
    fun `translate activity with a data scheme fails`() {
        val withScheme = sendFilter.replace(
            """<data android:mimeType="text/plain" />""",
            """<data android:mimeType="text/plain" /><data android:scheme="https" />""",
        )
        val result = violations(translateActivity(processTextFilter + withScheme))
        assertTrue(result.any { it.contains("data URI") }, result.toString())
    }

    @Test
    fun `translate activity with another mime type or a third filter fails`() {
        val image = sendFilter.replace("text/plain", "image/*")
        assertEquals(1, violations(translateActivity(processTextFilter + image)).size)
        val view = """
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        """
        assertEquals(1, violations(translateActivity(processTextFilter + sendFilter + view)).size)
        assertEquals(1, violations(translateActivity(processTextFilter)).size)
    }

    @Test
    fun `a new exported activity next to the translate activity still fails`() {
        val result = violations(
            appComponents + translateActivity(processTextFilter + sendFilter) +
                """<activity android:name="com.manjul.aikeyboard.fork.processtext.OtherActivity" android:exported="true" />""",
        )
        assertEquals(
            listOf("activity com.manjul.aikeyboard.fork.processtext.OtherActivity is exported but not on the allow-list"),
            result,
        )
    }

    @Test
    fun `compose test host activity fails in a non-debuggable manifest`() {
        val result = violations(
            """<activity android:name="androidx.activity.ComponentActivity" android:exported="true" />""",
        )
        assertEquals(1, result.size)
        assertTrue(result.single().contains("debuggable builds only"))
    }
}
