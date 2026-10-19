/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.testing

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Registers the empty activity `createComposeRule()` launches. `ui-test-manifest` declares it only in the debug
 * variant (it must never reach a release build), so the beta, release and benchmark unit tests need it added to
 * Robolectric's package manager. Use with a lower `order` than the compose rule.
 */
class ComposeHostActivityRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val context = RuntimeEnvironment.getApplication()
            val host = ComponentName(context, ComponentActivity::class.java)
            val packageManager = shadowOf(context.packageManager)
            packageManager.addActivityIfNotPresent(host)
            packageManager.addIntentFilterForActivity(
                host,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
            base.evaluate()
        }
    }
}
