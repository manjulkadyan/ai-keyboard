# Sprint 1 final review (whole branch)

Branch `sprint-1`, base `75ec4b14`, head `3bb0d56b`. Reviewer: final-rev. Date 2026-10-20.

## How the diff was covered

- `git diff --stat 75ec4b14 3bb0d56b` for scope, then targeted `git diff` of every upstream-edited file
  (FlorisImeService, KeyboardManager, EditorInstance/AbstractEditorInstance, ClipboardManager, ClipboardDatabase,
  AppPrefs, Smartbar, FlorisApplication, FlorisAppActivity, manifest, build scripts).
- Fork modules read in full: `:ime:policy`, `:ime:voice-ui`, `:core:analytics`, `:core:datastore`, `:core:voice`
  (platform + whisper), `:lib:whisper` (Kotlin, JNI C++, consumer rules), `app/.../fork/**` (policy, voice,
  smartbar, onboarding, clipboard gate).
- Module graph checked from every `build.gradle.kts` (no `:ime*` -> `:app`/`:core:ads`/`:core:billing`/`:feature:*`; no DI library).
- Grep sweeps over added lines: `Log.`/`flog`, exception messages, analytics call sites vs `EventCatalog`,
  `killProcess`/`System.exit`/`exitProcess`/`fallbackToDestructiveMigration`, string concatenation with resources,
  popup/swipe routes to `IME_UI_MODE_CLIPBOARD` and `VOICE_INPUT`.
- `scripts/check-fork-markers.sh 75ec4b14 3bb0d56b` -> OK (96 modified, 6 marker-exempt).
- `./gradlew test --continue` (with truststore) -> BUILD SUCCESSFUL, 0 failures.
- Not re-run: `assembleRelease`/R8 (done by Task 14; keep rules for JNI read and correct; Room/WorkManager ship their own consumer rules).

## Strengths

- Policy is a single source of truth: `ImePolicyController` decides once per `onStartInput`, fails closed
  (Locked / NoField), re-evaluates on settings emission (R7), and every consumer (editor learning, smartbar chips,
  voice key, clipboard capture gate, analytics minimal mode) reads the same `PolicyDecision`.
- Clipboard capture gate runs before `fromClipData`, and `insertOrMoveBeginning` is gated too: belt and braces.
  Destructive migration removed from both clipboard databases; TTL delete is a single DAO query.
- Voice stack is carefully generation-tokened: stale recognizer callbacks cannot write into a new field;
  composing span is discarded on cancel/field change; mic permission trampoline never starts recording itself.
- Whisper lifecycle is sound: one mutex-guarded context, abort-by-owner, capture thread owns `AudioRecord`, PCM
  buffer zeroed, native logging discarded, idle unloader + trim-memory, model download pinned by SHA-256 and fails
  closed when the pin is empty (R28), release gate `WHISPER_ENABLED_LANGS = ""`.
- Analytics is a closed catalog with registry/limit tests; release sink is `NoopSink`; no user content reaches logs
  or exception messages anywhere I looked.
- FORK marker discipline is mechanically enforced and passes; module boundaries hold.
- Test suite is broad and green, and the egress test exercises a real scripted password session (proxy selector +
  WorkManager + Room) rather than only fakes.

## Issues

### Critical

None.

### Important

1. **Clipboard history panel reachable in password fields and private apps.**
   `app/src/main/kotlin/com/manjul/aikeyboard/ime/keyboard/KeyboardManager.kt:755`
   (`KeyCode.IME_UI_MODE_CLIPBOARD -> activeState.imeUiMode = ImeUiMode.CLIPBOARD`) is ungated. Only the chip path
   is gated (`evaluateEnabled`, KeyboardManager.kt:1011). Two other routes reach the handler:
   the Enter long-press popup (`app/src/main/assets/ime/keyboard/org.florisboard.localization/popupMappings/default.json:7`,
   code -213; popups do not consult `evaluateEnabled`) and the swipe action `SWITCH_TO_CLIPBOARD_CONTEXT`
   (KeyboardManager.kt:284). In a password field or a private app the user can open full clipboard history and
   paste earlier clips, and see them on screen (screen share, shoulder surfing). Also, nothing closes an already
   open clipboard panel on a more-restrictive restart: `FlorisImeService.kt:415` keeps CLIPBOARD mode when the
   upstream pref `historyHideOnNextTextField` is off, and the restriction listeners do not reset `imeUiMode`; the
   same gap applies when a private-app decision lands after settings load (R7) while the panel is open.
   This breaks the privacy invariant "sensitive field: no clipboard history" and "more-restrictive restart closes panels".
   Fix: gate the handler at KeyboardManager.kt:755 with `policyDecision().allowClipboardHistory` (covers chip, popup
   and swipe at once), and in the policy restriction listener set `activeState.imeUiMode = ImeUiMode.TEXT` when
   `!allowClipboardHistory`. Add a test that dispatches -213 under a password decision and asserts mode stays TEXT.

