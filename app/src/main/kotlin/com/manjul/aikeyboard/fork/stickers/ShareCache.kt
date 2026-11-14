/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import java.io.File
import java.util.UUID

/**
 * The staging folder for stickers sent to another app (S5 Task 6, decision 14): `cache/share/`. It is the only
 * folder the `share` FileProvider path exposes, so only a file staged here ever gets a content URI. Each staged file
 * has a fresh random name (`<uuid>.png`); [sweep] deletes files older than 24 h and runs when the IME starts and each
 * time the panel opens.
 */
class ShareCache(cacheDir: File, private val clock: () -> Long) {
    val dir: File = File(cacheDir, DIR)

    /** Copies [src] to `share/<uuid>.png` and returns the copy. */
    fun stage(src: File): File {
        dir.mkdirs()
        val out = File(dir, "${UUID.randomUUID()}.png")
        requireStaged(out)
        src.copyTo(out, overwrite = false)
        out.setLastModified(clock())
        return out
    }

    /** Deletes the staged files older than 24 h. Nothing outside `share/` is touched. */
    fun sweep() {
        val now = clock()
        dir.listFiles()?.forEach { file ->
            if (file.isFile && now - file.lastModified() > MAX_AGE_MS) file.delete()
        }
    }

    /** True if [file] is directly inside `share/`, after resolving `..` and links. */
    fun isStaged(file: File): Boolean = file.canonicalFile.parentFile == dir.canonicalFile

    fun requireStaged(file: File) = require(isStaged(file)) { "file is not in the share cache" }

    companion object {
        const val DIR = "share"
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
