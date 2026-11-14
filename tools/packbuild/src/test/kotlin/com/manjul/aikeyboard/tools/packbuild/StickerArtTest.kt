/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StickerArtTest {
    @TempDir lateinit var tmp: File

    @Test fun `every pack gets 8 files of 512 x 512 and at most 60 KB`() {
        val packs = TestFiles.sourcesDir.resolve("stickers/packs.tsv")
        StickerArt.buildPacks(packs, tmp)
        for (pack in listOf("starter", "starter_plus")) {
            val files = File(tmp, pack).listFiles()!!.sortedBy { it.name }
            assertEquals((1..8).map { "%02d.png".format(it) }, files.map { it.name }, pack)
            for (f in files) {
                val img = ImageIO.read(f)
                assertEquals(512, img.width, f.name)
                assertEquals(512, img.height, f.name)
                assertTrue(f.length() <= 60 * 1024, "${f.name} is ${f.length()} bytes")
            }
        }
    }

    @Test fun `the same seed gives the same bytes`() {
        for (i in 1..8) assertContentEquals(StickerArt.png(seed = 42, index = i), StickerArt.png(seed = 42, index = i))
    }

    @Test fun `another seed gives other art`() {
        assertFalse(StickerArt.png(seed = 42, index = 1).contentEquals(StickerArt.png(seed = 43, index = 1)))
    }

    @Test fun `one pack can be written on its own`() {
        StickerArt.buildPacks(TestFiles.sourcesDir.resolve("stickers/packs.tsv"), tmp, only = "starter_plus")
        assertEquals(listOf("starter_plus"), tmp.listFiles()!!.map { it.name })
    }
}
