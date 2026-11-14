/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.dictionary

import android.app.Application
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A pack swap or Delete my data must not close a reader that a lookup still uses (final review m2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PackReadersTest {
    private val fixture = File(System.getProperty("content.contentDir")!!, "dict/en-fixture-1.db")
    private val copy = File.createTempFile("pack", ".db").also { fixture.copyTo(it, overwrite = true) }
    private var installed: File? = fixture
    private val readers = PackReaders { installed }

    @Test fun `a reader in use stays open across a swap and closes when its lookup ends`() {
        val inUse = assertNotNull(readers.current())
        installed = copy // a pack update between current() and find()
        val next = assertNotNull(readers.current())
        assertNotSame(inUse, next)
        assertTrue(inUse.find("run").isNotEmpty(), "the reader was closed under a running lookup")
        readers.release(inUse)
        assertTrue(runCatching { inUse.find("run") }.isFailure, "a swapped out reader must close after its last use")
        assertTrue(next.find("run").isNotEmpty())
        readers.release(next)
        assertTrue(next.find("run").isNotEmpty(), "the current reader stays open")
    }

    @Test fun `a removed pack closes the reader only after the lookup ends`() {
        val inUse = assertNotNull(readers.current())
        installed = null // Delete my data
        assertNull(readers.current())
        assertEquals("run", inUse.find("run").first().headword)
        readers.release(inUse)
        assertTrue(runCatching { inUse.find("run") }.isFailure)
    }
}
