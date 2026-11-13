/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.download

import com.manjul.aikeyboard.core.common.Outcome
import com.manjul.aikeyboard.core.common.outcomeOf
import com.manjul.aikeyboard.core.content.ContentPaths
import com.manjul.aikeyboard.core.content.manifest.PackSpec
import com.manjul.aikeyboard.core.content.source.ContentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Installs content packs under [root] (`files/content`) as `<kind>/<id>/<version>/<file name>`. Each file is written
 * to a `.part` file in a staging folder, checked for its size and SHA-256, then renamed; the staging folder becomes
 * the version folder only when every file passed. On any failure nothing new is left and the installed version stays.
 * A new version replaces the older ones. It has no analytics sink and logs nothing (plan F14): callers emit
 * `pack_download_*`.
 */
class PackDownloader(private val source: ContentSource, private val root: File) {
    private val mutex = Mutex()

    suspend fun install(pack: PackSpec): Outcome<File> = mutex.withLock {
        val packDir = File(root, "${pack.kind.value}/${pack.id}")
        val staging = File(packDir, ".staging-${pack.version}")
        val result = outcomeOf {
            require(ContentPaths.isSafeId(pack.id)) { "unsafe pack id" }
            withContext(Dispatchers.IO) { staging.deleteRecursively() }
            for (file in pack.files) {
                val bytes = source.fetch(file.path)
                withContext(Dispatchers.IO) {
                    if (bytes.size.toLong() != file.bytes || sha256(bytes) != file.sha256) {
                        throw PackHashMismatchException(pack.id)
                    }
                    staging.mkdirs()
                    val part = File(staging, file.name + PART)
                    part.writeBytes(bytes)
                    rename(part, File(staging, file.name))
                }
            }
            withContext(Dispatchers.IO) {
                val target = File(packDir, pack.version.toString())
                target.deleteRecursively()
                rename(staging, target)
                packDir.listFiles()?.filter { it != target }?.forEach { it.deleteRecursively() }
                target
            }
        }
        if (result is Outcome.Failure) {
            withContext(Dispatchers.IO) {
                staging.deleteRecursively()
                if (packDir.list()?.isEmpty() == true) packDir.delete()
                packDir.parentFile?.takeIf { it.list()?.isEmpty() == true }?.delete()
            }
        }
        result
    }

    /** Deletes every installed version of [packId], whatever its kind. */
    fun remove(packId: String) {
        if (!ContentPaths.isSafeId(packId)) return
        root.listFiles()?.forEach { kind -> File(kind, packId).deleteRecursively() }
    }

    private fun rename(from: File, to: File) {
        if (!from.renameTo(to)) throw IOException("rename failed")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val PART = ".part"
    }
}

/** A downloaded file did not match the size or SHA-256 of the manifest. The message holds the pack id only. */
class PackHashMismatchException(packId: String) : Exception("hash mismatch in pack $packId")
