/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.policy

import com.manjul.aikeyboard.ime.policy.PolicyDecision

/** How a [PolicyDecision] narrows the editor state that upstream derives from preferences and the field. */
object PolicyStateDerivation {
    fun composingEnabled(upstream: Boolean, decision: PolicyDecision): Boolean =
        upstream && decision.allowSuggestionStrip

    fun incognito(upstream: Boolean, decision: PolicyDecision): Boolean =
        upstream || !decision.allowLearning
}
