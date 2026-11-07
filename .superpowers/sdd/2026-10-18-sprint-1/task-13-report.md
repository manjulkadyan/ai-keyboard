# Task 13 report: on-device Whisper fallback, model manager, voice settings

Status: DONE_WITH_CONCERNS
Commit: `3656d105 feat(voice): on-device Whisper fallback with model manager and voice settings` (branch `sprint-1`, not pushed)

## TDD evidence

RED (`/tmp/t13-red.log`): tests written first. `:core:voice:compile{Debug,Beta,Release}UnitTestKotlin` and
`:app:compileDebugUnitTestKotlin` FAILED with 324 `Unresolved reference` errors, and nothing else.
The missing references were:

- `WhisperModelSpec`, `WhisperModelManager`, `ModelState`, `states`, `download`, `delete`, `offered`,
  `readyModel(Path)` and `workName`
- `WhisperSpeechEngine` and `WhisperModelSource`
- `silenceMsFromSlider` and `whisperWifiOnly`

`EventCatalogLimitsTest` and `BucketsTest` were also red: `packDownloadStart` and `packDownloadDone` were
missing, so was `Buckets.megabytes`, and the expected-name list had changed.

On the first RED attempt, `check*UnitTestAarMetadata` failed. The cause was mockwebserver3 5.5.0, which pulls
okhttp-android, and okhttp-android needs compileSdk 37. I switched to `okhttp3:mockwebserver` 4.12.0.

GREEN:

- `:core:voice:testDebugUnitTest`: 207 tests, 0 failures. This includes the new tests:
  - WhisperSpeechEngineTest (11)
  - WhisperModelManagerTest (8, Robolectric plus MockWebServer)
  - WhisperIdleUnloaderTest (5)
- `:core:analytics:test`: 54 tests, 0 failures.
- `:app:testDebugUnitTest --tests '*VoiceSettings*'`: 7 tests, 0 failures.
  - One test bug was fixed on the way. The slider selector matched `current=2000`, so it lost the node after
    the first change. The test now asserts the initial range info once and then selects by key.
- `:build-logic:convention:test`: green, with 2 new tests for the allow-list entries.
- Step 4, `$G ./gradlew test :app:lintDebug :app:assembleDebug`: BUILD SUCCESSFUL.
  - 1999 unit tests across all modules and variants, 0 failures.
  - Lint: nothing new outside the baseline. The 11 warnings are all on upstream lines.
- `scripts/check-fork-markers.sh`, run after the commit: OK.
- `scripts/check-16kb-alignment.sh app/build/outputs/apk/debug/*.apk`: OK, 15 native libraries.
- `:app:verify{Debug,Release}ExportedComponents` and `verify{Debug,Release}NoAdAutoInit`: green.

## R28: SHA-256 pins (findings and sources)

No authoritative SHA-256 is published for `ggml-base-q5_1.bin` or `ggml-small-q5_1.bin`:

- whisper.cpp `models/download-ggml-model.sh` (v1.9.5, vendored in `lib/whisper`) downloads with no checksum.
- `models/README.md` has a SHA-1 table for the unquantised (f16) models only.
- The whisper.wasm example (`examples/whisper.wasm/index-tmpl.html`) lists sizes and no hashes.
- Hugging Face (`ggerganov/whisper.cpp`) shows a Git LFS SHA-256 per file. That host is blocked in the
  sandbox, so I could not read or verify those values. A hash copied unverified would be worse than none.

What I did:

- `WhisperModelSpec.sha256` is `""` for both models, with `TODO(OD-01)`.
- `runDownload` rejects any hash that is not 64 lowercase hex characters. It ends as `HASH_MISMATCH`/FAILED,
  makes no network request (tested: 0 requests), and leaves no file. A release build can never install an
  unverified model.
- Tests inject the hash through `pinnedSha256`.
- To pin: take the LFS SHA-256 from the model host chosen for OD-01, check it against a downloaded file, and set
  `sha256` in `WhisperModelSpec`.

## Decisions

- **Model sizes.** I kept the q5_1 files the brief names: base about 57 MB, small about 182 MB. The ~140 / ~470 MB
  figures in global-constraints match the f16 models. See concern 1.
- **RAM gate.** SMALL is offered only at 6 GiB or more of `totalMem`. BASE is always offered. When both are READY,
  the largest READY model is used.
