/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

object Buckets {
    fun seconds(s: Long): String = when {
        s < 5 -> "0-5"
        s < 15 -> "5-15"
        s < 30 -> "15-30"
        s < 60 -> "30-60"
        s < 300 -> "60-300"
        else -> "300+"
    }

    fun duration(ms: Long): String = seconds(ms / 1000)

    fun words(n: Int): String = when {
        n <= 0 -> "0"
        n <= 5 -> "1-5"
        n <= 20 -> "6-20"
        n <= 50 -> "21-50"
        else -> "51+"
    }
}
