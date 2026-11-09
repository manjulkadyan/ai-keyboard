/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.themegen

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KeySoundSynthTest {
    private fun tmp(name: String): File =
        Files.createTempDirectory("themegen").toFile().resolve(name).also { it.mkdirs() }

    @Test fun everySoundIs48kMono16BitAtMost40ms() {
        for (name in KeySoundSynth.NAMES) {
            val wav = KeySoundSynth.wav(name)
            val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(wav, 0, 4))
            assertEquals("WAVE", String(wav, 8, 4))
            assertEquals(wav.size - 8, b.getInt(4))
            assertEquals(1, b.getShort(20).toInt())
            assertEquals(1, b.getShort(22).toInt())
            assertEquals(48_000, b.getInt(24))
            assertEquals(96_000, b.getInt(28))
            assertEquals(16, b.getShort(34).toInt())
            assertEquals("data", String(wav, 36, 4))
            val samples = b.getInt(40) / 2
            assertTrue(samples in 1..1920, "$name: $samples samples")
            assertTrue(wav.size <= 10 * 1024)
        }
    }

    @Test fun outputIsDeterministicAndTheFourSoundsDiffer() {
        val all = KeySoundSynth.NAMES.map { KeySoundSynth.wav(it) }
        for ((i, name) in KeySoundSynth.NAMES.withIndex()) assertContentEquals(all[i], KeySoundSynth.wav(name))
        assertEquals(4, all.map { it.toList() }.toSet().size)
    }

    @Test fun samplesStartAndEndSilentWithoutClipping() {
        for (name in KeySoundSynth.NAMES) {
            val pcm = KeySoundSynth.samples(name)
            assertEquals(0, pcm.last().toInt(), name)
            assertTrue(pcm.any { it.toInt() != 0 }, name)
            assertTrue(pcm.all { it in Short.MIN_VALUE + 1..Short.MAX_VALUE }, name)
        }
    }

    @Test fun writeTouchesOnlyTheFourKeyFiles() {
        val raw = tmp("raw")
        val other = File(raw, "other_sound.ogg").apply { writeText("keep") }
        val stale = File(raw, "key_soft.wav").apply { writeText("stale") }
        KeySoundSynth.write(raw)
        assertEquals("keep", other.readText())
        assertNotEquals("stale", stale.readText())
        assertEquals(setOf("key_soft.wav", "key_crisp.wav", "key_wood.wav", "key_pop.wav", "other_sound.ogg"),
            raw.list()!!.toSet())
    }

    @Test fun mainRefusesAThemeDirWithTheWrongName() {
        val wrong = tmp("not-themes")
        val keep = File(wrong, "stylesheets").apply { mkdirs() }.resolve("keep.json").apply { writeText("{}") }
        assertFailsWith<IllegalArgumentException> { Main.run(wrong, tmp("raw")) }
        assertTrue(keep.isFile)
    }

    @Test fun mainWritesStylesheetsAndSounds() {
        val themes = tmp("com.manjul.aikeyboard.themes")
        val raw = tmp("raw")
        Main.run(themes, raw)
        assertTrue(File(themes, "extension.json").isFile)
        assertEquals(24, File(themes, "stylesheets").list()!!.size)
        assertEquals(4, raw.list()!!.size)
        assertFalse(File(raw, "extension.json").exists())
    }
}
