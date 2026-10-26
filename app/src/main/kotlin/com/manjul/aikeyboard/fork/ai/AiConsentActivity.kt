/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.AppTheme
import com.manjul.aikeyboard.app.apptheme.FlorisAppTheme
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.ConsentKind
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.featureSettingsStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.compose.ProvideLocalizedResources

/**
 * AI consent trampoline (PRD §10.6, TRD §11.6). The keyboard cannot show the consent in its own window, so the ✦ chip
 * (and the auto-polish toggle) open this activity. The extra [EXTRA_KIND] must be exactly `AI` or `AUTO_POLISH`;
 * anything else finishes at once.
 *
 * Only Agree is consent: it writes `consentAiAt` (or `consentAutoPolishAt` and turns auto-polish on). Not now and
 * Back log a decline and leave it at 0; leaving the screen (home, recents) is not consent either. It never starts an
 * AI request: the user taps ✦ again in the keyboard. Not exported; it holds no field text.
 */
class AiConsentActivity : ComponentActivity() {
    @VisibleForTesting
    internal class Deps(
        val store: FeatureSettingsStore,
        val analytics: (AnalyticsEvent) -> Unit,
        val clock: () -> Long,
    )

    private lateinit var deps: Deps
    private lateinit var kind: ConsentKind

    /** Agree was tapped: the write is in flight and the screen takes no more input. */
    private var agreeing by mutableStateOf(false)
    private var answered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parsed = parseKind(intent?.getStringExtra(EXTRA_KIND))
        if (parsed == null) {
            finish()
            return
        }
        kind = parsed
        deps = depsOverride ?: defaultDeps(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = decline()
        })
        setContent {
            ProvideLocalizedResources(this, appName = R.string.app_name) {
                FlorisAppTheme(AppTheme.AUTO) {
                    AiConsentScreen(kind = kind, enabled = !agreeing, onAgree = ::agree, onNotNow = ::decline)
                }
            }
        }
    }

    /** Home or recents: no answer, nothing written, and the trampoline does not linger. */
    override fun onStop() {
        super.onStop()
        if (!isFinishing && !isChangingConfigurations && !agreeing) finish()
    }

    private fun agree() {
        if (answered) return
        answered = true
        agreeing = true
        val now = deps.clock()
        lifecycleScope.launch {
            // Not cancellable: leaving the screen right after Agree must not lose the answer.
            withContext(NonCancellable) {
                deps.store.update {
                    when (kind) {
                        ConsentKind.AI -> it.copy(consentAiAt = now)
                        ConsentKind.AUTO_POLISH -> it.copy(consentAutoPolishAt = now, autoPolish = true)
                    }
                }
            }
            deps.analytics(EventCatalog.consentResult(kind, agreed = true))
            finish()
        }
    }

    private fun decline() {
        if (answered) return
        answered = true
        deps.analytics(EventCatalog.consentResult(kind, agreed = false))
        finish()
    }

    companion object {
        /** `AI` or `AUTO_POLISH` ([ConsentKind] names); any other value finishes the activity. */
        const val EXTRA_KIND = "kind"

        @VisibleForTesting
        internal var depsOverride: Deps? = null

        fun intent(context: Context, kind: ConsentKind): Intent =
            Intent(context, AiConsentActivity::class.java)
                .putExtra(EXTRA_KIND, kind.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        internal fun parseKind(value: String?): ConsentKind? = ConsentKind.entries.firstOrNull { it.name == value }

        private fun defaultDeps(context: Context) = Deps(
            store = context.featureSettingsStore().value,
            analytics = context.appContext().value.analytics.value::log,
            clock = System::currentTimeMillis,
        )
    }
}
