/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.policy

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkQuery
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.voice.EngineKind
import com.manjul.aikeyboard.core.voice.SpeechEngine
import com.manjul.aikeyboard.core.voice.platform.RecognizerCapabilities
import com.manjul.aikeyboard.fork.clipboard.ClipboardCaptureGate
import com.manjul.aikeyboard.fork.voice.VoiceImeSession
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardHistoryDatabase
import com.manjul.aikeyboard.ime.clipboard.provider.ClipboardItem
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TRD section 4 / section 8 Sprint 1 exit: a scripted session in a password field makes no network call and no
 * Room write. FlorisImeService cannot run under Robolectric (native library), so the script drives the same real
 * pieces the service wires together: [ImePolicyController] gets onStartInput, [VoiceImeSession] gets the mic action,
 * the clipboard path runs the real [ClipboardCaptureGate] in front of the real Room DAO, and key presses and
 * backspace go through a real input connection. Sprint 1 has no suggestion engine (the provider is a stub), so
 * there is no suggestion tap to script.
 *
 * Network: a [ProxySelector] records every connection attempt made through java.net / OkHttp URL connections, and
 * WorkManager must hold no work (the only INTERNET user is the Whisper download worker, R29). Room: the database's
 * query callback records every INSERT / UPDATE / DELETE statement. The positive controls at the end prove both
 * detectors fire, so a silent guard cannot pass this test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PasswordFieldEgressTest {
    private val app = RuntimeEnvironment.getApplication()
    private val connectionAttempts = mutableListOf<URI>()
    private val writes = mutableListOf<String>()
    private var previousSelector: ProxySelector? = null
    private lateinit var db: ClipboardHistoryDatabase
    private lateinit var workManager: WorkManager

    private class Engine(override val kind: EngineKind) : SpeechEngine {
        override val supportsPartials = true
        override fun start(lang: String, listener: SpeechEngine.Listener) = Unit
        override fun stop() = Unit
        override fun cancel() = Unit
        override fun destroy() = Unit
    }

    private object Caps : RecognizerCapabilities {
        override val sdkInt = 34
        override fun isRecognitionAvailable() = true
        override fun isOnDeviceAvailable() = true
        override suspend fun onDeviceSupports(lang: String): Boolean? = true
        override fun whisperModelReady(lang: String) = false
        override fun whisperLangEnabled(lang: String) = false
    }

    @Before
    fun install() {
        previousSelector = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> {
                connectionAttempts += uri
                return listOf(Proxy.NO_PROXY)
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
        })
        db = Room.inMemoryDatabaseBuilder(app, ClipboardHistoryDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback({ sql, _ ->
                val head = sql.trimStart().uppercase()
                if (head.startsWith("INSERT") || head.startsWith("UPDATE") || head.startsWith("DELETE")) writes += sql
            }, { it.run() })
            .build()
        // WorkManager is a process singleton and may already exist from an earlier test; start from a clean slate.
        runCatching { WorkManager.initialize(app, Configuration.Builder().build()) }
        workManager = WorkManager.getInstance(app)
        workManager.cancelAllWork().result.get()
        workManager.pruneWork().result.get()
    }

    @After
    fun restore() {
        ProxySelector.setDefault(previousSelector)
        db.close()
    }

    private fun textField() = EditorInfo().apply {
        packageName = "some.app"
        inputType = InputType.TYPE_CLASS_TEXT
    }

    private fun passwordField() = EditorInfo().apply {
        packageName = "some.app"
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun run(block: suspend TestScope.() -> Unit) = runTest(StandardTestDispatcher()) {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        block()
    }

    private fun pendingWork() = workManager.getWorkInfos(
        WorkQuery.fromStates(WorkInfo.State.values().toList()),
    ).get()

    /** Same order as `ClipboardManager.onPrimaryClipChanged`: capture gate, then the insert-time gate, then the DAO. */
    private fun paste(gate: ClipboardCaptureGate, text: String) {
        val clip = ClipData.newPlainText("label", text)
        if (!gate.mayCapture(clip)) return
        val item = ClipboardItem.text(text)
        if (gate.allowsHistory() && !gate.isSensitive(item.text, item.isSensitive)) db.clipboardItemDao().insert(item)
    }

    private fun TestScope.session(policy: ImePolicyController, store: FeatureSettingsStore, engines: MutableList<Engine>) =
        VoiceImeSession(
            context = app,
            policy = policy,
            store = store,
            analytics = { },
            connection = { EditText(app).onCreateInputConnection(EditorInfo()) },
            isRawInputEditor = { false },
            setOwnsComposing = { },
            lang = { "en-US" },
            scope = backgroundScope,
            clock = { 0L },
            caps = { Caps },
            engineFactory = { kind, _ -> Engine(kind).also { engines += it } },
            online = { true },
        )

    @Test fun scriptedPasswordSessionMakesNoNetworkCallAndNoRoomWrite() = run {
        val store = FeatureSettingsStore(app, StandardTestDispatcher(testScheduler))
        val policy = ImePolicyController(store, keyguard = { false }, forcedPackages = { emptySet() }, scope = backgroundScope)
        val engines = mutableListOf<Engine>()
        val voice = session(policy, store, engines)
        val gate = ClipboardCaptureGate { policy.decision.value }
        val edit = EditText(app)
        val ic = edit.onCreateInputConnection(EditorInfo())

        // Normal field first, then the field turns into a password field (restarting = true), then a fresh start.
        policy.onStartInput(textField(), false)
        policy.onStartInput(passwordField(), restarting = true)
        runCurrent()
        for (field in listOf(null, passwordField())) {
            if (field != null) policy.onStartInput(field, false)
            // Key presses and backspace.
            "hunter2".forEach { ic.commitText(it.toString(), 1) }
            ic.deleteSurroundingText(1, 0)
            // Paste, plain and sensitive-looking, while the field is a password field.
            paste(gate, "correct horse battery staple")
            paste(gate, "123456")
            // Mic action.
            voice.controller.toggle()
            runCurrent()
        }
        policy.onFinishInputView()
        runCurrent()

        assertTrue(connectionAttempts.isEmpty(), "network attempts: $connectionAttempts")
        assertTrue(pendingWork().isEmpty(), "WorkManager has work: ${pendingWork()}")
        assertTrue(writes.isEmpty(), "Room writes: $writes")
        assertEquals(0, db.clipboardItemDao().getAll().size)
        assertTrue(engines.isEmpty(), "no recognizer is created in a password field")
    }

    @Test fun positiveControlsProveTheDetectorsFire() = run {
        // Network detector.
        runCatching { URL("http://example.invalid/").openConnection().connect() }
        assertTrue(connectionAttempts.isNotEmpty(), "ProxySelector guard did not see a connection attempt")
        // Room detector: the same paste path in an unrestricted field does write.
        val unrestricted = ClipboardCaptureGate { com.manjul.aikeyboard.ime.policy.PolicyDecision.Unrestricted }
        paste(unrestricted, "buy milk and eggs")
        assertTrue(writes.isNotEmpty(), "query callback did not see the insert")
        assertEquals(1, db.clipboardItemDao().getAll().size)
        // WorkManager detector.
        workManager.enqueue(OneTimeWorkRequestBuilder<NoopWorker>().build())
        assertEquals(1, pendingWork().size)
    }

    class NoopWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result = Result.success()
    }
}
