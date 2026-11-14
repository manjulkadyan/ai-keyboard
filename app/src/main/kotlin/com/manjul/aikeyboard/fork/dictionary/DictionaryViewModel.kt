/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.dictionary

import androidx.lifecycle.SavedStateHandle
import com.manjul.aikeyboard.core.content.dictionary.LookupResult
import com.manjul.aikeyboard.fork.routes.AppIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Dictionary screen shows. [result] is null before the first lookup of [submitted]. */
data class DictionaryUiState(
    val query: String = "",
    val submitted: String = "",
    val loading: Boolean = false,
    val result: LookupResult? = null,
    val online: Boolean = false,
) {
    // The query is user content (F17).
    override fun toString(): String =
        "DictionaryUiState(queryChars=${query.length}, loading=$loading, result=$result, online=$online)"
}

/**
 * The Dictionary screen (S-16, PRD FR-21). The query and the last submitted word live in [saved], so they survive a
 * process death; a restore runs the lookup again. A connectivity change runs the submitted word again and never
 * clears the query or the shown result (FR-21 AC2). [lookup] gets `allowNetwork` = the current connectivity; the
 * repository adds the `onlineDictionary` and AI consent gates. It never logs the word.
 */
class DictionaryViewModel(
    private val saved: SavedStateHandle,
    private val lookup: suspend (word: String, allowNetwork: Boolean) -> LookupResult,
    connectivity: Flow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(
        DictionaryUiState(query = saved[KEY_QUERY] ?: "", submitted = saved[KEY_SUBMITTED] ?: ""),
    )
    val state: StateFlow<DictionaryUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var seenConnectivity = false

    init {
        scope.launch {
            connectivity.collect { online ->
                _state.update { it.copy(online = online) }
                val first = !seenConnectivity
                seenConnectivity = true
                // The first value starts a restored lookup; every later change runs the word again.
                if (first) {
                    if (_state.value.submitted.isNotEmpty()) run(_state.value.submitted)
                } else {
                    rerun()
                }
            }
        }
    }

    fun onQueryChange(text: String) {
        val query = text.take(AppIntents.MAX_DICTIONARY_WORD_CHARS)
        saved[KEY_QUERY] = query
        _state.update { it.copy(query = query) }
    }

    /** Looks the query up. A blank query does nothing. */
    fun submit() {
        val word = _state.value.query.trim()
        if (word.isEmpty()) return
        saved[KEY_SUBMITTED] = word
        _state.update { it.copy(submitted = word) }
        run(word)
    }

    /** A word from the keyboard card's "More" or the "Define" sheet. */
    fun open(word: String) {
        onQueryChange(word)
        submit()
    }

    private fun rerun() {
        val word = _state.value.submitted
        if (word.isNotEmpty()) run(word)
    }

    private fun run(word: String) {
        job?.cancel()
        // The shown result stays until the new one arrives.
        _state.update { it.copy(loading = true) }
        job = scope.launch {
            val result = lookup(word, _state.value.online)
            _state.update { if (it.submitted == word) it.copy(loading = false, result = result) else it }
        }
    }

    companion object {
        const val KEY_QUERY = "dictionary_query"
        const val KEY_SUBMITTED = "dictionary_submitted"
    }
}
