/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.datastore

import android.app.Application
import com.manjul.aikeyboard.core.config.ConfigCatalog
import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.config.DefaultsRemoteConfig
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MirroredRemoteConfigTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Before
    fun clean() {
        File(app.filesDir, "datastore/rc_mirror.json").delete()
    }

    @Test
    fun `an empty mirror returns every catalog default`() {
        val config = MirroredRemoteConfig(RemoteConfigMirror(app))
        val defaults = DefaultsRemoteConfig()
        for (key in ConfigCatalog.all) {
            when (key.default) {
                is Boolean -> assertEquals(defaults.bool(key.name), config.bool(key.name))
                is Long -> assertEquals(defaults.long(key.name), config.long(key.name))
                is Double -> assertEquals(defaults.double(key.name), config.double(key.name))
                else -> assertEquals(defaults.string(key.name), config.string(key.name))
            }
        }
    }

    @Test
    fun `an unparsable value returns the default`() = runTest {
        val mirror = RemoteConfigMirror(app)
        mirror.replace(mapOf(ConfigKeys.AI_TIMEOUT_MS to "abc", ConfigKeys.AUTOCORRECT_MIN_SCORE to "x"))
        val config = MirroredRemoteConfig(mirror)
        assertEquals(10_000L, config.long(ConfigKeys.AI_TIMEOUT_MS))
        assertEquals(0.7, config.double(ConfigKeys.AUTOCORRECT_MIN_SCORE))
    }

    @Test
    fun `stored values win and a replace drops the old values`() = runTest {
        val mirror = RemoteConfigMirror(app, clock = { 42L })
        mirror.replace(mapOf(ConfigKeys.MIN_SUPPORTED_VERSION to "7"))
        mirror.replace(
            mapOf(ConfigKeys.AI_ENABLED_GLOBAL to "false", ConfigKeys.POLICY_FORCE_SENSITIVE_PACKAGES to "a.b,c.d"),
        )
        mirror.awaitLoaded()
        val config = MirroredRemoteConfig(mirror)
        assertFalse(config.bool(ConfigKeys.AI_ENABLED_GLOBAL))
        assertEquals("a.b,c.d", config.string(ConfigKeys.POLICY_FORCE_SENSITIVE_PACKAGES))
        assertEquals(0L, config.long(ConfigKeys.MIN_SUPPORTED_VERSION))
        assertEquals(42L, mirror.activatedAt)
    }
}
