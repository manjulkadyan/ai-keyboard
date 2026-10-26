/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.manjul.aikeyboard.app.FlorisAppActivity
import com.manjul.aikeyboard.fork.routes.RouteRegistry

/** The Pro placeholder screen in the app ("See Pro" on the quota card). Explicit component, never a chooser. */
internal fun proIntent(context: Context): Intent =
    Intent(Intent.ACTION_VIEW, PRO_DEEPLINK.toUri(), context, FlorisAppActivity::class.java)
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )

/** "Copy" on the sheet: a plain clip with no label. The text is never logged. */
internal fun copyAiOutput(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(null, text))
}

private const val PRO_DEEPLINK = "ui://florisboard/" + RouteRegistry.PRO
