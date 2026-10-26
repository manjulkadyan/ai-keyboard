/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.suggest

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The `.sdict` binary dictionary, big-endian (`DataOutputStream`):
 *
 * ```
 * "AKSD"                 4 bytes magic
 * version                u8, = 1
 * locale                 modified UTF-8 (writeUTF)
 * wordCount              i32
 * wordCount x { word: writeUTF, frequency: u8 }   strictly sorted by String.compareTo
 * bigramCount            i32 (0 when the file carries no bigrams)
 * bigramCount x { prevIndex: i32, wordIndex: i32, frequency: u8 }
 * ```
 *
 * Frequencies are log-scaled to 1..255 against the largest count in the table. Errors never carry file content.
 */
object DictionaryFormat {
    const val VERSION: Int = 1
    private val MAGIC = byteArrayOf('A'.code.toByte(), 'K'.code.toByte(), 'S'.code.toByte(), 'D'.code.toByte())

    fun write(
        out: OutputStream,
        locale: String,
        unigrams: List<Pair<String, Int>>,
        bigrams: List<Triple<String, String, Int>>,
    ) {
        val merged = HashMap<String, Int>(unigrams.size * 2)
        for ((word, count) in unigrams) {
            if (word.isEmpty()) continue
            val c = count.coerceAtLeast(0)
            merged[word] = maxOf(merged[word] ?: 0, c)
        }
        val words = merged.keys.sorted()
        val index = HashMap<String, Int>(words.size * 2).apply { words.forEachIndexed { i, w -> put(w, i) } }
        val maxCount = merged.values.maxOrNull() ?: 0

        val bigramMap = LinkedHashMap<Long, Int>()
        for ((prev, word, count) in bigrams) {
            val p = index[prev] ?: continue
            val w = index[word] ?: continue
            val key = (p.toLong() shl 32) or w.toLong()
            bigramMap[key] = maxOf(bigramMap[key] ?: 0, count.coerceAtLeast(0))
        }
        val maxBigram = bigramMap.values.maxOrNull() ?: 0

        val data = DataOutputStream(BufferedOutputStream(out))
        data.write(MAGIC)
        data.writeByte(VERSION)
        data.writeUTF(locale)
        data.writeInt(words.size)
        for (w in words) {
            data.writeUTF(w)
            data.writeByte(scale(merged.getValue(w), maxCount))
        }
        data.writeInt(bigramMap.size)
        for ((key, count) in bigramMap.entries.sortedBy { it.key }) {
            data.writeInt((key ushr 32).toInt())
            data.writeInt(key.toInt())
            data.writeByte(scale(count, maxBigram))
        }
        data.flush()
    }

    fun read(input: InputStream): SuggestDictionary {
        val data = DataInputStream(if (input is BufferedInputStream) input else BufferedInputStream(input))
        try {
            val magic = ByteArray(MAGIC.size)
            data.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "Not a suggest dictionary" }
            val version = data.readUnsignedByte()
            require(version == VERSION) { "Unsupported suggest dictionary version" }
            val locale = data.readUTF()
            val count = data.readInt()
            require(count >= 0) { "Corrupt suggest dictionary" }
            val words = arrayOfNulls<String>(count)
            val frequencies = ByteArray(count)
            for (i in 0 until count) {
                val w = data.readUTF()
                require(w.isNotEmpty() && (i == 0 || words[i - 1]!! < w)) { "Corrupt suggest dictionary" }
                words[i] = w
                frequencies[i] = data.readUnsignedByte().toByte()
            }
            val bigramCount = data.readInt()
            require(bigramCount >= 0) { "Corrupt suggest dictionary" }
            val bigrams = HashMap<Long, Int>(bigramCount * 2)
            repeat(bigramCount) {
                val p = data.readInt()
                val w = data.readInt()
                val f = data.readUnsignedByte()
                require(p in 0 until count && w in 0 until count) { "Corrupt suggest dictionary" }
                bigrams[(p.toLong() shl 32) or w.toLong()] = f
            }
            @Suppress("UNCHECKED_CAST")
            return SuggestDictionary(locale, words as Array<String>, frequencies, bigrams)
        } catch (_: IOException) {
            // EOF / malformed UTF: drop the original exception so nothing about the bytes leaks.
            throw IllegalArgumentException("Truncated or corrupt suggest dictionary")
        }
    }

    private fun scale(count: Int, max: Int): Int {
        if (count <= 0 || max <= 0) return 0
        return (255.0 * ln(1.0 + count) / ln(1.0 + max)).roundToInt().coerceIn(1, 255)
    }
}
