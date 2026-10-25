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

    /** Text length in chars (translations); the length itself is never sent. */
    fun chars(n: Int): String = when {
        n <= 0 -> "0"
        n <= 20 -> "1-20"
        n <= 50 -> "21-50"
        n <= 200 -> "51-200"
        n <= 1000 -> "201-1000"
        else -> "1001+"
    }

    /** Download size in decimal megabytes. */
    fun megabytes(bytes: Long): String = when {
        bytes < 50_000_000 -> "0-50"
        bytes < 100_000_000 -> "50-100"
        bytes < 250_000_000 -> "100-250"
        bytes < 500_000_000 -> "250-500"
        else -> "500+"
    }
}
