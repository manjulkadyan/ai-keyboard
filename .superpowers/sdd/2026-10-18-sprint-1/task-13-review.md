# Task 13 review (79f12c6e..3656d105)

### Spec Compliance

- ❌ Issues found:
  - **R31 (required change):** `SMALL` is gated at `6L shl 30` in `core/voice/src/main/kotlin/com/manjul/aikeyboard/core/voice/whisper/WhisperModelSpec.kt:27`. The ruling sets the gate at 5.5 GiB of `totalMem`. The KDoc at `WhisperModelManager.kt:69` and the test `smallIsOfferedOnlyWithSixGibOfRam` (`WhisperModelManagerTest.kt`, which only tries 4 and 8 GiB) also need updating. Add boundary cases just below and at 5.5 GiB.
  - **Review Focus 4 / download offer:** the selector computes `offerWhisperDownload` (`SpeechEngineSelector.kt:37,44`) and `VoiceController.kt:261` stores it in `VoiceUiState.Message`. `VoicePanel.kt:76-81` never renders it. It only renders `offerSystemDownload`. Case: no recognition service, Whisper language enabled (debug/beta), no model. The selector returns `no_service` with `offerSystemDownload=false` and `offerWhisperDownload=true`. The panel shows the "no service" text with no download action. The message is explicit, so this is not a silent failure, but the "right download offer" part of the requirement is not met. Release is unaffected because the gate is `[]`, so `offerWhisper` is false.
    - Fix size: small, about 20-30 lines.
      - Add an `onDownloadWhisperModel: (() -> Unit)?` parameter to `VoicePanel`, plus a chip string in `ime/voice-ui`.
      - Wire it in `SmartbarVoicePanel.kt:43` to open the existing `settings/voice` deep link (Routes `Settings.Voice`) with `FLAG_ACTIVITY_NEW_TASK`.
      - Add a panel or controller test that asserts the chip when `offerWhisperDownload=true`.
  - **Engine-preference mapping:** the new settings screen offers "On-device only", with the copy "Use only the phone's on-device speech recognition" (`VoiceSettingsScreen.kt:98`, `strings_fork.xml`). Nothing consumes `VoiceEnginePreference.ON_DEVICE`. The only mapping is `preferWhisper = pref == WHISPER` (`VoiceImeSession.kt:123` area, `MicPermissionActivity.kt:170`). The selector then falls through to `SYSTEM_DEFAULT` whenever a service is available and the device is online.
    - Likewise, "Offline (Whisper)" without a READY model falls through to the server recognizer (`SpeechEngineSelector.kt:24-33`).
    - The privacy-relevant copy this task adds is therefore false. Audio can leave the phone when the user chose "On-device only".
    - Fix:
      - Carry the preference, not just a boolean, in `VoiceRequest` and the selector.
      - `ON_DEVICE` must never return `SYSTEM_DEFAULT`.
      - `WHISPER` without a model should return `Unavailable(offerWhisperDownload=true)` rather than a server engine. Otherwise, change the copy and get product sign-off.
- ⚠️ Cannot verify from the diff alone:
  - `VoiceTimings.transcribeTimeoutMs = 45_000` (`VoiceTimings.kt:16`) is shorter than the time Whisper small may need on 60 s of audio. If SpeechSession cancels at 45 s, the engine aborts and the whole utterance is lost. The device checklist latency item must cover this.
  - The release merged manifest has no `ComponentActivity`. This is claimed and was not re-checked.

### Strengths

- `WhisperContexts` implements the R27 contract carefully:
  - The owner-scoped `abort` frees the context mid-call and handles an abort during load through the `aborts` counter (`WhisperContexts.kt:59-77`).
  - `release()` defers while a transcription is running, so the idle unloader and `onTrimMemory` never free under a live transcribe without the engine handling it (`WhisperContexts.kt:55-57`, `finishUse`).
- Engine:
  - The generation counter drops late results after `cancel` or `destroy`.
  - All three exceptions map to `onError(5)` through `catch RuntimeException`, and `CancellationException` is rethrown (`WhisperSpeechEngine.kt:152-162`).
  - The PCM buffer is zeroed on every path, including the grow path (`ensureCapacity`).
  - The 60 s audio cap matches `sessionCapMs`, and the session's later `stop()` is a no-op (tested).
  - RMS is computed per 100 ms chunk (10 Hz) on the SpeechRecognizer scale.
