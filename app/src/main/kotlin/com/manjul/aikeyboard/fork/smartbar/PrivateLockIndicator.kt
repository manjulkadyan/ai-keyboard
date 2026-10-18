/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.smartbar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.ime.policy.SensitivityReason
import com.manjul.aikeyboard.ime.smartbar.quickaction.QuickAction
import com.manjul.aikeyboard.ime.text.keyboard.TextKeyData
import com.manjul.aikeyboard.ime.theme.FlorisImeUi
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggIcon

/**
 * Shown while the field is sensitive. A long-press offers "Make this app private" unless the app already is.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrivateLockIndicator(modifier: Modifier = Modifier) {
    val decision = rememberPolicyDecision()
    if (!decision.sensitive && decision.reasons.isEmpty()) return
    val context = LocalContext.current
    val canMakePrivate = SensitivityReason.PRIVATE_APP !in decision.reasons
    val makePrivate = QuickAction.InsertKey(TextKeyData.TOGGLE_PRIVATE_APP)

    SnyggBox(
        elementName = FlorisImeUi.SmartbarActionKey.elementName,
        modifier = modifier,
        clickAndSemanticsModifier = Modifier
            .fillMaxHeight()
            .aspectRatio(1f)
            .combinedClickable(
                onLongClickLabel = if (canMakePrivate) stringRes(R.string.private_lock__make_private) else null,
                onLongClick = if (canMakePrivate) {
                    {
                        makePrivate.onPointerDown(context)
                        makePrivate.onPointerUp(context)
                    }
                } else null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        SnyggIcon(
            imageVector = Icons.Default.Lock,
            contentDescription = stringRes(R.string.private_lock__description),
        )
    }
}
