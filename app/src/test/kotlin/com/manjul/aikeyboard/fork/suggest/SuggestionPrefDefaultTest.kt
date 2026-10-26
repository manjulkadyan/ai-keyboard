/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.suggest

import com.manjul.aikeyboard.app.FlorisPreferenceModel
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import org.junit.Test
import kotlin.test.assertTrue

/**
 * PRD FR-05 AC1: word suggestions and composing are on by default for every MVP subtype. Upstream gates them on
 * `suggestion__enabled` (default false), so they must not depend on the emoji suggestion pref being on.
 */
class SuggestionPrefDefaultTest {
    @Test fun `word suggestions are on by default`() {
        val prefs: FlorisPreferenceModel by FlorisPreferenceStore
        assertTrue(prefs.suggestion.enabled.default)
    }
}
