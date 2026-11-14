/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import org.junit.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipboardCipherTest {
    private val keys = TestKeys()
    private val cipher = ClipboardCipher(keys)

    @Test fun `a round trip gives the same text`() {
        val text = "Meet at 10, the code is on the fridge 😀"
        val sealed = cipher.seal(text)
        assertTrue(sealed.startsWith("e1:"))
        assertTrue(cipher.isSealed(sealed))
        assertFalse(sealed.contains("fridge"))
        assertEquals(text, cipher.open(sealed))
    }

    @Test fun `the payload is iv, ciphertext and a 16 byte tag`() {
        val text = "hello"
        val payload = Base64.getDecoder().decode(cipher.seal(text).removePrefix("e1:"))
        assertEquals(12 + text.toByteArray().size + 16, payload.size)
    }

    @Test fun `two seals of one text differ`() {
        assertNotEquals(cipher.seal("same text"), cipher.seal("same text"))
    }

    @Test fun `one changed byte gives null`() {
        val payload = Base64.getDecoder().decode(cipher.seal("do not tamper").removePrefix("e1:"))
        for (index in listOf(0, 12, payload.size - 1)) {
            val copy = payload.copyOf()
            copy[index] = (copy[index].toInt() xor 0x01).toByte()
            assertNull(cipher.open("e1:" + Base64.getEncoder().encodeToString(copy)), "byte $index")
        }
    }

    @Test fun `another key gives null`() {
        val sealed = cipher.seal("secret-ish")
        assertNull(ClipboardCipher(TestKeys()).open(sealed))
    }

    @Test fun `a missing key gives null and seal refuses`() {
        val sealed = cipher.seal("text")
        keys.current = null
        assertNull(cipher.open(sealed))
        assertNull(cipher.sealOrNull("text"))
        val e = assertFailsWith<IllegalStateException> { cipher.seal("never in the message") }
        assertFalse(e.message.orEmpty().contains("never in the message"))
    }

    @Test fun `a missing prefix or a bad payload gives null`() {
        assertFalse(cipher.isSealed("plain text"))
        assertNull(cipher.open("plain text"))
        assertNull(cipher.open("e1:"))
        assertNull(cipher.open("e1:not base64 !!"))
        assertNull(cipher.open("e1:" + Base64.getEncoder().encodeToString(ByteArray(20))))
    }
}
