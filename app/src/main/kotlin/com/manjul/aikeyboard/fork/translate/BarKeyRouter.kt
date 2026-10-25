/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translate

import android.os.Build
import android.view.KeyEvent
import com.manjul.aikeyboard.ime.text.key.KeyCode

/**
 * Key routing while the Translate bar is open (TRD §4.1.3): text keys, space, backspace, delete-word, paste,
 * suggestion taps and glide words edit the bar buffer and never reach the editor; Enter is the bar's insert.
 * Keys that could change or delete host text without the bar (forward delete, cut, undo, redo, select) are
 * swallowed. Each function returns true when it consumed the input; the caller then does nothing else.
 * Everything else (shift, layout switches, the mic and Translate chips, arrows, hide) keeps working as usual.
 */
object BarKeyRouter {
    /** Keys that would edit or select host text behind the bar: consumed and ignored while the bar is open. */
    private val Swallowed = setOf(
        KeyCode.FORWARD_DELETE,
        KeyCode.FORWARD_DELETE_WORD,
        KeyCode.CLIPBOARD_CUT,
        KeyCode.CLIPBOARD_COPY,
        KeyCode.CLIPBOARD_SELECT,
        KeyCode.CLIPBOARD_SELECT_ALL,
        KeyCode.UNDO,
        KeyCode.REDO,
    )

    /**
     * A soft key release. [text] is the key's text for character and numeric keys (null for function keys).
     */
    fun onKey(bar: TranslateImeSession?, code: Int, text: String?): Boolean {
        if (bar == null || !bar.capturingInput) return false
        val buffer = bar.controller.buffer
        when (code) {
            KeyCode.DELETE -> buffer.backspace()
            KeyCode.DELETE_WORD -> buffer.deleteWord()
            KeyCode.ENTER -> bar.insert()
            KeyCode.SPACE, KeyCode.CJK_SPACE -> buffer.type(" ")
            in Swallowed -> Unit
            else -> if (!text.isNullOrEmpty()) buffer.type(text) else return false
        }
        return true
    }

    /** A suggestion tap or a glide word: appended as its own word. */
    fun onWord(bar: TranslateImeSession?, word: String): Boolean {
        if (bar == null || !bar.capturingInput) return false
        val buffer = bar.controller.buffer
        val current = buffer.text.value
        buffer.type(if (current.isEmpty() || current.last().isWhitespace()) word else " $word")
        return true
    }

    /** A paste (clipboard key, history item, clipboard suggestion). [text] is null for images and video: dropped. */
    fun onPaste(bar: TranslateImeSession?, text: String?): Boolean {
        if (bar == null || !bar.capturingInput) return false
        if (!text.isNullOrEmpty()) bar.controller.buffer.paste(text)
        return true
    }

    /** A hardware key press. Printable keys, space, backspace and Enter go to the bar; back closes it below API 33. */
    fun onHardwareKey(bar: TranslateImeSession?, keyCode: Int, event: KeyEvent?): Boolean {
        if (bar == null || !bar.capturingInput) return false
        val buffer = bar.controller.buffer
        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> buffer.backspace()
            KeyEvent.KEYCODE_FORWARD_DEL -> Unit
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> bar.insert()
            KeyEvent.KEYCODE_SPACE -> buffer.type(" ")
            // API 33+ closes the bar through the predictive back callback (SmartbarTranslateBar).
            KeyEvent.KEYCODE_BACK -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) bar.close() else return false
            else -> {
                val ch = event?.unicodeChar ?: 0
                if (ch <= 0 || event?.isCtrlPressed == true || event?.isAltPressed == true) return false
                buffer.type(String(Character.toChars(ch)))
            }
        }
        return true
    }
}
