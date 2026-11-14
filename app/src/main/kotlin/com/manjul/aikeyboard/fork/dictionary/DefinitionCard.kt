/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.dictionary

import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.content.dictionary.Definition
import com.manjul.aikeyboard.core.content.dictionary.LookupResult
import com.manjul.aikeyboard.dictionary
import com.manjul.aikeyboard.fork.routes.AppIntents
import com.manjul.aikeyboard.fork.routes.RouteRegistry
import com.manjul.aikeyboard.fork.suggest.LatinWordCandidate
import com.manjul.aikeyboard.fork.translit.TranslitCandidate
import com.manjul.aikeyboard.fork.translit.TranslitSuggestionProvider
import com.manjul.aikeyboard.fork.voice.BackDismisses
import com.manjul.aikeyboard.ime.nlp.ClipboardSuggestionCandidate
import com.manjul.aikeyboard.ime.nlp.EmojiSuggestionCandidate
import com.manjul.aikeyboard.ime.nlp.SuggestionCandidate
import com.manjul.aikeyboard.ime.theme.FlorisImeUi
import com.manjul.aikeyboard.userWordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox

/** What the keyboard's definition card shows. [result] is null while the pack is read. */
data class DefinitionCardUi(
    val word: String,
    /** A learned word: the card offers "Remove suggestion" (plan decision 21). */
    val learned: Boolean,
    val result: LookupResult? = null,
    /** A native transliteration candidate the card may save as a mapping (S6), else null. */
    val mapping: TranslitCandidate? = null,
    val mappingSaved: Boolean = false,
    /** The last "Save mapping" was blocked or failed: the card says "Not saved" and offers the action again. */
    val mappingFailed: Boolean = false,
) {
    val first: Definition? get() = result?.definitions?.firstOrNull()

    // The word is user content (F17).
    override fun toString(): String = "DefinitionCardUi(learned=$learned, result=$result)"
}

/**
 * The keyboard's definition card (S5-dict, plan decisions 6 and 21). A long-press on a word suggestion opens it. It
 * reads the offline pack only ([com.manjul.aikeyboard.core.content.dictionary.DictionaryRepository.lookupOffline]):
 * a typed word never reaches the network from the keyboard. "More" opens the app's Dictionary screen with the word
 * in [RouteRegistry.EXTRA_DICTIONARY_WORD]. No ads or billing code here (plan F8).
 */
object DefinitionCards {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<DefinitionCardUi?>(null)
    val state: StateFlow<DefinitionCardUi?> = _state.asStateFlow()

    private var job: Job? = null
    private var remove: (() -> Boolean)? = null
    @Volatile private var saving = false

    /** Saves a mapping for a native transliteration candidate. The second argument reports whether it was written. */
    @Volatile var saveMappingHandler: ((TranslitCandidate, (Boolean) -> Unit) -> Unit)? = null

    /**
     * Opens the card for [word]. [remove] is upstream's suggestion removal, offered only when [learned]. [lookup] is
     * the offline lookup.
     */
    fun show(
        word: String,
        learned: Boolean,
        lookup: suspend (String) -> LookupResult,
        remove: () -> Boolean,
        mapping: TranslitCandidate? = null,
    ) {
        job?.cancel()
        saving = false
        this.remove = if (learned) remove else null
        _state.value = DefinitionCardUi(word = word, learned = learned, mapping = mapping)
        job = scope.launch {
            val result = lookup(word)
            _state.update { if (it?.word == word) it.copy(result = result) else it }
        }
    }

    fun dismiss() {
        job?.cancel()
        job = null
        remove = null
        _state.value = null
    }

    /** Upstream's removal, then the card closes. */
    fun removeSuggestion() {
        val action = remove
        dismiss()
        action?.invoke()
    }

    /** The candidate "Save mapping" applies to: native only, and only where learning is allowed. */
    internal fun mappingFor(candidate: SuggestionCandidate): TranslitCandidate? =
        (candidate as? TranslitCandidate)?.takeIf {
            !it.latin && (it.sourceProvider as? TranslitSuggestionProvider)?.canSaveMapping() == true
        }

