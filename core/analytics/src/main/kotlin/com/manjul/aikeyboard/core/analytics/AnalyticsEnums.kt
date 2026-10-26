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

/** Kind of downloadable pack; [WHISPER] and [TRANSLATE] (ML Kit models) are downloaded so far. */
enum class PackKind(val value: String) {
    TRANSLATE("translate"), DICT("dict"), TRANSLIT("translit"), WHISPER("whisper")
}

enum class PackResult(val value: String) {
    OK("ok"), FAILED("failed"), HASH_MISMATCH("hash_mismatch"), CANCELLED("cancelled")
}

/** Where a translation happened; conversation and camera arrive in S5. */
enum class TranslateSurface(val value: String) {
    BAR("bar"), TRANSLATOR("translator"), PROCESS_TEXT("process_text")
}

enum class TranslateEngineParam(val value: String) { ON_DEVICE("on_device"), CLOUD("cloud") }

/** PRD §12 reasons plus `other` for engine-internal errors and unsupported pairs (plan decision 9). */
enum class TranslateErrorReason(val value: String) {
    NO_PACK("no_pack"), TIMEOUT("timeout"), NET("net"), QUOTA("quota"), OTHER("other")
}

enum class ProcessTextAction(val value: String) { PROCESS_TEXT("process_text"), SEND("send") }

/** Which engine served an AI call (`ai_request{engine}`); only [CLOUD] exists until OD-20 (plan decision 9). */
enum class AiEngineParam(val value: String) { CLOUD("cloud"), ON_DEVICE("on_device") }

/**
 * The rewrite tone (`ai_rewrite_done`, `ai_output_reported`). The first seven match the kb-api rewrite modes;
 * [POLISH] is for `ai_output_reported` on Polish output, which has no tone.
 */
enum class AiTone(val value: String) {
    FIX("fix"), FORMAL("formal"), FRIENDLY("friendly"), SHORTER("shorter"), LONGER("longer"),
    EMOJIFY("emojify"), TRANSLATE("translate"), POLISH("polish"),
}

/** How a rewrite was applied (PRD §12 `ai_rewrite_done.action`: replace/insert). */
enum class AiAction(val value: String) { REPLACE("replace"), INSERT("insert") }

enum class PolishMode(val value: String) { CLEAN("clean"), CLEAN_TRANSLATE("clean_translate") }

enum class AiFeature(val value: String) { REWRITE("rewrite"), POLISH("polish") }

/** `ai_error{reason}`: the client error classes of TRD §4.4.5 (quota has its own event, `ai_quota_hit`). */
enum class AiErrorReason(val value: String) {
    OFFLINE("offline"), TIMEOUT("timeout"), REFUSED("refused"), PAUSED("paused"), TOO_LONG("too_long"),
    UNAVAILABLE("unavailable"), RATE_LIMITED("rate_limited"), TEXT_CHANGED("text_changed"),
}

/** PRD §10.6 consent classes that have an Agree screen this sprint. */
enum class ConsentKind(val value: String) { AI("ai"), AUTO_POLISH("auto_polish") }
