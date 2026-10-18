/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.subtypes

import com.manjul.aikeyboard.ime.core.Subtype
import com.manjul.aikeyboard.ime.core.SubtypePreset
import com.manjul.aikeyboard.lib.FlorisLocale

/** Language tags of the eight MVP subtypes, in picker order. Mirrors the entries in `res/xml/method.xml`. */
val MVP_LANGUAGE_TAGS = listOf("en-US", "es-ES", "fr-FR", "pt-BR", "ar", "ur-PK", "id", "hi-IN")

/**
 * Pure decision behind `SubtypeManager.ensureDefaultSubtypes`: the tags from [wanted] (in order, without
 * duplicates) whose primary locale is not in [existing].
 */
fun missingSubtypeTags(existing: List<FlorisLocale>, wanted: List<String>): List<String> {
    val taken = existing.toMutableSet()
    return wanted.filter { taken.add(FlorisLocale.fromTag(it)) }
}

/** Preset for [locale]: exact locale match first, then any preset of the same language. */
fun findPreset(presets: List<SubtypePreset>?, locale: FlorisLocale): SubtypePreset? =
    presets?.find { it.locale == locale } ?: presets?.find { it.locale.language == locale.language }

/**
 * [current] plus one subtype per missing tag of [tags], built from [presets] in tag order. Tags without a
 * preset are skipped. New ids are [baseId], [baseId] + 1, ... Returns [current] when nothing is added.
 */
fun buildSeededList(current: List<Subtype>, presets: List<SubtypePreset>?, tags: List<String>, baseId: Long): List<Subtype> {
    val added = missingSubtypeTags(current.map { it.primaryLocale }, tags)
        .mapNotNull { findPreset(presets, FlorisLocale.fromTag(it))?.toSubtype() }
        .mapIndexed { index, subtype -> subtype.copy(id = baseId + index) }
    return if (added.isEmpty()) current else current + added
}
