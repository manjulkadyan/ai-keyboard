/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content

import com.manjul.aikeyboard.core.content.source.ContentFetchException
import com.manjul.aikeyboard.core.content.source.ContentSource
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** The Task 1 fixture content and its test-only signing key (plan decision 12), read in place (plan F15). */
internal object TestContent {
    val contentDir: File get() = File(System.getProperty("content.contentDir")!!)
    val manifestBytes: ByteArray get() = File(contentDir, "manifest.json").readBytes()
    val manifestSig: String get() = File(contentDir, "manifest.json.sig").readText()

    private val pem: String get() = File(System.getProperty("content.fixtureKeyPem")!!).readText()

    /** The `aikb.content.publicKey` form of the fixture key: one base64 line of the X.509 key. */
    val publicKeyBase64: String get() = Base64.getEncoder().encodeToString(block("PUBLIC KEY"))

    /** Signs [bytes] like `:tools:packbuild` `ManifestSigner`: SHA256withECDSA, DER, base64. */
    fun sign(bytes: ByteArray): String {
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(block("PRIVATE KEY")))
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(bytes)
            sign()
        }
        return Base64.getEncoder().encodeToString(der)
    }

    /** A fresh P-256 public key that did not sign anything here. */
    fun otherPublicKeyBase64(): String {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
        return Base64.getEncoder().encodeToString(pair.public.encoded)
    }

    private fun block(type: String): ByteArray {
        val begin = "-----BEGIN $type-----"
        val end = "-----END $type-----"
        val text = pem
        return Base64.getMimeDecoder().decode(text.substring(text.indexOf(begin) + begin.length, text.indexOf(end)))
    }
}

/** A [ContentSource] backed by a map, counting requests. A missing path throws like a 404. */
internal class FakeContentSource(var files: Map<String, ByteArray> = emptyMap()) : ContentSource {
    val requests = mutableListOf<String>()
    var offline = false

    override suspend fun fetch(path: String): ByteArray {
        requests += path
        if (offline) throw ContentFetchException("offline")
        return files[path] ?: throw ContentFetchException("HTTP 404")
    }
}
