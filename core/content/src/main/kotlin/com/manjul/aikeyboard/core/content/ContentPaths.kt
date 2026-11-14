/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content

/** The safe forms of content paths and pack ids, shared by the manifest check, the sources and the downloader. */
internal object ContentPaths {
    /** Relative, `/` separated; no segment is empty or starts with a dot, so there is no `..`, query or fragment. */
    private val PATH = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]*(/[A-Za-z0-9_-][A-Za-z0-9._-]*)*")
    private val ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")

    fun isSafePath(path: String): Boolean = path.length <= MAX_PATH && PATH.matches(path)

    fun isSafeId(id: String): Boolean = ID.matches(id)

    private const val MAX_PATH = 256
}
