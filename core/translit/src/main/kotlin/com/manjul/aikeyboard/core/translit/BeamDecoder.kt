/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

/**
 * Segments a Roman word left to right with the [table] rules and keeps the best [beamWidth] partial outputs at every
 * input offset (spec "BeamDecoder"). A partial whose native text is not a [lexicon] prefix is pruned, unless that
 * would empty the beam, so unknown words still decode.
 *
 * Hindi only: when two consonant matches meet, the decoder joins them with a virama (्), unless a row for the joined
 * Roman maps to something else (a digraph such as "kh" or "ng"). It also offers the plain pair, which keeps the
 * inherent vowel ("karna" needs र then न), at [SCHWA_COST]. Duplicate outputs keep the lowest cost.
 *
 * The Roman word and the outputs are user content: nothing here logs them, and [toString] holds no text.
 */
class BeamDecoder(private val table: RuleTable, private val lexicon: TranslitLexicon, val beamWidth: Int = 16) {
    init {
        require(beamWidth > 0) { "beamWidth must be positive" }
    }

    private val hindi = table.lang == TranslitLang.HI

    /** Native outputs per Roman part, for the virama rule. */
    private val nativesByRoman: Map<String, Set<String>> =
        table.rules.groupBy({ it.roman }, { it.native }).mapValues { it.value.toSet() }

    /** Test hook: called with the size of every pruned beam. */
    internal var beamObserver: ((Int) -> Unit)? = null

    /** [consonant] is the previous match when it was a Hindi consonant match, else null. */
    private class Partial(val native: String, val cost: Double, val consonant: Rule?)

    /** Complete hypotheses, best (lowest cost) first, one per native spelling. */
    fun decode(roman: String): List<Hypothesis> {
        if (roman.isEmpty() || roman.length > MAX_INPUT_LENGTH) return emptyList()
        val text = RuleTable.lowerCase(roman)
        val n = text.length
        val pending = arrayOfNulls<HashMap<String, Partial>>(n + 1)
        pending[0] = hashMapOf("" to Partial("", 0.0, null))
        for (offset in 0 until n) {
            val beam = pending[offset]?.let { prune(it.values) } ?: continue
            pending[offset] = null
            val rules = table.matchesAtLowered(text, offset)
            for (partial in beam) {
                for (rule in rules) {
                    val end = offset + rule.roman.length
                    val into = pending[end] ?: HashMap<String, Partial>().also { pending[end] = it }
                    expand(partial, rule) { add(into, it) }
                }
            }
        }
        val done = pending[n] ?: return emptyList()
        // States differ only in `consonant`, so one spelling can appear twice. Keep its lowest cost.
        val best = HashMap<String, Double>()
        for (p in prune(done.values)) {
            val old = best[p.native]
            if (old == null || p.cost < old) best[p.native] = p.cost
        }
        return best.map { Hypothesis(it.key, it.value) }.sortedBy { it.cost }
    }

    private inline fun expand(partial: Partial, rule: Rule, emit: (Partial) -> Unit) {
        val next = if (hindi && startsWithConsonant(rule.native)) rule else null
        val prev = partial.consonant
        if (prev != null && next != null) {
            if (viramaAllowed(prev, next)) {
                emit(Partial(partial.native + VIRAMA + rule.native, partial.cost + rule.cost, next))
            }
            emit(Partial(partial.native + rule.native, partial.cost + rule.cost + SCHWA_COST, next))
            return
        }
        val consonant = if (hindi && endsWithConsonant(rule.native)) rule else null
        emit(Partial(partial.native + rule.native, partial.cost + rule.cost, consonant))
    }

    /** No generic virama when a row for the joined Roman exists and none of its outputs is that conjunct. */
    private fun viramaAllowed(prev: Rule, next: Rule): Boolean {
        val joined = nativesByRoman[prev.roman + next.roman] ?: return true
        return (prev.native + VIRAMA + next.native) in joined
    }

    private fun add(into: HashMap<String, Partial>, p: Partial) {
        val key = if (p.consonant == null) p.native else p.native + '\u0000' + p.consonant.roman
        val old = into[key]
        if (old == null || p.cost < old.cost) into[key] = p
    }

    /** Lexicon prefixes only (all of them if none is a prefix), then the [beamWidth] cheapest. */
    private fun prune(partials: Collection<Partial>): List<Partial> {
        val known = partials.filter { lexicon.prefixExists(it.native) }
        val kept = (known.ifEmpty { partials }).sortedBy { it.cost }.take(beamWidth)
        beamObserver?.invoke(kept.size)
        return kept
    }

    override fun toString(): String = "BeamDecoder(lang=${table.lang.tag}, beamWidth=$beamWidth)"

    companion object {
        /** Longer inputs return no hypotheses (brief: more than 24 letters). */
        const val MAX_INPUT_LENGTH: Int = 24

        /** Cost of keeping the inherent vowel between two Hindi consonants. A placeholder, tuned in Task 6. */
        const val SCHWA_COST: Double = 0.5

        private const val VIRAMA = '्'
        private const val NUKTA = '़'

        private fun isConsonant(c: Char): Boolean = c in 'क'..'ह' || c in 'क़'..'य़'

        private fun startsWithConsonant(native: String): Boolean = native.isNotEmpty() && isConsonant(native[0])

        private fun endsWithConsonant(native: String): Boolean {
            if (native.isEmpty()) return false
            val last = native[native.length - 1]
            return isConsonant(last) || (last == NUKTA && native.length > 1 && isConsonant(native[native.length - 2]))
        }
    }
}
