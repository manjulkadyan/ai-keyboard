/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.download

import com.manjul.aikeyboard.core.common.Outcome
import com.manjul.aikeyboard.core.common.outcomeOf
import com.manjul.aikeyboard.core.content.ContentPaths
import com.manjul.aikeyboard.core.content.manifest.PackFile
import com.manjul.aikeyboard.core.content.manifest.PackSpec
import com.manjul.aikeyboard.core.content.source.ContentSource
import com.manjul.aikeyboard.core.content.source.ContentTooLargeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.DigestOutputStream
import java.security.MessageDigest

/**
 * Installs content packs under [root] (`files/content`) as `<kind>/<id>/<version>/<file name>`. Each file is streamed
 * into a `.part` file in a staging folder while its SHA-256 is computed. The copy stops as soon as it passes the size
 * in the manifest. The size and SHA-256 are then checked, and the `.part` file is renamed. The staging folder becomes
 * the version folder only when every file passed. On any exit without success (an error or a cancellation) the
 * staging folder is deleted, so nothing new is left and the installed version stays. A new version replaces the older
 * ones. It has no analytics sink and logs nothing (plan F14): callers emit `pack_download_*`.
 */
class PackDownloader(private val source: ContentSource, private val root: File) {
    private val mutex = Mutex()

    suspend fun install(pack: PackSpec): Outcome<File> = mutex.withLock {
        // The id is checked before any path is built from it.
        if (!ContentPaths.isSafeId(pack.id)) {
            return@withLock Outcome.Failure(IllegalArgumentException("unsafe pack id"))
        }
        val packDir = File(root, "${pack.kind.value}/${pack.id}")
        val staging = File(packDir, ".staging-${pack.version}")
        var result: Outcome<File>? = null
        try {
            val outcome = outcomeOf {
                withContext(Dispatchers.IO) { staging.deleteRecursively() }
                for (file in pack.files) download(pack.id, file, staging)
                withContext(Dispatchers.IO) {
                    val target = File(packDir, pack.version.toString())
                    target.deleteRecursively()
                    rename(staging, target)
                    packDir.listFiles()?.filter { it != target }?.forEach { it.deleteRecursively() }
                    target
                }
            }
            result = outcome
            outcome
        } finally {
            if (result !is Outcome.Success) {
                // Also on cancellation: the cleanup must run even though the coroutine is cancelled.
                withContext(NonCancellable + Dispatchers.IO) {
                    staging.deleteRecursively()
                    if (packDir.list()?.isEmpty() == true) packDir.delete()
                    packDir.parentFile?.takeIf { it.list()?.isEmpty() == true }?.delete()
                }
            }
        }
    }

    /** Deletes every installed version of [packId], whatever its kind. It waits for a running [install]. */
    suspend fun remove(packId: String): Unit = mutex.withLock {
        if (!ContentPaths.isSafeId(packId)) return@withLock
        withContext(Dispatchers.IO) {
            root.listFiles()?.forEach { kind -> File(kind, packId).deleteRecursively() }
        }
    }

    private suspend fun download(packId: String, file: PackFile, staging: File) {
        val part = File(staging, file.name + PART)
        val digest = MessageDigest.getInstance("SHA-256")
        val written = withContext(Dispatchers.IO) {
            staging.mkdirs()
            try {
                DigestOutputStream(part.outputStream().buffered(), digest).use {
                    source.fetchTo(file.path, it, maxBytes = file.bytes)
                }
            } catch (_: ContentTooLargeException) {
                throw PackHashMismatchException(packId)
            }
        }
        withContext(Dispatchers.IO) {
            if (written != file.bytes || hex(digest.digest()) != file.sha256) {
                part.delete()
                throw PackHashMismatchException(packId)
            }
            rename(part, File(staging, file.name))
        }
    }

    private fun rename(from: File, to: File) {
        if (!from.renameTo(to)) throw IOException("rename failed")
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private companion object {
        const val PART = ".part"
    }
}

/**
 * A downloaded file did not match the size or SHA-256 of the manifest, or was longer than that size. The message holds
 * the pack id only.
 */
class PackHashMismatchException(packId: String) : Exception("hash mismatch in pack $packId")
