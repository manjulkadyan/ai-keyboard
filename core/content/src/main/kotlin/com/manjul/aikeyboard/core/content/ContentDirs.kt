/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content

import android.content.Context
import java.io.File

/** Where content lives in the app's private files. Neither folder is in the backup rules. */
object ContentDirs {
    /**
     * The [com.manjul.aikeyboard.core.content.download.PackDownloader] root: `files/content/<kind>/<id>/<version>/`.
     */
    fun packs(context: Context): File = File(context.filesDir, "content")

    /** The [com.manjul.aikeyboard.core.content.manifest.ManifestCache] folder. It holds the public manifest only. */
    fun manifest(context: Context): File = File(context.filesDir, "content_manifest")
}
