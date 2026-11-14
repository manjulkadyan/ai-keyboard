/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The clipboard key in AndroidKeyStore: AES-256-GCM, made on first use, no user authentication (TRD §11.6), so
 * the IME can read history on a locked screen as upstream does. Nothing touches the key store until [key].
 * [key] is null when the key store fails. [lookup] classifies a failure with [classifyKeyFailure]: an
 * invalidated key, or an unrecoverable key without a key store error behind it, is [KeyLookup.Lost]. Every other
 * failure, a keystore2 error wrapped in `UnrecoverableKeyException` included, is [KeyLookup.Unavailable].
 */
class KeystoreKeyProvider(private val alias: String = DEFAULT_ALIAS) : KeyProvider {
    @Volatile
    private var cached: SecretKey? = null

    override fun key(): SecretKey? = (lookup() as? KeyLookup.Present)?.key

    @Synchronized
    override fun lookup(): KeyLookup {
        cached?.let { return KeyLookup.Present(it) }
        return try {
            KeyLookup.Present(loadOrCreate().also { cached = it })
        } catch (e: Exception) {
            classifyKeyFailure(e)
        }
    }

    @Synchronized
    override fun delete() {
        cached = null
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun loadOrCreate(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "aikb_clipboard_v1"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_BITS = 256
    }
}