2. **`ime_session_start` is never emitted.**
   `core/analytics/src/main/kotlin/com/manjul/aikeyboard/core/analytics/EventCatalog.kt:84` and
   `core/analytics/src/main/kotlin/com/manjul/aikeyboard/core/analytics/SessionSampler.kt:9` have no production
   caller (only tests). PRD §12 defines it as the IME-session event (sampled 1 per 10 min) and it is the only event
   the minimal-analytics policy is designed to let through (`is_sensitive=true`). Without it the D1/D7 retention
   and sensitive-session metrics the PRD relies on cannot be computed from Sprint 1 builds, and the minimal-mode
   pass-through is dead code. Plan Task 6 only asked for the factory, so this is a plan gap, not an implementer miss.
   Fix: in `FlorisImeService.onStartInputView`, after the policy decision, call
   `if (sampler.shouldLogImeSession()) analytics.log(EventCatalog.imeSessionStart(subtypeTag, decision.analyticsMinimal))`
   with one `SessionSampler(System::currentTimeMillis)` per service; add a FORK marker and a FORK_PATCHES row.

### Minor (7)

1. `app/src/main/kotlin/com/manjul/aikeyboard/fork/voice/VoiceImeSession.kt:59` - the default `caps` argument
   touches `context.whisperVoice().value` at every IME `onCreate`, contradicting the FlorisApplication.kt:109
   intent ("created on first Whisper use") and making `isInitialized()` in `onTrimMemory` (FlorisApplication.kt:120)
   always true. Cheap today; pass a lazy capabilities provider instead.
2. `app/src/main/kotlin/com/manjul/aikeyboard/fork/voice/WhisperVoice.kt:43,57` - first `whisperReady` call
   (first mic tap, main thread, debug/beta where the gate is open) constructs `WhisperModelManager`
   (file stat + `WorkManager.getInstance`). Small main-thread I/O; prewarm on a background dispatcher or check on IO.
3. `core/analytics/src/main/kotlin/com/manjul/aikeyboard/core/analytics/BatchingRelay.kt:69` - the minimal-policy
   filter runs at flush time, so events logged in a normal field are dropped if the 60 s flush happens while a
   sensitive field is focused (including `voice_session_end` with `end_reason=field_change` when the user moves
   into a password field). Metric loss, not a leak. Filter at `log()` time against the decision then current.
4. `app/src/main/kotlin/com/manjul/aikeyboard/fork/voice/ImeTextTarget.kt:24` - `owned` is not reset when the
   InputConnection changes on a non-restart field switch; a later `clearComposing`/`finishComposing` may act on the
   new connection. The expected-text check limits damage; reset `owned` in `onStartInput`.
5. `app/src/main/kotlin/com/manjul/aikeyboard/FlorisImeService.kt:459-462` - on a field change through
   `onFinishInput`, the base class calls `finishComposingText()` before our cancel, so a partial transcript is
   committed into the old field, whereas hide (R25) discards it. Not a leak (it stays in the field it was dictated
   into), but behaviour differs by path; cancel voice before `super.onFinishInput()` for consistency.
6. `app/src/main/kotlin/com/manjul/aikeyboard/fork/smartbar/PolicyQuickActions.kt:36` - non-chip upstream quick
   actions (undo/redo etc., if the user adds them) pass `isAllowedBy` in password fields. TRD exit says "no smartbar
   action appears"; checklist documents emoji/settings as by design, but the rest is undocumented. Either filter to
   an allow-list or record the deviation.
7. `core/voice/src/main/kotlin/com/manjul/aikeyboard/core/voice/whisper/WhisperIdleUnloader.kt:37` - threshold
   `>= TRIM_MEMORY_RUNNING_LOW` also matches `TRIM_MEMORY_UI_HIDDEN` (20), so the model is freed whenever the
   settings UI is backgrounded, forcing a reload on next dictation. Test at WhisperIdleUnloaderTest.kt:68 locks it in;
   decide deliberately (exclude UI_HIDDEN or keep and document).

## Declined to judge

- Native socket egress from whisper.cpp: the egress test cannot observe native sockets (parked in ledger); needs a device network capture.
- R8/release APK behaviour: not rebuilt here; relied on Task 14's `assembleRelease` and read the keep rules.
- On-device recognizer quality and segmented-session behaviour on API 33+: needs real devices (device checklist).
- 16 KB page alignment of `libwhisper.so`: script exists, not run against a release artifact in this review.
- Strings/localization quality of `strings_fork.xml` translations: outside reviewer competence.

## Recommendations

- Fix Important 1 before merge; it is a small, contained change with a clear test.
- Fix Important 2 before the first beta that is meant to produce retention data (can follow merge if beta is not imminent).
- Add a privacy regression test that walks every route to clipboard/voice (chip, key, popup, swipe) under each restrictive decision.
- Batch the Minors into a Sprint 2 hygiene task.

## Assessment

Ready to merge? **With fixes.**

The architecture is solid and the privacy model is centralised and mostly enforced end to end; tests are green and
boundaries hold. One privacy invariant (no clipboard history in sensitive fields, panels close on restriction) is
breakable through upstream routes the chip gating does not cover, and the sprint's core usage event is never sent.
Both fixes are small. With Important 1 fixed (and Important 2 fixed or explicitly deferred by the lead), merge.
