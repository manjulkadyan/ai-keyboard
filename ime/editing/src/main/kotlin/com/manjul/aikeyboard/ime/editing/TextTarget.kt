/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

/**
 * The host text field, seen by voice typing and the translate bar. The text is user content: implementations never
 * log it.
 */
interface TextTarget {
    /**
     * Shows [text] as the composing region: replaces our own region, or starts a new one at the cursor
     * (a region someone else set is finished first, never replaced). False if the field has no composing support.
     */
    fun setComposing(text: String): Boolean

    /**
     * Replaces our composing region (if any) with [text] and finishes it. With [removeSpaceBefore], a single space
     * directly before the insertion point is removed first (the trailing space of our previous commit).
     */
    fun commit(text: String, removeSpaceBefore: Boolean = false): Boolean

    /** Finishes our composing region: the text stays as it is. Never deletes. */
    fun finishComposing(): Boolean

    /**
     * Removes our composing region, but only while it is ours and the text right before the cursor is still
     * [expected]; otherwise finishes it like [finishComposing]. Returns whether it removed anything.
     */
    fun clearComposing(expected: String): Boolean

    /**
     * Removes [expected] right before the cursor, but only if the text there still equals it and nothing is selected;
     * otherwise does nothing. Only for undoing our own commit (see [OwnWriteUndo]). Returns whether it removed it.
     */
    fun deleteBeforeCursorIfEquals(expected: String): Boolean
}
