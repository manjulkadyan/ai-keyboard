/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * In-memory [KeyProvider]: Robolectric has no AndroidKeyStore. [delete] behaves like a deleted alias.
 * A null [current] is an unavailable key store. [lost] is a permanently invalidated or unrecoverable key.
 */
class TestKeys(var current: SecretKey? = newKey()) : KeyProvider {
    var deletes = 0
        private set
    var lost = false

    override fun key(): SecretKey? = if (lost) null else current

    override fun lookup(): KeyLookup = if (lost) KeyLookup.Lost else super.lookup()

    override fun delete() {
        deletes++
        lost = false
        current = newKey()
    }

    companion object {
        fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}
