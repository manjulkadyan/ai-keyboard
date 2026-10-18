/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import android.content.ClipData
import android.content.ClipDescription
import com.manjul.aikeyboard.FlorisImeService
import com.manjul.aikeyboard.core.privacy.ClipSensitivityClassifier
import com.manjul.aikeyboard.ime.policy.PolicyDecision
import org.florisboard.lib.android.AndroidVersion

/**
 * Decides whether a clip may enter the clipboard history. Fails closed: with no [PolicyDecision] (no live IME
 * service) nothing is captured, because the clipboard manager is app-scoped and can fire without an IME.
 */
class ClipboardCaptureGate(
    private val decisionProvider: () -> PolicyDecision? = {
        FlorisImeService.policyControllerOrNull()?.decision?.value
    },
) {
    fun allowsHistory(): Boolean = decisionProvider()?.allowClipboardHistory == true

    fun isSensitive(clip: ClipData): Boolean {
        val text = clip.getItemAt(0)?.text?.toString()
        return ClipSensitivityClassifier.isSensitive(text, isFlaggedSensitive(clip.description))
    }

    fun isSensitive(text: String?, flagged: Boolean): Boolean = ClipSensitivityClassifier.isSensitive(text, flagged)

    /** True when history capture is allowed by policy and the clip is not sensitive. */
    fun mayCapture(clip: ClipData): Boolean = allowsHistory() && !isSensitive(clip)

    companion object {
        /** Value of `ClipDescription.EXTRA_IS_SENSITIVE`, which exists from API 33. */
        const val EXTRA_IS_SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"

        fun isFlaggedSensitive(description: ClipDescription?): Boolean {
            val extras = description?.extras ?: return false
            if (AndroidVersion.ATLEAST_API33_T && extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE)) return true
            return extras.getBoolean(EXTRA_IS_SENSITIVE_KEY)
        }

        fun expiryCutoffMs(nowMs: Long, afterMinutes: Int): Long = nowMs - afterMinutes * 60_000L
    }
}
