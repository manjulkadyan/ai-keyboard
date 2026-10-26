/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.suggest

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.room.Room
import com.manjul.aikeyboard.core.database.AppDatabase
import com.manjul.aikeyboard.core.database.UserWordRepository
import com.manjul.aikeyboard.core.datastore.FeatureSettings
import com.manjul.aikeyboard.core.suggest.DictionaryFormat
import com.manjul.aikeyboard.ime.core.Subtype
import com.manjul.aikeyboard.ime.editor.EditorContent
import com.manjul.aikeyboard.ime.editor.EditorRange
import com.manjul.aikeyboard.ime.nlp.latin.LatinLanguageProvider
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.lib.FlorisLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LatinLanguageProviderTest {
    private val app = RuntimeEnvironment.getApplication()
    private val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repo = UserWordRepository(db.userWordDao(), scope = repoScope)
    private var settings = FeatureSettings()
    private var privateSession = false
    private val context = SuggestContext().apply { update(textField(), PolicyDecision.Unrestricted) }
    private val suggester = LatinSuggester(
        openAsset = { path -> app.assets.open(path) },
        learned = { repo },
        settings = { settings },
        context = context,
        isPrivateSession = { privateSession },
    )
    private val provider = LatinLanguageProvider(app, suggester)

    @After fun tearDown() {
        repoScope.cancel()
        db.close()
    }

    private fun textField(type: Int = InputType.TYPE_CLASS_TEXT) = EditorInfo().apply { inputType = type }

    private fun subtype(lang: String) = Subtype.DEFAULT.copy(primaryLocale = FlorisLocale.from(lang))

    /** The editor holds [before] then the composing word [word] with the cursor after it. */
    private fun composing(word: String, before: String = ""): EditorContent {
        val text = before + word
        val range = EditorRange(before.length, text.length)
        return EditorContent(text, 0, EditorRange.cursor(text.length), range, range)
    }

    private fun suggest(lang: String, word: String, before: String = "") = runBlocking {
        provider.suggest(subtype(lang), composing(word, before), 3, false, privateSession)
    }

    @Test fun `every MVP subtype completes 3-letter prefixes of its top words`() = runBlocking {
        for (lang in FeatureSettings.MVP_LANGUAGES) {
            provider.preload(subtype(lang))
            val dict = app.assets.open("dict/$lang.sdict").use { DictionaryFormat.read(it.buffered()) }
            val top = dict.wordList().sortedByDescending { dict.frequency(it) }.filter { it.length >= 4 }.take(100)
            assertTrue(top.size >= 50, lang)
            for (word in top) {
                val prefix = word.substring(0, 3)
                val candidates = provider.suggest(subtype(lang), composing(prefix), 3, false, false)
                assertTrue(candidates.isNotEmpty(), "$lang: no candidates for a 3-letter prefix of word #${top.indexOf(word)}")
                assertTrue(candidates.size <= 3, lang)
            }
        }
    }

    @Test fun `teh autocorrects to the in a text field`() {
        runBlocking { provider.preload(subtype("en")) }
        val first = suggest("en", "teh").first()
        assertEquals("the", first.text.toString())
        assertTrue(first.isEligibleForAutoCommit)
        assertTrue(suggest("en", "teh").drop(1).none { it.isEligibleForAutoCommit })
    }

    @Test fun `url field is never autocorrected`() {
        runBlocking { provider.preload(subtype("en")) }
        context.update(textField(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI), PolicyDecision.Unrestricted)
        val candidates = suggest("en", "teh")
        assertEquals("the", candidates.first().text.toString())
        assertTrue(candidates.none { it.isEligibleForAutoCommit })
    }

    @Test fun `hindi is not autocorrected by default`() {
        runBlocking { provider.preload(subtype("hi")) }
        val dict = app.assets.open("dict/hi.sdict").use { DictionaryFormat.read(it.buffered()) }
        val word = dict.wordList().sortedByDescending { dict.frequency(it) }.first { it.length >= 4 }
        // A transposition of the last two characters: a typo the engine corrects.
        val typo = word.dropLast(2) + word.last() + word[word.length - 2]
        assertFalse("hi" in FeatureSettings().autocorrectLocales)
        assertTrue(suggest("hi", typo).none { it.isEligibleForAutoCommit })
        assertTrue(suggest("hi", word.take(3)).none { it.isEligibleForAutoCommit })
    }

    @Test fun `a locale enabled in settings autocorrects`() {
        settings = settings.copy(autocorrectLocales = setOf("es"))
        runBlocking { provider.preload(subtype("en")) }
        assertTrue(suggest("en", "teh").none { it.isEligibleForAutoCommit })
    }

    @Test fun `a reverted word is not autocorrected again`() = runBlocking {
        provider.preload(subtype("en"))
        val first = suggest("en", "teh").first()
        provider.notifySuggestionReverted(subtype("en"), first)
        assertTrue(suggest("en", "teh").none { it.isEligibleForAutoCommit })
    }

    @Test fun `suggestions are hidden when the policy hides the strip or the user turned them off`() = runBlocking {
        provider.preload(subtype("en"))
        context.update(textField(), PolicyDecision.Unrestricted.copy(allowSuggestionStrip = false))
        assertTrue(suggest("en", "hel").isEmpty())
        context.update(textField(), PolicyDecision.Unrestricted)
        settings = settings.copy(suggestionsEnabled = false)
        assertTrue(suggest("en", "hel").isEmpty())
    }

    @Test fun `private session accepts learn nothing`() = runBlocking {
        provider.preload(subtype("en"))
        privateSession = true
        val candidate = suggest("en", "hel").first()
        provider.notifySuggestionAccepted(subtype("en"), candidate)
        provider.notifySuggestionAccepted(subtype("en"), suggest("en", "wor").first())
        assertEquals(0, repo.count())
    }

    @Test fun `a field that forbids learning learns nothing`() = runBlocking {
        provider.preload(subtype("en"))
        context.update(textField(), PolicyDecision.Unrestricted.copy(allowLearning = false))
        provider.notifySuggestionAccepted(subtype("en"), suggest("en", "hel").first())
        provider.notifySuggestionAccepted(subtype("en"), suggest("en", "wor").first())
        assertEquals(0, repo.count())
    }

    @Test fun `an accepted suggestion is learned when learning is allowed`() = runBlocking {
        provider.preload(subtype("en"))
        provider.notifySuggestionAccepted(subtype("en"), suggest("en", "hel").first { !it.isEligibleForAutoCommit })
        assertEquals(1, repo.count())
    }

    @Test fun `an autocorrection is learned only once it is not reverted`() = runBlocking {
        provider.preload(subtype("en"))
        val auto = suggest("en", "teh").first()
        assertTrue(auto.isEligibleForAutoCommit)
        provider.notifySuggestionAccepted(subtype("en"), auto)
        provider.notifySuggestionReverted(subtype("en"), auto)
        provider.notifySuggestionAccepted(subtype("en"), suggest("en", "hel").first { !it.isEligibleForAutoCommit })
        assertEquals(1, repo.count())
    }

    @Test fun `a removed suggestion is never suggested again`() = runBlocking {
        provider.preload(subtype("en"))
        val candidate = suggest("en", "hel").first()
        assertTrue(provider.removeSuggestion(subtype("en"), candidate))
        assertTrue(suggest("en", "hel").none { it.text.toString().equals(candidate.text.toString(), ignoreCase = true) })
    }

    @Test fun `a removed dictionary word is not autocorrected to a neighbour`() = runBlocking {
        provider.preload(subtype("en"))
        repo.block("en", "hell")
        val candidates = suggest("en", "hell")
        assertTrue(candidates.none { it.text.toString() == "hell" })
        assertTrue(candidates.none { it.isEligibleForAutoCommit }, "a real word is never a typo")
    }

    @Test fun `an email field ranks without the learned words`() = runBlocking {
        provider.preload(subtype("en"))
        val plain = suggest("en", "hel").map { it.text.toString() }
        val wide = provider.suggest(subtype("en"), composing("hel"), 12, false, false).map { it.text.toString() }
        val rare = wide.last { it !in plain }
        repeat(20) { repo.recordAccepted("en", rare, prev = null) }
        assertTrue(rare in suggest("en", "hel").map { it.text.toString() }, "learned words rank higher in a text field")

        val email = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        context.update(
            textField(email),
            PolicyDecision.Unrestricted.copy(allowLearning = false, allowSuggestionHistory = false),
        )
        val inEmail = suggest("en", "hel").map { it.text.toString() }
        assertTrue(inEmail.isNotEmpty(), "the strip still shows dictionary words")
        assertFalse(rare in inEmail, "no personalised ranking where the policy forbids suggestion history")
    }

    @Test fun `spell does not reveal learned words`() = runBlocking {
        provider.preload(subtype("en"))
        repo.recordAccepted("en", "zqxlearned", prev = null)
        val result = provider.spell(subtype("en"), "zqxlearned", emptyList(), emptyList(), 3, false, false)
        assertTrue(result.isTypo, "the system spell checker answers from the dictionary only")
    }

    @Test fun `preloading es after en releases en`() = runBlocking {
        provider.preload(subtype("en"))
        assertEquals("en", suggester.loadedLanguage)
        provider.preload(subtype("es"))
        assertEquals("es", suggester.loadedLanguage)
        assertTrue(suggest("en", "teh").isEmpty())
        assertTrue(suggest("es", "hol").isNotEmpty())
    }

    @Test fun `a language without a dictionary holds none`() = runBlocking {
        provider.preload(subtype("en"))
        provider.preload(subtype("de"))
        assertNull(suggester.loadedLanguage)
        assertTrue(suggest("de", "hal").isEmpty())
    }

    @Test fun `spell checks against the dictionary`() = runBlocking {
        provider.preload(subtype("en"))
        val valid = provider.spell(subtype("en"), "hello", emptyList(), emptyList(), 3, false, false)
        assertFalse(valid.isTypo)
        val typo = provider.spell(subtype("en"), "helllo", emptyList(), emptyList(), 3, false, false)
        assertTrue(typo.isTypo)
        assertTrue(typo.suggestions().contains("hello"))
    }

    @Test fun `word list and frequency come from the dictionary`() = runBlocking {
        provider.preload(subtype("en"))
        assertTrue(provider.getListOfWords(subtype("en")).size >= 20_000)
        assertTrue(provider.getFrequencyForWord(subtype("en"), "the") > provider.getFrequencyForWord(subtype("en"), "zymurgy"))
    }
}
