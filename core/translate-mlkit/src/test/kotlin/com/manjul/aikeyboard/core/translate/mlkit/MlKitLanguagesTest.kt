/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import com.manjul.aikeyboard.core.translate.Lang
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MlKitLanguagesTest {
    @Test fun mvpLanguagesAndBengaliMapToMlKit() {
        for (tag in listOf("en", "es", "fr", "pt", "ar", "ur", "id", "hi", "bn")) {
            assertNotNull(MlKitLanguages.toMlKit(Lang(tag)), tag)
            assertTrue(Lang(tag) in MlKitLanguages.all, tag)
        }
    }

    @Test fun pashtoIsNotSupported() {
        assertNull(MlKitLanguages.toMlKit(Lang("ps")))
    }

    @Test fun regionTagsMapThroughThePrimarySubtag() {
        assertEquals(MlKitLanguages.toMlKit(Lang("pt")), MlKitLanguages.toMlKit(Lang.of("pt-BR")))
    }
}
