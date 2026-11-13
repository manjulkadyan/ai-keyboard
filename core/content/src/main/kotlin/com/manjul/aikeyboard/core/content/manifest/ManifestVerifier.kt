/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.manifest

import com.manjul.aikeyboard.core.content.ContentPaths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Checks the manifest signature (ECDSA P-256, SHA256withECDSA, DER, base64) against [publicKeyBase64] (the one-line
 * base64 X.509 key from `aikb.content.publicKey`), then parses it. [verify] never throws: a bad key, a bad signature,
 * an unknown [FORMAT], a missing or malformed file hash, an unsafe path or id, or duplicates all give null. A pack of
 * an unknown kind is skipped so a newer CDN can add kinds.
 */
class ManifestVerifier(publicKeyBase64: String) {
    private val key: PublicKey? = runCatching {
        val encoded = Base64.getDecoder().decode(publicKeyBase64.trim())
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
    }.getOrNull()

    fun verify(manifest: ByteArray, signatureBase64: String): ContentManifest? {
        val key = key ?: return null
        val ok = runCatching {
            val signature = Base64.getDecoder().decode(signatureBase64.trim())
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(manifest)
                verify(signature)
            }
        }.getOrDefault(false)
        if (!ok) return null
        return runCatching { parse(Json.parseToJsonElement(manifest.decodeToString())) }.getOrNull()
    }

    private fun parse(root: JsonElement): ContentManifest {
        val obj = root as? JsonObject ?: invalid()
        if (obj.int("format") != FORMAT) invalid()
        val issuedAt = obj.long("issued_at") ?: invalid()
        val packs = (obj["packs"] as? JsonArray ?: invalid()).mapNotNull { pack(it as? JsonObject ?: invalid()) }
        if (packs.map { it.id }.toSet().size != packs.size) invalid()
        return ContentManifest(FORMAT, issuedAt, packs)
    }

    /** The pack, or null for a pack of an unknown kind (skipped). Throws [InvalidManifest] for a bad pack. */
    private fun pack(obj: JsonObject): PackSpec? {
        val kind = PackKindSpec.of(obj.string("kind") ?: invalid()) ?: return null
        val files = (obj["files"] as? JsonArray ?: invalid()).map { file(it as? JsonObject ?: invalid()) }
        // The downloader stores the files of a pack side by side, so their names must differ.
        if (files.isEmpty() || files.map { it.name }.toSet().size != files.size) invalid()
        val licence = obj["licence"] as? JsonObject ?: invalid()
        return PackSpec(
            id = obj.string("id")?.takeIf(ContentPaths::isSafeId) ?: invalid(),
            kind = kind,
            version = obj.int("version")?.takeIf { it >= 0 } ?: invalid(),
            premium = obj.boolean("premium") ?: invalid(),
            title = obj.string("title") ?: invalid(),
            files = files,
            licence = PackLicence(
                name = licence.string("name") ?: invalid(),
                url = licence.string("url") ?: invalid(),
                attribution = licence.string("attribution") ?: invalid(),
            ),
        )
    }

    private fun file(obj: JsonObject) = PackFile(
        path = obj.string("path")?.takeIf(ContentPaths::isSafePath) ?: invalid(),
        sha256 = obj.string("sha256")?.takeIf(SHA256::matches) ?: invalid(),
        bytes = obj.long("bytes")?.takeIf { it >= 0 } ?: invalid(),
        mime = obj.string("mime") ?: invalid(),
    )

    private object InvalidManifest : Exception() {
        private fun readResolve(): Any = InvalidManifest
    }

    private fun invalid(): Nothing = throw InvalidManifest

    private fun JsonObject.primitive(name: String) = this[name] as? JsonPrimitive

    private fun JsonObject.string(name: String) = primitive(name)?.takeIf { it.isString }?.content

    private fun JsonObject.int(name: String) = primitive(name)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.long(name: String) = primitive(name)?.takeIf { !it.isString }?.longOrNull

    private fun JsonObject.boolean(name: String) = primitive(name)?.takeIf { !it.isString }?.booleanOrNull

    companion object {
        /** The only manifest format this build reads. */
        const val FORMAT = 1
        private val SHA256 = Regex("[0-9a-f]{64}")
    }
}
