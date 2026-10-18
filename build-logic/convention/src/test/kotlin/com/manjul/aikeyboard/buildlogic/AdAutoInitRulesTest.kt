/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdAutoInitRulesTest {
    private fun manifest(applicationBody: String) = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
            <application>
                $applicationBody
            </application>
        </manifest>
    """.trimIndent()

    private fun violations(applicationBody: String) =
        AdAutoInitRules.findViolations(manifest(applicationBody).byteInputStream())

    @Test
    fun `clean manifest passes`() {
        val body = """
            <activity android:name="com.google.android.gms.ads.AdActivity" />
            <provider android:name="androidx.core.content.FileProvider" android:authorities="x.provider" />
            <provider android:name="androidx.startup.InitializationProvider" android:authorities="x.startup">
                <meta-data android:name="androidx.emoji2.text.EmojiCompatInitializer" android:value="androidx.startup" />
            </provider>
        """
        assertEquals(emptyList<String>(), violations(body))
    }

    @Test
    fun `gma init provider is reported`() {
        val result = violations(
            """<provider android:name="com.google.android.gms.ads.MobileAdsInitProvider" android:authorities="x.mobileadsinitprovider" />""",
        )
        assertEquals(1, result.size)
        assertTrue(result.single().contains("MobileAdsInitProvider"))
    }

    @Test
    fun `ump and next-gen gma providers are reported`() {
        val result = violations(
            """
            <provider android:name="com.google.android.ump.SomeInitProvider" android:authorities="a" />
            <provider android:name="com.google.android.libraries.ads.mobile.sdk.InitProvider" android:authorities="b" />
            """,
        )
        assertEquals(2, result.size)
    }

    @Test
    fun `startup initializer from an ad package is reported`() {
        val result = violations(
            """
            <provider android:name="androidx.startup.InitializationProvider" android:authorities="x.startup">
                <meta-data android:name="com.manjul.aikeyboard.core.ads.AdsStartupInitializer" android:value="androidx.startup" />
            </provider>
            """,
        )
        assertEquals(1, result.size)
        assertTrue(result.single().contains("AdsStartupInitializer"))
    }
}
