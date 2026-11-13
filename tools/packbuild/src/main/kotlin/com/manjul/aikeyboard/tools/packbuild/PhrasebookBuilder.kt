/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import java.io.File

/**
 * Builds the bundled phrasebook DB (TRD §3.1: `meta`, `phrase_category(id, key, sort)`, `phrase(id, category_id,
 * lang, text, audio_hint)`) from a TSV with the header `category_key sort phrase_id lang text`.
 *
 * `phrase.id` is the source `phrase_id`, so favourites (`c:<id>`) survive a new version. A duplicate `phrase_id`, a
 * category with two `sort` values or an empty field fails the build. Category ids follow `sort`, then `key`.
 */
object PhrasebookBuilder {
    const val FORMAT = 1
    val HEADER = listOf("category_key", "sort", "phrase_id", "lang", "text")

    internal data class Line(
        val categoryKey: String,
        val sort: Int,
        val phraseId: Long,
        val lang: String,
        val text: String,
    )

    fun build(input: File, out: File, version: Int = 1, licence: PackLicence = TEAM_LICENCE) {
        require(version > 0) { "version must be positive" }
        val lines = read(input)
        val categories = lines.groupBy { it.categoryKey }.map { (key, ls) ->
            val sorts = ls.map { it.sort }.distinct()
            require(sorts.size == 1) { "category $key has more than one sort value: $sorts" }
            key to sorts.single()
        }.sortedWith(compareBy({ it.second }, { it.first }))
        val categoryId = categories.mapIndexed { i, (key, _) -> key to (i + 1) }.toMap()
        writeSqlite(out) { c ->
            c.exec(
                "CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)",
                "CREATE TABLE phrase_category(id INTEGER PRIMARY KEY, key TEXT NOT NULL UNIQUE, sort INTEGER NOT NULL)",
                "CREATE TABLE phrase(id INTEGER PRIMARY KEY, " +
                    "category_id INTEGER NOT NULL REFERENCES phrase_category(id), lang TEXT NOT NULL, " +
                    "text TEXT NOT NULL, audio_hint TEXT)",
                "CREATE INDEX idx_phrase_category ON phrase(category_id)",
            )
            c.insertMeta(listOf("format" to "$FORMAT", "version" to "$version") + licence.metaRows())
            c.prepareStatement("INSERT INTO phrase_category(id, key, sort) VALUES (?, ?, ?)").use { ps ->
                for ((key, sort) in categories) {
                    ps.setInt(1, categoryId.getValue(key))
                    ps.setString(2, key)
                    ps.setInt(3, sort)
                    ps.executeUpdate()
                }
            }
            c.prepareStatement("INSERT INTO phrase(id, category_id, lang, text, audio_hint) VALUES (?, ?, ?, ?, NULL)")
                .use { ps ->
                    for (l in lines.sortedBy { it.phraseId }) {
                        ps.setLong(1, l.phraseId)
                        ps.setInt(2, categoryId.getValue(l.categoryKey))
                        ps.setString(3, l.lang)
                        ps.setString(4, l.text)
                        ps.executeUpdate()
                    }
                }
        }
    }

    internal fun read(input: File): List<Line> {
        val all = input.readLines().filter { it.isNotBlank() }
        require(all.firstOrNull()?.split('\t') == HEADER) { "the first line must be ${HEADER.joinToString("\\t")}" }
        val seen = HashSet<Long>()
        return all.drop(1).mapIndexed { i, raw ->
            val f = raw.split('\t')
            val where = "line ${i + 2}"
            require(f.size == HEADER.size) { "$where has ${f.size} fields, want ${HEADER.size}" }
            require(f.all { it.isNotBlank() }) { "$where has an empty field" }
            val sort = requireNotNull(f[1].toIntOrNull()) { "$where: sort is not a number" }
            val id = requireNotNull(f[2].toLongOrNull()?.takeIf { it > 0 }) { "$where: phrase_id must be positive" }
            require(seen.add(id)) { "$where: duplicate phrase_id $id" }
            Line(f[0].trim(), sort, id, f[3].trim(), f[4].trim())
        }
    }
}
