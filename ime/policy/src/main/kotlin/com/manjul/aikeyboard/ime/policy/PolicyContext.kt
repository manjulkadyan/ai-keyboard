/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.policy

/** Non-field inputs to the policy: device state, the user's incognito toggle and package lists. */
data class PolicyContext(
    val deviceLocked: Boolean,
    val incognitoManual: Boolean,
    val privatePackages: Set<String>,
    val forceSensitivePackages: Set<String>,
)
