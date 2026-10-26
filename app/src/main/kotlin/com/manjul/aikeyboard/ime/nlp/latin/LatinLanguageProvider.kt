/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.manjul.aikeyboard.ime.nlp.latin

import android.content.Context
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.featureSettingsStore // FORK(S3-suggest)
import com.manjul.aikeyboard.fork.suggest.LatinSuggester // FORK(S3-suggest)
import com.manjul.aikeyboard.keyboardManager // FORK(S3-suggest)
import com.manjul.aikeyboard.suggestContext // FORK(S3-suggest)
import com.manjul.aikeyboard.userWordRepository // FORK(S3-suggest)
import com.manjul.aikeyboard.ime.core.Subtype
import com.manjul.aikeyboard.ime.editor.EditorContent
import com.manjul.aikeyboard.ime.nlp.SpellingProvider
import com.manjul.aikeyboard.ime.nlp.SpellingResult
import com.manjul.aikeyboard.ime.nlp.SuggestionCandidate
import com.manjul.aikeyboard.ime.nlp.SuggestionProvider
// FORK(S3-suggest): removed the debug-log, JSON and lock imports of the upstream test dictionary

// FORK(S3-suggest): the provider delegates to the fork's LatinSuggester (dict/<lang>.sdict, user dictionary,
// opt-in autocorrect, PC-1 learning gate). The internal constructor lets tests inject one.
class LatinLanguageProvider internal constructor(
    context: Context,
    injected: LatinSuggester?,
) : SpellingProvider, SuggestionProvider {
    constructor(context: Context) : this(context, null) // FORK(S3-suggest)

    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"
    }

    // FORK(S3-suggest): resolved on first use so a test can build the provider on a plain Application
    private val appContext by lazy { context.appContext().value }

    // FORK(S3-suggest): replaces the upstream data.json word map; built on first use, never at construction
    private val suggester by lazy {
        injected ?: LatinSuggester(
            openAsset = { path -> appContext.assets.open(path) },
            learned = { appContext.userWordRepository().value },
            settings = { appContext.featureSettingsStore().value.current },
            context = appContext.suggestContext(),
            isPrivateSession = { appContext.keyboardManager().value.activeState.isIncognitoMode },
        )
    }

    private fun Subtype.dictLanguage() = LatinSuggester.languageOf(primaryLocale.base) // FORK(S3-suggest)

    override val providerId = ProviderId

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
    }

    override suspend fun preload(subtype: Subtype) {
        // FORK(S3-suggest): loads dict/<lang>.sdict for this subtype on Dispatchers.IO and the user dictionary
        // snapshot, after releasing the previous language. At most one engine is held.
        suggester.preload(subtype.dictLanguage())
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        // FORK(S3-suggest): dictionary and engine instead of the upstream "typo"/"gerror" sample
        return suggester.spell(subtype.dictLanguage(), word, maxSuggestionCount)
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        // FORK(S3-suggest): up to maxCandidateCount candidates for the composing word; the first may auto-commit
        // on space when AutocorrectPolicy allows it. Empty while the dictionary loads (never blocks typing).
        val composing = content.composingText
        if (composing.isEmpty()) return emptyList()
        val before = content.text.substring(0, content.localComposing.start.coerceIn(0, content.text.length))
        return suggester.suggest(
            language = subtype.dictLanguage(),
            typed = composing,
            previousWord = LatinSuggester.previousWord(before),
            max = maxCandidateCount,
            source = this,
        )
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // FORK(S3-suggest): learn only where the field and session allow it (PC-1); never log the candidate
        suggester.onAccepted(candidate)
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        suggester.onReverted(candidate) // FORK(S3-suggest): not learned, and not autocorrected again for a while
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        return suggester.onRemoved(candidate) // FORK(S3-suggest): blocked in the user dictionary
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        // FORK(S3-suggest): glide typing asks for the word list; load the subtype's dictionary if needed
        suggester.preload(subtype.dictLanguage())
        return suggester.words(subtype.dictLanguage())
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return suggester.frequency(subtype.dictLanguage(), word) // FORK(S3-suggest)
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if
        // the app process is killed (which will most likely always be the case).
    }
}
