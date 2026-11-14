/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** S5 Task 7, on a device: the clipboard key lives in AndroidKeyStore, is made once and is replaced when deleted. */
@RunWith(AndroidJUnit4::class)
class KeystoreKeyProviderTest {
    private val alias = "aikb_clipboard_test"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Before @After fun clean() {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun aliasCount() = keyStore.aliases().toList().count { it == alias }

    @Test fun theAliasIsCreatedOnce() {
        assertFalse(keyStore.containsAlias(alias))
        val provider = KeystoreKeyProvider(alias)
        assertNotNull(provider.key())
        assertNotNull(provider.key())
        assertEquals(1, aliasCount())
        val sealed = ClipboardCipher(provider).seal("hello")
        // A second provider (a new process) finds the same key.
        assertEquals("hello", ClipboardCipher(KeystoreKeyProvider(alias)).open(sealed))
        assertEquals(1, aliasCount())
    }

    @Test fun aDeletedAliasGivesANewKey() {
        val provider = KeystoreKeyProvider(alias)
        val sealed = ClipboardCipher(provider).seal("hello")
        provider.delete()
        assertFalse(keyStore.containsAlias(alias))
        assertNull(ClipboardCipher(provider).open(sealed))
        assertTrue(keyStore.containsAlias(alias))
        assertEquals(1, aliasCount())
        val again = ClipboardCipher(provider)
        assertEquals("again", again.open(again.seal("again")))
    }
}
