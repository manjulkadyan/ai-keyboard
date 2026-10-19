/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

/** Closed value sets for catalog params: only these strings can reach an event (PRD §12). */
enum class OnboardingSource(val value: String) { LAUNCHER("launcher"), PROCESS_TEXT("process_text"), DEEPLINK("deeplink") }

enum class OnboardingStep(val value: String) { ONE("1"), TWO("2"), THREE("3"), TRY("try") }

enum class VoiceEngine(val value: String) {
    SYSTEM_ON_DEVICE("system_on_device"), SYSTEM_DEFAULT("system_default"), WHISPER("whisper")
}

enum class EndReason(val value: String) {
    USER("user"), SILENCE("silence"), ERROR("error"), CAP("cap"), FIELD_CHANGE("field_change")
}

enum class UnavailableReason(val value: String) {
    NO_SERVICE("no_service"), LANG_UNSUPPORTED("lang_unsupported"), OFFLINE_NO_MODEL("offline_no_model")
}

/** Kind of downloadable pack; only [WHISPER] is downloaded so far. */
enum class PackKind(val value: String) {
    TRANSLATE("translate"), DICT("dict"), TRANSLIT("translit"), WHISPER("whisper")
}

enum class PackResult(val value: String) {
    OK("ok"), FAILED("failed"), HASH_MISMATCH("hash_mismatch"), CANCELLED("cancelled")
}
