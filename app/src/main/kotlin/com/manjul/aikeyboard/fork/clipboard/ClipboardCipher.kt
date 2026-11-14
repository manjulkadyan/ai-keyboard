/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The clipboard history key. [key] is null when the key is lost or the key store is unavailable. */
fun interface KeyProvider {
    fun key(): SecretKey?

    /** Deletes the key, so that the next [key] call makes a new one. */
    fun delete() {}
}

/**
 * AES-256-GCM for the clipboard history text column (TRD §11.6). A sealed value is
 * `"e1:" + base64(iv(12) || ciphertext || tag(16))`. No method puts the text in an exception or a log.
 */
class ClipboardCipher(private val keys: KeyProvider) {
    /** Seals [plain]. Throws [IllegalStateException] (without the text) when no key is available. */
    fun seal(plain: String): String = sealOrNull(plain) ?: error("clipboard key unavailable")

    /** Seals [plain], or null when no key is available or the key store fails. */
    fun sealOrNull(plain: String): String? {
        val key = keys.key() ?: return null
        return try {
            // No IV is passed in: AndroidKeyStore requires randomized encryption and picks the IV itself.
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
            val iv = cipher.iv
            if (iv == null || iv.size != IV_BYTES) return null
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.getEncoder().encodeToString(iv + body)
        } catch (_: Exception) {
            null
        }
    }

    /** The plain text, or null on a bad tag, a missing key or a missing prefix. */
    fun open(stored: String): String? {
        if (!stored.startsWith(PREFIX)) return null
        val payload = decode(stored) ?: return null
        if (payload.size < IV_BYTES + TAG_BYTES) return null
        val key = keys.key() ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, payload, 0, IV_BYTES))
            String(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * True when [stored] has the `e1:` prefix and a payload long enough for an IV and a tag. Pre-S5 plaintext rows
     * are false and pass through on read.
     */
    fun isSealed(stored: String): Boolean {
        if (!stored.startsWith(PREFIX)) return false
        val payload = decode(stored) ?: return false
        return payload.size >= IV_BYTES + TAG_BYTES
    }

    private fun decode(stored: String): ByteArray? =
        try {
            Base64.getDecoder().decode(stored.substring(PREFIX.length))
        } catch (_: IllegalArgumentException) {
            null
        }

    companion object {
        const val PREFIX = "e1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
    }
}
