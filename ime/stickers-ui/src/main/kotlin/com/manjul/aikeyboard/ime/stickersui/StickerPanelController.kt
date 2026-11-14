/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.stickersui

import com.manjul.aikeyboard.core.content.stickers.StickerCatalog
import com.manjul.aikeyboard.core.content.stickers.StickerPackUi
import com.manjul.aikeyboard.core.content.stickers.StickerUi
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.core.common.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the sticker panel shows. [premiumCard] is the "See Pro" card for a premium pack or sticker. */
data class StickerPanelState(
    val open: Boolean = false,
    val packs: List<StickerPackUi> = emptyList(),
    val selectedPackId: String? = null,
    val premiumCard: Boolean = false,
    val installing: String? = null,
    val installFailed: Boolean = false,
) {
    val selected: StickerPackUi? get() = packs.firstOrNull { it.id == selectedPackId } ?: packs.firstOrNull()
}

/** The answer to a sticker tap. Only [Insert] may reach the host app. */
sealed interface TapResult {
    data class Insert(val sticker: StickerUi) : TapResult

    /** A premium sticker without Pro. [route] opens the paywall; the panel only shows the card, never the route. */
    data class NeedsPro(val route: String) : TapResult

    /** The field does not allow stickers (a sensitive field, or no cloud): nothing is sent. */
    data object Blocked : TapResult
}

/**
 * The sticker panel's logic (S5 Task 6, PRD FR-23). Stickers need [PolicyDecision.allowCloud] (they can come from
 * the content CDN and they go to another app), so [decision] is checked again on [onOpen], on every tap and on
 * [onDecisionChanged]. In a field that does not allow them the panel closes, nothing is fetched (PC-1, FR-23 AC3)
 * and a tap is [TapResult.Blocked]. [isPro] is read at each tap and pack selection, so a Pro change applies at
 * once. Nothing here opens the paywall by itself: a premium tap shows the card, and only "See Pro" opens it.
 */
class StickerPanelController(
    private val repo: StickerCatalog,
    private val isPro: () -> Boolean,
    private val decision: () -> PolicyDecision,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(StickerPanelState())
    val state: StateFlow<StickerPanelState> = mutableState.asStateFlow()

    private var packsJob: Job? = null

    private fun allowed(): Boolean = decision().let { it.allowCloud && !it.sensitive }

    fun onOpen() {
        if (!allowed()) {
            close()
            return
        }
        mutableState.update { it.copy(open = true, premiumCard = false, installFailed = false) }
        if (packsJob?.isActive != true) {
            packsJob = scope.launch {
                repo.packs.collect { packs ->
                    mutableState.update { s ->
                        val selected = s.selectedPackId?.takeIf { id -> packs.any { it.id == id } }
                        s.copy(packs = packs, selectedPackId = selected ?: packs.firstOrNull()?.id)
                    }
                }
            }
        }
        scope.launch { repo.refresh() }
    }

    /** The policy changed while the panel is open: a field that does not allow stickers closes it. */
    fun onDecisionChanged() {
        if (mutableState.value.open && !allowed()) close()
    }

    fun onPackSelected(id: String) {
        if (!allowed()) {
            close()
            return
        }
        val pack = mutableState.value.packs.firstOrNull { it.id == id } ?: return
        val locked = pack.premium && !isPro()
        mutableState.update { it.copy(selectedPackId = id, premiumCard = locked && !pack.installed) }
        if (!pack.installed && !locked && mutableState.value.installing == null) install(id)
    }

    fun onStickerTapped(sticker: StickerUi): TapResult {
        if (!allowed()) {
            close()
            return TapResult.Blocked
        }
        if (sticker.premium && !isPro()) {
            mutableState.update { it.copy(premiumCard = true) }
            return TapResult.NeedsPro(PREMIUM_STICKER_ROUTE)
        }
        mutableState.update { it.copy(premiumCard = false) }
        return TapResult.Insert(sticker)
    }

    fun onDismissPremiumCard() = mutableState.update { it.copy(premiumCard = false) }

    fun close() {
        packsJob?.cancel()
        packsJob = null
        mutableState.update { it.copy(open = false, premiumCard = false, installing = null) }
    }

    private fun install(id: String) {
        mutableState.update { it.copy(installing = id, installFailed = false) }
        scope.launch {
            val ok = repo.install(id) is Outcome.Success
            mutableState.update { it.copy(installing = null, installFailed = !ok) }
        }
    }

    companion object {
        /** `RouteRegistry.paywall(PaywallTrigger.PREMIUM_STICKER)` in `:app`; a test there keeps the two equal. */
        const val PREMIUM_STICKER_ROUTE = "paywall?trigger=premium_sticker"
    }
}
