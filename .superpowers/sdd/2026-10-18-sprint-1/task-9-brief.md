### Task 9: `:core:voice` session state machine and error policy

**Files:**
- Create: `core/voice/build.gradle.kts` (`aikeyboard.android.library`), `core/voice/src/main/kotlin/com/manjul/aikeyboard/core/voice/{SpeechEngine,SpeechSession,VoiceState,VoiceEvent,VoiceTimings,SpokenPunctuation}.kt`
- Test: `core/voice/src/test/.../{SpeechSessionTest,SpokenPunctuationTest}.kt`, `core/voice/src/test/.../FakeSpeechEngine.kt`

**Interfaces:**
- Produces:
  - `interface SpeechEngine { val kind: EngineKind; val supportsPartials: Boolean; fun start(lang: String, listener: Listener); fun stop(); fun cancel(); fun destroy(); interface Listener { fun onReady(); fun onRms(db: Float); fun onPartial(text: String); fun onSegment(text: String); fun onFinal(text: String); fun onEndOfSpeech(); fun onError(code: Int) } }`; `enum class EngineKind(val analytics: String) { SYSTEM_ON_DEVICE("system_on_device"), SYSTEM_DEFAULT("system_default"), WHISPER("whisper") }`.
  - `sealed interface VoiceState { Idle; Starting; Listening(rmsDb: Float); Transcribing /* whisper */; Finalizing; data class Error(val kind: VoiceErrorKind) }`; `enum class VoiceErrorKind { NO_PERMISSION, NETWORK, LANGUAGE_UNAVAILABLE, BUSY, NO_ENGINE, OTHER }`.
  - `sealed interface VoiceOutput { data class Composing(val text: String); data class Commit(val text: String); object ClearComposing }`.
  - `data class VoiceTimings(val silenceMs: Long = 2000, val restartDelayMs: Long = 250, val maxRestarts: Int = 3, val busyDelayMs: Long = 400, val maxBusyRetries: Int = 2, val sessionCapMs: Long = 60_000)`.
  - `class SpeechSession(engineFactory: () -> SpeechEngine, scope: CoroutineScope, timings: VoiceTimings, punctuation: SpokenPunctuation, clock: () -> Long) { val state: StateFlow<VoiceState>; val output: Flow<VoiceOutput>; val endReason: String? ; fun start(lang: String, continuous: Boolean); fun stopByUser(); fun cancel() /* field change, hide, sensitive: ClearComposing, no Commit */ }`. Segmented-session detection: if a session ends with `onFinal` and no `onSegment` was seen, `segmentedSupported[engineKind+lang] = false` and later sessions use the restart loop.
  - `object SpokenPunctuation { fun apply(text: String, lang: String): String }` — en "comma"→",", "full stop"/"period"→".", "question mark"→"?", "new line"→"\n"; ur/hi/ar/es/pt/fr/id forms in a table (ur "کاما"→"،", "سوالیہ نشان"→"؟", "نئی لائن"→"\n"; hi "अल्पविराम"→",", "पूर्ण विराम"→"।", "नई लाइन"→"\n"; ar "فاصلة"→"،", "علامة استفهام"→"؟", "سطر جديد"→"\n"; es "coma", "punto", "nueva línea"; pt "vírgula", "ponto final", "nova linha"; fr "virgule", "point", "à la ligne"; id "koma", "titik", "baris baru").

- [ ] **Step 1: Write failing tests** with `FakeSpeechEngine` and virtual time:
  - partials emit `Composing` (latest only), final emits `Commit("hello ")` (trailing space unless the text ends with "\n").
  - continuous: after a final, no speech for 2000 ms → session ends, `endReason = "silence"`; with `silenceMs = 5000` it waits 5000 ms.
  - error 6 then 7 then 6 → three restarts each after 250 ms; the 4th consecutive → stop with `endReason = "error"`, state `Idle` and a "tap to resume" flag; a final in between resets the counter.
  - error 8 → `destroy()` then new engine after 400 ms; third consecutive 8 → `Error(BUSY)`.
  - error 9 → `Error(NO_PERMISSION)`, no restart, no activity launch.
  - network errors 2/1/4 → `Error(NETWORK)`; 12/13 → `Error(LANGUAGE_UNAVAILABLE)`.
  - 60 s cap → `endReason = "cap"`, pending partial committed.
  - `cancel()` mid-partial → `ClearComposing`, no `Commit`, engine destroyed, state `Idle`.
  - Whisper-kind engine (`supportsPartials = false`): `stopByUser()` → state `Transcribing` until `onFinal`.
  - Segment fallback detection as specified above.
  - `SpokenPunctuationTest`: one case per language per mapping; words inside other words not replaced ("commander" unchanged).
- [ ] **Step 2: Run** `$G ./gradlew :core:voice:test` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `$G ./gradlew :core:voice:test` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(voice): speech session state machine and error policy`.

