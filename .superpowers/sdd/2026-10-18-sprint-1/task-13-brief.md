### Task 13: Whisper engine, model manager and voice settings

**Files:**
- Create: `core/voice/src/main/kotlin/com/manjul/aikeyboard/core/voice/whisper/{WhisperSpeechEngine,PcmRecorder,WhisperModelManager,WhisperModelSpec,WhisperIdleUnloader}.kt`, `core/voice/src/main/kotlin/com/manjul/aikeyboard/core/voice/whisper/ModelDownloadWorker.kt`
- Create: `app/src/main/kotlin/com/manjul/aikeyboard/fork/settings/VoiceSettingsScreen.kt` (engine preference on-device/auto/Offline (Whisper), spoken punctuation toggle, auto-stop 2–5 s slider, Whisper models list with size, status, download/delete, Wi-Fi only toggle)
- Modify (FORK S1-whisper): `app/AndroidManifest.xml` (`INTERNET` permission, used only by the model download worker in the app), settings nav (Voice row), `FlorisApplication.kt` (lazy `whisperModelManager`; `onTrimMemory` → unloader), `app/build.gradle.kts` (`buildConfigField WHISPER_MODEL_BASE_URL`, WorkManager), `gradle/libs.versions.toml`, `strings_fork.xml`, `docs/FORK_PATCHES.md`
- Test: `core/voice/src/test/.../whisper/{WhisperSpeechEngineTest,WhisperModelManagerTest,WhisperIdleUnloaderTest}.kt`, `app/src/test/.../fork/settings/VoiceSettingsTest.kt`

**Interfaces:**
- Consumes: Task 9 `SpeechEngine`, `EngineKind.WHISPER`; Task 10 `RecognizerCapabilities.whisperModelReady/whisperLangEnabled`; Task 12 `WhisperApi`; Task 2 store; Task 6 `EventCatalog` (`pack_download_start`/`pack_download_done{lang, kind=whisper, size_bucket, result}` — add these two to the catalog with their registry entries).
- Produces:
  - `enum class WhisperModelSpec(val fileName: String, val bytes: Long, val sha256: String, val minRamBytes: Long) { BASE("ggml-base-q5_1.bin", …, …, 0), SMALL("ggml-small-q5_1.bin", …, …, 6L shl 30) }` — fill bytes/sha256 from the upstream model card at implementation time and note the source in KDoc.
  - `class WhisperModelManager(filesDir /* no_backup/models */, workManager, ramBytes: Long) { val states: StateFlow<Map<WhisperModelSpec, ModelState>>; fun offered(): List<WhisperModelSpec> /* SMALL only if ramBytes >= 6 GiB */; fun download(spec, wifiOnly: Boolean); fun delete(spec); fun readyModel(): WhisperModelSpec? }`; `enum class ModelState { ABSENT, DOWNLOADING, READY, FAILED }`. Download = unique WorkManager work per model, `.part` file, SHA-256 verified before rename, network-type constraint UNMETERED when Wi-Fi only.
  - `class WhisperSpeechEngine(api: WhisperApi, models, recorder: PcmRecorder, scope, idle: WhisperIdleUnloader) : SpeechEngine` (`supportsPartials = false`; `start` records 16 kHz mono PCM, `stop` transcribes on `Dispatchers.Default`, emits `onRms` while recording and `onFinal` after; hard cap 60 s of audio).
  - `class WhisperIdleUnloader(clock, scope, free: () -> Unit) { fun touch(); fun onTrimMemory(level: Int) }` — frees after 60 s without `touch()` or when `level >= TRIM_MEMORY_RUNNING_LOW`.
- [ ] **Step 1: Write failing tests.**
  - `WhisperModelManagerTest` (Robolectric + `WorkManagerTestInitHelper` + `MockWebServer` serving a 1 KB fake model): download → READY with correct SHA; wrong SHA → FAILED and no file left; `offered()` with 4 GiB RAM → BASE only, with 8 GiB → BASE and SMALL; `delete` → ABSENT; files live under `no_backup/models/`.
  - `WhisperSpeechEngineTest` (fake recorder + fake `WhisperApi`): start → RMS callbacks → stop → `onFinal(transcript)`; 60 s cap stops recording; no model → `onError(LANGUAGE_UNAVAILABLE code)`; the PCM buffer is cleared after transcription.
  - `WhisperIdleUnloaderTest`: free called at 60 s idle, not at 59 s; `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` frees immediately; `touch()` resets.
  - `VoiceSettingsTest`: slider writes 2000..5000 only; picking "Offline (Whisper)" with no model shows the download row; release config with `whisper_enabled_langs = []` hides Whisper for every language, debug shows all.
- [ ] **Step 2: Run** `$G ./gradlew :core:voice:test :app:testDebugUnitTest --tests '*VoiceSettings*'` — Expected: FAIL.
- [ ] **Step 3: Implement**, then wire `RecognizerCapabilities.whisperModelReady/whisperLangEnabled` (Task 10) to the manager and the language gate (decision 5).
- [ ] **Step 4: Run** `$G ./gradlew test :app:lintDebug :app:assembleDebug && scripts/check-fork-markers.sh && scripts/check-16kb-alignment.sh app/build/outputs/apk/debug/*.apk` — Expected: PASS (full unit suite).
- [ ] **Step 5: Commit** `feat(voice): on-device Whisper fallback with model manager and voice settings`.

