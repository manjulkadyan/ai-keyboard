/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** In-memory [KeyProvider]: Robolectric has no AndroidKeyStore. [delete] behaves like a deleted alias. */
class TestKeys(var current: SecretKey? = newKey()) : KeyProvider {
    var deletes = 0
        private set

    override fun key(): SecretKey? = current

    override fun delete() {
        deletes++
        current = newKey()
    }

    companion object {
        fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}
