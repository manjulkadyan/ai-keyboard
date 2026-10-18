/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/** Registers the AI Keyboard custom lint checks. Loaded via META-INF/services and the jar manifest. */
class AiKeyboardIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(
        ProcessTerminationDetector.ISSUE,
    )

    override val api: Int = CURRENT_API

    override val minApi: Int = 14

    override val vendor: Vendor = Vendor(
        vendorName = "AI Keyboard",
        identifier = "com.manjul.aikeyboard:lint-rules",
    )
}
