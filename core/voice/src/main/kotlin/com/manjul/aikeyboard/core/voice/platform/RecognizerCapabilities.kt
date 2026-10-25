/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.voice.platform

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

interface RecognizerCapabilities {
    val sdkInt: Int
    fun isRecognitionAvailable(): Boolean
    fun isOnDeviceAvailable(): Boolean

    /** Null when unknown (below API 33 or the check timed out). */
    suspend fun onDeviceSupports(lang: String): Boolean?
    fun whisperModelReady(lang: String): Boolean
    fun whisperLangEnabled(lang: String): Boolean
}

/** True when [installed] has [lang] by exact tag or by language subtag alone (case-insensitive). */
internal fun languageInstalled(lang: String, installed: Collection<String>): Boolean {
    val tag = lang.lowercase()
    val language = tag.substringBefore('-')
    return installed.any { val t = it.lowercase(); t == tag || t.substringBefore('-') == language }
}

private const val SUPPORT_CHECK_TIMEOUT_MS = 1_500L

/**
 * How long the support-check recognizer outlives its answer. Destroying it unbinds the system recognition
 * service; when it is the last client the service shuts down, and a session recognizer created right after
 * gets ERROR_SERVER_DISCONNECTED (seen on Pixel / Android 16). Holding it until the session has bound keeps the
 * service up.
 */
private const val PROBE_RELEASE_DELAY_MS = 3_000L

/**
 * Platform-backed capabilities. The whisper* answers come from the app (the model manager and the Whisper
 * language gate) and are false when not supplied.
 */
class PlatformRecognizerCapabilities(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val whisperReady: (lang: String) -> Boolean = { false },
    private val whisperEnabled: (lang: String) -> Boolean = { false },
) : RecognizerCapabilities {
    override val sdkInt: Int get() = Build.VERSION.SDK_INT

    /** Languages the on-device recognizer reported as installed. Only positive answers: a model may be added later. */
    private val supported = mutableSetOf<String>()

    override fun isRecognitionAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun isOnDeviceAvailable(): Boolean =
        sdkInt >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override suspend fun onDeviceSupports(lang: String): Boolean? {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) return null
        if (synchronized(supported) { lang in supported }) return true
        val result = withTimeoutOrNull(SUPPORT_CHECK_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                // The recognizer must be created and used on the main thread.
                mainHandler.post {
                    if (!cont.isActive) return@post // timed out before the main thread got here
                    var recognizer: SpeechRecognizer? = null
                    runCatching {
                        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                        recognizer = r
                        // Every way out (answer, error, timeout) releases it late, and only once: a timeout is
                        // followed by a session on this same service, and the answer may still arrive after it.
                        var released = false
                        val release = Runnable {
                            if (!released) {
                                released = true
                                runCatching { r.destroy() }
                            }
                        }
                        cont.invokeOnCancellation { mainHandler.postDelayed(release, PROBE_RELEASE_DELAY_MS) }
                        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                        r.checkRecognitionSupport(
                            intent,
                            context.mainExecutor,
                            object : RecognitionSupportCallback {
                                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                                    val ok = languageInstalled(lang, recognitionSupport.installedOnDeviceLanguages)
                                    if (cont.isActive) cont.resume(ok)
                                    mainHandler.postDelayed(release, PROBE_RELEASE_DELAY_MS)
                                }

                                override fun onError(error: Int) {
                                    if (cont.isActive) cont.resume(null)
                                    mainHandler.postDelayed(release, PROBE_RELEASE_DELAY_MS)
                                }
                            },
                        )
                    }.onFailure {
                        runCatching { recognizer?.destroy() }
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        }
        if (result == true) synchronized(supported) { supported += lang }
        return result
    }

    override fun whisperModelReady(lang: String): Boolean = whisperReady(lang)

    override fun whisperLangEnabled(lang: String): Boolean = whisperEnabled(lang)
}