- **Language gate (decision 5).** `WHISPER_ENABLED_LANGS` is `"*"` in defaultConfig (debug and beta) and `""` in
  release. Benchmark inherits `""` through `initWith(release)`.
  - `WhisperLanguageGate` feeds `RecognizerCapabilities.whisperLangEnabled`.
  - `whisperModelReady` is the gate plus a READY model.
  - The settings screen hides the Offline option and the models section when no language is enabled.
- **WorkManager.** Version 2.12.0 from the catalog (AI Keyboard blocks). It initialises on demand:
  - `WorkManagerInitializer` is removed from the startup provider.
  - `FlorisApplication` implements `Configuration.Provider` with `ModelDownloadWorkerFactory`.
  - The manager is lazy, so the IME cold start never touches WorkManager.
  - Each model has its own unique work, `whisper_model_<name>`, with KEEP.
  - Constraints: UNMETERED when Wi-Fi only (the default), otherwise CONNECTED; plus storage not low.
  - Backoff is exponential, starting at 30 s, with at most 3 attempts.
- **Download.**
  - Files are written as `no_backup/models/<file>.part`, with HTTP Range resume (on a 206, the existing part is
    hashed first).
  - Downloads are capped at 2x the expected size, and free space must cover the file plus 50 MB.
  - The file is synced with `fd.sync`, verified, and only then renamed.
  - A system stop keeps `.part` so the retry resumes. User cancel or delete, a failure, or a hash mismatch deletes
    `.part`.
  - The worker logs nothing. Its input is the enum name only.
- **Engine.**
  - Batch recognition: 16 kHz mono `VOICE_RECOGNITION`, capped at 60 s, after which it stops on its own.
  - No partials. One `onFinal`, with `[BLANK_AUDIO]`-style markers removed.
  - Transcription is serialised per shared context by `WhisperContexts` (a Mutex).
  - `cancel()` while transcribing frees the context (that is the abort) and bumps a generation, so the late
    result is dropped.
  - `IllegalArgumentException`, `IllegalStateException` and `WhisperException` all map to `onError(5)`.
    No model → 13, permission → 9, recorder failure → 3.
  - The audio buffer is zero-filled after use.
  - RMS is mapped to the SpeechRecognizer -2..10 scale, so the existing silence detector and the 45 s timeout in
    SpeechSession work unchanged.
- **Unloader.** After 60 s idle, the model is freed. `onTrimMemory(>= RUNNING_LOW)` frees it immediately. Both run
  on the main thread, and `release()` is non-blocking: it defers the free if a transcription is running.
  `FlorisApplication.onTrimMemory` touches Whisper only if it was already initialised.
- **Engine factories (task 10).** `VoiceImeSession` and `MicPermissionActivity` use
  `WhisperVoice.capabilities()`. The factory has a WHISPER branch, and `VoiceEngines.supports` is now true for
  every kind.
- **Analytics (task 6).** Events: `pack_download_start{lang, kind, size_bucket}` and
  `pack_download_done{lang, kind, size_bucket, result}`.
  - `lang` is `"mul"`, because Whisper models are multilingual.
  - `kind` is the closed `PackKind` enum, `result` the closed `PackResult` enum.
  - `size_bucket` comes from `Buckets.megabytes`.
  - `EventCatalogLimitsTest`'s expected names are updated.
- **Settings.** I reused FeatureSettings (`voiceEnginePreference`, `spokenPunctuation`, `voiceSilenceMs`) and added
  `whisperWifiOnly`, default true.
  - The slider runs from 2000 to 5000 in 500 ms steps (`steps = 5`).
  - The Voice row on Settings home follows the private-apps style.
  - All copy is in `strings_fork.xml`, using `{size}` / `{s}` curly arguments with no concatenation. "Download Base"
    and "Download Small" are separate strings.
- **Exported components.** Two WorkManager entries went on the allow-list:
  - `SystemJobService`, which must require `BIND_JOB_SERVICE`
  - `DiagnosticsReceiver`, which must require `DUMP`

  I also added `androidx.activity.ComponentActivity` (debuggable builds only). It comes from
  `ui-test-manifest`, added in S1-onboarding (75e146d5), and `verifyDebugExportedComponents` was already failing on
  it before this task. The release merged manifest does not contain it (checked).
- **Unit tests in non-debug variants (also older than this task).** `createComposeRule()` failed in
  beta, release and benchmark, because `ui-test-manifest` is debug-only. `OnboardingScreenTest` was already failing
  in `testBenchmarkUnitTest`.
  - The new `fork/testing/ComposeHostActivityRule` registers the host activity with Robolectric.
  - `OnboardingScreenTest` and `VoiceSettingsTest` use it.
  - The gate test checks the expected value for each variant.

