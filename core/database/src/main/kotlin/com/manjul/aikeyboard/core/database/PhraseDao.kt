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
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** User phrases and phrase favourites. */
@Dao
abstract class PhraseDao {
    @Insert
    abstract suspend fun insert(phrase: UserPhraseEntity): Long

    @Update
    abstract suspend fun update(phrase: UserPhraseEntity)

    @Query("SELECT * FROM user_phrase WHERE id = :id")
    abstract suspend fun phrase(id: Long): UserPhraseEntity?

    @Query("SELECT * FROM user_phrase ORDER BY updated_at DESC, id DESC")
    abstract fun observePhrases(): Flow<List<UserPhraseEntity>>

    @Query("SELECT * FROM phrase_favourite ORDER BY created_at DESC")
    abstract fun observeFavourites(): Flow<List<PhraseFavouriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM phrase_favourite WHERE phrase_ref = :ref)")
    abstract suspend fun isFavourite(ref: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertFavourite(favourite: PhraseFavouriteEntity)

    @Query("DELETE FROM phrase_favourite WHERE phrase_ref = :ref")
    protected abstract suspend fun deleteFavourite(ref: String)

    @Query("DELETE FROM user_phrase WHERE id = :id")
    protected abstract suspend fun deletePhrase(id: Long)

    @Query("DELETE FROM user_phrase")
    protected abstract suspend fun deleteAllPhrases()

    @Query("DELETE FROM phrase_favourite")
    protected abstract suspend fun deleteAllFavourites()

    /** Flips the favourite state of [ref]; returns true when it is now a favourite. */
    @Transaction
    open suspend fun toggleFavourite(ref: String, now: Long): Boolean {
        if (isFavourite(ref)) {
            deleteFavourite(ref)
            return false
        }
        insertFavourite(PhraseFavouriteEntity(ref, now))
        return true
    }

    /** Deletes the user phrase [id] and its favourite. */
    @Transaction
    open suspend fun delete(id: Long) {
        deletePhrase(id)
        deleteFavourite(UserPhraseEntity.ref(id))
    }

    /** Deletes every user phrase and every favourite, catalogue ones included. */
    @Transaction
    open suspend fun clearAll() {
        deleteAllFavourites()
        deleteAllPhrases()
    }
}
