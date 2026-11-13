/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.packbuild

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Signs the exact manifest bytes with ECDSA P-256 (`SHA256withECDSA`, DER, base64), like the S4 entitlement
 * signature. The key file is PEM with a PKCS#8 `PRIVATE KEY` block and an X.509 `PUBLIC KEY` block. The device takes
 * the public key as one base64 line ([publicKeyBase64], the `aikb.content.publicKey` value).
 */
object ManifestSigner {
    const val ALGORITHM = "SHA256withECDSA"

    fun sign(manifest: ByteArray, pem: String): String {
        val key = privateKey(pem)
        val der = Signature.getInstance(ALGORITHM).run {
            initSign(key)
            update(manifest)
            sign()
        }
        return Base64.getEncoder().encodeToString(der)
    }

    fun verify(manifest: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean = runCatching {
        Signature.getInstance(ALGORITHM).run {
            initVerify(publicKey(publicKeyBase64))
            update(manifest)
            verify(Base64.getDecoder().decode(signatureBase64.trim()))
        }
    }.getOrDefault(false)

    /** The base64 X.509 SubjectPublicKeyInfo of the PEM's `PUBLIC KEY` block, checked against its private key. */
    fun publicKeyBase64(pem: String): String {
        val encoded = Base64.getEncoder().encodeToString(block(pem, "PUBLIC KEY"))
        val probe = "packbuild key check".toByteArray()
        check(verify(probe, sign(probe, pem), encoded)) { "the PUBLIC KEY block does not match the PRIVATE KEY block" }
        return encoded
    }

    private fun privateKey(pem: String): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(block(pem, "PRIVATE KEY")))

    private fun publicKey(base64: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64.trim())))

    private fun block(pem: String, type: String): ByteArray {
        val begin = "-----BEGIN $type-----"
        val end = "-----END $type-----"
        val start = pem.indexOf(begin)
        val stop = pem.indexOf(end)
        require(start >= 0 && stop > start) { "no $type block in the key file" }
        return Base64.getMimeDecoder().decode(pem.substring(start + begin.length, stop))
    }
}
