/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.download

import androidx.work.NetworkType
import com.manjul.aikeyboard.core.analytics.PackKind
import com.manjul.aikeyboard.core.content.manifest.PackKindSpec
import com.manjul.aikeyboard.core.content.manifest.PackLicence
import com.manjul.aikeyboard.core.content.manifest.PackSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackDownloadWorkerTest {
    private fun pack(kind: PackKindSpec) =
        PackSpec("p", kind, 1, premium = false, title = "P", files = emptyList(), licence = PackLicence("n", "u", "a"))

    @Test fun `wifi only waits for an unmetered network`() {
        assertEquals(NetworkType.UNMETERED, PackDownloadWorker.constraints(wifiOnly = true).requiredNetworkType)
        assertEquals(NetworkType.CONNECTED, PackDownloadWorker.constraints(wifiOnly = false).requiredNetworkType)
    }

    @Test fun `analytics use en for dictionaries and mul for stickers, with the kind only`() {
        assertEquals("en", PackDownloadWorker.lang(pack(PackKindSpec.DICT)))
        assertEquals(PackKind.DICT, PackDownloadWorker.kind(pack(PackKindSpec.DICT)))
        assertEquals("mul", PackDownloadWorker.lang(pack(PackKindSpec.STICKERS)))
        assertEquals(PackKind.STICKERS, PackDownloadWorker.kind(pack(PackKindSpec.STICKERS)))
    }

    @Test fun `a download makes three attempts at most`() {
        assertEquals(3, PackDownloadWorker.MAX_ATTEMPTS)
        assertEquals(listOf(true, true, false, false), (0..3).map(PackDownloadWorker::shouldRetry))
    }

    @Test fun `every pack download carries the shared tag`() {
        assertTrue(PackDownloadWorker.uniqueName("starter_plus").startsWith(PackDownloadWorker.TAG))
    }
}
