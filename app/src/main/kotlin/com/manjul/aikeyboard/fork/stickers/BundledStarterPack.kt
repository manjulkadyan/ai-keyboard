/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import android.content.Context
import com.manjul.aikeyboard.core.content.stickers.BundledPacks
import com.manjul.aikeyboard.core.content.stickers.StickerPackUi
import com.manjul.aikeyboard.core.content.stickers.StickerUi
import java.io.File

/**
 * The free `starter` pack in the APK (`assets/stickers/starter/`, decision 14). The files are copied once to
 * `no_backup/stickers_bundled/starter/`, so the panel and the inserter can work with files. That folder is outside
 * `files/content`, so Delete my data does not remove the bundled pack.
 */
class BundledStarterPack(context: Context) : BundledPacks {
    private val app = context.applicationContext

    override fun packs(): List<StickerPackUi> {
        val names = runCatching { app.assets.list(ASSET_DIR)?.filter { it.endsWith(".png") }?.sorted() }
            .getOrNull().orEmpty()
        if (names.isEmpty()) return emptyList()
        val dir = File(app.noBackupFilesDir, "stickers_bundled/$PACK_ID").apply { mkdirs() }
        val stickers = names.mapNotNull { name ->
            val file = File(dir, name)
            val ok = file.isFile || runCatching {
                val tmp = File(dir, "$name.tmp")
                app.assets.open("$ASSET_DIR/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
            }.getOrDefault(false)
            if (ok) StickerUi("$PACK_ID/$name", PACK_ID, file, premium = false) else null
        }
        return listOf(StickerPackUi(PACK_ID, TITLE, premium = false, installed = true, stickers = stickers))
    }

    companion object {
        const val PACK_ID = "starter"
        const val TITLE = "Starter"
        private const val ASSET_DIR = "stickers/$PACK_ID"
    }
}
