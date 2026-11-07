# Task 10 report

Implemented in `:core:voice` `...core.voice.platform`: `PlatformSpeechEngine` (+ internal `buildRecognizerIntent(lang, sdkInt, segmented)`), `SpeechEngineSelector`/`EngineChoice`, `RecognizerCapabilities` + `PlatformRecognizerCapabilities` (prod; whisper* return false until Task 13), `AudioFocusHolder` (adds `isHeld`).
Manifest `<queries>` RecognitionService (FORK S1-voice) + FORK_PATCHES row; `ManifestHardeningTest` appended; `core/voice/build.gradle.kts` applies robolectric-offline and adds robolectric.

Decisions:
- `PlatformSpeechEngine(context, onDevice, segmented = false, mainHandler)`. `onSegmentResults` -> `onSegment`; `onEndOfSegmentedSession` -> `onFinal("")` (text already delivered via segments; session's onEndOfSpeech is a no-op, so only onFinal drives restart/finish, and commit("") just clears composing).
- Selector: on-device branch requires sdk >= 31 and `isOnDeviceAvailable`; reasons: no service -> no_service; on-device said unsupported -> lang_unsupported; else offline_no_model. offerSystemDownload = reason != no_service && sdk >= 33. offerWhisperDownload = whisperLangEnabled. Whisper fallback needs enabled+ready.
- onDeviceSupports: main-thread recognizer, 1500 ms timeout -> null; null also on callback error; matches installed languages by full tag or primary subtag.
- Robolectric ShadowSpeechRecognizer posts setRecognitionListener; tests idle the main looper.

Tests: `./gradlew :core:voice:test :app:testDebugUnitTest --tests '*ManifestHardening*'` BUILD SUCCESSFUL; check-fork-markers OK.
TDD: tests written first against missing classes (compile-RED); first GREEN run had 3 failures (test-data error in selector case, listener not idled), fixed in tests. No separate RED log captured.
Not covered: onDeviceSupports (no shadow-driven test), createOnDeviceSpeechRecognizer path.

## Fix round 1
- I1: `useOnDeviceFactory(onDevice, sdkInt)` (>= S) used by `PlatformSpeechEngine`; tests for 30/31/33 plus an onDevice=true engine start test.
- M1: `onDeviceSupports` main-thread block wrapped in runCatching (destroys recognizer, resumes null); pure `languageInstalled` + `LanguageInstalledTest` (exact, language-only, no match).
- M2: SpeechSession marks segmented unsupported only when final text is non-blank and no segment arrived; test `blankFinalWithoutSegmentsKeepsSegmentedSupported`.
- M3: double-release test proves abandon ran once (second abandon would overwrite the last abandoned request recorded by another holder).
- M4: added cases. Order used (brief): preferWhisper+model ready -> WHISPER (language enablement not required); on-device only on sdk>=31; SYSTEM_DEFAULT needs service+online; whisper fallback needs enabled+ready. So: sdk30/onDevice=true offline -> offline_no_model; whisper ready but not enabled offline -> offline_no_model (offerSystemDownload sdk>=33); preferWhisper ready/not enabled -> WHISPER.
- M5: import order fixed.
Verification: `:core:voice:test :app:testDebugUnitTest --tests '*ManifestHardening*'` -> BUILD SUCCESSFUL; `scripts/check-fork-markers.sh` -> check-fork-markers: OK.

## Fix round 2
`recognizer` assigned right after createOnDeviceSpeechRecognizer so every failure path destroys it; ManifestHardeningTest imports sorted.
Command: `./gradlew :core:voice:test :app:testDebugUnitTest --tests '*ManifestHardening*'`
```

BUILD SUCCESSFUL in 10s
263 actionable tasks: 31 executed, 232 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.2.0/userguide/configuration_cache_enabling.html
```
