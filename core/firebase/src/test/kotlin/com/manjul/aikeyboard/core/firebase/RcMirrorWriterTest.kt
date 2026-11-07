/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.firebase

import com.manjul.aikeyboard.core.config.ConfigCatalog
import com.manjul.aikeyboard.core.config.ConfigKeys
import kotlin.test.Test
import kotlin.test.assertEquals

class RcMirrorWriterTest {
    private val activated = ConfigCatalog.all.associate { it.name to "v-${it.name}" } + ("not_in_catalog" to "x")

    @Test
    fun `a cold start copies every catalog key and drops unknown keys`() {
        val next = RcMirrorWriter.next(current = mapOf("stale" to "1"), activated = activated, updatedKeys = null)
        assertEquals(ConfigCatalog.all.map { it.name }.toSet(), next.keys)
        assertEquals("v-${ConfigKeys.MIN_SUPPORTED_VERSION}", next[ConfigKeys.MIN_SUPPORTED_VERSION])
    }

    @Test
    fun `a cold start leaves out keys the activated config does not have`() {
        val next = RcMirrorWriter.next(emptyMap(), mapOf(ConfigKeys.AI_TIMEOUT_MS to "500"), null)
        assertEquals(mapOf(ConfigKeys.AI_TIMEOUT_MS to "500"), next)
    }

    @Test
    fun `a live update changes only kill switch keys (decision 25)`() {
        val current = mapOf(ConfigKeys.AI_ENABLED_GLOBAL to "true", ConfigKeys.MIN_SUPPORTED_VERSION to "3")
        val next = RcMirrorWriter.next(
            current = current,
            activated = mapOf(ConfigKeys.AI_ENABLED_GLOBAL to "false", ConfigKeys.MIN_SUPPORTED_VERSION to "9"),
            updatedKeys = setOf(ConfigKeys.AI_ENABLED_GLOBAL, ConfigKeys.MIN_SUPPORTED_VERSION),
        )
        assertEquals(mapOf(ConfigKeys.AI_ENABLED_GLOBAL to "false", ConfigKeys.MIN_SUPPORTED_VERSION to "3"), next)
    }

    @Test
    fun `a kill switch removed from the activated config is removed from the mirror`() {
        val current = mapOf(ConfigKeys.POLICY_FORCE_SENSITIVE_PACKAGES to "a.b")
        val next = RcMirrorWriter.next(current, emptyMap(), setOf(ConfigKeys.POLICY_FORCE_SENSITIVE_PACKAGES))
        assertEquals(emptyMap(), next)
    }
}
