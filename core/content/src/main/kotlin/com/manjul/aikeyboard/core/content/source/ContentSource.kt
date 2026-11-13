/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.source

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Where content files come from: the CDN ([HttpContentSource]) or, in debug builds without a base URL, the app assets
 * ([AssetContentSource]). [path] is relative to `content/v1/`, for example `manifest.json`.
 */
interface ContentSource {
    /**
     * Streams [path] into [sink] and returns the number of bytes written. It reads at most [maxBytes] + 1 bytes: once
     * the file is longer than [maxBytes] it stops and throws [ContentTooLargeException], so an oversized body is never
     * read in full or held in memory. Throws [ContentFetchException] when the file cannot be read.
     */
    suspend fun fetchTo(path: String, sink: OutputStream, maxBytes: Long): Long
}

/** Reads a small file (the manifest or its signature) into memory, bounded by [maxBytes]. */
suspend fun ContentSource.fetchBytes(path: String, maxBytes: Long): ByteArray =
    ByteArrayOutputStream().also { fetchTo(path, it, maxBytes) }.toByteArray()

/**
 * A content file could not be read. The message names the failure only (for example `HTTP 404`), never a URL, so the
 * exception is safe to log.
 */
open class ContentFetchException(message: String) : Exception(message)

/** The file is longer than the size the caller expects (for a pack file, the size in the signed manifest). */
class ContentTooLargeException : ContentFetchException("content too large")

/** The source of beta and release builds without `aikb.content.baseUrl`: there is no content, nothing is sent. */
object UnavailableContentSource : ContentSource {
    override suspend fun fetchTo(path: String, sink: OutputStream, maxBytes: Long): Long =
        throw ContentFetchException("no content source")
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

/**
 * Copies [input] to [sink] in small blocks. It never asks for more than [maxBytes] + 1 bytes in total, and throws
 * [ContentTooLargeException] as soon as the input passes [maxBytes]; the bytes past the limit are not written.
 */
internal fun copyBounded(input: InputStream, sink: OutputStream, maxBytes: Long): Long {
    require(maxBytes >= 0) { "maxBytes must not be negative" }
    val buffer = ByteArray(COPY_BUFFER)
    var total = 0L
    while (true) {
        val want = minOf(buffer.size.toLong(), maxBytes - total + 1).toInt()
        val n = input.read(buffer, 0, want)
        if (n < 0) return total
        total += n
        if (total > maxBytes) throw ContentTooLargeException()
        sink.write(buffer, 0, n)
    }
}

private const val COPY_BUFFER = 8 * 1024
