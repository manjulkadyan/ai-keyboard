/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ai

/** What an AI stream emits. Text fields are user content: [toString] never prints them. */
sealed interface AiEvent {
    /** The next piece of streamed text. */
    data class Delta(val text: String) : AiEvent {
        override fun toString(): String = "Delta(chars=${text.length})"
    }

    /**
     * The stream finished. [quotaLeft] is the free actions left today (-1 when the server did not say);
     * [trimmed] means the output hit the token cap. Polish fills [polished], [translation] and
     * [sourceLangDetected]; a rewrite leaves them null.
     */
    data class Done(
        val quotaLeft: Int,
        val trimmed: Boolean,
        val polished: String?,
        val translation: String?,
        val sourceLangDetected: String?,
    ) : AiEvent {
        override fun toString(): String =
            "Done(quotaLeft=$quotaLeft, trimmed=$trimmed, polished=${polished?.length}, " +
                "translation=${translation?.length}, source=$sourceLangDetected)"
    }

    /** No text yet after `ai_still_working_ms`; emitted at most once per stream, before the first delta. */
    data object StillWorking : AiEvent
}
