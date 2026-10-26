/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * The user dictionary tables. Counts are bumped with insert-or-ignore plus update (SQLite `UPSERT` needs 3.24,
 * newer than minSdk 26 ships).
 */
@Dao
abstract class UserWordDao {
    @Query("SELECT * FROM user_word WHERE locale = :locale")
    abstract suspend fun words(locale: String): List<UserWordEntity>

    @Query("SELECT * FROM user_bigram WHERE locale = :locale")
    abstract suspend fun bigrams(locale: String): List<UserBigramEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertWordIfAbsent(entity: UserWordEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertBigramIfAbsent(entity: UserBigramEntity)

    @Query("UPDATE user_word SET count = count + 1, last_used_at = :now WHERE locale = :locale AND word = :word")
    protected abstract suspend fun bumpWord(locale: String, word: String, now: Long)

    @Query("UPDATE user_bigram SET count = count + 1 WHERE locale = :locale AND prev = :prev AND word = :word")
    protected abstract suspend fun bumpBigram(locale: String, prev: String, word: String)

    @Query("UPDATE user_word SET blocked = 1 WHERE locale = :locale AND word = :word")
    protected abstract suspend fun setBlocked(locale: String, word: String)

    @Query("SELECT COUNT(*) FROM user_word WHERE locale = :locale")
    protected abstract suspend fun wordCount(locale: String): Int

    /** The [n] least recently used words of [locale] (ties by word, so the order is stable). */
    @Query("SELECT word FROM user_word WHERE locale = :locale ORDER BY last_used_at ASC, word ASC LIMIT :n")
    protected abstract suspend fun oldestWords(locale: String, n: Int): List<String>

    @Query("DELETE FROM user_word WHERE locale = :locale AND word IN (:words)")
    protected abstract suspend fun deleteWords(locale: String, words: List<String>)

    @Query("DELETE FROM user_bigram WHERE locale = :locale AND word IN (:words)")
    protected abstract suspend fun deleteBigramsTo(locale: String, words: List<String>)

    @Query("DELETE FROM user_word")
    protected abstract suspend fun deleteAllWords()

    @Query("DELETE FROM user_bigram")
    protected abstract suspend fun deleteAllBigrams()

    /** Learned words over all languages, blocked ones included. */
    @Query("SELECT COUNT(*) FROM user_word")
    abstract suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM user_bigram")
    abstract suspend fun bigramCount(): Int

    /**
     * Counts one use of [word] (and of [prev] -> [word] when [prev] is set) at [now], then trims [locale] to
     * [maxWords] by evicting the least recently used words and the bigrams leading to them. Returns the evicted words.
     */
    @Transaction
    open suspend fun recordUse(locale: String, word: String, prev: String?, now: Long, maxWords: Int): List<String> {
        insertWordIfAbsent(UserWordEntity(locale, word, count = 0, lastUsedAt = now, blocked = false))
        bumpWord(locale, word, now)
        if (prev != null) {
            insertBigramIfAbsent(UserBigramEntity(locale, prev, word, count = 0))
            bumpBigram(locale, prev, word)
        }
        val excess = wordCount(locale) - maxWords
        if (excess <= 0) return emptyList()
        val evicted = oldestWords(locale, excess)
        // SQLite caps bound parameters (999 before 3.32), so delete in chunks.
        for (chunk in evicted.chunked(500)) {
            deleteBigramsTo(locale, chunk)
            deleteWords(locale, chunk)
        }
        return evicted
    }

    /** Marks [word] blocked, adding it with no uses when it was never learned. */
    @Transaction
    open suspend fun block(locale: String, word: String, now: Long) {
        insertWordIfAbsent(UserWordEntity(locale, word, count = 0, lastUsedAt = now, blocked = true))
        setBlocked(locale, word)
    }

    @Transaction
    open suspend fun clearAll() {
        deleteAllBigrams()
        deleteAllWords()
    }
}
