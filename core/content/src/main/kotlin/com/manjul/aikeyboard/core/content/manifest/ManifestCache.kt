/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.manifest

import com.manjul.aikeyboard.core.content.source.ContentSource
import com.manjul.aikeyboard.core.content.source.UnavailableContentSource
import com.manjul.aikeyboard.core.content.source.fetchBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The last good [ContentManifest], kept in [dir] with its signature. [current] asks [source] at most once per
 * [MAX_AGE_MS] once the source answered (a rejected answer counts, also when there is no stored copy yet). A fetch that
 * got no answer is tried again on the next call. A new manifest replaces the stored one only when it verifies and is
 * not older (`issued_at`). Otherwise the last good copy stays. The stored copy is verified again when read, so a
 * changed file is not trusted. The manifest and its signature are read with the size limits [MAX_MANIFEST_BYTES] and
 * [MAX_SIGNATURE_BYTES].
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
            if (fetchedAt != null && now >= fetchedAt && now - fetchedAt < MAX_AGE_MS) {
                // This day's answer came already: the stored copy, or null when that answer was rejected.
                return@withContext stored
            }
            val bytes: ByteArray
            val signature: String
            try {
                bytes = source.fetchBytes(MANIFEST_PATH, MAX_MANIFEST_BYTES)
                signature = source.fetchBytes(SIGNATURE_PATH, MAX_SIGNATURE_BYTES).decodeToString()
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

    /**
     * Forgets the manifest: the in-memory copy, the stored files and the stamp, so the next [current] fetches again.
     * Delete my data calls it.
     */
    suspend fun clear(): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            memory = null
            memoryLoaded = false
            check(!dir.exists() || dir.deleteRecursively()) { "manifest cache not deleted" }
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
        const val MAX_MANIFEST_BYTES = 1L shl 20
        const val MAX_SIGNATURE_BYTES = 1L shl 10
        private const val STAMP = "manifest.fetched"
    }
}
