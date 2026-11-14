/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One definition of a word fetched online: the `dictionary_entry` table (Sprint 5). Only [SOURCE_ONLINE] rows live
 * here; offline packs stay in their own files. The looked-up word is user content: it is never logged.
 */
@Entity(
    tableName = "dictionary_entry",
    indices = [Index(value = ["lang", "headword_norm"])],
)
data class DictionaryEntryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "lang") val lang: String,
    @ColumnInfo(name = "headword") val headword: String,
    /** `HeadwordNorm.of(headword)` from `:core:content`. */
    @ColumnInfo(name = "headword_norm") val headwordNorm: String,
    @ColumnInfo(name = "pos") val pos: String,
    @ColumnInfo(name = "definition") val definition: String,
    @ColumnInfo(name = "example") val example: String?,
    @ColumnInfo(name = "source") val source: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    /**
     * A JSON array of strings, the same format as the pack's `entry.synonyms` (`DictPackBuilder.encodeSynonyms`), for
     * example `["pome"]`. Null when the source gave none (plan F3).
     */
    @ColumnInfo(name = "synonyms") val synonyms: String? = null,
) {
    // Keeps the looked-up word out of any accidental log or exception message (PC-10).
    override fun toString(): String = "DictionaryEntryEntity(id=$id, lang=$lang, source=$source, fetchedAt=$fetchedAt)"

    companion object {
        const val SOURCE_ONLINE = "online"
    }
}
