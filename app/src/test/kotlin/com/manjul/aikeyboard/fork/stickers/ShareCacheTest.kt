/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The share cache: staged stickers live only in `cache/share/` and are swept after 24 h (S5 Task 6). */
class ShareCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private val hour = 60 * 60 * 1000L
    private var now = 100 * hour

    @Test fun `a 25 h old file is deleted and a 1 h old file stays`() {
        val cache = ShareCache(tmp.root) { now }
        val share = File(tmp.root, "share").apply { mkdirs() }
        val old = File(share, "old.png").apply { writeText("o") }
        old.setLastModified(now - 25 * hour)
        val fresh = File(share, "fresh.png").apply { writeText("f") }
        fresh.setLastModified(now - hour)
        val outside = File(tmp.root, "other.png").apply { writeText("x") }
        outside.setLastModified(now - 48 * hour)

        cache.sweep()

        assertFalse(old.exists())
        assertTrue(fresh.exists())
        assertTrue(outside.exists(), "the sweep touches only share/")
    }

    @Test fun `stage never writes outside share`() {
        val cache = ShareCache(tmp.root) { now }
        val src = File(tmp.newFolder("in"), "..%2F..%2Fevil.png").apply { writeBytes(byteArrayOf(7, 8)) }
        val staged = cache.stage(src)
        val share = File(tmp.root, "share").canonicalFile
        assertEquals(share, staged.canonicalFile.parentFile)
        assertTrue(staged.name.matches(Regex("[0-9a-f-]{36}\\.png")))
        assertTrue(staged.readBytes().contentEquals(byteArrayOf(7, 8)))
        assertEquals(listOf(staged.name), share.list()!!.toList())
        assertTrue(cache.isStaged(staged))
    }

    @Test fun `a staged file survives the sweep that runs right after`() {
        val cache = ShareCache(tmp.root) { now }
        val staged = cache.stage(File(tmp.root, "src.png").apply { writeText("s") })
        cache.sweep()
        assertTrue(staged.exists())
    }

    @Test fun `a file outside share is not staged`() {
        val cache = ShareCache(tmp.root) { now }
        assertFalse(cache.isStaged(File(tmp.root, "backup-and-restore/x.png")))
        assertFalse(cache.isStaged(File(tmp.root, "share/../x.png")))
        assertFailsWith<IllegalArgumentException> { cache.requireStaged(File(tmp.root, "x.png")) }
    }
}
