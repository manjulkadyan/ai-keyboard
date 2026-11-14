/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.routes

import android.content.Intent
import android.net.Uri
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.fork.processtext.ProcessTextInput
import java.util.concurrent.atomic.AtomicReference

/** What `FlorisAppActivity` does with an incoming intent. */
sealed interface AppIntent {
    /**
     * Open an allow-listed route; [translatorText] prefills the Translator and [dictionaryWord] the Dictionary. Both
     * are user content and never logged.
     */
    data class OpenRoute(
        val route: RouteRegistry.Resolved,
        val translatorText: String? = null,
        val dictionaryWord: String? = null,
    ) : AppIntent {
        override fun toString(): String = "OpenRoute(route=${route.name}, unknown=${route.unknown}, " +
            "translatorChars=${translatorText?.length}, dictionaryChars=${dictionaryWord?.length})"
    }

    /** Upstream's extension import (VIEW of a content URI, or SEND with a clip). */
    data class ImportExtension(val uri: Uri) : AppIntent
}

/**
 * Classifies `FlorisAppActivity` intents (S2-routes, PRD FR-32). The activity is exported, so the intent can carry
 * anything: missing data, extras of the wrong type, parcels that fail to unpack. Nothing here throws; an intent we
 * cannot read is treated as a plain launch.
 *
 * - An `open_screen` extra, or a `VIEW` of a `ui://` or `app://` link (or any BROWSABLE `VIEW`), goes through
 *   [RouteRegistry]; a value that is not allow-listed opens Home and logs `deeplink_unknown`.
 * - Before [onboardingDone] (read by the caller) every paywall route opens Home (PRD FR-32 AC3).
 * - Other `VIEW` intents with data and `SEND` with a clip URI keep upstream's extension import.
 */
object AppIntents {
    private val routeSchemes = setOf("ui", "app")

    fun classify(intent: Intent?, onboardingDone: Boolean = true, log: (AnalyticsEvent) -> Unit): AppIntent? {
        intent ?: return null
        val request = runCatching { read(intent, onboardingDone) }.getOrNull()
        if (request is AppIntent.OpenRoute && request.route.unknown) log(EventCatalog.deeplinkUnknown())
        return request
    }

    private fun read(intent: Intent, onboardingDone: Boolean): AppIntent? {
        if (intent.hasExtra(RouteRegistry.EXTRA_OPEN_SCREEN)) {
            // A non-String value reads as null: not allow-listed.
            val name = runCatching { intent.getStringExtra(RouteRegistry.EXTRA_OPEN_SCREEN) }.getOrNull()
            val route = RouteRegistry.resolve(name ?: "", null, onboardingDone)
            return AppIntent.OpenRoute(route, translatorText(intent, route), dictionaryWord(intent, route))
        }
        val data = intent.data
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val browsable = intent.categories?.contains(Intent.CATEGORY_BROWSABLE) == true
                if (browsable || data?.scheme in routeSchemes) {
                    return AppIntent.OpenRoute(RouteRegistry.resolve(null, data ?: Uri.EMPTY, onboardingDone))
                }
                if (data != null) return AppIntent.ImportExtension(data)
            }
            Intent.ACTION_SEND -> {
                val clip = intent.clipData ?: return null
                if (clip.itemCount == 0) return null
                return clip.getItemAt(0).uri?.let { AppIntent.ImportExtension(it) }
            }
        }
        return null
    }

    private fun translatorText(intent: Intent, route: RouteRegistry.Resolved): String? {
        if (route.unknown || route.name != RouteRegistry.TRANSLATOR) return null
        val text = runCatching { intent.getCharSequenceExtra(RouteRegistry.EXTRA_TRANSLATOR_TEXT) }.getOrNull()
        return text?.toString()?.takeIf { it.isNotBlank() }?.let(ProcessTextInput::truncate)
    }

    /** S5-dict: the trimmed word of `open_screen=dictionary`, at most [MAX_DICTIONARY_WORD_CHARS] chars. */
    private fun dictionaryWord(intent: Intent, route: RouteRegistry.Resolved): String? {
        if (route.unknown || route.name != RouteRegistry.DICTIONARY) return null
        val word = runCatching { intent.getCharSequenceExtra(RouteRegistry.EXTRA_DICTIONARY_WORD) }.getOrNull()
        return word?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_DICTIONARY_WORD_CHARS }
    }

    const val MAX_DICTIONARY_WORD_CHARS = 64
}

/**
 * Hands text from the Translate sheet's "Open in app" to the Translator screen, once. The Translator (Task 10)
 * calls [take] when it opens; the text is user content and lives only in memory.
 */
object TranslatorHandoff {
    private val pending = AtomicReference<String?>(null)

    fun offer(text: String) = pending.set(text)

    fun take(): String? = pending.getAndSet(null)
}

/** Hands the keyboard card's word to the Dictionary screen, once (S5-dict). It lives only in memory. */
object DictionaryHandoff {
    private val pending = AtomicReference<String?>(null)

    fun offer(word: String) = pending.set(word)

    fun take(): String? = pending.getAndSet(null)
}
