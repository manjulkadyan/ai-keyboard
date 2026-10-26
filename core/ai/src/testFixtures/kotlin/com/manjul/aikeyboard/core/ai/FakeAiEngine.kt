/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * A scripted [AiEngine] for controller tests. Every [stream] call is recorded in [requests] and answered by
 * [script]; [respondWith] and [failWith] cover the common cases, a custom [script] can suspend or emit slowly.
 */
class FakeAiEngine(override val name: String = "cloud") : AiEngine {
    val requests: MutableList<AiRequest> = CopyOnWriteArrayList()

    @Volatile var script: (AiRequest) -> Flow<AiEvent> = { emptyFlow() }

    /** Every request emits [events] in order and completes. */
    fun respondWith(vararg events: AiEvent) {
        script = { flowOf(*events) }
    }

    /** Every request emits [before], then fails with [error]. */
    fun failWith(error: AiError, vararg before: AiEvent) {
        script = {
            flow {
                before.forEach { emit(it) }
                throw AiException(error)
            }
        }
    }

    override fun stream(req: AiRequest): Flow<AiEvent> = flow {
        requests += req
        script(req).collect { emit(it) }
    }
}
