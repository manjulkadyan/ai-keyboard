/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.config

/**
 * The compiled-in values of every [ConfigKeys] key. This is the whole remote config until a Firebase
 * project exists (S4); [overrides] lets tests (and later a fetched config) replace single values.
 */
class DefaultsRemoteConfig(overrides: Map<String, Any> = emptyMap()) : RemoteConfig {
    private val values: Map<String, Any>

    init {
        for ((key, value) in overrides) {
            val default = requireNotNull(DEFAULTS[key]) { "Unknown config key $key" }
            require(default::class == value::class) { "Config key $key needs a ${default::class.simpleName}" }
        }
        values = DEFAULTS + overrides
    }

    override fun bool(key: String): Boolean = read(key) as? Boolean ?: wrongType(key, "Boolean")

    override fun long(key: String): Long = read(key) as? Long ?: wrongType(key, "Long")

    override fun double(key: String): Double = when (val value = read(key)) {
        is Double -> value
        is Long -> value.toDouble()
        else -> wrongType(key, "Double")
    }

    private fun read(key: String): Any = requireNotNull(values[key]) { "Unknown config key $key" }

    private fun wrongType(key: String, type: String): Nothing =
        throw IllegalArgumentException("Config key $key is not a $type")

    companion object {
        val DEFAULTS: Map<String, Any> = mapOf(
            ConfigKeys.AI_ENABLED_GLOBAL to true,
            ConfigKeys.AUTOCORRECT_MIN_SCORE to 0.7,
            ConfigKeys.AI_STILL_WORKING_MS to 4_000L,
            ConfigKeys.AI_TIMEOUT_MS to 10_000L,
        )
    }
}
