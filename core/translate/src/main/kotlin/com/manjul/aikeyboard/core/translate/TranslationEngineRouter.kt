/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

/** The router's engine choice for a pair. */
sealed interface Route {
    data class Use(val engine: EngineId) : Route

    /** On-device would work after downloading packs; the UI offers the download. Never a silent cloud call. */
    data class NeedsDownload(val pair: LangPair) : Route

    /** Only the cloud engine supports the pair and the user is not Pro or has not opted in. */
    data object CloudOnlyNeedsPro : Route

    /** The field policy refuses translation (or refuses the cloud egress the pair would need). */
    data object Refused : Route

    data object Unsupported : Route
}

/**
 * Picks an engine and runs a translation (TRD §4.3.1, PRD FR-11 AC3). Request text is user content: the router
 * never logs it and only passes it to the [resolver], the [cache] and the chosen engine.
 *
 * Rules, in order:
 * 1. `!allowTranslate` refuses without touching the detector, the cache or any engine (PC-1).
 * 2. On-device unsupported: cloud if [RouterContext.cloudPermitted] and cloud supports the pair; if only the
 *    field forbids cloud, [Route.Refused]; if cloud could but the user is not Pro / opted in,
 *    [Route.CloudOnlyNeedsPro]; else [Route.Unsupported].
 * 3. Cloud when permitted, supported and the user prefers quality.
 * 4. On device when its models are present; [Route.NeedsDownload] when they are not. There is no automatic
 *    fallback to cloud: a missing pack is surfaced, never sent off the device.
 */
class TranslationEngineRouter(
    private val onDevice: TranslationEngine,
    private val cloud: TranslationEngine,
    private val resolver: SourceResolver,
    private val cache: TranslationCache,
    private val chunker: SentenceChunker = SentenceChunker,
) {
    suspend fun route(pair: LangPair, ctx: RouterContext): Route {
        if (!ctx.allowTranslate) return Route.Refused
        val local = onDevice.supports(pair)
        val remote = cloud.supports(pair)
        val cloudCan = remote == Support.CLOUD_ONLY || remote == Support.ON_DEVICE
        if (local == Support.NONE || local == Support.CLOUD_ONLY) {
            return when {
                !cloudCan -> Route.Unsupported
                ctx.cloudPermitted -> Route.Use(cloud.id)
                ctx.pro && ctx.cloudOptIn -> Route.Refused
                else -> Route.CloudOnlyNeedsPro
            }
        }
        if (ctx.preferQuality && ctx.cloudPermitted && cloudCan) return Route.Use(cloud.id)
        return when (local) {
            Support.ON_DEVICE -> Route.Use(onDevice.id)
            else -> Route.NeedsDownload(pair)
        }
    }

    /**
     * Translates [req]. Detects the source when [TranslateRequest.source] is `null` (keeping [lastSource] when
     * unsure), returns the text unchanged when source equals target, serves repeats from the cache, and sends
     * long text sentence by sentence, joining the results with the original separators.
     */
    suspend fun translate(req: TranslateRequest, ctx: RouterContext, lastSource: Lang): TranslateResult {
        if (!ctx.allowTranslate) return TranslateResult.Failed(FailKind.POLICY)
        val source = resolver.resolve(req.text, req.source, lastSource)
        if (req.text.isBlank() || source == req.target) {
            return TranslateResult.Ok(req.text, source, onDevice.id)
        }
        val pair = LangPair(source, req.target)
        val engine = when (val route = route(pair, ctx)) {
            is Route.Use -> if (route.engine == cloud.id) cloud else onDevice
            is Route.NeedsDownload -> return TranslateResult.Failed(FailKind.NO_PACK)
            Route.CloudOnlyNeedsPro -> return TranslateResult.Failed(FailKind.CLOUD_UNAVAILABLE)
            Route.Refused -> return TranslateResult.Failed(FailKind.POLICY)
            Route.Unsupported -> return TranslateResult.Failed(FailKind.UNSUPPORTED)
        }
        val key = CacheKey(engine.id, source, req.target, req.text)
        cache.get(key)?.let { return TranslateResult.Ok(it, source, engine.id) }

        val chunks = chunker.split(req.text)
        val translated = ArrayList<String>(chunks.size)
        for (chunk in chunks) {
            if (chunk.body.isBlank()) {
                translated += chunk.body
                continue
            }
            when (val r = engine.translate(TranslateRequest(chunk.body, source, req.target, req.origin))) {
                is TranslateResult.Ok -> translated += r.text
                is TranslateResult.Failed -> return r
            }
        }
        val joined = chunker.join(chunks, translated)
        cache.put(key, joined)
        return TranslateResult.Ok(joined, source, engine.id)
    }
}
