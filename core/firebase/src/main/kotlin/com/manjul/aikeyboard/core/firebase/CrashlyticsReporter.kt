/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.firebase

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.manjul.aikeyboard.core.common.NonFatalReporter

/**
 * Crashlytics on the default FirebaseApp (Sprint 4 decision 17). Collection stays off (manifest default) until
 * [setCollection] turns it on. A report carries only the error's class name and stack trace, for the error and each
 * cause; the original message never leaves the device (PC-10).
 */
class CrashlyticsReporter(private val bootstrap: FirebaseBootstrap) : NonFatalReporter {
    @Volatile private var instance: FirebaseCrashlytics? = null

    private fun crashlytics(): FirebaseCrashlytics? {
        instance?.let { return it }
        val app = bootstrap.defaultApp() ?: return null
        return synchronized(this) {
            instance ?: app.get(FirebaseCrashlytics::class.java).also { instance = it }
        }
    }

    override fun report(area: String, error: Throwable) {
        runCatching { crashlytics()?.recordException(scrub(area, error)) }
    }

    fun setCollection(enabled: Boolean) {
        runCatching { crashlytics()?.isCrashlyticsCollectionEnabled = enabled }
    }

    /** Starts Crashlytics, so it installs its crash handler. Does nothing when Firebase is not configured. */
    fun start() {
        runCatching { crashlytics() }
    }

    companion object {
        private const val MAX_CAUSES = 8

        /** A copy of [error] that keeps the class names and stack traces and drops every message. */
        fun scrub(area: String, error: Throwable): Throwable {
            val chain = generateSequence(error) { it.cause }.take(MAX_CAUSES).toList()
            var cause: Throwable? = null
            for (e in chain.asReversed()) {
                cause = ScrubbedException(e::class.java.name, cause).apply { stackTrace = e.stackTrace }
            }
            return ScrubbedException("$area: ${error::class.java.name}", cause?.cause).apply {
                stackTrace = error.stackTrace
            }
        }
    }
}

/** Holds a class name in place of the message of the error it replaces. */
class ScrubbedException(label: String, cause: Throwable?) : Exception(label, cause)
