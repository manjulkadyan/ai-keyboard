/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

import kotlinx.coroutines.flow.Flow

/**
 * Something that runs an [AiRequest]. Only [CloudAiEngine] exists for MVP; an on-device Gemini Nano engine
 * (OD-20, Sprint 3 decision 9) would be a second implementation. [name] is the `ai_request{engine}` value.
 */
interface AiEngine {
    /** `"cloud"` or `"nano"`. */
    val name: String

    /** Streams the result; fails with [AiException]. Cancelling the collection cancels the work. */
    fun stream(req: AiRequest): Flow<AiEvent>
}
