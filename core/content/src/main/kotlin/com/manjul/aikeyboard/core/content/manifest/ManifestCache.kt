/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.manifest

import com.manjul.aikeyboard.core.content.source.ContentSource
import com.manjul.aikeyboard.core.content.source.UnavailableContentSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The last good [ContentManifest], kept in [dir] with its signature. [current] asks [source] at most once per
 * [MAX_AGE_MS] once the source answered (a rejected answer counts); a fetch that got no answer is tried again on the
 * next call. A new manifest replaces the stored
 * one only when it verifies and is not older (`issued_at`); otherwise the last good copy stays. The stored copy is
 * verified again when read, so a changed file is not trusted.
 */
class ManifestCache(
    private val dir: File,
    private val source: ContentSource,
    private val verifier: ManifestVerifier,
    private val clock: () -> Long,
) {
    private val mutex = Mutex()
    private var memory: ContentManifest? = null
    private var memoryLoaded = false

    private val manifestFile get() = File(dir, MANIFEST_PATH)
    private val signatureFile get() = File(dir, SIGNATURE_PATH)
    private val stampFile get() = File(dir, STAMP)

    suspend fun current(): ContentManifest? = mutex.withLock {
        if (source === UnavailableContentSource) return@withLock null
        withContext(Dispatchers.IO) {
            val stored = stored()
            val fetchedAt = stampFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
            val now = clock()
            if (stored != null && fetchedAt != null && now >= fetchedAt && now - fetchedAt < MAX_AGE_MS) {
                return@withContext stored
            }
            val bytes: ByteArray
            val signature: String
            try {
                bytes = source.fetch(MANIFEST_PATH)
                signature = source.fetch(SIGNATURE_PATH).decodeToString()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No answer (offline, HTTP error): the next call tries again.
                return@withContext stored
            }
            val manifest = verifier.verify(bytes, signature)
            if (manifest == null || (stored != null && manifest.issuedAt < stored.issuedAt)) {
                // An answer that is rejected still counts as this day's fetch.
                writeStamp(now)
                return@withContext stored
            }
            save(bytes, signature)
            writeStamp(now)
            memory = manifest
            manifest
        }
    }

    private fun stored(): ContentManifest? {
        if (!memoryLoaded) {
            memory = runCatching {
                verifier.verify(manifestFile.readBytes(), signatureFile.readText())
            }.getOrNull()
            memoryLoaded = true
        }
        return memory
    }

    private fun save(bytes: ByteArray, signature: String) {
        dir.mkdirs()
        // Written through temporary files and renamed. A crash between the two leaves a pair that fails the check
        // in stored(), so it is never trusted.
        write(manifestFile, bytes)
        write(signatureFile, signature.toByteArray())
    }

    private fun writeStamp(now: Long) {
        dir.mkdirs()
        write(stampFile, now.toString().toByteArray())
    }

    private fun write(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "rename failed" }
        }
    }

    companion object {
        const val MANIFEST_PATH = "manifest.json"
        const val SIGNATURE_PATH = "manifest.json.sig"
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000
        private const val STAMP = "manifest.fetched"
    }
}
