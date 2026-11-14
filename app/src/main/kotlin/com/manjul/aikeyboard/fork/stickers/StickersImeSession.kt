/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import android.content.Context
import android.content.Intent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.net.toUri
import com.manjul.aikeyboard.app.FlorisAppActivity
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.ime.stickersui.StickerPanelController
import com.manjul.aikeyboard.ime.stickersui.TapResult
import com.manjul.aikeyboard.core.content.stickers.StickerUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The sticker panel of the running IME (S5 Task 6, PRD FR-23). The STICKERS chip calls [open]. The controller
 * re-checks the field decision on open and on each tap: a sensitive field or a field without cloud access gets no
 * catalog request and no insert (PC-1). Any policy restriction closes the panel and goes back to the keyboard.
 * Nothing here opens the paywall by itself: only the "See Pro" button of the premium card does.
 */
class StickersImeSession(
    private val context: Context,
    private val graph: StickersAppGraph,
    private val decision: () -> PolicyDecision,
    isPro: () -> Boolean,
    private val scope: CoroutineScope,
    private val editor: () -> EditorInfo?,
    private val connection: () -> InputConnection?,
    private val showPanel: (Boolean) -> Unit,
) {
    val controller = StickerPanelController(graph.repository, isPro, decision, scope)

    private val inserter = StickerInserter(context, graph.shareCache, isPro, graph.analytics)

    fun bindTo(policy: ImePolicyController) {
        policy.addRestrictionListener { onDecisionChanged() }
    }

    /** Deletes the staged share files older than 24 h. Runs at IME start and at each panel open. */
    fun sweep() {
        scope.launch(Dispatchers.IO) { runCatching { graph.shareCache.sweep() } }
    }

    fun open() {
        sweep()
        controller.onOpen()
        if (controller.state.value.open) showPanel(true)
    }

    fun onStickerTapped(sticker: StickerUi) {
        when (val result = controller.onStickerTapped(sticker)) {
            is TapResult.Insert -> {
                val info = editor() ?: return
                val ic = connection()
                scope.launch {
                    try {
                        inserter.insert(result.sticker, info, ic, ::allowed)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // a failed insert leaves the keyboard as it was
                    }
                }
            }
            TapResult.Blocked -> close()
            is TapResult.NeedsPro -> Unit // the panel shows the premium card; the user picks "See Pro"
        }
    }

    fun seePro() {
        controller.onDismissPremiumCard()
        context.startActivity(paywallIntent(context))
    }

    fun close() {
        if (!controller.state.value.open) return
        controller.close()
        showPanel(false)
    }

    /** The field decision still lets a sticker go out (PC-1). Checked again after the file copy. */
    private fun allowed(): Boolean = decision().let { it.allowCloud && !it.sensitive }

    private fun onDecisionChanged() {
        controller.onDecisionChanged()
        if (!controller.state.value.open) showPanel(false)
    }

    companion object {
        /** `ui://florisboard/paywall?trigger=premium_sticker`, the same route as `paywallPath(PREMIUM_STICKER)`. */
        const val PAYWALL_DEEPLINK = "ui://florisboard/" + StickerPanelController.PREMIUM_STICKER_ROUTE

        fun paywallIntent(context: Context): Intent =
            Intent(Intent.ACTION_VIEW, PAYWALL_DEEPLINK.toUri(), context, FlorisAppActivity::class.java)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
    }
}
