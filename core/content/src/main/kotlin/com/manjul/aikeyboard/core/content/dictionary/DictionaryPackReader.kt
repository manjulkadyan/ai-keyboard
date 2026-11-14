/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.dictionary

import android.database.sqlite.SQLiteDatabase
import com.manjul.aikeyboard.core.content.text.HeadwordNorm
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.io.Closeable
import java.io.File

/** One sense of a headword, from the offline pack or from an online answer. */
data class Definition(
    val headword: String,
    val pos: String,
    val definition: String,
    val example: String?,
    val synonyms: List<String>,
) {
    // Keeps the looked-up word out of any accidental log or exception message (F17).
    override fun toString(): String = "Definition(pos=$pos)"
}

/** Who wrote the definitions and under which licence (FR-21 AC3). */
data class Attribution(
    val source: String,
    val attribution: String,
    val licenceName: String,
    val licenceUrl: String,
)

/**
 * Reads an installed dictionary pack (`:tools:packbuild` format 1): a read-only SQLite file with a `meta` key-value
 * table and an `entry` table keyed by `headword_norm`. It never logs.
 */
class DictionaryPackReader private constructor(private val db: SQLiteDatabase) : Closeable {
    private val meta: Map<String, String> = buildMap {
        db.rawQuery("SELECT key, value FROM meta", null).use { c ->
            while (c.moveToNext()) put(c.getString(0), c.getString(1))
        }
    }

    val version: Int = meta["version"]?.toIntOrNull() ?: 0

    val attribution: Attribution? = run {
        val source = meta["source"] ?: return@run null
        Attribution(
            source = source,
            attribution = meta["attribution"] ?: return@run null,
            licenceName = meta["licence_name"] ?: return@run null,
            licenceUrl = meta["licence_url"] ?: return@run null,
        )
    }

    /** The senses of [word] in pack order. A blank or unknown word gives an empty list. */
    fun find(word: String): List<Definition> {
        val norm = HeadwordNorm.of(word)
        if (norm.isEmpty()) return emptyList()
        return db.rawQuery(
            "SELECT headword, pos, definition, example, synonyms FROM entry WHERE headword_norm = ? ORDER BY id",
            arrayOf(norm),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Definition(
                            headword = c.getString(0),
                            pos = c.getString(1),
                            definition = c.getString(2),
                            example = if (c.isNull(3)) null else c.getString(3),
                            synonyms = if (c.isNull(4)) emptyList() else parseSynonyms(c.getString(4)),
                        ),
                    )
                }
            }
        }
    }

    override fun close() = db.close()

    companion object {
        /** Opens [file] read-only. A missing file, a broken file or one without the tables gives null. */
        fun open(file: File): DictionaryPackReader? {
            if (!file.isFile) return null
            var db: SQLiteDatabase? = null
            return try {
                db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
                DictionaryPackReader(db)
            } catch (_: RuntimeException) {
                db?.close()
                null
            }
        }

        /** A JSON array of strings; anything else gives no synonyms. */
        internal fun parseSynonyms(json: String): List<String> = runCatching {
            (Json.parseToJsonElement(json) as? JsonArray).orEmpty().mapNotNull { e ->
                (e as? JsonPrimitive)?.takeIf { it.isString }?.content
            }
        }.getOrDefault(emptyList())
    }
}