## Concerns

1. **Size mismatch.** global-constraints says about 140 / 470 MB (the f16 sizes). The brief and this code use q5_1
   (57 / 182 MB). The lead should confirm q5_1. If f16 is wanted, change `fileName` and `bytes` in `WhisperModelSpec`.
2. **Downloads cannot work yet.** The hashes are unpinned and the host is a placeholder (OD-01). Every download fails
   closed with `HASH_MISMATCH`. That is intended, but the feature is not usable until OD-01 is resolved.
3. **No in-keyboard download prompt.** The IME panel has no Whisper download chip yet: `offerWhisperDownload` is not
   used in the IME UI. Users have to download from Settings → Voice. This is a follow-up.
4. **No progress percentage.** The settings screen shows only "Downloading, 57 MB".
5. **Worker time limit.** A single `Worker` run is limited to about 10 minutes. On a slow network, small (182 MB)
   relies on stop and resume across retries, with at most 3 attempts. A long-running foreground worker would be
   more robust, but it needs a notification and the FOREGROUND_SERVICE_DATA_SYNC type.
6. **RAM threshold.** Phones marketed as "6 GB" often report 5.5–5.8 GiB of `totalMem`, so they would not be offered
   small. The threshold may need to be about 5.5 GiB.
7. **First-use cost.** `WorkManager.getInstance()` runs on the main thread the first time the model manager is used:
   in settings, or at the first Whisper capability check in the IME. It initialises on demand, and the cost is a
   one-off database open.
8. **INTERNET is new.** It is used only by the download worker. The network security config still applies (HTTPS only).

## Device checklist items created by this task

- [ ] Once hashes are pinned: download base over Wi-Fi, check `no_backup/models/ggml-base-q5_1.bin`, and check that
      no `.part` file is left behind.
- [ ] With Wi-Fi only on and mobile data only, the download waits. Turn it off and the download starts.
- [ ] Kill the app or lose the network mid-download, then check that it resumes (Range) and the hash still verifies.
- [ ] Delete during a download: the work is cancelled and `.part` is removed.
- [ ] Offline (Whisper) dictation in en-US and hi-IN on a debug build: check the RMS animation, that auto-stop after
      silence works, and that a single final text is inserted.
- [ ] Measure transcription latency and peak memory for base on a 4 GB device, and for small on an 8 GB device.
- [ ] Cancel during transcription: no late text, and no crash.
- [ ] Leave the app idle for 60 s, or send `adb shell am send-trim-memory <pkg> RUNNING_LOW`: the model is freed, and
      the next dictation reloads it.
- [ ] Release build: the Offline option and the models section are hidden, and the voice engine never picks Whisper.
- [ ] IME cold start does not initialise WorkManager or load `libaikb_whisper.so`. Check with
      `adb shell dumpsys jobscheduler` and the loaded libraries in `/proc/<pid>/maps`.

---

## Fix round 1 (commit 41a62f80, base 3656d105)

