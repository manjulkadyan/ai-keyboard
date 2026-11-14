/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CustomMappingsTest {
    @Test fun putGetAndRemove() {
        val m = CustomMappings()
        assertNull(m.get("kya"))
        m.put("kya", "کیا")
        assertEquals("کیا", m.get("kya"))
        m.put("kya", "کیاں")
        assertEquals("کیاں", m.get("kya"))
        m.remove("kya")
        assertNull(m.get("kya"))
        m.remove("absent")
    }

    @Test fun romanKeyIgnoresCase() {
        val m = CustomMappings()
        m.put("Kya", "کیا")
        assertEquals("کیا", m.get("kya"))
        assertEquals("کیا", m.get("KYA"))
        m.remove("KyA")
        assertNull(m.get("kya"))
    }

    @Test fun replaceAllDropsOldEntries() {
        val m = CustomMappings()
        m.put("old", "x")
        m.replaceAll(mapOf("hai" to "ہے", "Nahi" to "نہیں"))
        assertNull(m.get("old"))
        assertEquals("ہے", m.get("hai"))
        assertEquals("نہیں", m.get("nahi"))
    }

    @Test fun toStringHoldsNoText() {
        val m = CustomMappings()
        m.put("secretword", "راز")
        assertEquals("CustomMappings(size=1)", m.toString())
        assertFalse(m.toString().contains("secret"))
    }
}
