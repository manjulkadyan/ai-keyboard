/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest
import java.sql.DriverManager
import java.util.Locale

/**
 * Writes the content manifest (`format = 1`, plan decision 4) for a content root such as `assets/content/v1/`:
 * - every `dict/<id>-<version>.db` pack (`kind = dict`; version, lang and licence from its `meta` table);
 * - every pack of `packs.tsv` that has a `stickers/<id>/` folder (`kind = stickers`).
 *
 * File paths are relative to the root. Packs are sorted by id and files by path, keys keep a fixed order and
 * `issued_at` (epoch milliseconds) comes from the caller, so the same content gives the same bytes.
 */
object ManifestWriter {
    const val FORMAT = 1
    const val MIME_SQLITE = "application/vnd.sqlite3"
    const val MIME_PNG = "image/png"

    fun write(root: File, packsTsv: File, issuedAt: Long): ByteArray {
        require(issuedAt >= 0) { "issued_at must not be negative" }
        val packs = (dictPacks(root) + stickerPacks(root, packsTsv)).sortedBy { it.first }
        require(packs.map { it.first }.distinct().size == packs.size) { "duplicate pack id in $root" }
        val json = buildJsonObject {
            put("format", FORMAT)
            put("issued_at", issuedAt)
            put("packs", buildJsonArray { packs.forEach { add(it.second) } })
        }
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun dictPacks(root: File): List<Pair<String, JsonObject>> {
        val files = File(root, "dict").listFiles { f -> f.isFile && f.name.endsWith(".db") }.orEmpty()
        return files.map { file ->
            val meta = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}?open_mode=1").use { c ->
                c.createStatement().use { st ->
                    st.executeQuery("SELECT key, value FROM meta").use { rs ->
                        buildMap { while (rs.next()) put(rs.getString(1), rs.getString(2)) }
                    }
                }
            }
            val version = requireNotNull(meta["version"]?.toIntOrNull()) { "${file.name}: meta has no version" }
            val id = file.name.removeSuffix("-$version.db")
            require(id != file.name && id.isNotEmpty()) { "${file.name} must be named <id>-$version.db" }
            val lang = meta.getValue("lang")
            val title = Locale.forLanguageTag(lang).getDisplayLanguage(Locale.ENGLISH) + " dictionary"
            id to pack(
                id = id,
                kind = "dict",
                version = version,
                premium = false,
                title = title,
                files = listOf(file),
                root = root,
                mime = MIME_SQLITE,
                licence = Triple(
                    meta.getValue("licence_name"),
                    meta.getValue("licence_url"),
                    meta.getValue("attribution"),
                ),
            )
        }
    }

    private fun stickerPacks(root: File, packsTsv: File): List<Pair<String, JsonObject>> =
        StickerArt.readPacks(packsTsv).mapNotNull { row ->
            val dir = File(root, "stickers/${row.id}")
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }.orEmpty().toList()
            if (files.isEmpty()) return@mapNotNull null
            row.id to pack(
                id = row.id,
                kind = "stickers",
                version = row.version,
                premium = row.premium,
                title = row.title,
                files = files,
                root = root,
                mime = MIME_PNG,
                licence = Triple(TEAM_LICENCE.name, TEAM_LICENCE.url, TEAM_LICENCE.attribution),
            )
        }

    private fun pack(
        id: String,
        kind: String,
        version: Int,
        premium: Boolean,
        title: String,
        files: List<File>,
        root: File,
        mime: String,
        licence: Triple<String, String, String>,
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("kind", kind)
        put("version", version)
        put("premium", premium)
        put("title", title)
        put(
            "files",
            buildJsonArray {
                for (f in files.sortedBy { it.name }) {
                    add(
                        buildJsonObject {
                            put("path", f.relativeTo(root).invariantSeparatorsPath)
                            put("sha256", sha256Hex(f.readBytes()))
                            put("bytes", JsonPrimitive(f.length()))
                            put("mime", mime)
                        },
                    )
                }
            },
        )
        put(
            "licence",
            buildJsonObject {
                put("name", licence.first)
                put("url", licence.second)
                put("attribution", licence.third)
            },
        )
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
