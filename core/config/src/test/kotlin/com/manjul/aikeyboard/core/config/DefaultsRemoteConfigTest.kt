/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultsRemoteConfigTest {
    private val config: RemoteConfig = DefaultsRemoteConfig()

    @Test
    fun `decision 5 defaults`() {
        assertTrue(config.bool(ConfigKeys.AI_ENABLED_GLOBAL))
        assertEquals(0.7, config.double(ConfigKeys.AUTOCORRECT_MIN_SCORE))
        assertEquals(4_000L, config.long(ConfigKeys.AI_STILL_WORKING_MS))
        assertEquals(10_000L, config.long(ConfigKeys.AI_TIMEOUT_MS))
    }

    @Test
    fun `overrides replace a default`() {
        val overridden = DefaultsRemoteConfig(mapOf(ConfigKeys.AI_TIMEOUT_MS to 500L, ConfigKeys.AI_ENABLED_GLOBAL to false))
        assertEquals(500L, overridden.long(ConfigKeys.AI_TIMEOUT_MS))
        assertEquals(false, overridden.bool(ConfigKeys.AI_ENABLED_GLOBAL))
        assertEquals(4_000L, overridden.long(ConfigKeys.AI_STILL_WORKING_MS))
    }

    @Test
    fun `unknown keys and wrong types fail`() {
        assertFailsWith<IllegalArgumentException> { config.bool("no_such_key") }
        assertFailsWith<IllegalArgumentException> { config.long(ConfigKeys.AI_ENABLED_GLOBAL) }
        assertFailsWith<IllegalArgumentException> { DefaultsRemoteConfig(mapOf("no_such_key" to 1L)) }
    }

    @Test
    fun `long values are readable as double`() {
        assertEquals(4_000.0, config.double(ConfigKeys.AI_STILL_WORKING_MS))
    }
}
