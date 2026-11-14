/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartChipsTest {
    @Test fun `an email and a url`() {
        assertEquals(
            listOf(SmartChip(SmartChipKind.EMAIL, "a@b.co"), SmartChip(SmartChipKind.URL, "https://x.org")),
            SmartChips.extract("Mail a@b.co or visit https://x.org"),
        )
    }

    @Test fun `a phone number with spaces`() {
        assertEquals(listOf(SmartChip(SmartChipKind.PHONE, "+92 300 1234567")), SmartChips.extract("+92 300 1234567"))
    }

    @Test fun `five digits are not a phone number`() {
        assertEquals(emptyList(), SmartChips.extract("12345"))
    }

    @Test fun `phone numbers need 7 to 15 digits`() {
        assertEquals(listOf(SmartChip(SmartChipKind.PHONE, "123 4567")), SmartChips.extract("call 123 4567"))
        assertEquals(emptyList(), SmartChips.extract("call 123456"))
        assertEquals(emptyList(), SmartChips.extract("card 1234 5678 9012 3456"))
    }

    @Test fun `at most 3 chips`() {
        val text = "a@b.co c@d.co e@f.co g@h.co https://x.org +92 300 1234567"
        assertEquals(3, SmartChips.extract(text).size)
        assertEquals(1, SmartChips.extract(text, max = 1).size)
    }

    @Test fun `trailing punctuation is not part of a url and duplicates collapse`() {
        assertEquals(
            listOf(SmartChip(SmartChipKind.URL, "https://x.org/a?b=1")),
            SmartChips.extract("See https://x.org/a?b=1. Again: https://x.org/a?b=1"),
        )
    }

    @Test fun `digits inside an email or a url are not a phone number`() {
        val chips = SmartChips.extract("https://x.org/1234567890 and 1234567@b.co")
        assertTrue(chips.none { it.kind == SmartChipKind.PHONE }, chips.toString())
    }

    @Test fun `no chip on a masked item or an empty text`() {
        assertEquals(emptyList(), SmartChips.forItem("Mail a@b.co", masked = true))
        assertEquals(emptyList(), SmartChips.forItem(null, masked = false))
        assertEquals(1, SmartChips.forItem("Mail a@b.co", masked = false).size)
    }
}