- Model manager:
  - The SHA-256 is checked before the rename (`WhisperModelManager.kt:234-239`). An empty or malformed pin fails closed before any network I/O; the test asserts 0 requests.
  - Work is unique per model with KEEP. UNMETERED or CONNECTED is set, plus storage-not-low.
  - Range resume re-hashes the existing `.part`. A size cap and a free-space check are in place.
  - The `.part` file is kept on a system stop and deleted on cancel, failure or mismatch.
  - `adopt` and `restoreDownloads` cover process death.
  - The file name comes from the enum only, so there is no path traversal. Files live under `noBackupFilesDir/models`.
- Analytics: `pack_download_*` uses closed enums (`PackKind`, `PackResult`), a size bucket and a `tag()`-sanitised lang. The test asserts there is no URL or path.
- Cold start: the native library loads lazily (`WhisperJni.loaded by lazy`). In release, `gate.enabled` short-circuits, so `models` and WorkManager are never touched from the IME.
- Manifest allow-list entries carry the right permissions (`BIND_JOB_SERVICE`, `DUMP`), and `ComponentActivity` is `debuggableOnly`. Each has positive and negative tests.
- `ComposeHostActivityRule` is a sound fix for the existing non-debug variant failure in `OnboardingScreenTest`.
- Settings reuse the existing `FeatureSettings` fields. The slider stays within 2000..5000 in 500 ms steps, tested both through the pure function and through semantics `SetProgress`.
- FORK markers and FORK_PATCHES rows are present for every upstream file touched: `app/build.gradle.kts`, `AndroidManifest.xml`, `FlorisApplication.kt`, `Routes.kt`, `HomeScreen.kt` and `libs.versions.toml`.

### Issues

#### Critical (Must Fix)

None.

#### Important (Should Fix)

1. **R31 RAM gate is 6 GiB.** `WhisperModelSpec.kt:27`; see Spec Compliance. Use `5.5 GiB = 11L shl 29` and add a boundary test.
2. **The IME panel drops `offerWhisperDownload`** (`ime/voice-ui/.../VoicePanel.kt:76-81`, `app/.../fork/voice/SmartbarVoicePanel.kt:43`). Review Focus 4's "right download offer" is not met in debug/beta. The fix is small (see above).
3. **The "On-device only" and "Offline (Whisper)" preferences are not honoured** (`VoiceSettingsScreen.kt:98`, `VoiceImeSession.kt:123`, `MicPermissionActivity.kt:170`, `SpeechEngineSelector.kt:24-33`). The user-visible privacy promise is false and server recognition can be used. Either map the full preference or change the copy.

#### Minor (Nice to Have)

1. `strings_fork.xml:84`: `Say "comma" or "full stop" …` uses unescaped double quotes. aapt strips them, so the screen shows `Say comma or full stop …`. Escape them as `\"`, or use typographic quotes.
2. `WhisperModelManager.kt:163` (`Outcome.Stopped`, not current, `part.delete()`): after delete then re-download, the stopping old worker can delete the new worker's `.part`. The new worker then fails with HASH_MISMATCH. This fails closed but is spurious. Use a per-request part name (`<file>.<requestId>.part`), or skip the delete when another request is current.
3. Some tests are missing: delete while downloading (work cancelled, `.part` removed), Range resume after `retry`, and `restoreDownloads` marking pending work DOWNLOADING after a restart. These behaviours are only on the device checklist.
4. `PcmRecorder.kt:85`: `thread.join(500)` runs on the IME main thread from `stop()` and `cancel()`. It is usually ≤100 ms, but it blocks the UI.
5. `PcmRecorder.kt:49-51`: only `IllegalStateException` from `startRecording` releases `r`. A `SecurityException` there leaks the `AudioRecord`. Catch it, release, and rethrow.
6. In the `capture` loop, a negative `read` (mic lost) ends capture without telling the engine. The session only ends through its silence or cap timers. Consider `onError(ERROR_AUDIO)`.
7. `VoiceSettingsScreen.kt:66-67` touches `whisper.models` (WorkManager init plus `restoreDownloads`) even when `gate.anyEnabled` is false, as in release. Guard it with the gate.
8. Test output: the report claims the run was clean apart from 11 upstream lint warnings in the baseline. That was accepted and not re-run.

### Assessment

**Task quality:** Needs fixes

**Reasoning:** The engine, context lifetime and model manager are well built and fail closed. Three required changes remain:

- The R31 gate.
- The missing Whisper download offer in the IME panel (Review Focus 4).
- Engine preferences whose privacy copy the selector mapping does not honour.

All three are small, contained fixes.
