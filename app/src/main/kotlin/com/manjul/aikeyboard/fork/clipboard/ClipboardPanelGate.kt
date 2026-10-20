/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.clipboard

import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.ime.ImeUiMode
import com.manjul.aikeyboard.ime.policy.PolicyDecision

/**
 * Keeps the clipboard history panel closed where the policy denies clipboard history (password fields, private
 * apps, locked device). Every route into the panel (smartbar chip, Enter long-press popup, swipe action) ends in
 * the `IME_UI_MODE_CLIPBOARD` key handler, which asks [onClipboardKey]; an open panel closes as soon as a more
 * restrictive decision arrives ([closeOnRestriction]) and again in onStartInputView ([onStartInputView]).
 */
object ClipboardPanelGate {
    /** The UI mode after the clipboard key: the panel opens only if the decision allows clipboard history. */
    fun onClipboardKey(mode: ImeUiMode, decision: PolicyDecision): ImeUiMode =
        if (decision.allowClipboardHistory) ImeUiMode.CLIPBOARD else mode

    /**
     * The UI mode after the policy controller reported [decision]. "No field" (between onFinishInput and the next
     * onStartInput) leaves the panel to upstream's "hide on next text field" rule; the next field's decision
     * closes it if needed.
     */
    fun afterRestriction(mode: ImeUiMode, decision: PolicyDecision): ImeUiMode = when {
        decision == ImePolicyController.NoField -> mode
        mode == ImeUiMode.CLIPBOARD && !decision.allowClipboardHistory -> ImeUiMode.TEXT
        else -> mode
    }

    /** Upstream's onStartInputView rule (keep only an open clipboard panel, if the pref says so) plus the policy. */
    fun onStartInputView(mode: ImeUiMode, hideOnNextTextField: Boolean, decision: PolicyDecision): ImeUiMode =
        if (mode != ImeUiMode.CLIPBOARD || hideOnNextTextField || !decision.allowClipboardHistory) {
            ImeUiMode.TEXT
        } else {
            mode
        }

    /** Closes the panel on every restriction [policy] reports, including restarts and late settings (R7). */
    fun closeOnRestriction(policy: ImePolicyController, mode: () -> ImeUiMode, setMode: (ImeUiMode) -> Unit) {
        policy.addRestrictionListener { decision ->
            val current = mode()
            val next = afterRestriction(current, decision)
            if (next != current) setMode(next)
        }
    }
}
