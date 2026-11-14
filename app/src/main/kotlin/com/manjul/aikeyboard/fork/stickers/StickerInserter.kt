/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.manjul.aikeyboard.BuildConfig
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.analytics.AnalyticsSink
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.StickerMethod
import com.manjul.aikeyboard.core.content.stickers.StickerUi

/** How a sticker reached the host app. [wire] is the `sticker_send` `method` value. */
enum class InsertMethod(val wire: String, internal val analytics: StickerMethod) {
    COMMIT_CONTENT("commit_content", StickerMethod.COMMIT_CONTENT),
    SHARE("share", StickerMethod.SHARE),
    CLIPBOARD("clipboard", StickerMethod.CLIPBOARD),
}

/**
 * Sends a sticker to the host app (S5 Task 6, PRD FR-23). The chain:
 * 1. If the field accepts `image/png`, the file is staged in `cache/share/` and sent with commitContent and a
 *    read-only URI grant.
 * 2. Otherwise, or if the host refuses it, an `ACTION_SEND` goes to the host package only, with a read grant. It is
 *    skipped when the host has no share target.
 * 3. Otherwise, on API 33 and up, the sticker goes on the clipboard (not marked sensitive) with "Sticker copied".
 * 4. Otherwise "This app does not accept stickers".
 *
 * [isPro] is checked again for a premium sticker: without Pro nothing is staged or sent and the result is null.
 * `sticker_send` fires once per non-null result. The host package name is only used to address the share intent;
 * it is never logged.
 */
class StickerInserter(
    private val context: Context,
    private val shareCache: ShareCache,
    private val isPro: () -> Boolean,
    private val analytics: AnalyticsSink,
    private val notify: (Int) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
) {
    fun insert(sticker: StickerUi, editor: EditorInfo, ic: InputConnection?): InsertMethod? {
        if (sticker.premium && !isPro()) return null
        val uri = runCatching { uriFor(shareCache.stage(sticker.file)) }.getOrNull()
        val method = uri?.let { send(it, editor, ic) }
        if (method == null) {
            notify(NOT_ACCEPTED_NOTICE)
            return null
        }
        analytics.log(EventCatalog.stickerSend(sticker.packId, sticker.premium, method.analytics))
        return method
    }

    private fun send(uri: Uri, editor: EditorInfo, ic: InputConnection?): InsertMethod? = when {
        ic != null && acceptsPng(editor) && commit(uri, editor, ic) -> InsertMethod.COMMIT_CONTENT
        share(uri, editor.packageName) -> InsertMethod.SHARE
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && copy(uri) -> {
            notify(COPIED_NOTICE)
            InsertMethod.CLIPBOARD
        }
        else -> null
    }

    /**
     * The FileProvider URI of a staged file: `content://<authority>/share/<uuid>.png`, the form
     * `FileProvider.getUriForFile` gives for the `share` cache-path. It is built here because FileProvider caches its
     * roots per authority for the life of the process. Only a file directly inside `share/` gets a URI.
     */
    private fun uriFor(staged: java.io.File): Uri {
        shareCache.requireStaged(staged)
        return Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .encodedPath(Uri.encode(ShareCache.DIR) + "/" + Uri.encode(staged.name, "/"))
            .build()
    }

    private fun acceptsPng(editor: EditorInfo): Boolean =
        EditorInfoCompat.getContentMimeTypes(editor).any { ClipDescription.compareMimeTypes(PNG, it) }

    private fun commit(uri: Uri, editor: EditorInfo, ic: InputConnection): Boolean {
        val info = InputContentInfoCompat(uri, ClipDescription("", arrayOf(PNG)), null)
        val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        return runCatching { InputConnectionCompat.commitContent(ic, editor, info, flags, null) }.getOrDefault(false)
    }

    private fun share(uri: Uri, host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val intent = Intent(Intent.ACTION_SEND)
            .setType(PNG)
            .setPackage(host)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.clipData = ClipData(ClipDescription("", arrayOf(PNG)), ClipData.Item(uri))
        if (intent.resolveActivity(context.packageManager) == null) return false
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun copy(uri: Uri): Boolean {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        val description = ClipDescription("", arrayOf(PNG)).apply {
            extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) }
        }
        return runCatching { clipboard.setPrimaryClip(ClipData(description, ClipData.Item(uri))) }.isSuccess
    }

    companion object {
        const val PNG = "image/png"
        const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider.file"

        @StringRes val COPIED_NOTICE = R.string.stickers__copied

        @StringRes val NOT_ACCEPTED_NOTICE = R.string.stickers__not_accepted
    }
}
