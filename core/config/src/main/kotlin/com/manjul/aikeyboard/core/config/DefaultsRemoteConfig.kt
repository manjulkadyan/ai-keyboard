/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.config

/**
 * The compiled-in default of every [ConfigCatalog] key. Used where no fetched or mirrored config exists (tests,
 * builds without Firebase). [overrides] lets tests replace single values; each must match the key's type.
 */
class DefaultsRemoteConfig(private val overrides: Map<String, Any> = emptyMap()) : CatalogRemoteConfig() {
    init {
        for ((name, value) in overrides) {
            val key = requireNotNull(ConfigCatalog.byName(name)) { UNKNOWN_KEY }
            require(key.default::class == value::class) { "config key override has the wrong type" }
        }
    }

    override fun valueOf(key: ConfigKey<*>): Any = overrides[key.name] ?: key.default

    companion object {
        val DEFAULTS: Map<String, Any> = ConfigCatalog.all.associate { it.name to it.default }
    }
}
