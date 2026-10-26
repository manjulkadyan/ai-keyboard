/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

import java.security.MessageDigest

/**
 * In-memory cache of finished AI outputs, so repeating the same action on the same text costs no quota. Keyed
 * by mode, target, locale and the SHA-256 of the input; the input itself is never stored. Never persisted:
 * [clear] when the field becomes sensitive or the keyboard closes. Thread-safe.
 */
class AiLru(private val capacity: Int = DEFAULT_CAPACITY) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    /** [textSha256] is lower-case hex. [toString] has no text, only a hash prefix. */
    data class Key(val mode: String, val target: String?, val locale: String, val textSha256: String) {
        override fun toString(): String = "Key($mode, $target, $locale, ${textSha256.take(8)})"

        companion object {
            fun of(mode: String, target: String?, locale: String, text: String): Key =
                Key(mode, target, locale, sha256(text))

            /** Rewrite: its mode. Polish: `polish:<script>` with the source language as the locale. */
            fun of(req: AiRequest): Key = when (req) {
                is AiRequest.Rewrite -> of(req.mode.wire, req.targetLang, req.locale, req.text)
                is AiRequest.Polish -> of("polish:${req.script.wire}", req.targetLang, req.sourceLang.orEmpty(), req.transcript)
            }
        }
    }

    private val map = object : LinkedHashMap<Key, String>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, String>?): Boolean = size > capacity
    }

    val size: Int
        @Synchronized get() = map.size

    @Synchronized
    fun get(key: Key): String? = map[key]

    @Synchronized
    fun put(key: Key, output: String) {
        map[key] = output
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    companion object {
        const val DEFAULT_CAPACITY = 32

        /** Lower-case hex SHA-256 of the UTF-8 bytes of [text]. */
        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
