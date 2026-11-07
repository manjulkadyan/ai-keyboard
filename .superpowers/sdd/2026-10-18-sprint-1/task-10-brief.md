### Task 10: Platform recognizer engine, selection and audio focus

**Files:**
- Create: `core/voice/src/main/kotlin/com/manjul/aikeyboard/core/voice/platform/{PlatformSpeechEngine,SpeechEngineSelector,RecognizerCapabilities,AudioFocusHolder}.kt`
- Modify (FORK S1-voice): `app/AndroidManifest.xml` (`<queries><intent><action android:name="android.speech.RecognitionService"/></intent></queries>`), `docs/FORK_PATCHES.md`
- Test: `core/voice/src/test/.../platform/{SpeechEngineSelectorTest,AudioFocusHolderTest,PlatformSpeechEngineTest}.kt`, `app/src/test/.../manifest/ManifestHardeningTest.kt` (queries present)

**Interfaces:**
- Consumes: Task 9 `SpeechEngine`, `EngineKind`.
- Produces:
  - `interface RecognizerCapabilities { val sdkInt: Int; fun isRecognitionAvailable(): Boolean; fun isOnDeviceAvailable(): Boolean; suspend fun onDeviceSupports(lang: String): Boolean? /* null = unknown below 33 */; fun whisperModelReady(lang: String): Boolean; fun whisperLangEnabled(lang: String): Boolean }`.
  - `sealed interface EngineChoice { data class Use(val kind: EngineKind); data class Unavailable(val reason: String /* no_service|lang_unsupported|offline_no_model */, val offerSystemDownload: Boolean, val offerWhisperDownload: Boolean) }`.
  - `class SpeechEngineSelector(caps: RecognizerCapabilities) { suspend fun choose(lang: String, preferWhisper: Boolean, online: Boolean): EngineChoice }` (`:core:voice` does not depend on `:core:datastore`; the caller maps `VoiceEnginePreference.WHISPER` to `preferWhisper = true`) per TRD §4.2.2 + §4.2.4 order: preferWhisper and model ready → WHISPER; API 31+ on-device available and supports lang (or unknown) → SYSTEM_ON_DEVICE; recognition service available and `online` → SYSTEM_DEFAULT (with `EXTRA_PREFER_OFFLINE`); whisper enabled+ready → WHISPER; else `Unavailable`.
  - `class PlatformSpeechEngine(context, onDevice: Boolean, mainHandler) : SpeechEngine` — `EXTRA_PARTIAL_RESULTS=true`, `EXTRA_LANGUAGE`, `EXTRA_PREFER_OFFLINE=true`, API 33+ `EXTRA_ENABLE_FORMATTING` and `EXTRA_SEGMENTED_SESSION` when the session asks for it; maps `RecognitionListener` to `SpeechEngine.Listener`.
  - `class AudioFocusHolder(audioManager) { fun acquire(): Boolean; fun release() }` — `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, idempotent release.
- [ ] **Step 1: Write failing tests.** `SpeechEngineSelectorTest`: table-driven over (sdk, service, onDevice, supports, online, whisper ready/enabled, preferWhisper) → expected choice, including: no service + no model → `Unavailable("no_service", offerWhisperDownload = whisperLangEnabled)`; offline + no on-device + no model → `Unavailable("offline_no_model", offerSystemDownload = sdk >= 33, …)`; preferWhisper + model missing falls through to system. `AudioFocusHolderTest` (Robolectric `ShadowAudioManager`): acquire then release → focus abandoned; double release safe. `PlatformSpeechEngineTest` (Robolectric `ShadowSpeechRecognizer`): `start` builds the intent with the extras above for API 33 and omits formatting/segmented on API 30; `destroy` calls `SpeechRecognizer.destroy()`.
- [ ] **Step 2: Run** `$G ./gradlew :core:voice:test :app:testDebugUnitTest --tests '*ManifestHardening*'` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** the same — Expected: PASS.
- [ ] **Step 5: Commit** `feat(voice): platform recognizer engine, selection order and audio focus`.

