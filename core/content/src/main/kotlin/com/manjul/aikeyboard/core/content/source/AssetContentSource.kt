/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.source

import android.content.res.AssetManager
import com.manjul.aikeyboard.core.content.ContentPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream

/**
 * Reads content files from the app assets under `content/v1/`. Only debug builds with an empty `CONTENT_BASE_URL`
 * use it (see [ContentSources.select]); the fixture content lives in `app/src/debug/assets`.
 */
class AssetContentSource(private val assets: AssetManager) : ContentSource {
    override suspend fun fetchTo(path: String, sink: OutputStream, maxBytes: Long): Long {
        if (!ContentPaths.isSafePath(path)) throw ContentFetchException("unsafe path")
        return withContext(Dispatchers.IO) {
            val input = try {
                assets.open("$ROOT/$path")
            } catch (_: IOException) {
                throw ContentFetchException("missing asset")
            }
            try {
                input.use { copyBounded(it, sink, maxBytes) }
            } catch (_: IOException) {
                throw ContentFetchException("asset read failed")
            }
        }
    }

    private companion object {
        const val ROOT = "content/v1"
    }
}
