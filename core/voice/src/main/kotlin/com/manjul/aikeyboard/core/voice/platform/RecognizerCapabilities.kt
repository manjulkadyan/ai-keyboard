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

private const val SUPPORT_CHECK_TIMEOUT_MS = 1_500L

/** Platform-backed capabilities. The whisper* answers are false until the Whisper engine is wired in. */
class PlatformRecognizerCapabilities(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
) : RecognizerCapabilities {
    override val sdkInt: Int get() = Build.VERSION.SDK_INT

    override fun isRecognitionAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    override fun isOnDeviceAvailable(): Boolean =
        sdkInt >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override suspend fun onDeviceSupports(lang: String): Boolean? {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) return null
        return withTimeoutOrNull(SUPPORT_CHECK_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                // The recognizer must be created and used on the main thread.
                mainHandler.post {
                    val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    cont.invokeOnCancellation { mainHandler.post { recognizer.destroy() } }
                    val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                    recognizer.checkRecognitionSupport(
                        intent,
                        context.mainExecutor,
                        object : RecognitionSupportCallback {
                            override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                                val installed = recognitionSupport.installedOnDeviceLanguages
                                val tag = lang.lowercase()
                                val ok = installed.any { it.lowercase() == tag || it.substringBefore('-').lowercase() == tag.substringBefore('-') }
                                recognizer.destroy()
                                if (cont.isActive) cont.resume(ok)
                            }

                            override fun onError(error: Int) {
                                recognizer.destroy()
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun whisperModelReady(lang: String): Boolean = false

    override fun whisperLangEnabled(lang: String): Boolean = false
}
