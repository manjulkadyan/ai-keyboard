/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CloudTranslationEngineTest {
    private val engine = CloudTranslationEngine()

    @Test fun pashtoIsCloudOnlyAndOthersUnsupported() = runTest {
        assertEquals(Support.CLOUD_ONLY, engine.supports(LangPair(Lang("en"), Lang("ps"))))
        assertEquals(Support.NONE, engine.supports(LangPair(Lang("en"), Lang("ur"))))
    }

    @Test fun everyTranslationFailsAsCloudUnavailable() = runTest {
        val result = engine.translate(TranslateRequest("hello", Lang("en"), Lang("ps"), Origin.BAR))
        assertEquals(TranslateResult.Failed(FailKind.CLOUD_UNAVAILABLE), result)
        assertEquals(EngineId.CLOUD, engine.id)
    }

    @Test fun requestAndResultToStringHaveNoText() {
        val req = TranslateRequest("secret", Lang("en"), Lang("es"), Origin.BAR).toString()
        val ok = TranslateResult.Ok("secret", Lang("en"), EngineId.MLKIT).toString()
        assertEquals(false, "secret" in req || "secret" in ok)
    }
}
