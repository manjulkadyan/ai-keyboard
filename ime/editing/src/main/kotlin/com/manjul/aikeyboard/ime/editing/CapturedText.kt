/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

import java.security.MessageDigest

/**
 * Host text captured for an AI rewrite or Polish: [range] (field offsets, `start until end`) held [text] when it was
 * read, and [sha256] is its hash. Replace targets [range], never the cursor, and only while it still hashes to
 * [sha256] (see [SafeReplacer]). [text] is user content: [toString] holds only its kind and length.
 */
data class CapturedText(val range: IntRange, val text: String, val sha256: String, val kind: Kind) {
    enum class Kind { SELECTION, FIELD, BEFORE_CURSOR, DICTATED }

    init {
        require(range.last + 1 - range.first == text.length) { "range and text lengths differ" }
    }

    override fun toString(): String = "CapturedText(kind=$kind, range=$range, chars=${text.length})"

    companion object {
        /** Lower-case hex SHA-256 of [text] in UTF-8. */
        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        fun of(range: IntRange, text: String, kind: Kind) = CapturedText(range, text, sha256(text), kind)
    }
}
