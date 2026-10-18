/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageInstalledTest {
    @Test fun exactTagMatchIgnoresCase() = assertTrue(languageInstalled("en-US", listOf("EN-us")))

    @Test fun languageOnlyMatchesRegionalTag() {
        assertTrue(languageInstalled("en-GB", listOf("en-US")))
        assertTrue(languageInstalled("ur", listOf("ur-PK")))
    }

    @Test fun noMatch() {
        assertFalse(languageInstalled("ur-PK", listOf("en-US", "de-DE")))
        assertFalse(languageInstalled("en", emptyList()))
    }
}
