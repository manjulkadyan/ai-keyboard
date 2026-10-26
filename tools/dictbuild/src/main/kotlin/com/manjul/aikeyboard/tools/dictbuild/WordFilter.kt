/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.dictbuild

import java.lang.Character.UnicodeScript

/**
 * Which raw word-list entries go into a shipped dictionary (plan Task 2). An entry is kept only when it is made of
 * letters (and combining marks) of the locale's script, is at most [MAX_LENGTH] chars, has a count of at least
 * [MIN_COUNT] and is not on our own block list. Apostrophes are allowed inside en, fr and pt words.
 *
 * The block list only keeps words out of suggestions and autocorrect; upstream's `allowPossiblyOffensive` flag is
 * a separate, unrelated setting.
 */
object WordFilter {
    const val MAX_LENGTH: Int = 24
    const val MIN_COUNT: Int = 3

    private val LATIN = setOf("en", "es", "fr", "pt", "id")
    private val APOSTROPHE = setOf("en", "fr", "pt")
    private val APOSTROPHES = setOf('\'', '’')
    private const val ZWNJ = '‌'
    private const val ZWJ = '‍'

    private fun scriptOf(locale: String): UnicodeScript? = when (locale) {
        in LATIN -> UnicodeScript.LATIN
        "ar", "ur" -> UnicodeScript.ARABIC
        "hi" -> UnicodeScript.DEVANAGARI
        else -> null
    }

    fun keep(locale: String, word: String): Boolean = keep(locale, word, Int.MAX_VALUE)

    fun keep(locale: String, word: String, count: Int): Boolean {
        if (count < MIN_COUNT) return false
        if (word.isEmpty() || word.length > MAX_LENGTH) return false
        val script = scriptOf(locale) ?: return false
        var hasLetter = false
        for (i in word.indices) {
            val c = word[i]
            val inside = i > 0 && i < word.length - 1
            if (c in APOSTROPHES) {
                if (locale in APOSTROPHE && inside) continue
                return false
            }
            if ((c == ZWNJ || c == ZWJ) && script != UnicodeScript.LATIN && inside) continue
            if (c.isSurrogate()) return false
            when (Character.getType(c).toByte()) {
                Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
                Character.OTHER_LETTER,
                -> {
                    if (UnicodeScript.of(c.code) != script) return false
                    hasLetter = true
                }
                Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK -> {
                    if (i == 0) return false
                    val s = UnicodeScript.of(c.code)
                    if (s != script && s != UnicodeScript.INHERITED) return false
                }
                else -> return false
            }
        }
        if (!hasLetter) return false
        return word.lowercase() !in blockList(locale)
    }

    /** Our own profanity and slur list per Latin locale (<= 50 each, lowercase); empty for ar, ur and hi. */
    fun blockList(locale: String): Set<String> = BLOCK[locale].orEmpty()

    private val BLOCK: Map<String, Set<String>> = mapOf(
        "en" to setOf(
            "fuck", "fucks", "fucking", "fucked", "fucker", "fuckers", "motherfucker", "motherfuckers",
            "motherfucking", "shit", "shits", "shitty", "bullshit", "bitch", "bitches", "cunt", "cunts",
            "cocksucker", "dickhead", "asshole", "assholes", "whore", "whores", "slut", "sluts", "nigger",
            "niggers", "nigga", "niggas", "faggot", "faggots", "fag", "fags", "retard", "retarded", "wanker",
            "twat", "dildo", "jizz", "tits", "chink", "spic", "kike", "tranny",
        ),
        "es" to setOf(
            "puta", "putas", "puto", "putos", "mierda", "coño", "joder", "jodido", "jodida", "cabrón", "cabrones",
            "pendejo", "pendeja", "pendejos", "gilipollas", "verga", "chingar", "chingada", "chingado", "maricón",
            "maricones", "culero", "zorra", "polla", "follar", "hijoputa", "malparido", "mamón", "cojones",
            "putita", "sudaca",
        ),
        "fr" to setOf(
            "putain", "merde", "connard", "connards", "connasse", "salope", "salopes", "enculé", "enculés",
            "enculer", "pute", "putes", "bite", "couille", "couilles", "nique", "niquer", "pédé", "pédés",
            "foutre", "salaud", "salauds", "branleur", "chatte", "conne", "pétasse", "bougnoule", "tapette",
        ),
        "pt" to setOf(
            "porra", "caralho", "merda", "puta", "putas", "foda", "foder", "fodase", "fodido", "fodida", "buceta",
            "cuzão", "viado", "viados", "bosta", "vagabunda", "arrombado", "desgraçado", "cacete", "punheta",
            "piroca", "xoxota", "putaria", "boquete",
        ),
        "id" to setOf(
            "bangsat", "kontol", "memek", "ngentot", "jancok", "jancuk", "bajingan", "brengsek", "keparat",
            "goblok", "tolol", "perek", "pelacur", "lonte", "ngewe", "pantek", "jembut", "coli",
        ),
    )
}
