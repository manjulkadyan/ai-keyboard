/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.buildlogic

/** One broken dependency rule. [chain] is the project path from [from] to [to], both included. */
data class ModuleRuleViolation(
    val from: String,
    val to: String,
    val chain: List<String>,
    val rule: String,
) {
    override fun toString(): String = "$rule: ${chain.joinToString(" -> ")}"
}

/**
 * The module dependency rules from TRD §2.2, as a pure function over the project graph so they can be
 * unit-tested without Gradle.
 *
 * 1. A project whose path starts with `:ime` must not reach `:core:ads`, `:core:billing` or any
 *    `:feature:*` project, directly or through other projects.
 * 2. A `:feature:*` project must not depend directly on another `:feature:*` project.
 */
object ModuleRules {
    const val RULE_IME = "IME modules must not depend on ads, billing or feature modules"
    const val RULE_FEATURE = "Feature modules must not depend on other feature modules"

    private val imeForbidden = setOf(":core:ads", ":core:billing")

    fun isIme(path: String): Boolean = path.startsWith(":ime")

    fun isFeature(path: String): Boolean = path.startsWith(":feature:")

    fun isForbiddenForIme(path: String): Boolean = path in imeForbidden || isFeature(path)

    /**
     * @param graph project path -> project paths it declares a dependency on (any configuration).
     * @return every violation, sorted for stable output; empty when the graph is clean.
     */
    fun check(graph: Map<String, Set<String>>): List<ModuleRuleViolation> {
        val violations = mutableListOf<ModuleRuleViolation>()

        for (ime in graph.keys.filter(::isIme)) {
            // Breadth-first so the reported chain is the shortest one.
            val parent = mutableMapOf<String, String>()
            val seen = mutableSetOf(ime)
            val queue = ArrayDeque(listOf(ime))
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (next in graph[current].orEmpty().sorted()) {
                    if (!seen.add(next)) continue
                    parent[next] = current
                    if (isForbiddenForIme(next)) {
                        violations += ModuleRuleViolation(ime, next, chainTo(next, parent), RULE_IME)
                    } else {
                        queue.addLast(next)
                    }
                }
            }
        }

        for ((from, deps) in graph) {
            if (!isFeature(from)) continue
            for (to in deps) {
                if (to != from && isFeature(to)) {
                    violations += ModuleRuleViolation(from, to, listOf(from, to), RULE_FEATURE)
                }
            }
        }

        return violations.distinct().sortedWith(compareBy({ it.rule }, { it.from }, { it.to }))
    }

    private fun chainTo(target: String, parent: Map<String, String>): List<String> {
        val chain = mutableListOf(target)
        var node = target
        while (true) {
            node = parent[node] ?: break
            chain += node
        }
        return chain.asReversed()
    }
}
