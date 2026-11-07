/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.billing

import com.manjul.aikeyboard.core.ai.kb.Entitlement
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Checks kb-api's ECDSA P-256 (`SHA256withECDSA`) signature over [Entitlement.signedPayload] (plan decision 4).
 * [publicKeyBase64] is a base64 X.509 SubjectPublicKeyInfo key. The signature is base64 DER.
 * An empty key accepts every entitlement in debug builds only, so a release build never trusts an unsigned cache.
 */
class EntitlementSignature(
    private val publicKeyBase64: String,
    private val debug: Boolean = BuildConfig.DEBUG,
) {
    private val key: PublicKey? by lazy {
        runCatching {
            val spec = X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.trim()))
            KeyFactory.getInstance("EC").generatePublic(spec)
        }.getOrNull()
    }

    fun isValid(e: Entitlement): Boolean {
        if (publicKeyBase64.isBlank()) return debug
        val publicKey = key ?: return false
        return runCatching {
            Signature.getInstance(ALGORITHM).run {
                initVerify(publicKey)
                update(e.signedPayload.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(e.signature))
            }
        }.getOrDefault(false)
    }

    private companion object {
        const val ALGORITHM = "SHA256withECDSA"
    }
}
