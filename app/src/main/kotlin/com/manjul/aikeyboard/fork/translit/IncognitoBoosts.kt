/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import android.content.Context
import com.manjul.aikeyboard.translitGraph

/**
 * Decision 11: when incognito turns on, the in-memory boosts made before it are forgotten at once (the provider's
 * lazy clear in `suggest` stays as a backstop). Does not build the graph if nothing used it yet.
 */
object IncognitoBoosts {
    /** Calls [clear] only on the switch from off to on. */
    fun onChange(wasIncognito: Boolean, isIncognito: Boolean, clear: () -> Unit) {
        if (isIncognito && !wasIncognito) clear()
    }

    fun onChange(context: Context, wasIncognito: Boolean, isIncognito: Boolean) =
        onChange(wasIncognito, isIncognito) {
            val graph = context.translitGraph()
            if (graph.isInitialized()) graph.value.clearBoosts()
        }
}
