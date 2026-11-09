/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** PRD §12 `delete_data`: "Data deletion confirmed" (S4 Task 12). No params, so nothing about the user leaves. */
class DeleteDataEventTest {
    @Test fun `delete_data has no params`() {
        val event = EventCatalog.deleteData()
        assertEquals("delete_data", event.name)
        assertEquals(emptyMap(), event.params)
        assertTrue("delete_data" in EventCatalog.allNames)
    }
}
