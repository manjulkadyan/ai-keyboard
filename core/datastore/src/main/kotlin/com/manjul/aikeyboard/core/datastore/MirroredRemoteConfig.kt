/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.datastore

import com.manjul.aikeyboard.core.config.CatalogRemoteConfig
import com.manjul.aikeyboard.core.config.ConfigKey

/**
 * The IME's [com.manjul.aikeyboard.core.config.RemoteConfig]: reads [mirror] on every call, so a kill-switch update
 * written by the app process applies to the next read. A missing or unparsable value gives the catalog default.
 */
class MirroredRemoteConfig(private val mirror: RemoteConfigMirror) : CatalogRemoteConfig() {
    override fun valueOf(key: ConfigKey<*>): Any = mirror.current[key.name]?.let(key::parse) ?: key.default
}
