/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

import android.content.Context
import com.google.mlkit.common.MlKit

/**
 * Initialises ML Kit on first translate, detect or model use (TRD §2.3, §11.1). The merged `MlKitInitProvider`
 * is removed by this module's manifest, so nothing ML Kit runs at process start. Never call this from
 * `Application.onCreate` or `FlorisImeService.onCreate`.
 */
object MlKitBootstrap {
    @Volatile private var initialized = false

    fun ensure(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                MlKit.initialize(context.applicationContext)
            } catch (_: IllegalStateException) {
                // Already initialised (e.g. by another ML Kit entry point); that is the state we want.
            }
            initialized = true
        }
    }
}
