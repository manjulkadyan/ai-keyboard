/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.firebase

import com.manjul.aikeyboard.core.ai.AuthHeaders
import com.manjul.aikeyboard.core.ai.TokenSource
import com.manjul.aikeyboard.core.ai.TokenState

/**
 * Local development auth (Sprint 3 decision 6): sends `X-Dev-Uid` instead of App Check and an ID token. The
 * backend accepts it only with `AUTH_MODE=dev` and refuses dev mode on Cloud Run. `:app` uses this only in a
 * debug build with `-Paikb.ai.devAuth=true`. [uid] is a fixed developer value, not a device or install id.
 */
class DevTokenSource(private val uid: String = DEFAULT_UID) : TokenSource {
    init {
        require(VALID_UID.matches(uid)) { "Dev uid must match ${VALID_UID.pattern}" }
    }

    override suspend fun headers(): TokenState = TokenState.Ready(mapOf(AuthHeaders.DEV_UID to uid))

    override fun prefetch() = Unit

    companion object {
        const val DEFAULT_UID = "dev-local"

        /** The backend's `DevAuth` uid pattern. */
        val VALID_UID = Regex("[A-Za-z0-9_\\-]{1,128}")
    }
}
