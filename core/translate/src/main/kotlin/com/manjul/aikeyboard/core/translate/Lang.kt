/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

import java.util.Locale

/**
 * A translation language, keyed by its lower-case primary BCP-47 subtag (`"ur"`, `"hi"`, `"en"`). Region and
 * script are dropped: `Lang.of("ur-PK") == Lang("ur")`. A tag is metadata, not user content.
 */
@JvmInline
value class Lang(val tag: String) {
    override fun toString(): String = tag

    companion object {
        val English = Lang("en")

        /** Normalises any BCP-47 tag (`"pt-BR"`, `"zh_Hant"`, `"UR"`) to its primary subtag. */
        fun of(tag: String): Lang = Lang(tag.trim().substringBefore('-').substringBefore('_').lowercase(Locale.ROOT))
    }
}

/** Source and target of a translation. Language tags only; never carries text. */
data class LangPair(val source: Lang, val target: Lang) {
    fun swapped(): LangPair = LangPair(target, source)
}

/** Which engine produced a result. [analytics] is the PRD §12 `engine` param value. */
enum class EngineId(val analytics: String) {
    MLKIT("on_device"),
    CLOUD("cloud"),
}

/** How an engine can serve a [LangPair]. */
enum class Support {
    /** Models present, translates offline now. */
    ON_DEVICE,

    /** Supported on device once the language packs are downloaded. */
    NEEDS_DOWNLOAD,

    /** Only a cloud engine supports the pair. */
    CLOUD_ONLY,

    /** No engine supports the pair. */
    NONE,
}

/** The surface a translate request comes from. */
enum class Origin {
    BAR,
    TRANSLATOR,
    PROCESS_TEXT,
    VOICE,
}
