/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.phrasebook

import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream

/** A phrasebook category. [virtual] ones ("favourites", "my_phrases") are not in the DB. */
data class PhraseCategory(val key: String, val sort: Int, val virtual: Boolean = false)

/** One English catalogue phrase. Our own text, not user content. */
data class CatalogPhrase(val id: Long, val categoryKey: String, val text: String) {
    override fun toString(): String = "CatalogPhrase(id=$id, categoryKey=$categoryKey)"
}

/**
 * Reads the bundled phrasebook DB (`:tools:packbuild` format 1: `meta`, `phrase_category`, `phrase`). [open] copies
 * the asset to `<dir>/phrasebook-<version>.db` once, deletes the other versions and opens the copy read-only. The whole
 * catalogue (a few hundred rows) is read once into memory. It never logs.
 */
class PhrasebookReader private constructor(private val db: SQLiteDatabase) : Closeable {
    private val categories: List<PhraseCategory> =
        db.rawQuery("SELECT key, sort FROM phrase_category ORDER BY sort, id", null).use { c ->
            buildList { while (c.moveToNext()) add(PhraseCategory(c.getString(0), c.getInt(1))) }
        }

    private val phrases: List<CatalogPhrase> = db.rawQuery(
        "SELECT p.id, c.key, p.text FROM phrase p JOIN phrase_category c ON c.id = p.category_id " +
            "WHERE p.lang = 'en' ORDER BY c.sort, p.id",
        null,
    ).use { c ->
        buildList { while (c.moveToNext()) add(CatalogPhrase(c.getLong(0), c.getString(1), c.getString(2))) }
    }

    private val byId: Map<Long, CatalogPhrase> = phrases.associateBy { it.id }
    private val byCategory: Map<String, List<CatalogPhrase>> = phrases.groupBy { it.categoryKey }

    /** The catalogue categories in `sort` order. */
    fun categories(): List<PhraseCategory> = categories

    /** The English phrases of [categoryKey] in id order. An unknown key gives an empty list. */
    fun phrases(categoryKey: String): List<CatalogPhrase> = byCategory[categoryKey].orEmpty()

    /** The catalogue phrase [id], or null. */
    fun phrase(id: Long): CatalogPhrase? = byId[id]

    override fun close() = db.close()

    companion object {
        private const val PREFIX = "phrasebook-"
        private const val SUFFIX = ".db"

        /**
         * Opens version [version] of the phrasebook from [dir] (the app's `no_backup` folder), copying it from [asset]
         * first when the copy is missing. A failed copy or a broken file gives null and leaves no copy behind. Other
         * `phrasebook-*.db` versions are deleted once the copy opens. Call it off the main thread.
         */
        fun open(dir: File, version: Int, asset: () -> InputStream): PhrasebookReader? {
            val file = File(dir, "$PREFIX$version$SUFFIX")
            if (!file.isFile && !copy(dir, file, asset)) return null
            var db: SQLiteDatabase? = null
            return try {
                db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
                PhrasebookReader(db).also { deleteOthers(dir, file) }
            } catch (_: RuntimeException) {
                db?.close()
                file.delete()
                null
            }
        }

        private fun copy(dir: File, file: File, asset: () -> InputStream): Boolean {
            val part = File(dir, file.name + ".part")
            return try {
                dir.mkdirs()
                asset().use { input -> part.outputStream().use { input.copyTo(it) } }
                part.renameTo(file)
            } catch (_: IOException) {
                false
            } finally {
                part.delete()
            }
        }

        private fun deleteOthers(dir: File, keep: File) {
            dir.listFiles()?.forEach { f ->
                val base = f.name.removeSuffix("-journal").removeSuffix("-wal").removeSuffix("-shm")
                if (f.name.startsWith(PREFIX) && base.endsWith(SUFFIX) && base != keep.name) f.delete()
            }
        }
    }
}
