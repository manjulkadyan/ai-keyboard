/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.subtypes

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
