/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.stickers

import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.PackResult
import com.manjul.aikeyboard.core.common.Outcome
import com.manjul.aikeyboard.core.content.download.PackDownloadWorker
import com.manjul.aikeyboard.core.content.download.PackDownloader
import com.manjul.aikeyboard.core.content.download.PackHashMismatchException
import com.manjul.aikeyboard.core.content.manifest.ContentManifest
import com.manjul.aikeyboard.core.content.manifest.ManifestCache
import com.manjul.aikeyboard.core.content.manifest.PackKindSpec
import com.manjul.aikeyboard.core.content.manifest.PackSpec
import com.manjul.aikeyboard.core.database.StickerDao
import com.manjul.aikeyboard.core.database.StickerEntity
import com.manjul.aikeyboard.core.database.StickerPackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The sticker packs (S5 Task 6, PRD FR-23): the [bundled] packs, the packs stored in [dao], and the manifest's
 * sticker packs that are not installed yet. They are merged by id; a bundled pack wins over a stored or manifest pack
 * with the same id (plan F4). [install] emits `pack_download_start` and `pack_download_done` with `lang = "mul"` and
 * the pack kind only, never a URL or a path (plan F14). A pack whose files fail the SHA-256 check stores nothing.
 * [canDownload] is false while `contentWifiOnly` is on and the network is metered: then [install] fetches nothing,
 * emits no event and gives [WaitsForWifiException]. [refresh] and [install] never throw, except a cancellation.
 */
class StickerRepository(
    private val manifest: ManifestCache,
    private val downloader: PackDownloader,
    private val dao: StickerDao,
    private val bundled: BundledPacks,
    private val analytics: AnalyticsSink,
    private val clock: () -> Long = System::currentTimeMillis,
    private val canDownload: () -> Boolean = { true },
) : StickerCatalog {
    private val state = MutableStateFlow<List<StickerPackUi>>(emptyList())
    override val packs: StateFlow<List<StickerPackUi>> = state.asStateFlow()

    /** The last manifest seen by [refresh]; [install] reads it again when it is missing. */
    @Volatile private var lastManifest: ContentManifest? = null

    override suspend fun refresh() {
        val remote = try {
            manifest.current()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (remote != null) lastManifest = remote
        safePublish()
    }

    override suspend fun install(packId: String): Outcome<Unit> = try {
        installChecked(packId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        safePublish()
        Outcome.Failure(e)
    }

    private suspend fun installChecked(packId: String): Outcome<Unit> {
        if (!canDownload()) return Outcome.Failure(WaitsForWifiException())
        val spec = specFor(packId) ?: return Outcome.Failure(IllegalArgumentException("unknown sticker pack"))
        val bytes = spec.files.sumOf { it.bytes }
        val lang = PackDownloadWorker.lang(spec)
        val kind = PackDownloadWorker.kind(spec)
        analytics.log(EventCatalog.packDownloadStart(lang, kind, bytes))
        val outcome = downloader.install(spec)
        val result = when (outcome) {
            is Outcome.Success -> try {
                store(spec, outcome.value)
                PackResult.OK
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                PackResult.FAILED
            }
            is Outcome.Failure ->
                if (outcome.error is PackHashMismatchException) PackResult.HASH_MISMATCH else PackResult.FAILED
        }
        analytics.log(EventCatalog.packDownloadDone(lang, kind, bytes, result))
        safePublish()
        return when {
            result == PackResult.OK -> Outcome.Success(Unit)
            outcome is Outcome.Failure -> outcome
            else -> Outcome.Failure(IllegalStateException("sticker pack not stored"))
        }
    }

    private suspend fun specFor(packId: String): PackSpec? {
        val find = { m: ContentManifest? -> m?.packs?.firstOrNull { it.id == packId && it.kind == STICKERS } }
        return find(lastManifest) ?: find(manifest.current()?.also { lastManifest = it })
    }

    private suspend fun store(spec: PackSpec, dir: File) {
        val stickers = spec.files.filter { it.mime == PNG }.map { file ->
            StickerEntity(
                id = "${spec.id}/${file.name}",
                packId = spec.id,
                filePath = File(dir, file.name).absolutePath,
                mime = file.mime,
                keywords = "",
            )
        }
        // The pack row has no URL: the manifest path is the same for every pack.
        dao.upsertPack(StickerPackEntity(spec.id, spec.title, spec.version, spec.premium, "", true, clock()))
        dao.upsertStickers(stickers)
    }

    /** A failed read of the stored packs keeps the last list. */
    private suspend fun safePublish() {
        try {
            publish()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Keep the packs already shown.
        }
    }

    private suspend fun publish() {
        val bundledPacks = withContext(Dispatchers.IO) { bundled.packs() }
        val bundledIds = bundledPacks.map { it.id }.toSet()
        val stored = dao.packs().filter { it.id !in bundledIds && it.downloaded }.map { pack ->
            val stickers = dao.stickers(pack.id).map { StickerUi(it.id, pack.id, File(it.filePath), pack.isPremium) }
            val present = withContext(Dispatchers.IO) { stickers.filter { it.file.isFile } }
            StickerPackUi(pack.id, pack.title, pack.isPremium, installed = true, present)
        }.filter { it.stickers.isNotEmpty() } // A pack whose files are gone (a wipe) shows as not installed.
        val known = bundledIds + stored.map { it.id }
        val remote = lastManifest?.packs.orEmpty()
            .filter { it.kind == STICKERS && it.id !in known }
            .map { StickerPackUi(it.id, it.title, it.premium, installed = false, stickers = emptyList()) }
        state.value = bundledPacks + stored + remote
    }

    private companion object {
        val STICKERS = PackKindSpec.STICKERS
        const val PNG = "image/png"
    }
}
