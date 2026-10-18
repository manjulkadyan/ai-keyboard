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
}
