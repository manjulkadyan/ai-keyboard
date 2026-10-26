/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

import kotlinx.coroutines.flow.Flow

/** The kb-api engine: every request goes through [AiClient]. */
class CloudAiEngine(private val client: AiClient) : AiEngine {
    override val name: String = NAME

    override fun stream(req: AiRequest): Flow<AiEvent> = client.stream(req)

    companion object {
        const val NAME = "cloud"
    }
}
