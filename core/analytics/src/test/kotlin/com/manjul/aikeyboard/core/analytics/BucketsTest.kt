/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class BucketsTest {
    @Test fun seconds() {
        val cases = mapOf(
            0L to "0-5", 4L to "0-5", 5L to "5-15", 14L to "5-15", 15L to "15-30", 29L to "15-30",
            30L to "30-60", 59L to "30-60", 60L to "60-300", 299L to "60-300", 300L to "300+", 9999L to "300+",
        )
        cases.forEach { (s, b) -> assertEquals(b, Buckets.seconds(s), "seconds($s)") }
    }

    @Test fun duration() {
        assertEquals("0-5", Buckets.duration(4_999))
        assertEquals("5-15", Buckets.duration(5_000))
        assertEquals("300+", Buckets.duration(300_000))
    }

    @Test fun words() {
        val cases = mapOf(
            0 to "0", 1 to "1-5", 5 to "1-5", 6 to "6-20", 20 to "6-20", 21 to "21-50", 50 to "21-50", 51 to "51+",
        )
        cases.forEach { (n, b) -> assertEquals(b, Buckets.words(n), "words($n)") }
    }

    @Test fun megabytes() {
        val cases = mapOf(
            -1L to "0-50", 0L to "0-50", 49_999_999L to "0-50", 50_000_000L to "50-100", 99_999_999L to "50-100",
            100_000_000L to "100-250", 250_000_000L to "250-500", 499_999_999L to "250-500", 500_000_000L to "500+",
        )
        cases.forEach { (b, bucket) -> assertEquals(bucket, Buckets.megabytes(b), "megabytes($b)") }
    }

    @Test fun chars() {
        val cases = mapOf(
            -1 to "0", 0 to "0", 1 to "1-20", 20 to "1-20", 21 to "21-50", 50 to "21-50", 51 to "51-200",
            200 to "51-200", 201 to "201-1000", 1000 to "201-1000", 1001 to "1001+", Int.MAX_VALUE to "1001+",
        )
        cases.forEach { (n, b) -> assertEquals(b, Buckets.chars(n), "chars($n)") }
    }
}
