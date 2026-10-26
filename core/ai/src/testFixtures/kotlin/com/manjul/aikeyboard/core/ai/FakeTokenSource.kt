/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

/** A [TokenSource] that answers [state]; counts calls so tests can check nothing was fetched. */
class FakeTokenSource(
    @Volatile var state: TokenState = TokenState.Ready(
        mapOf(AuthHeaders.APP_CHECK to "test-app-check", AuthHeaders.AUTHORIZATION to "Bearer test-id-token"),
    ),
) : TokenSource {
    @Volatile var headerCalls: Int = 0
        private set

    @Volatile var prefetchCalls: Int = 0
        private set

    override suspend fun headers(): TokenState {
        headerCalls++
        return state
    }

    override fun prefetch() {
        prefetchCalls++
    }
}
