/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.ads.cap

import androidx.datastore.core.CorruptionException
import com.manjul.aikeyboard.core.ads.placement.AdFormat
import com.manjul.aikeyboard.core.ads.placement.AdPlacementId
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AdsStateSerializerTest {
    private val state = AdsState(
        cap = AdCapState(listOf(AdShowRecord(AdPlacementId("app_open"), AdFormat.APP_OPEN, 42L))),
        session = AdSessionState(
            sessionCount = 3,
            lastBackgroundAtMs = 7,
            featureUsesSinceInterstitial = 2,
            lastInterstitialAtMs = 5,
            lastAppOpenAtMs = 42,
        ),
    )

    @Test
    fun `round trip keeps every field`() = runTest {
        val out = ByteArrayOutputStream()
        AdsStateSerializer.writeTo(state, out)
        assertEquals(state, AdsStateSerializer.readFrom(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun `persisted names follow the TRD`() = runTest {
        val out = ByteArrayOutputStream()
        AdsStateSerializer.writeTo(state, out)
        val json = out.toString(Charsets.UTF_8)
        for (key in listOf("last_interstitial_at", "last_app_open_at", "session_count", "shows")) {
            assert(key in json) { key }
        }
    }

    @Test
    fun `an unknown placement or format is dropped`() = runTest {
        val json = """{"shows":[{"placement":"app_open","format":"APP_OPEN","at":1},""" +
            """{"placement":"Bad Id","format":"APP_OPEN","at":2},{"placement":"app_open","format":"POPUP","at":3}]}"""
        val read = AdsStateSerializer.readFrom(ByteArrayInputStream(json.toByteArray()))
        assertEquals(listOf(AdShowRecord(AdPlacementId("app_open"), AdFormat.APP_OPEN, 1)), read.cap.records)
    }

    @Test
    fun `invalid json is a corruption without the content`() = runTest {
        val e = runCatching { AdsStateSerializer.readFrom(ByteArrayInputStream("{secret".toByteArray())) }
            .exceptionOrNull()
        assertIs<CorruptionException>(e)
        assertEquals("ads_state.json is not valid", e.message)
    }
}
