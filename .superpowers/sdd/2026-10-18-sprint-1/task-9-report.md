# Task 9 report: :core:voice

Implemented `:core:voice` (aikeyboard.android.library, namespace com.manjul.aikeyboard.core.voice; registered in settings.gradle.kts with FORK(S1-voice) marker + FORK_PATCHES row). Files: SpeechEngine (+EngineKind), VoiceState (+VoiceErrorKind), VoiceEvent (VoiceOutput), VoiceTimings, SpokenPunctuation, SpeechSession, plus FakeSpeechEngine and two test classes.

Tests: `./gradlew :core:voice:test` -> BUILD SUCCESSFUL, 29 tests (19 SpeechSession, 10 SpokenPunctuation) pass. `scripts/check-fork-markers.sh` OK after commit.

TDD evidence: honest deviation. I wrote implementation before running tests, so there is no true RED run. As substitute I ran a mutation (busy limit `>=` -> `>`): `busyDestroysAndRetriesThenFailsOnThird() FAILED, 29 tests completed, 1 failed`; reverted, GREEN again.

Decisions:
- Exposed `tapToResume: StateFlow<Boolean>`, `isSegmentedSupported(kind, lang)`, `startedAtMs` (clock use). Cap/silence use scope `delay` (virtual time).
- Segmented detection: marked false on an onFinal when no onSegment was seen in that engine run. After a final in continuous mode the engine is always restarted after restartDelayMs; SpeechEngine.start has no segmented flag (interface fixed by brief), so Task 10 should consult `isSegmentedSupported` itself if it needs to pick the mode.
- Non-continuous session ending on its own final: endReason "silence".
- On 6/7 restarts and on exhaustion, pending partial is committed; on cancel/fatal errors ComposingClear only.
- Extra error mapping: 10 and 11 -> NETWORK; unknown codes -> OTHER. Finalizing (partial-capable engine, user stop) has a 3000 ms safety timeout that commits the pending partial; Whisper Transcribing has no timeout.
- SpokenPunctuation: lang primary subtag, case-insensitive, longest phrase first, eats spaces before symbol (and around newline); no extra phrases beyond the brief.
- Output is a SharedFlow (buffer 64): collectors must subscribe before start().

Concerns: none blocking.

## Fix round 1 (commit 341e8379)
Changes: cap with a non-partial engine now calls stop() and goes to Transcribing (endReason "cap" on final); output is `Channel(UNLIMITED).receiveAsFlow()` (KDoc updated); errors while stopping (incl. BUSY) commit pending partial and finish with the stop reason, a Whisper engine with no text surfaces `Error(kind)` / endReason "error" since the recording is lost (my choice, consistent with the R20 timeout); `VoiceTimings.transcribeTimeoutMs = 45_000` -> `Error(OTHER)`, no Commit (a ClearComposing is emitted); onSegment resets restarts; loud rms (> 3 dB, `SPEECH_RMS_DB`) re-arms the silence window; segment detection only for continuous partial-capable engines; SpokenPunctuation uses inline `(?iu)` (RegexOption.UNICODE_CASE does not exist in Kotlin) and ur yeh/kaf normalisation with index mapping back onto the original text; KDoc for R19 and the punctuation-leading Commit (Task 11 strips the previous space).
Tests added: timeout at 44_999/45_001, cap+Whisper, lossless output, errors/BUSY while stopping (partial-capable and Whisper), stale callback after restart, cancel during restart gap, cap across restarts, segment resets counter, rms re-arm, segment detection scope, "À la ligne"/"Vírgula", ur yeh/kaf.
Result: `./gradlew :core:voice:test` BUILD SUCCESSFUL, 44 tests (32 + 12); `scripts/check-fork-markers.sh` -> "check-fork-markers: OK".

## Fix round 2
Replaced inline (?iu) with Pattern.compile(src, CASE_INSENSITIVE or UNICODE_CASE).toRegex(). Command: `./gradlew :core:voice:test`
```

BUILD SUCCESSFUL in 986ms
49 actionable tasks: 3 executed, 46 up-to-date
Consider enabling configuration cache to speed up this build: https://docs.gradle.org/9.2.0/userguide/configuration_cache_enabling.html
```
