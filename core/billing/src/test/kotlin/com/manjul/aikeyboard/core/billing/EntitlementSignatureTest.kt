/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.billing

import com.manjul.aikeyboard.core.ai.kb.Entitlement
import com.manjul.aikeyboard.core.ai.kb.SubState
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntitlementSignatureTest {
    private val keys: KeyPair = testKeyPair()
    private val verifier = EntitlementSignature(publicKeyBase64(keys), debug = false)
    private val signed = sign(keys, Entitlement(true, SubState.ACTIVE, 1_800_000_000_000L, 1_700_000_000_000L, ""))

    @Test
    fun `the payload is decision 4`() {
        assertEquals("v1|true|active|1800000000000|1700000000000", signed.signedPayload)
    }

    @Test
    fun `a payload signed with the test key verifies`() {
        assertTrue(verifier.isValid(signed))
    }

    @Test
    fun `changing any signed field fails`() {
        assertFalse(verifier.isValid(signed.copy(proActive = false)))
        assertFalse(verifier.isValid(signed.copy(state = SubState.GRACE)))
        assertFalse(verifier.isValid(signed.copy(expiresAtMs = signed.expiresAtMs + 1)))
        assertFalse(verifier.isValid(signed.copy(issuedAtMs = signed.issuedAtMs - 1)))
    }

    @Test
    fun `a signature from another key, garbage or an empty signature fails`() {
        assertFalse(verifier.isValid(sign(testKeyPair(), signed)))
        assertFalse(verifier.isValid(signed.copy(signature = "not base64 !!")))
        assertFalse(verifier.isValid(signed.copy(signature = "")))
    }

    @Test
    fun `an empty key is accepted in debug builds only`() {
        assertTrue(EntitlementSignature("", debug = true).isValid(signed.copy(signature = "")))
        assertFalse(EntitlementSignature("", debug = false).isValid(signed))
    }

    @Test
    fun `a malformed key rejects everything without throwing`() {
        assertFalse(EntitlementSignature("AAAA", debug = true).isValid(signed))
    }

    companion object {
        fun testKeyPair(): KeyPair =
            KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

        fun publicKeyBase64(keys: KeyPair): String = Base64.getEncoder().encodeToString(keys.public.encoded)

        fun sign(keys: KeyPair, e: Entitlement): Entitlement {
            val sig = Signature.getInstance("SHA256withECDSA").apply {
                initSign(keys.private)
                update(e.signedPayload.toByteArray(Charsets.UTF_8))
            }.sign()
            return e.copy(signature = Base64.getEncoder().encodeToString(sig))
        }
    }
}
