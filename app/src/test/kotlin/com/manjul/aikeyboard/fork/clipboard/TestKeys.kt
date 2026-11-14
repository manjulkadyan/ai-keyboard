/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * In-memory [KeyProvider]: Robolectric has no AndroidKeyStore. [delete] behaves like a deleted alias.
 * A null [current] is an unavailable key store. [lost] is a permanently invalidated or unrecoverable key.
 * [failure] is an exception the key store throws, classified as `KeystoreKeyProvider` does.
 */
class TestKeys(var current: SecretKey? = newKey()) : KeyProvider {
    var deletes = 0
        private set
    var lost = false
    var failure: Exception? = null

    override fun key(): SecretKey? = (lookup() as? KeyLookup.Present)?.key

    override fun lookup(): KeyLookup {
        failure?.let { return classifyKeyFailure(it) }
        if (lost) return KeyLookup.Lost
        return current?.let { KeyLookup.Present(it) } ?: KeyLookup.Unavailable
    }

    override fun delete() {
        deletes++
        lost = false
        failure = null
        current = newKey()
    }

    companion object {
        /** What keystore2 throws from `getKeyEntry` on most errors, a dead daemon included (API 31+). */
        fun wrappedKeyStoreError(): UnrecoverableKeyException =
            UnrecoverableKeyException("wrapped").apply { initCause(KeyStoreException("system error")) }

        fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}
