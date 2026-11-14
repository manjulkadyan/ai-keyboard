/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Builds a version-1 database from the exported `schemas/…/1.json` (the exact SQL Room shipped), then opens it with
 * the current [AppDatabase]: Room runs the auto-migration and validates the result against the version-2 schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {
    private val app = RuntimeEnvironment.getApplication()
    private val schemaDir = File("schemas/${AppDatabase::class.java.name}")

    private fun createV1(file: File) = create(file, 1) { db ->
        db.execSQL(
            "INSERT INTO translation_history (source_text, target_text, source_lang, target_lang, engine, " +
                "origin, is_favorite, created_at) VALUES ('hello', 'hola', 'en', 'es', 'mlkit', 'text', 1, 42)",
        )
    }

    private fun createV2(file: File) = create(file, 2) { db ->
        db.execSQL(
            "INSERT INTO translation_history (source_text, target_text, source_lang, target_lang, engine, " +
                "origin, is_favorite, created_at) VALUES ('hello', 'hola', 'en', 'es', 'mlkit', 'text', 1, 42)",
        )
        db.execSQL("INSERT INTO user_word (locale, word, count, last_used_at, blocked) VALUES ('en', 'zork', 3, 9, 0)")
    }

    /** Creates [file] with the exact SQL of `schemas/.../<version>.json`, then runs [rows] on it. */
    private fun create(file: File, version: Int, rows: (SQLiteDatabase) -> Unit) {
        val json = File(schemaDir, "$version.json").readText()
        val schema = Json.parseToJsonElement(json).jsonObject["database"]!!.jsonObject
        assertEquals(version, schema["version"]!!.jsonPrimitive.content.toInt())
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            for (entity in schema["entities"]!!.jsonArray) {
                val e = entity.jsonObject
                val table = e["tableName"]!!.jsonPrimitive.content
                db.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                e["indices"]?.jsonArray?.forEach {
                    db.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            for (q in schema["setupQueries"]!!.jsonArray) db.execSQL(q.jsonPrimitive.content)
            rows(db)
            db.version = version
        }
    }

    @Test fun `v1 with a history row migrates to the current version and keeps the row`() = runBlocking {
        assertEquals(true, File(schemaDir, "2.json").isFile, "schema 2 must be exported")
        val file = app.getDatabasePath("migration_test.db").apply { parentFile?.mkdirs(); delete() }
        createV1(file)

        val db = Room.databaseBuilder(app, AppDatabase::class.java, file.absolutePath).allowMainThreadQueries().build()
        try {
            val rows = db.translationHistoryDao().observe(null, favoritesOnly = false, limit = 10).first()
            assertEquals(1, rows.size)
            assertEquals("hello", rows[0].sourceText)
            assertEquals("hola", rows[0].targetText)
            assertEquals(true, rows[0].isFavorite)
            assertEquals(42L, rows[0].createdAt)
            assertEquals(AppDatabase.VERSION, db.openHelper.readableDatabase.version)

            // The new tables exist and work.
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val repo = UserWordRepository(db.userWordDao(), clock = { 1L }, scope = scope)
            repo.recordAccepted("en", "hello", prev = "say")
            assertEquals(1, repo.count())
            scope.cancel()
        } finally {
            db.close()
            file.delete()
        }
    }

    @Test fun `v2 with a history row and a user word migrates to v3, keeps both and adds the content tables`() =
        runBlocking {
            assertEquals(true, File(schemaDir, "3.json").isFile, "schema 3 must be exported")
            val file = app.getDatabasePath("migration_test_v2.db").apply { parentFile?.mkdirs(); delete() }
            createV2(file)

            val db = Room.databaseBuilder(app, AppDatabase::class.java, file.absolutePath)
                .allowMainThreadQueries()
                .build()
            try {
                val rows = db.translationHistoryDao().observe(null, favoritesOnly = false, limit = 10).first()
                assertEquals(listOf("hello"), rows.map { it.sourceText })
                val words = db.userWordDao().words("en")
                assertEquals(listOf("zork"), words.map { it.word })
                assertEquals(3, words[0].count)
                val sql = db.openHelper.readableDatabase
                assertEquals(3, sql.version)

                val tables = sql.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(0)) }
                }
                val content = setOf("dictionary_entry", "sticker_pack", "sticker", "user_phrase", "phrase_favourite")
                assertEquals(content, content.intersect(tables))
                val dictColumns = sql.query("PRAGMA table_info(dictionary_entry)").use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name"))) }
                }
                assertEquals(true, "synonyms" in dictColumns, "plan F3: dictionary_entry.synonyms")

                // The new tables work after the migration.
                db.phraseDao().toggleFavourite("c:1", now = 1)
                assertEquals(true, db.phraseDao().isFavourite("c:1"))
            } finally {
                db.close()
                file.delete()
            }
        }
}
