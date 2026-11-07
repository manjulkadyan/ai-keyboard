/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigCatalogTest {
    private val names = ConfigCatalog.all.map { it.name }

    @Test
    fun `names are unique snake_case and at most 40 chars`() {
        assertEquals(names.size, names.toSet().size, "duplicate key names")
        val snake = Regex("^[a-z][a-z0-9]*(_[a-z0-9]+)*$")
        for (name in names) {
            assertTrue(snake.matches(name), "$name is not snake_case")
            assertTrue(name.length <= 40, "$name is longer than 40 chars")
        }
    }

    @Test
    fun `kill switches are exactly the two S4 keys`() {
        val expected = setOf(ConfigKeys.AI_ENABLED_GLOBAL, ConfigKeys.POLICY_FORCE_SENSITIVE_PACKAGES)
        assertEquals(expected, ConfigCatalog.killSwitches)
        assertTrue(names.containsAll(ConfigCatalog.killSwitches))
    }

    @Test
    fun `catalog holds the S3 keys and the S4 keys with their defaults`() {
        val config: RemoteConfig = DefaultsRemoteConfig()
        assertTrue(config.bool(ConfigKeys.AI_ENABLED_GLOBAL))
        assertEquals(0.7, config.double(ConfigKeys.AUTOCORRECT_MIN_SCORE))
        assertEquals("", config.string(ConfigKeys.POLICY_FORCE_SENSITIVE_PACKAGES))
        assertEquals(0L, config.long(ConfigKeys.MIN_SUPPORTED_VERSION))
        assertTrue(config.bool(ConfigKeys.ADS_ENABLED_GLOBAL))
        assertTrue(config.bool(ConfigKeys.PAYWALL_ONBOARDING_ENABLED))
        assertEquals(7, ConfigKeys.AD_UNITS.size)
        for (unit in ConfigKeys.AD_UNITS) assertEquals("", config.string(unit))
        assertEquals(15, names.size)
    }

    @Test
    fun `unknown key throws without the key or a value in the message`() {
        val config: RemoteConfig = DefaultsRemoteConfig()
        val e = assertFailsWith<IllegalArgumentException> { config.string("nope") }
        assertEquals("unknown config key", e.message)
        assertFailsWith<IllegalArgumentException> { config.bool("nope") }
    }

    @Test
    fun `parse reads typed values and rejects bad ones`() {
        val timeout = ConfigCatalog.byName(ConfigKeys.AI_TIMEOUT_MS)!!
        assertEquals(500L, timeout.parse("500"))
        assertEquals(null, timeout.parse("abc"))
        val ai = ConfigCatalog.byName(ConfigKeys.AI_ENABLED_GLOBAL)!!
        assertEquals(false, ai.parse("false"))
        assertEquals(null, ai.parse("maybe"))
        assertFalse(ConfigCatalog.byName("nope") != null)
    }

    @Test
    fun `forced packages parse a comma separated list`() {
        assertEquals(setOf("a.b", "c.d"), ConfigKeys.forcedPackages(" a.b, ,c.d,"))
        assertEquals(emptySet(), ConfigKeys.forcedPackages(""))
    }
}
