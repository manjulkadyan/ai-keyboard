/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.translate

/**
 * Everything the router needs to pick an engine besides the pair. Flags only, no text.
 *
 * @param allowTranslate the live field decision (`PolicyDecision.allowTranslate`); `false` for password,
 *   no-learning, incognito, locked, private-app and other sensitive fields (PC-1, TRD §4.1.2).
 * @param allowCloud the live field decision for cloud egress (`PolicyDecision.allowCloud`).
 * @param pro the Pro entitlement; hard-wired `false` until S4 billing.
 * @param cloudOptIn the user opted in to cloud translation.
 * @param preferQuality the user prefers cloud quality over on-device for pairs both support.
 */
data class RouterContext(
    val allowTranslate: Boolean,
    val allowCloud: Boolean,
    val pro: Boolean,
    val cloudOptIn: Boolean,
    val preferQuality: Boolean,
) {
    /** Cloud may be used at all: Pro, opted in, and the field allows egress. */
    val cloudPermitted: Boolean get() = pro && cloudOptIn && allowCloud
}
