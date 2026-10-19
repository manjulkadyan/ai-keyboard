/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.voice

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import androidx.work.WorkManager
import com.manjul.aikeyboard.BuildConfig
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.voice.SpeechEngine
import com.manjul.aikeyboard.core.voice.platform.PlatformRecognizerCapabilities
import com.manjul.aikeyboard.core.voice.platform.RecognizerCapabilities
import com.manjul.aikeyboard.core.voice.whisper.AudioRecordPcmRecorder
import com.manjul.aikeyboard.core.voice.whisper.WhisperContexts
import com.manjul.aikeyboard.core.voice.whisper.WhisperIdleUnloader
import com.manjul.aikeyboard.core.voice.whisper.WhisperLanguageGate
import com.manjul.aikeyboard.core.voice.whisper.WhisperModelManager
import com.manjul.aikeyboard.core.voice.whisper.WhisperSpeechEngine
import com.manjul.aikeyboard.lib.whisper.WhisperNative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app-wide Whisper pieces, owned by FlorisApplication: the loaded model (shared by every engine), its
 * idle unloader and the model manager. Nothing touches WorkManager or the native library until Whisper is
 * actually used, so the IME cold start does not pay for it.
 */
class WhisperVoice(private val context: Context, analytics: () -> (AnalyticsEvent) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Languages Whisper may serve in this build: all in debug and beta, none in release (decision 5). */
    val gate = WhisperLanguageGate.parse(BuildConfig.WHISPER_ENABLED_LANGS)

    val contexts = WhisperContexts(WhisperNative)

    val unloader = WhisperIdleUnloader(clock = SystemClock::elapsedRealtime, scope = scope, free = contexts::release)

    val models: WhisperModelManager by lazy {
        WhisperModelManager(
            noBackupDir = context.noBackupFilesDir,
            workManager = WorkManager.getInstance(context),
            ramBytes = totalRam(context),
            baseUrl = BuildConfig.WHISPER_MODEL_BASE_URL,
            analytics = { analytics()(it) },
            scope = scope,
        )
    }

    /** Recognizer capabilities with the Whisper answers filled in from the gate and the downloaded models. */
    fun capabilities(context: Context): RecognizerCapabilities = PlatformRecognizerCapabilities(
        context,
        whisperReady = { lang -> gate.enabled(lang) && models.readyModel() != null },
        whisperEnabled = gate::enabled,
    )

    /** A Whisper engine whose callbacks run on [scope] (the IME's main-thread scope). */
    fun engine(scope: CoroutineScope): SpeechEngine = WhisperSpeechEngine(
        contexts = contexts,
        models = models,
        recorder = AudioRecordPcmRecorder(),
        scope = scope,
        idle = unloader,
    )

    private companion object {
        fun totalRam(context: Context): Long {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info) ?: return 0L
            return info.totalMem
        }
    }
}
