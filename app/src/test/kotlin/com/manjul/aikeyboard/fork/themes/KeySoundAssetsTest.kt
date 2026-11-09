/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The four committed key sounds (S4 Task 10) exist, stay small and are 48 kHz mono 16-bit PCM of at most 40 ms. */
class KeySoundAssetsTest {
    private val raw = listOf("src/main/res/raw", "app/src/main/res/raw").map(::File).first { it.isDirectory }

    @Test fun `the four raw files exist and each is at most 10 KB`() {
        for (name in listOf("soft", "crisp", "wood", "pop")) {
            val f = File(raw, "key_$name.wav")
            assertTrue(f.isFile, f.path)
            assertTrue(f.length() <= 10 * 1024, "${f.name} is ${f.length()} bytes")
            val b = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals("RIFF", String(f.readBytes(), 0, 4))
            assertEquals(1, b.getShort(20).toInt(), "PCM")
            assertEquals(1, b.getShort(22).toInt(), "mono")
            assertEquals(48_000, b.getInt(24))
            assertEquals(16, b.getShort(34).toInt())
            val samples = b.getInt(40) / 2
            assertTrue(samples in 1..1920, "${f.name} has $samples samples")
        }
    }

    @Test fun `the LICENSES file records the key sounds as generated`() {
        val licenses = File(raw, "../../assets/LICENSES.md").readText()
        assertTrue("key_soft.wav" in licenses || "key_{soft,crisp,wood,pop}.wav" in licenses)
        assertTrue(":tools:themegen" in licenses)
    }
}
