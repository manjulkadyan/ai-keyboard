/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

/** Target scripts for Roman input. [tag] names the rule table resource (`/translit/<tag>.tsv`). */
enum class TranslitLang(val tag: String) { UR("ur"), HI("hi") }
