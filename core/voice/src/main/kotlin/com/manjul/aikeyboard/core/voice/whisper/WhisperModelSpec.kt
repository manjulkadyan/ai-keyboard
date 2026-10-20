/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.whisper

/**
 * Downloadable Whisper models: multilingual whisper.cpp ggml files, Q5_1 quantised.
 *
 * Sources (ggml-org/whisper.cpp, master, checked 2026-10-19):
 * - file names: `models/download-ggml-model.sh` (`base-q5_1`, `small-q5_1`).
 * - [bytes]: `examples/whisper.wasm/index-tmpl.html` ("base (Q5_1, 57 MB)", "small (Q5_1, 182 MB)"). These are
 *   rounded, so they are used for display, the free-space check and the download size cap only, never to
 *   verify a file.
 * - [sha256]: whisper.cpp publishes no SHA-256 for these files (`models/README.md` lists SHA-1 for the
 *   unquantised models only). Each is empty until the hosted file is pinned, and an empty hash fails every
 *   download closed (see [WhisperModelManager]).
 *
 * [minRamBytes] is the total device RAM needed before the model is offered: 5.5 GiB for SMALL (ruling R31).
 */
enum class WhisperModelSpec(val fileName: String, val bytes: Long, val sha256: String, val minRamBytes: Long) {
    // TODO(OD-01): pin the SHA-256 of the file served from WHISPER_MODEL_BASE_URL.
    BASE("ggml-base-q5_1.bin", 57_000_000L, "", 0L),

    // TODO(OD-01): pin the SHA-256 of the file served from WHISPER_MODEL_BASE_URL.
    SMALL("ggml-small-q5_1.bin", 182_000_000L, "", 11L shl 29),
}