    /** "Save mapping": hands the candidate to [saveMappingHandler], once per card. */
    fun saveMapping() {
        val card = _state.value ?: return
        val candidate = card.mapping ?: return
        val handler = saveMappingHandler ?: return
        if (card.mappingSaved || saving) return
        saving = true
        handler(candidate) { ok ->
            saving = false
            _state.update { if (it?.mapping === candidate) it.copy(mappingSaved = ok, mappingFailed = !ok) else it }
        }
    }

    /**
     * The suggestion strip's long-press. A word suggestion opens the card and returns true. Clipboard and emoji
     * suggestions return null, so the caller keeps upstream's behaviour.
     */
    fun onLongPress(context: Context, candidate: SuggestionCandidate, remove: () -> Boolean): Boolean? {
        if (candidate is ClipboardSuggestionCandidate || candidate is EmojiSuggestionCandidate) return null
        val word = candidate.text.toString().trim()
        if (word.isEmpty()) return null
        val learned = candidate.isEligibleForUserRemoval && candidate is LatinWordCandidate &&
            runCatching { context.userWordRepository().value.boost(candidate.language, word.lowercase()) > 0 }
                .getOrDefault(false)
        val app = context.applicationContext
        // "Save mapping" shows only for a native candidate, and only where learning is allowed.
        show(
            word,
            learned,
            lookup = { app.dictionary().value.repository.lookupOffline(it) },
            remove = remove,
            mapping = mappingFor(candidate),
        )
        return true
    }
}

/** The card over the keyboard area while open; nothing while closed. It takes every touch, and Back closes it. */
@Composable
fun BoxScope.DefinitionCardOverlay() {
    val card by DefinitionCards.state.collectAsState()
    val ui = card ?: return
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        BackDismisses(LocalView.current) { DefinitionCards.dismiss() }
    }
    SnyggBox(
        FlorisImeUi.Window.elementName,
        modifier = Modifier
            .matchParentSize()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) {
        DefinitionCardContent(
            ui = ui,
            onMore = {
                DefinitionCards.dismiss()
                try {
                    context.startActivity(AppIntents.dictionaryIntent(context, ui.word))
                } catch (_: ActivityNotFoundException) {
                    // Our own Activity is always there; nothing to open otherwise.
                }
            },
            onRemove = DefinitionCards::removeSuggestion,
            onSaveMapping = DefinitionCards::saveMapping,
            canSave = DefinitionCards.saveMappingHandler != null,
            onClose = DefinitionCards::dismiss,
        )
    }
}

/** The card body. Split out so tests can render it without the IME. */
@Composable
internal fun DefinitionCardContent(
    ui: DefinitionCardUi,
    onMore: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
    onSaveMapping: () -> Unit = {},
    canSave: Boolean = true,
) {
    val color = LocalContentColor.current
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(ui.word, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        val result = ui.result
        val first = ui.first
        when {
            result == null -> Text(stringRes(R.string.dictionary__loading), color = color)
            first != null -> {
                Text(first.pos, color = color, fontStyle = FontStyle.Italic)
                Text(first.definition, color = color)
            }
            result.packMissing -> Text(stringRes(R.string.dictionary__card_no_pack), color = color)
            else -> Text(stringRes(R.string.dictionary__not_found), color = color)
        }
        val buttons = ButtonDefaults.textButtonColors(contentColor = color)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onMore, colors = buttons) { Text(stringRes(R.string.dictionary__card_more)) }
            if (ui.learned) {
                TextButton(onClick = onRemove, colors = buttons) {
                    Text(stringRes(R.string.dictionary__card_remove))
                }
            }
            if (ui.mapping != null && canSave) {
                if (ui.mappingSaved) {
                    Text(stringRes(R.string.translit__mapping_saved), color = color)
                } else {
                    if (ui.mappingFailed) Text(stringRes(R.string.translit__mapping_not_saved), color = color)
                    TextButton(onClick = onSaveMapping, colors = buttons) {
                        Text(stringRes(R.string.translit__save_mapping))
                    }
                }
            }
            TextButton(onClick = onClose, colors = buttons) { Text(stringRes(R.string.dictionary__card_close)) }
        }
    }
}