### Changes per review item
- **R31 Small gate**: `WhisperModelSpec.SMALL.minRamBytes = 11L shl 29` (5.5 GiB). Boundary test `smallIsOfferedFromFiveAndAHalfGibOfRam` checks 5.5 GiB minus 1 byte (not offered) and exactly 5.5 GiB (offered).
- **IME Whisper download chip**: `VoicePanel` has a new `onDownloadWhisperModel` callback. It shows the `voice__download_whisper` ("Get offline model") chip when `offerWhisperDownload` is true. `SmartbarVoicePanel` dismisses the panel and opens `ui://florisboard/settings/voice` in FlorisAppActivity (`voiceSettingsIntent`). Tests: `VoicePanelTest` (3), `VoiceSettingsIntentTest` (1).
- **R32 full preference**: `SpeechEngineSelector.choose(lang, VoiceEnginePreference, online)`. ON_DEVICE and WHISPER never pick SYSTEM_DEFAULT (the server recognizer). WHISPER picks the model when it is ready, otherwise on-device, otherwise Unavailable. Unavailable gained `localOnly`. Callers: VoiceController/VoiceRequest, VoiceImeSession, MicPermissionActivity (localOnly maps to the ON_DEVICE disclosure). Settings copy was rewritten so only AUTO says voice can leave the phone. New message `voice__msg_no_local_engine`. Tests: the selector test was rewritten (auto / onDeviceNeverUsesTheServerRecognizer / whisperFallsBackToOnDeviceOnly), plus VoiceControllerTest, VoiceImeSessionTest, VoiceSettingsTest (onlyAutoMentionsThatVoiceCanLeaveThePhone) and MicPermissionActivityTest.
- **R33 timeout**: `VoiceTimings.whisperTranscribeTimeoutMs = 120_000` and `transcribeTimeoutFor(kind)`. SpeechSession uses it. Tests: `whisperTranscribeTimeoutIsTwoMinutes` (still Transcribing at 119_999 ms, Error at 121 s) and `otherEnginesWithoutPartialsKeepTheFortyFiveSecondTimeout`.
- **Minors**
  - strings_fork.xml spoken punctuation quotes are escaped (`\"`). Test: `spokenPunctuationCopyKeepsItsQuotes`.
  - Download `.part` files are now per request (`<file>.<requestId>.part`). `deleteParts(spec, keep)` handles cleanup.
  - The worker checks that it is still current before renaming, and returns `Stopped` if not.
  - restoreDownloads keeps the pending request's partial file and drops all others.
  - Tests: deleteWhileDownloading…, aStoppedOldWorkerLeavesTheNewDownloadsPartialFileAlone, anOldWorkerThatFinishesAfterDeleteDoesNotInstallTheModel, aRetryResumesThePartialFileWithARangeRequest, pendingWorkIsDownloadingAfterARestartAndStalePartialFilesAreDropped.
  - PcmRecorder: `stop()` no longer blocks, and there is no `thread.join`. Each run has its own `AtomicBoolean`, and the capture thread stops and releases the AudioRecord itself. A SecurityException from `startRecording` releases the AudioRecord and is rethrown. A negative read calls `onFailure` once, and WhisperSpeechEngine turns that into `onError(ERROR_AUDIO = 3)`. Tests: aLostMicrophoneIsAnAudioErrorAndDropsTheAudio and aFailureFromAnOldRecordingIsIgnored.
  - VoiceSettingsScreen reads the model manager (WorkManager) only when `whisper.gate.anyEnabled`.

### TDD evidence
- RED: the first run failed to compile because the APIs did not exist yet. After adding stubs, the behavioural RED was 8 failures in core:voice (whisper timeout ×2, lost mic, selector ×2, manager RAM gate, stale partial, restore) and 2 in ime:voice-ui (controller, panel).
- These tests already passed on the old code: delete-while-downloading, stale-finish-after-delete (the old delete removed the shared part, so the rename failed) and the resume test. I kept them as regression guards.
- RED for the app tests (VoiceImeSession on-device-only, VoiceSettingsIntentTest, settings copy) was not captured, because I wrote those tests after the implementation.
- GREEN: `$G ./gradlew test :app:lintDebug :app:assembleDebug --continue` gave BUILD SUCCESSFUL, exit 0, 0 FAILED. Lint first flagged one new UseKtx warning (my `Uri.parse` in SmartbarVoicePanel). I switched it to `toUri()` and re-ran `:app:testDebugUnitTest --tests '*VoiceSettingsIntentTest*' :app:lintDebug :app:assembleDebug`, which passed. The 11 lint warnings left are not in files I touched.
- `scripts/check-16kb-alignment.sh app/build/outputs/apk/debug/*.apk`: OK (15 native libraries).
- `scripts/check-fork-markers.sh` (after the commit): OK.

### Deviations / decisions
- Analytics `UnavailableReason` stays at the 3 PRD values. The new `localOnly` flag on Unavailable selects the UI message (NO_LOCAL_ENGINE) and the disclosure kind, so no new analytics values were added.
- With WHISPER chosen and no model, the download is offered only when `whisperLangEnabled` is true. The release language gate is currently empty, so it is not always offered.
- The recorder no longer joins its thread. If recording restarts immediately, the old thread can run for up to about 100 ms alongside the new one. SpeechSession's 250 ms restart delay covers this.
- All changed :app files are in `fork/` packages, so no FORK markers or FORK_PATCHES rows were needed.
- The Zocdoc standards subagent was not spawned: the lead forbade subagents, and the zd-standards MCP was unreachable (ENOTFOUND).

### Earlier concerns
- #3 (Small gate) is resolved by R31.
- #6 (Whisper timeout) is resolved by R33.
