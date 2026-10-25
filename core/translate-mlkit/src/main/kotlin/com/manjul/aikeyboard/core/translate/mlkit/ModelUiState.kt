/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate.mlkit

/**
 * A translation model as the Offline languages screen and the bar show it. ML Kit reports no byte progress,
 * so [DOWNLOADING] is shown as an indeterminate indicator with a "Stop" action (RN-06).
 */
enum class ModelUiState { ABSENT, DOWNLOADING, READY, FAILED }
