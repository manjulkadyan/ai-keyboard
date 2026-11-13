/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.download

import com.manjul.aikeyboard.core.common.Outcome
import com.manjul.aikeyboard.core.content.FakeContentSource
import com.manjul.aikeyboard.core.content.manifest.PackFile
import com.manjul.aikeyboard.core.content.manifest.PackKindSpec
import com.manjul.aikeyboard.core.content.manifest.PackLicence
import com.manjul.aikeyboard.core.content.manifest.PackSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PackDownloaderTest {
    @TempDir lateinit var root: File

    private val one = "sticker one".toByteArray()
    private val two = "sticker two".toByteArray()
    private val source = FakeContentSource(
        mapOf("stickers/starter_plus/01.png" to one, "stickers/starter_plus/02.png" to two),
    )

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun file(path: String, bytes: ByteArray, sha: String = sha(bytes), size: Long = bytes.size.toLong()) =
        PackFile(path = path, sha256 = sha, bytes = size, mime = "image/png")

    private fun pack(version: Int = 1, files: List<PackFile> = goodFiles()) = PackSpec(
        id = "starter_plus",
        kind = PackKindSpec.STICKERS,
        version = version,
        premium = true,
        title = "Starter Plus",
        files = files,
        licence = PackLicence("Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0", "AI Keyboard contributors"),
    )

    private fun goodFiles() = listOf(
        file("stickers/starter_plus/01.png", one),
        file("stickers/starter_plus/02.png", two),
    )

    /** Every file under [root], relative, so a test sees leftovers such as `.part` files. */
    private fun tree(): List<String> = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).path }
        .sorted().toList()

    @Test fun `a good pack lands in the versioned folder`() = runTest {
        val result = PackDownloader(source, root).install(pack())
        val dir = assertIs<Outcome.Success<File>>(result).value
        assertEquals(File(root, "stickers/starter_plus/1").canonicalFile, dir.canonicalFile)
        assertContentEquals(one, File(dir, "01.png").readBytes())
        assertContentEquals(two, File(dir, "02.png").readBytes())
        assertEquals(listOf("stickers/starter_plus/1/01.png", "stickers/starter_plus/1/02.png"), tree())
    }

    @Test fun `a dict pack goes under the dict folder`() = runTest {
        val db = "sqlite".toByteArray()
        source.files = mapOf("dict/en-fixture-1.db" to db)
        val spec = pack(files = listOf(file("dict/en-fixture-1.db", db)))
            .copy(id = "en-fixture", kind = PackKindSpec.DICT)
        val dir = assertIs<Outcome.Success<File>>(PackDownloader(source, root).install(spec)).value
        assertEquals(File(root, "dict/en-fixture/1").canonicalFile, dir.canonicalFile)
        assertContentEquals(db, File(dir, "en-fixture-1.db").readBytes())
    }

    @Test fun `a hash mismatch leaves no file`() = runTest {
        val bad = listOf(file("stickers/starter_plus/01.png", one), file("stickers/starter_plus/02.png", two, sha(one)))
        val result = PackDownloader(source, root).install(pack(files = bad))
        assertIs<PackHashMismatchException>(assertIs<Outcome.Failure>(result).error)
        assertEquals(emptyList(), tree())
    }

    @Test fun `a size mismatch leaves no file`() = runTest {
        val bad = listOf(file("stickers/starter_plus/01.png", one, size = one.size + 1L))
        val result = PackDownloader(source, root).install(pack(files = bad))
        assertIs<PackHashMismatchException>(assertIs<Outcome.Failure>(result).error)
        assertEquals(emptyList(), tree())
    }

    @Test fun `a failed fetch leaves no file and keeps the installed version`() = runTest {
        val downloader = PackDownloader(source, root)
        downloader.install(pack())
        source.offline = true
        assertIs<Outcome.Failure>(downloader.install(pack(version = 2)))
        assertEquals(listOf("stickers/starter_plus/1/01.png", "stickers/starter_plus/1/02.png"), tree())
    }

    @Test fun `a new version replaces the old one`() = runTest {
        val downloader = PackDownloader(source, root)
        downloader.install(pack())
        assertIs<Outcome.Success<File>>(downloader.install(pack(version = 2)))
        assertEquals(listOf("stickers/starter_plus/2/01.png", "stickers/starter_plus/2/02.png"), tree())
    }

    @Test fun `installing the same version again works`() = runTest {
        val downloader = PackDownloader(source, root)
        downloader.install(pack())
        assertIs<Outcome.Success<File>>(downloader.install(pack()))
        assertEquals(listOf("stickers/starter_plus/1/01.png", "stickers/starter_plus/1/02.png"), tree())
    }

    @Test fun `remove deletes the pack and nothing else`() = runTest {
        val downloader = PackDownloader(source, root)
        downloader.install(pack())
        val other = File(root, "dict/en-fixture/1/en-fixture-1.db").apply { parentFile!!.mkdirs(); writeText("x") }
        downloader.remove("starter_plus")
        assertFalse(File(root, "stickers/starter_plus").exists())
        assertTrue(other.isFile)
    }

    @Test fun `remove refuses an unsafe id`() = runTest {
        val outside = File(root.parentFile, "keep-${root.name}").apply { writeText("x") }
        try {
            PackDownloader(source, File(root, "content")).remove("..")
            assertTrue(outside.isFile)
            assertTrue(root.isDirectory)
        } finally {
            outside.delete()
        }
    }

    /** Every file and folder under [root], so a test also sees an empty `.staging-*` folder. */
    private fun everything(): List<String> = root.walkTopDown().filter { it != root }.map { it.relativeTo(root).path }
        .sorted().toList()

    @Test fun `a cancelled install leaves no staging folder and no part file`() = runTest {
        val downloader = PackDownloader(source, root)
        val inSecondFetch = CompletableDeferred<Unit>()
        source.beforeFetch = { path ->
            if (path.endsWith("02.png")) {
                inSecondFetch.complete(Unit)
                awaitCancellation()
            }
        }
        val job = launch { downloader.install(pack()) }
        inSecondFetch.await()
        assertTrue(everything().any { it.contains(".staging-1") }, "the first file is staged: ${everything()}")
        job.cancelAndJoin()
        assertEquals(emptyList(), everything())
    }

    @Test fun `a cancelled upgrade keeps the installed version and nothing else`() = runTest {
        val downloader = PackDownloader(source, root)
        downloader.install(pack())
        source.beforeFetch = { path -> if (path.endsWith("02.png")) awaitCancellation() }
        val job = launch { downloader.install(pack(version = 2)) }
        testScheduler.advanceUntilIdle()
        job.cancelAndJoin()
        assertEquals(listOf("stickers/starter_plus/1/01.png", "stickers/starter_plus/1/02.png"), tree())
        assertFalse(everything().any { it.contains(".staging") || it.endsWith(".part") }, "${everything()}")
    }

    @Test fun `an oversized file fails without being read in full`() = runTest {
        val big = ByteArray(1_000_000) { 7 }
        source.files = mapOf("stickers/starter_plus/01.png" to big)
        val spec = pack(files = listOf(file("stickers/starter_plus/01.png", one)))
        val result = PackDownloader(source, root).install(spec)
        assertIs<PackHashMismatchException>(assertIs<Outcome.Failure>(result).error)
        assertTrue(source.read <= one.size + 1L, "read ${source.read} bytes of ${big.size}")
        assertEquals(emptyList(), everything())
    }

    @Test fun `an unsafe pack id fails before any folder is made`() = runTest {
        val bad = pack().copy(id = "../escape")
        assertIs<Outcome.Failure>(PackDownloader(source, root).install(bad))
        assertEquals(emptyList(), source.requests)
        assertEquals(emptyList(), everything())
        assertFalse(File(root.parentFile, "escape").exists())
    }

    @Test fun `remove waits for a running install`() = runTest {
        val downloader = PackDownloader(source, root)
        val release = CompletableDeferred<Unit>()
        source.beforeFetch = { path -> if (path.endsWith("02.png")) release.await() }
        val install = async { downloader.install(pack()) }
        testScheduler.advanceUntilIdle()
        val remove = launch { downloader.remove("starter_plus") }
        testScheduler.advanceUntilIdle()
        assertFalse(remove.isCompleted, "remove must wait for the install lock")
        release.complete(Unit)
        assertIs<Outcome.Success<File>>(install.await())
        remove.join()
        assertFalse(File(root, "stickers/starter_plus").exists())
    }
}
