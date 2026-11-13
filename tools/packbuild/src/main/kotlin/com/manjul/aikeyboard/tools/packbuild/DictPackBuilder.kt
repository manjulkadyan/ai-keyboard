/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import java.io.File

/**
 * Builds the dictionary pack (SQLite, `format = 1`) from Wiktextract JSONL, one word and part of speech per line
 * (the kaikki.org line format). It reads only `word`, `pos`, `senses[].glosses`, `senses[].examples[].text` and the
 * `synonyms[].word` linkages of the word and of each kept sense. Field names checked against
 * https://github.com/tatuylonen/wiktextract (README.md and src/wiktextract/extractor/en/type_utils.py).
 *
 * Rules: the first non-blank gloss of a sense is its definition, and a sense without one is dropped. Only the first
 * [MAX_SENSES] senses per headword and part of speech are kept, across lines. Rows are sorted by `headword_norm`,
 * `headword`, `pos` and source order, so the same input always gives the same bytes.
 */
object DictPackBuilder {
    const val FORMAT = 1
    const val MAX_SENSES = 3

    /** The licence of the team-written fixture. A real extract passes its own (CC-BY-SA, R-08: never in the repo). */
    val FIXTURE_LICENCE: PackLicence = TEAM_LICENCE

    private val json = Json { ignoreUnknownKeys = true }

    internal data class Row(
        val headword: String,
        val pos: String,
        val definition: String,
        val example: String?,
        val synonyms: List<String>,
        val order: Int,
    ) {
        val norm: String = HeadwordNorm.of(headword)
    }

    fun build(input: File, out: File, lang: String, version: Int, licence: PackLicence = FIXTURE_LICENCE) {
        require(lang.isNotBlank()) { "lang is empty" }
        require(version > 0) { "version must be positive" }
        val rows = read(input)
        writeSqlite(out) { c ->
            c.exec(
                "CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)",
                "CREATE TABLE entry(id INTEGER PRIMARY KEY, headword TEXT NOT NULL, headword_norm TEXT NOT NULL, " +
                    "pos TEXT NOT NULL, definition TEXT NOT NULL, example TEXT, synonyms TEXT)",
                "CREATE INDEX idx_entry_norm ON entry(headword_norm)",
            )
            c.insertMeta(listOf("format" to "$FORMAT", "lang" to lang, "version" to "$version") + licence.metaRows())
            c.prepareStatement(
                "INSERT INTO entry(id, headword, headword_norm, pos, definition, example, synonyms) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
            ).use { ps ->
                rows.forEachIndexed { i, r ->
                    ps.setInt(1, i + 1)
                    ps.setString(2, r.headword)
                    ps.setString(3, r.norm)
                    ps.setString(4, r.pos)
                    ps.setString(5, r.definition)
                    ps.setString(6, r.example)
                    ps.setString(7, encodeSynonyms(r.synonyms))
                    ps.executeUpdate()
                }
            }
        }
    }

    /** `synonyms` is a JSON array of strings, or NULL when there are none. Task 2 and Task 4 read the same encoding. */
    fun encodeSynonyms(synonyms: List<String>): String? =
        if (synonyms.isEmpty()) null else buildJsonArray { synonyms.forEach { add(JsonPrimitive(it)) } }.toString()

    internal fun read(input: File): List<Row> {
        val rows = ArrayList<Row>()
        val kept = HashMap<Pair<String, String>, Int>()
        input.useLines { lines ->
            lines.forEachIndexed { lineNo, raw ->
                if (raw.isBlank()) return@forEachIndexed
                val obj = runCatching { json.parseToJsonElement(raw) as JsonObject }
                    .getOrElse { throw IllegalArgumentException("line ${lineNo + 1} is not a JSON object") }
                val word = obj.string("word")?.trim().orEmpty()
                val pos = obj.string("pos")?.trim().orEmpty()
                if (word.isEmpty() || pos.isEmpty()) return@forEachIndexed
                val key = HeadwordNorm.of(word) to pos
                val wordSynonyms = linkageWords(obj["synonyms"])
                for (sense in obj.array("senses")) {
                    if ((kept[key] ?: 0) >= MAX_SENSES) break
                    val s = sense as? JsonObject ?: continue
                    val gloss = s.array("glosses").firstNotNullOfOrNull { (it as? JsonPrimitive)?.text() } ?: continue
                    val example = s.array("examples")
                        .firstNotNullOfOrNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.text() }
                    val synonyms = (linkageWords(s["synonyms"]) + wordSynonyms).distinct()
                    rows += Row(word, pos, gloss, example, synonyms, rows.size)
                    kept[key] = (kept[key] ?: 0) + 1
                }
            }
        }
        return rows.sortedWith(compareBy<Row>({ it.norm }, { it.headword }, { it.pos }, { it.order }))
    }

    private fun linkageWords(e: JsonElement?): List<String> =
        (e as? JsonArray).orEmpty().mapNotNull { ((it as? JsonObject)?.get("word") as? JsonPrimitive)?.text() }

    private fun JsonPrimitive.text(): String? = if (isString) content.trim().ifEmpty { null } else null

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.array(key: String): List<JsonElement> = (get(key) as? JsonArray).orEmpty()
}
