/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.stickers

import com.manjul.aikeyboard.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** One sticker on disk. [id] is `<packId>/<file name>`; [premium] comes from its pack. */
data class StickerUi(val id: String, val packId: String, val file: File, val premium: Boolean)

/**
 * One pack in the sticker panel. [installed] is false for a manifest pack that is not downloaded yet: it has no
 * [stickers] until [StickerCatalog.install] stores them.
 */
data class StickerPackUi(
    val id: String,
    val title: String,
    val premium: Boolean,
    val installed: Boolean,
    val stickers: List<StickerUi>,
)

/** The packs the sticker panel shows. [StickerRepository] is the production one. */
interface StickerCatalog {
    val packs: StateFlow<List<StickerPackUi>>

    /** Reads the stored packs and the content manifest again. A manifest fetch carries no user identifier. */
    suspend fun refresh()

    /** Downloads [packId] from the manifest, checks every file's SHA-256 and stores the pack and its stickers. */
    suspend fun install(packId: String): Outcome<Unit>
}

/** The packs in the APK (the free `starter` pack, decision 14). They are always installed. */
fun interface BundledPacks {
    fun packs(): List<StickerPackUi>
}
