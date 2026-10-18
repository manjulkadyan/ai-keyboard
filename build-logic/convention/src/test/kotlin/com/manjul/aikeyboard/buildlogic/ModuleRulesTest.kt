/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ModuleRulesTest {
    @Test
    fun `allowed graph has no violations`() {
        val graph = mapOf(
            ":app" to setOf(":core:ads", ":core:billing", ":feature:paywall", ":ime:policy"),
            ":ime:policy" to setOf(":core:common"),
            ":ime:smartbar-ext" to setOf(":ime:policy", ":core:translate", ":core:common"),
            ":core:translate" to setOf(":core:common"),
            ":core:common" to emptySet(),
            ":feature:paywall" to setOf(":core:billing", ":core:common"),
        )
        assertEquals(emptyList<ModuleRuleViolation>(), ModuleRules.check(graph))
    }

    @ParameterizedTest
    @ValueSource(strings = [":core:ads", ":core:billing", ":feature:translator"])
    fun `ime module must not depend directly on forbidden modules`(forbidden: String) {
        val violations = ModuleRules.check(mapOf(":ime:policy" to setOf(forbidden)))
        assertEquals(
            listOf(ModuleRuleViolation(":ime:policy", forbidden, listOf(":ime:policy", forbidden), ModuleRules.RULE_IME)),
            violations,
        )
    }

    @Test
    fun `ime module must not reach ads through another module`() {
        val graph = mapOf(
            ":ime" to setOf(":core:analytics"),
            ":core:analytics" to setOf(":core:ads"),
        )
        val violations = ModuleRules.check(graph)
        assertEquals(1, violations.size)
        assertEquals(listOf(":ime", ":core:analytics", ":core:ads"), violations.single().chain)
    }

    @Test
    fun `feature module must not depend on another feature module`() {
        val graph = mapOf(
            ":feature:history" to setOf(":feature:translator", ":core:common"),
            ":feature:translator" to setOf(":core:common"),
        )
        val violations = ModuleRules.check(graph)
        assertEquals(1, violations.size)
        assertEquals(ModuleRules.RULE_FEATURE, violations.single().rule)
        assertEquals(":feature:history", violations.single().from)
        assertEquals(":feature:translator", violations.single().to)
    }

    @Test
    fun `dependency cycles terminate`() {
        val graph = mapOf(
            ":ime:a" to setOf(":ime:b"),
            ":ime:b" to setOf(":ime:a", ":core:billing"),
        )
        val violations = ModuleRules.check(graph)
        assertEquals(setOf(":ime:a", ":ime:b"), violations.map { it.from }.toSet())
        assertTrue(violations.all { it.to == ":core:billing" })
    }

    @Test
    fun `non ime modules may use ads and billing`() {
        assertTrue(ModuleRules.check(mapOf(":core:analytics" to setOf(":core:ads"))).isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = [":ime", ":ime:policy", ":ime:a:b"])
    fun `ime and its sub-projects are ime modules`(path: String) {
        assertTrue(ModuleRules.isIme(path))
    }

    @ParameterizedTest
    @ValueSource(strings = [":imeTools", ":ime-bench", ":imex:policy", ":core:ime", ":app"])
    fun `projects that only share the ime prefix are not ime modules`(path: String) {
        assertFalse(ModuleRules.isIme(path))
        assertTrue(ModuleRules.check(mapOf(path to setOf(":core:ads", ":feature:paywall"))).isEmpty())
    }

    @ParameterizedTest
    @ValueSource(strings = [":feature", ":featureFlags", ":featureFlags:core", ":core:feature"])
    fun `projects that only share the feature prefix are not feature modules`(path: String) {
        assertFalse(ModuleRules.isFeature(path))
        assertTrue(ModuleRules.check(mapOf(":ime:policy" to setOf(path))).isEmpty())
    }
}
