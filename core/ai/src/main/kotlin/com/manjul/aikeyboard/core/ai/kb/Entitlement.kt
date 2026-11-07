/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai.kb

/** kb-api subscription states, with the wire strings of `/v1/entitlement` (S4 Task 2 and Task 3). */
enum class SubState(val wire: String) {
    NONE("none"), PENDING("pending"), ACTIVE("active"), GRACE("grace"), ON_HOLD("hold"), PAUSED("paused"),
    EXPIRED("expired"), REVOKED("revoked"),
    ;

    companion object {
        fun fromWire(wire: String): SubState? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * kb-api's signed entitlement verdict (ARCH-006: the server decides, the device only caches). Times are epoch ms.
 * [signature] is base64 ECDSA P-256 over [signedPayload]. [toString] leaves the signature out.
 */
data class Entitlement(
    val proActive: Boolean,
    val state: SubState,
    val expiresAtMs: Long,
    val issuedAtMs: Long,
    val signature: String,
) {
    /** The bytes kb-api signs (plan decision 4): `v1|<pro_active>|<state>|<expires_at_ms>|<issued_at_ms>`. */
    val signedPayload: String get() = "v1|$proActive|${state.wire}|$expiresAtMs|$issuedAtMs"

    override fun toString(): String =
        "Entitlement(proActive=$proActive, state=$state, expiresAtMs=$expiresAtMs, issuedAtMs=$issuedAtMs)"
}

enum class EntitlementError { INVALID_TOKEN, OFFLINE, UNAVAILABLE, NOT_CONFIGURED }

/**
 * Result of an [EntitlementClient] call. `:core:ai` is a pure JVM module and cannot use the Android `Outcome` of
 * `:core:common`, so the client has its own small result type.
 */
sealed interface EntitlementResult {
    data class Ok(val entitlement: Entitlement) : EntitlementResult

    data class Err(val error: EntitlementError) : EntitlementResult
}
