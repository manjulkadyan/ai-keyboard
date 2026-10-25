/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

/**
 * Outcome of a translate call. Engines return [Failed] instead of throwing; the translated text in [Ok] is user
 * content, never logged, and left out of [toString].
 */
sealed interface TranslateResult {
    data class Ok(val text: String, val source: Lang, val engine: EngineId) : TranslateResult {
        override fun toString(): String = "Ok(chars=${text.length}, source=$source, engine=$engine)"
    }

    data class Failed(val kind: FailKind) : TranslateResult
}

/**
 * Why a translation failed. Engine exception messages are dropped and mapped to one of these, so no text can
 * reach a log or analytics event through an error. [analytics] is the PRD §12 `translate_error.reason` value.
 */
enum class FailKind(val analytics: String) {
    /** The language pack for the pair is not on the device. */
    NO_PACK("no_pack"),

    /** A network was needed and is not available. */
    OFFLINE("net"),
    TIMEOUT("timeout"),
    QUOTA("quota"),

    /** No engine supports the pair. */
    UNSUPPORTED("other"),

    /** The pair needs the cloud engine, which is unavailable (not Pro, not opted in, or OD-05 stub). */
    CLOUD_UNAVAILABLE("other"),

    /** The field policy refuses translation (password, incognito, private app, ...). */
    POLICY("other"),

    /** Any other engine error. */
    ENGINE("other"),
}
