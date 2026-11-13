/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.source

/**
 * Where content files come from: the CDN ([HttpContentSource]) or, in debug builds without a base URL, the app assets
 * ([AssetContentSource]). [path] is relative to `content/v1/`, for example `manifest.json`.
 */
interface ContentSource {
    /** The bytes of [path]. Throws [ContentFetchException] when they cannot be read. */
    suspend fun fetch(path: String): ByteArray
}

/**
 * A content file could not be read. The message names the failure only (for example `HTTP 404`), never a URL, so the
 * exception is safe to log.
 */
class ContentFetchException(message: String) : Exception(message)

/** The source of beta and release builds without `aikb.content.baseUrl`: there is no content, nothing is sent. */
object UnavailableContentSource : ContentSource {
    override suspend fun fetch(path: String): ByteArray = throw ContentFetchException("no content source")
}

object ContentSources {
    /**
     * The CDN when [baseUrl] is set; else the app assets in a [debug] build, and [UnavailableContentSource] in beta and
     * release.
     */
    fun select(baseUrl: String, debug: Boolean, assets: () -> ContentSource): ContentSource = when {
        baseUrl.isNotEmpty() -> HttpContentSource(baseUrl)
        debug -> assets()
        else -> UnavailableContentSource
    }
}
