/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

/** Param values are enums, buckets, language tags or booleans only; never user content (PRD §12). */
data class AnalyticsEvent(val name: String, val params: Map<String, Any>)
