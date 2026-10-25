/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import com.google.mlkit.common.MlKitException
import com.manjul.aikeyboard.core.translate.FailKind
import com.manjul.aikeyboard.core.translate.TranslateResult
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ExecutionException
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// Robolectric: MlKitException's constructor calls android.text.TextUtils.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MlKitErrorsTest {
    private fun mlKit(code: Int) = MlKitException("secret text", code)

    @Test fun mlKitCodesMapToFailKinds() {
        val expected = mapOf(
            MlKitException.NOT_ENOUGH_SPACE to FailKind.NO_PACK,
            MlKitException.UNAVAILABLE to FailKind.NO_PACK,
            MlKitException.NOT_FOUND to FailKind.NO_PACK,
            MlKitException.NETWORK_ISSUE to FailKind.OFFLINE,
            MlKitException.DEADLINE_EXCEEDED to FailKind.TIMEOUT,
            MlKitException.INTERNAL to FailKind.ENGINE,
            MlKitException.UNKNOWN to FailKind.ENGINE,
        )
        for ((code, kind) in expected) assertEquals(kind, MlKitErrors.kind(mlKit(code)), "code $code")
    }

    @Test fun wrappedMlKitExceptionIsFound() {
        assertEquals(FailKind.OFFLINE, MlKitErrors.kind(ExecutionException(mlKit(MlKitException.NETWORK_ISSUE))))
    }

    @Test fun otherExceptionsAreEngineErrorsAndTheMessageIsDropped() {
        val kind = MlKitErrors.kind(RuntimeException("secret text"))
        assertEquals(FailKind.ENGINE, kind)
        assertFalse("secret" in TranslateResult.Failed(kind).toString())
    }
}
