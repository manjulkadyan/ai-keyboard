/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.paywall

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.LocalNavController
import com.manjul.aikeyboard.app.Routes
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.fork.billing.proGate
import org.florisboard.lib.compose.FlorisSimpleCard
import org.florisboard.lib.compose.stringRes

const val WIN_BACK_CARD_TAG = "home_win_back_card"

/**
 * T8 win-back (PRD §10.4): a soft Home card on day 3 and day 10, never for Pro, at most 2 in a lifetime. It never
 * opens the paywall by itself; a tap does. The showing is claimed once per screen visit and kept across rotation.
 */
@Composable
fun HomeWinBackCard() {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val store = remember { context.featureSettingsStore().value }
    val frequency = remember { PaywallFrequency(store, System::currentTimeMillis) }
    var claimed by rememberSaveable { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        if (claimed == null) claimed = frequency.claimWinBack(isPro = proGate(store)())
    }
    if (claimed != true) return
    FlorisSimpleCard(
        modifier = Modifier.padding(8.dp).testTag(WIN_BACK_CARD_TAG),
        text = stringRes(R.string.paywall__win_back_card_title),
        secondaryText = stringRes(R.string.paywall__win_back_card_body),
        onClick = { navController.navigate(Routes.Paywall(PaywallTrigger.WIN_BACK.wire)) },
    )
}
