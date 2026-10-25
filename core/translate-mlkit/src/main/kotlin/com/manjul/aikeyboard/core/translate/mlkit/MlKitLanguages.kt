/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import com.google.mlkit.nl.translate.TranslateLanguage
import com.manjul.aikeyboard.core.translate.Lang

/** Maps our [Lang] tags to ML Kit Translation language codes. Tags only, no text. */
object MlKitLanguages {
    /** The ML Kit code for [lang], or `null` when ML Kit cannot translate it (e.g. `ps`). */
    fun toMlKit(lang: Lang): String? = TranslateLanguage.fromLanguageTag(lang.tag)

    /** Every language ML Kit translates. */
    val all: Set<Lang> by lazy { TranslateLanguage.getAllLanguages().map(Lang::of).toSet() }
}
