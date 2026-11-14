/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.translit

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The long-press card's "Save mapping" tap. The card checked learning when it opened; this checks again at tap time
 * ([TranslitSuggestionProvider.canSaveMapping]: field allows learning, not incognito), then writes off the main thread.
 * A database failure is swallowed: the keyboard must not crash, and no exception text (it could carry user content) is
 * kept. The Latin chip is never saved.
 */
class SaveMappingHandler(
    private val repository: () -> CustomMappingsRepository,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) : (TranslitCandidate) -> Unit {
    override fun invoke(candidate: TranslitCandidate) {
        if (candidate.latin) return
        val provider = candidate.sourceProvider as? TranslitSuggestionProvider
        if (provider?.canSaveMapping() != true) return
        scope.launch(io) {
            try {
                repository().save(candidate.lang, candidate.typed, candidate.text.toString())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Invalid word or a failed write: nothing saved, nothing logged.
            }
        }
    }
}
