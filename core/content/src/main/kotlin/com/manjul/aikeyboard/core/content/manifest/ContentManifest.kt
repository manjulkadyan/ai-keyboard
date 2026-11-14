/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.manifest

/** The signed list of downloadable content packs (`content/v1/manifest.json`, built by `:tools:packbuild`). */
data class ContentManifest(val format: Int, val issuedAt: Long, val packs: List<PackSpec>)

data class PackSpec(
    val id: String,
    val kind: PackKindSpec,
    val version: Int,
    val premium: Boolean,
    val title: String,
    val files: List<PackFile>,
    val licence: PackLicence,
)

enum class PackKindSpec(val value: String) {
    DICT("dict"),
    STICKERS("stickers"),
    ;

    companion object {
        fun of(value: String): PackKindSpec? = entries.firstOrNull { it.value == value }
    }
}

/** One file of a pack. [path] is relative to `content/v1/`; [sha256] is 64 lower-case hex digits. */
data class PackFile(val path: String, val sha256: String, val bytes: Long, val mime: String) {
    /** The file name inside the installed pack folder. */
    val name: String get() = path.substringAfterLast('/')
}

data class PackLicence(val name: String, val url: String, val attribution: String)
