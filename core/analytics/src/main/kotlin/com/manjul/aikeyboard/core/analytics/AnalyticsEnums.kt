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
    USER("user"), SILENCE("silence"), ERROR("error"), CAP("cap"), FIELD_CHANGE("field_change"), HIDDEN("hidden")
}

enum class UnavailableReason(val value: String) {
    NO_SERVICE("no_service"), LANG_UNSUPPORTED("lang_unsupported"), OFFLINE_NO_MODEL("offline_no_model")
}

/** Kind of downloadable pack: [WHISPER], [TRANSLATE] (ML Kit models), and [DICT] and [STICKERS] content packs. */
enum class PackKind(val value: String) {
    TRANSLATE("translate"), DICT("dict"), TRANSLIT("translit"), WHISPER("whisper"), STICKERS("stickers")
}

enum class PackResult(val value: String) {
    OK("ok"), FAILED("failed"), HASH_MISMATCH("hash_mismatch"), CANCELLED("cancelled")
}

/** How a sticker reached the host app (S5 Task 6): the commitContent, share and clipboard chain. */
enum class StickerMethod(val value: String) {
    COMMIT_CONTENT("commit_content"), SHARE("share"), CLIPBOARD("clipboard")
}

/** Where a translation happened. Conversation and camera are out of scope for this app (docs/decisions/od-21). */
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

/** `paywall_view{trigger}` and `paywall_close{trigger}`: the PRD §10.4 trigger ids T1 to T8. */
enum class PaywallTriggerParam(val value: String) {
    ONBOARDING("onboarding"), AI_QUOTA("ai_quota"), PREMIUM_THEME("premium_theme"), REMOVE_ADS("remove_ads"),
    CLOUD_QUALITY("cloud_quality"), PREMIUM_STICKER("premium_sticker"), AUTO_POLISH("auto_polish"),
    WIN_BACK("win_back"),
}

/** The Pro plan (`purchase_start`, `trial_start`, `purchase_success`). */
enum class ProPlanParam(val value: String) { MONTHLY("monthly"), ANNUAL("annual") }

/**
 * `purchase_fail{code}`: the Play `BillingResponseCode` constant name in lower case, plus [INVALID_TOKEN] when kb-api
 * rejects the purchase. Never the Play debug message.
 */
enum class PurchaseFailCode(val value: String) {
    USER_CANCELED("user_canceled"), SERVICE_TIMEOUT("service_timeout"), FEATURE_NOT_SUPPORTED("feature_not_supported"),
    SERVICE_DISCONNECTED("service_disconnected"), SERVICE_UNAVAILABLE("service_unavailable"),
    BILLING_UNAVAILABLE("billing_unavailable"), ITEM_UNAVAILABLE("item_unavailable"),
    DEVELOPER_ERROR("developer_error"), ERROR("error"), ITEM_ALREADY_OWNED("item_already_owned"),
    ITEM_NOT_OWNED("item_not_owned"), NETWORK_ERROR("network_error"), INVALID_TOKEN("invalid_token"),
    ;

    companion object {
        /** [name] is a `BillingResponseCode` constant name; an unknown one is [ERROR]. */
        fun fromResponseCodeName(name: String): PurchaseFailCode = entries.firstOrNull { it.name == name } ?: ERROR
    }
}

/** `subscription_state{state}`: the kb-api `SubState` wire strings. */
enum class SubscriptionStateParam(val value: String) {
    NONE("none"), PENDING("pending"), ACTIVE("active"), GRACE("grace"), ON_HOLD("hold"), PAUSED("paused"),
    EXPIRED("expired"), REVOKED("revoked"),
}

/** The `pro_status` user property. */
enum class ProStatus(val value: String) { FREE("free"), TRIAL("trial"), PRO("pro"), GRACE("grace") }

/** `ad_paid_event{format}`: the ad format of the placement. */
enum class AdFormatParam(val value: String) {
    APP_OPEN("app_open"), INTERSTITIAL("interstitial"), REWARDED("rewarded"), BANNER("banner"), NATIVE("native"),
}

/** `ad_paid_event{precision}`: GMA's `AdValue.PrecisionType`, by name. */
enum class AdPrecisionParam(val value: String) {
    UNKNOWN("unknown"), ESTIMATED("estimated"), PUBLISHER_PROVIDED("publisher_provided"), PRECISE("precise"),
}

/** `update_prompt{type}`: the Play in-app update type. */
enum class UpdateTypeParam(val value: String) { FLEXIBLE("flexible"), IMMEDIATE("immediate") }

/** `update_prompt{result}`: what the user did with the Play update dialog, or [FAILED] when Play could not run it. */
enum class UpdateResultParam(val value: String) { ACCEPTED("accepted"), DECLINED("declined"), FAILED("failed") }
