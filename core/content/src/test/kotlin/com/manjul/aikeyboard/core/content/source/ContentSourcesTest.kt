/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.content.source

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class ContentSourcesTest {
    private val assets = object : ContentSource {
        override suspend fun fetch(path: String): ByteArray = ByteArray(0)
    }

    @Test fun `a base URL always wins`() {
        assertIs<HttpContentSource>(ContentSources.select("https://cdn.example.com/v1/", debug = true) { assets })
        assertIs<HttpContentSource>(ContentSources.select("https://cdn.example.com/v1/", debug = false) { assets })
    }

    @Test fun `an empty base URL reads the app assets in debug only`() {
        assertSame(assets, ContentSources.select("", debug = true) { assets })
        assertSame(UnavailableContentSource, ContentSources.select("", debug = false) { error("no assets") })
    }

    @Test fun `the unavailable source never answers`() = runTest {
        assertThrows<ContentFetchException> { UnavailableContentSource.fetch("manifest.json") }
    }
}
