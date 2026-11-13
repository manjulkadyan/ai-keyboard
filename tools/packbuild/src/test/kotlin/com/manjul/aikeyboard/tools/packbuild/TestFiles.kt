/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** Shared helpers for the packbuild tests. */
internal object TestFiles {
    val sourcesDir: File get() = File(System.getProperty("packbuild.sourcesDir"))
    val mainAssetsDir: File get() = File(System.getProperty("packbuild.mainAssetsDir"))
    val contentDir: File get() = File(System.getProperty("packbuild.contentDir"))

    /** The test-only fixture key (plan decision 12). */
    val fixtureKeyPem: String
        get() = requireNotNull(TestFiles::class.java.getResource("/fixture-signing-key.pem")).readText()

    fun <T> query(db: File, block: (Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use(block)

    fun rows(db: File, sql: String): List<List<String?>> = query(db) { c ->
        c.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                val n = rs.metaData.columnCount
                buildList { while (rs.next()) add((1..n).map { rs.getString(it) }) }
            }
        }
    }
}
