/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.phrasebook

import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import com.manjul.aikeyboard.core.content.phrasebook.Phrase
import com.manjul.aikeyboard.core.content.phrasebook.PhraseCategory
import com.manjul.aikeyboard.core.content.phrasebook.PhrasebookRepository
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the Phrasebook screen shows. [category] is null on the category list. */
data class PhrasebookUiState(
    val target: String,
    val category: String? = null,
    val categories: List<PhraseCategory> = emptyList(),
    val phrases: List<Phrase> = emptyList(),
    val packMissing: Boolean = false,
    val loading: Boolean = false,
) {
    // Phrase (and so user phrase) text never reaches a log line.
    override fun toString(): String =
        "PhrasebookUiState(target=$target, category=$category, phrases=${phrases.size}, packMissing=$packMissing)"
}

/**
 * The Phrasebook screen (S-17, PRD FR-22). The target language and the open category live in [saved]. The target is
 * the Translator pair target, else the first MVP language that is not English. A change of the target or of the
 * target pack's presence ([packReady]) translates the rows again. It never logs phrase text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhrasebookViewModel(
    private val saved: SavedStateHandle,
    categories: suspend () -> List<PhraseCategory>,
    private val phrases: (category: String, target: String) -> Flow<List<Phrase>>,
    private val packReady: (target: String) -> Flow<Boolean>,
    settings: FeatureSettings,
    scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(
        PhrasebookUiState(target = saved[KEY_TARGET] ?: defaultTarget(settings), category = saved[KEY_CATEGORY]),
    )
    val state: StateFlow<PhrasebookUiState> = _state.asStateFlow()

    init {
        scope.launch {
            val all = categories()
            _state.update { it.copy(categories = all) }
        }
        scope.launch {
            _state.map { it.category to it.target }
                .distinctUntilChanged()
                .flatMapLatest { (category, target) ->
                    if (category == null) return@flatMapLatest emptyFlow()
                    _state.update { it.copy(loading = true) }
                    packReady(target).distinctUntilChanged().flatMapLatest { ready ->
                        phrases(category, target).map { rows -> Triple(category, rows, !ready) }
                    }
                }
                .collect { (category, rows, missing) ->
                    _state.update {
                        if (it.category != category) return@update it
                        it.copy(phrases = rows, packMissing = missing, loading = false)
                    }
                }
        }
    }

    fun openCategory(key: String) {
        saved[KEY_CATEGORY] = key
        _state.update { it.copy(category = key, phrases = emptyList(), packMissing = false) }
    }

    /** Back to the category list. Returns false when the list is already shown. */
    fun closeCategory(): Boolean {
        if (_state.value.category == null) return false
        saved.remove<String>(KEY_CATEGORY)
        _state.update { it.copy(category = null, phrases = emptyList(), packMissing = false) }
        return true
    }

    fun setTarget(tag: String) {
        if (tag !in TARGETS) return
        saved[KEY_TARGET] = tag
        _state.update { it.copy(target = tag) }
    }

    /** "Send via keyboard" (decision 18): the share sheet with the shown text. */
    fun shareIntent(phrase: Phrase): Intent {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shownText(phrase))
        return Intent.createChooser(send, null)
    }

    companion object {
        const val KEY_TARGET = "phrasebook_target"
        const val KEY_CATEGORY = "phrasebook_category"

        /** The languages a phrase can be shown in: the MVP languages except the English source. */
        val TARGETS: List<String> = FeatureSettings.MVP_LANGUAGES.filter { it != PhrasebookRepository.SOURCE }

        fun defaultTarget(settings: FeatureSettings): String =
            settings.translatePair?.target?.takeIf { it in TARGETS } ?: TARGETS.first()

        /** The text a row shows, speaks, copies and shares: the translation, else the English text. */
        fun shownText(phrase: Phrase): String = phrase.translation ?: phrase.text
    }
}

