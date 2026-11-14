/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The clipboard history key. [key] is null when the key is lost or the key store is unavailable. */
fun interface KeyProvider {
    fun key(): SecretKey?

    /**
     * Tells an unavailable key store (a transient fault: hide sealed rows, keep everything) from a lost key (wipe).
     * The default knows no loss, so a null [key] is [KeyLookup.Unavailable].
     */
    fun lookup(): KeyLookup = key()?.let { KeyLookup.Present(it) } ?: KeyLookup.Unavailable

    /** Deletes the key, so that the next [key] call makes a new one. */
    fun delete() {}
}

sealed interface KeyLookup {
    data class Present(val key: SecretKey) : KeyLookup

    /** The key store failed for now. The key can come back, so nothing is deleted. */
    data object Unavailable : KeyLookup

    /** The key is permanently invalidated or unrecoverable. */
    data object Lost : KeyLookup
}

/** True for a real key loss: a bad GCM tag from a present key, or an invalidated or unrecoverable key. */
internal fun isPermanentKeyLoss(error: Throwable): Boolean {
    var e: Throwable? = error
    var depth = 0
    while (e != null && depth++ < MAX_CAUSE_DEPTH) {
        if (e is AEADBadTagException || e is KeyPermanentlyInvalidatedException || e is UnrecoverableKeyException) {
            return true
        }
        e = e.cause
    }
    return false
}

private const val MAX_CAUSE_DEPTH = 8

/** The result of [ClipboardCipher.trySeal]. */
internal sealed interface Sealing {
    data class Done(val value: String) : Sealing
    data object Unavailable : Sealing
    data object Lost : Sealing
}

/** The result of [ClipboardCipher.tryOpen]. */
internal sealed interface Opening {
    data class Opened(val text: String) : Opening
    data object Unavailable : Opening
    data object Lost : Opening
}

/**
 * AES-256-GCM for the clipboard history text column (TRD §11.6). A sealed value is
 * `"e1:" + base64(iv(12) || ciphertext || tag(16))`. No method puts the text in an exception or a log.
 */
class ClipboardCipher(private val keys: KeyProvider) {
    /** Seals [plain]. Throws [IllegalStateException] (without the text) when no key is available. */
    fun seal(plain: String): String = sealOrNull(plain) ?: error("clipboard key unavailable")

    /** Seals [plain], or null when no key is available or the key store fails. */
    fun sealOrNull(plain: String): String? = (trySeal(plain) as? Sealing.Done)?.value

    /** The plain text, or null on a bad tag, a missing key or a missing prefix. */
    fun open(stored: String): String? = (tryOpen(stored) as? Opening.Opened)?.text

    internal fun trySeal(plain: String): Sealing {
        val key = when (val lookup = keys.lookup()) {
            is KeyLookup.Present -> lookup.key
            KeyLookup.Unavailable -> return Sealing.Unavailable
            KeyLookup.Lost -> return Sealing.Lost
        }
        return try {
            // No IV is passed in: AndroidKeyStore requires randomized encryption and picks the IV itself.
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
            val iv = cipher.iv
            if (iv == null || iv.size != IV_BYTES) return Sealing.Unavailable
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Sealing.Done(PREFIX + Base64.getEncoder().encodeToString(iv + body))
        } catch (e: Exception) {
            if (isPermanentKeyLoss(e)) Sealing.Lost else Sealing.Unavailable
        }
    }

    /**
     * [Opening.Lost] only for a real loss (see [isPermanentKeyLoss]). A missing prefix, a bad payload or a key
     * store fault is [Opening.Unavailable], which never wipes.
     */
    internal fun tryOpen(stored: String): Opening {
        if (!stored.startsWith(PREFIX)) return Opening.Unavailable
        val payload = decode(stored) ?: return Opening.Unavailable
        if (payload.size < IV_BYTES + TAG_BYTES) return Opening.Unavailable
        val key = when (val lookup = keys.lookup()) {
            is KeyLookup.Present -> lookup.key
            KeyLookup.Unavailable -> return Opening.Unavailable
            KeyLookup.Lost -> return Opening.Lost
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BYTES * 8, payload, 0, IV_BYTES))
            Opening.Opened(String(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES), Charsets.UTF_8))
        } catch (e: Exception) {
            if (isPermanentKeyLoss(e)) Opening.Lost else Opening.Unavailable
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
