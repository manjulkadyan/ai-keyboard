/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.config

/**
 * One typed remote config key with its compiled-in default. [parse] reads the string form that Firebase Remote
 * Config and the IME mirror store, and returns null when the value does not parse.
 */
sealed class ConfigKey<T : Any>(val name: String, val default: T) {
    abstract fun parse(raw: String): T?
}

class BoolKey(name: String, default: Boolean) : ConfigKey<Boolean>(name, default) {
    override fun parse(raw: String): Boolean? = raw.trim().toBooleanStrictOrNull()
}

class LongKey(name: String, default: Long) : ConfigKey<Long>(name, default) {
    override fun parse(raw: String): Long? = raw.trim().toLongOrNull()
}

class DoubleKey(name: String, default: Double) : ConfigKey<Double>(name, default) {
    override fun parse(raw: String): Double? = raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
}

class StringKey(name: String, default: String) : ConfigKey<String>(name, default) {
    override fun parse(raw: String): String = raw
}

/** Every remote config key, typed, with its default (S4 Task 1). `remote_config_defaults.xml` mirrors this list. */
object ConfigCatalog {
    val all: List<ConfigKey<*>> = listOf(
        BoolKey(ConfigKeys.AI_ENABLED_GLOBAL, true),
        DoubleKey(ConfigKeys.AUTOCORRECT_MIN_SCORE, 0.7),
        LongKey(ConfigKeys.AI_STILL_WORKING_MS, 4_000L),
        LongKey(ConfigKeys.AI_TIMEOUT_MS, 10_000L),
        StringKey(ConfigKeys.POLICY_FORCE_SENSITIVE_PACKAGES, ""),
        LongKey(ConfigKeys.MIN_SUPPORTED_VERSION, 0L),
        BoolKey(ConfigKeys.ADS_ENABLED_GLOBAL, true),
        BoolKey(ConfigKeys.PAYWALL_ONBOARDING_ENABLED, true),
    ) + ConfigKeys.AD_UNITS.map { StringKey(it, "") }

    /**
     * Keys that a live Remote Config update applies at once, also in the IME mirror (decision 25). Every other key
     * keeps its cold-start value until the next cold start.
     */
    val killSwitches: Set<String> = setOf(ConfigKeys.AI_ENABLED_GLOBAL, ConfigKeys.POLICY_FORCE_SENSITIVE_PACKAGES)

    private val byName: Map<String, ConfigKey<*>> = all.associateBy { it.name }

    fun byName(name: String): ConfigKey<*>? = byName[name]
}

/**
 * A [RemoteConfig] over the catalog: the subclass gives the value of a known key, already typed. Unknown keys and
 * wrong-type reads throw [IllegalArgumentException] with a fixed message that never holds a value.
 */
abstract class CatalogRemoteConfig : RemoteConfig {
    /** The value for [key], of the type of `key.default`. */
    protected abstract fun valueOf(key: ConfigKey<*>): Any

    final override fun bool(key: String): Boolean = read(key) as? Boolean ?: wrongType()

    final override fun long(key: String): Long = read(key) as? Long ?: wrongType()

    final override fun double(key: String): Double = when (val value = read(key)) {
        is Double -> value
        is Long -> value.toDouble()
        else -> wrongType()
    }

    final override fun string(key: String): String = read(key) as? String ?: wrongType()

    private fun read(name: String): Any = valueOf(requireNotNull(ConfigCatalog.byName(name)) { UNKNOWN_KEY })

    private fun wrongType(): Nothing = throw IllegalArgumentException("config key read as the wrong type")

    companion object {
        const val UNKNOWN_KEY = "unknown config key"
    }
}
