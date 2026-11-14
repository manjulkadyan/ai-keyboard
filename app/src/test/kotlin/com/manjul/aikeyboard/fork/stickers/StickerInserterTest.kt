/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import com.manjul.aikeyboard.BuildConfig
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.content.stickers.StickerUi
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The commitContent, share and clipboard insert chain, and the premium re-check (S5 Task 6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StickerInserterTest {
    @get:Rule val tmp = TemporaryFolder()

    private val app: Application = RuntimeEnvironment.getApplication()
    private val host = "com.example.chat"
    private val events = mutableListOf<AnalyticsEvent>()
    private val notices = mutableListOf<Int>()
    private var isPro = true
    private val authority = "${BuildConfig.APPLICATION_ID}.provider.file"

    private val inserter by lazy {
        StickerInserter(
            context = app,
            shareCache = ShareCache(app.cacheDir) { System.currentTimeMillis() },
            isPro = { isPro },
            analytics = object : AnalyticsSink {
                override fun log(event: AnalyticsEvent) {
                    events += event
                }
            },
            notify = { notices += it },
        )
    }

    private fun sticker(premium: Boolean = false): StickerUi {
        val file = File(tmp.newFolder(), "01.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        return StickerUi("starter/01", "starter", file, premium)
    }

    private fun editor(vararg mimes: String) = EditorInfo().apply {
        packageName = host
        if (mimes.isNotEmpty()) EditorInfoCompat.setContentMimeTypes(this, arrayOf(*mimes))
    }

    /** Records commitContent; [accept] is what the host answers. */
    private class RecordingConnection(private val accept: Boolean) : InputConnectionWrapper(null, false) {
        var committed: InputContentInfo? = null
        var flags = 0

        override fun commitContent(info: InputContentInfo, flags: Int, opts: Bundle?): Boolean {
            committed = info
            this.flags = flags
            return accept
        }
    }

    private fun registerShareTarget() {
        val component = ComponentName(host, "$host.ShareActivity")
        val pm = shadowOf(app.packageManager)
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_SEND).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataType("image/png")
            },
        )
    }

    @Test fun `image mime accepted gives COMMIT_CONTENT with the file provider authority`() {
        val ic = RecordingConnection(accept = true)
        assertEquals(InsertMethod.COMMIT_CONTENT, inserter.insert(sticker(), editor("image/*"), ic))
        val uri = assertNotNull(ic.committed).contentUri
        assertEquals(authority, uri.authority)
        assertTrue(uri.path!!.contains("/share/"))
        assertTrue(ic.flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("image/png", ic.committed!!.description.getMimeType(0))
        val event = events.single()
        assertEquals("sticker_send", event.name)
        assertEquals("commit_content", event.params["method"])
        assertEquals("starter", event.params["pack_id"])
        assertTrue(event.params.values.none { it.toString().contains(host) })
    }

    @Test fun `no mime types and a host share target gives SHARE to the host only`() {
        registerShareTarget()
        val ic = RecordingConnection(accept = true)
        assertEquals(InsertMethod.SHARE, inserter.insert(sticker(), editor(), ic))
        assertNull(ic.committed)
        val intent = assertNotNull(shadowOf(app).nextStartedActivity)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(host, intent.`package`)
        assertEquals("image/png", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals(authority, stream?.authority)
        assertEquals("share", events.single().params["method"])
    }

    @Test fun `a host that refuses commitContent falls back to SHARE`() {
        registerShareTarget()
        assertEquals(InsertMethod.SHARE, inserter.insert(sticker(), editor("image/png"), RecordingConnection(false)))
    }

    @Test fun `no share target on API 33 and up gives CLIPBOARD`() {
        assertEquals(InsertMethod.CLIPBOARD, inserter.insert(sticker(), editor(), RecordingConnection(true)))
        val clip = assertNotNull(app.getSystemService(ClipboardManager::class.java).primaryClip)
        assertEquals(authority, clip.getItemAt(0).uri.authority)
        assertEquals(false, clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true))
        assertEquals(listOf(StickerInserter.COPIED_NOTICE), notices)
        assertEquals("clipboard", events.single().params["method"])
    }

    @Test fun `a premium sticker after Pro lapses gives null and no event`() {
        isPro = false
        val ic = RecordingConnection(accept = true)
        assertNull(inserter.insert(sticker(premium = true), editor("image/*"), ic))
        assertNull(ic.committed)
        assertTrue(events.isEmpty())
        assertTrue(File(app.cacheDir, "share").list().isNullOrEmpty())
    }

    @Test fun `a premium sticker with Pro inserts and reports premium`() {
        val method = inserter.insert(sticker(premium = true), editor("image/*"), RecordingConnection(true))
        assertEquals(InsertMethod.COMMIT_CONTENT, method)
        assertEquals("true", events.single().params["premium"].toString())
    }
}
