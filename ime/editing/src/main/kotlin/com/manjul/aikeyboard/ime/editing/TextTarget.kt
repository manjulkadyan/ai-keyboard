/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.editing

/**
 * The host text field, seen by voice typing, the translate bar and AI rewrite. The text is user content: implementations never
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

    // Range access for AI rewrite and Polish (TRD §4.1.3, see [SafeReplacer]). Offsets are field offsets; a range is
    // `start until end`. The defaults refuse, so targets without field access (the translate bar) never take part.

    /** The selection (`start until end`, empty when only a cursor), or null when the field cannot be read. */
    fun selection(): IntRange? = null

    /** The text in [range], or null when the field cannot be read or [range] is not inside it. */
    fun textInRange(range: IntRange): String? = null

    /**
     * The field text, or null when it cannot be read. A field of at most [maxChars] characters comes back whole, at
     * offset 0. A longer one may come back as a window that holds the selection and at least the [maxChars]
     * characters before it, and is then longer than [maxChars] or starts after 0, so a snapshot at offset 0 with at
     * most [maxChars] characters is always the whole field.
     */
    fun fieldText(maxChars: Int): FieldSnapshot? = null

    /**
     * Replaces [range] with [text] in one batch edit (select the range, commit), the cursor at the end of [text].
     * Any composing region is finished first, never replaced. With [expected], the range is re-read inside the batch
     * and nothing is written unless it still equals [expected]. Returns whether it wrote.
     */
    fun replaceRange(range: IntRange, text: String, expected: String? = null): Boolean = false
}

/**
 * Part of the field as [TextTarget.fieldText] read it: [text] starts at field offset [startOffset]; [selection] is in
 * field offsets too. [toString] holds no text.
 */
data class FieldSnapshot(val text: String, val startOffset: Int, val selection: IntRange?) {
    override fun toString(): String = "FieldSnapshot(chars=${text.length}, startOffset=$startOffset, selection=$selection)"
}
