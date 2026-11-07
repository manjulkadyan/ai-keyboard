# Task 11 report: voice in the keyboard

Status: DONE_WITH_CONCERNS
Commit: `8a532cad feat(voice): in-keyboard voice typing with composing span and permission trampoline` (branch `sprint-1`, not pushed)

## What was implemented

**New module `:ime:voice-ui`** (Android library + Compose, no `:app` types, no ads/billing/feature deps):
- `ComposingSpanController`: owns the voice composing region through a `TextTarget` (`setComposing`, `commit(text, removeSpaceBefore)`, `finishComposing`). Keeps a queue of expected region lengths so our own late `onUpdateSelection` reports do not look like host edits. If the region disappears or the cursor moves into it, it drops the span once (finish composing, never delete) and the next partial opens a new span at the cursor. A commit that starts with punctuation removes the previous trailing space, so "hello " + ", " gives "hello, ". This only happens when the last thing we committed is still right before the cursor.
- `VoiceController`: drives `toggle` / `cancel(reason)` / `dismiss` / `requestPermission` / `downloadSystemModel`.
  - Order of checks: policy first (a no-op unless `allowVoice`), then permission (shows a chip, never launches anything on its own), then engine choice via `SpeechEngineSelector` (an `Unavailable` result becomes `Message(OFFLINE_NO_MODEL, offerSystemDownload)`), then audio focus (`MIC_BUSY` on denial), then `SpeechSession`.
  - The `FieldGeneration` is captured at start. Output for a stale generation is discarded.
  - Analytics: `voiceSessionStart` / `voiceSessionEnd` (`end_reason`, word count, duration, no text), `voiceUnavailable`, and `firstDictation` once, persisted in `firstDictationAt`.
- `VoiceUiState`, `VoicePanel` (Listening: waveform + status + stop; Transcribing; NeedsPermission / TapToResume: chip + close; Message: status + optional "download" chip + close), `Waveform` (rms-driven Canvas), `VoiceChip`, `strings.xml` (copy includes "Allow microphone, then tap the mic again"). Panel text uses `TextDirection.Content`.

**`:app` `fork/voice`:**
- `ImeTextTarget`: the InputConnection side of `TextTarget`. Uses batch edits, calls `finishComposingText` on any region we do not own before writing our own, and deletes at most one character, and only when it is a space, for the punctuation join. Tells the editor via `setOwnsComposing`.
- `VoiceImeSession`: wires the controller to `ImePolicyController`, `FeatureSettingsStore` (`voiceSilenceMs`, `spokenPunctuation`, `voiceEnginePreference`, `firstDictationAt`), the platform engines and caps, `ConnectivityManager` (`online`) and `AudioFocusHolder`.
  - WHISPER is reported as unsupported until Task 13.
  - Restriction listener and `onFinishInputView` both call `cancel("field_change")`.
  - API 33+ system model download goes through `createOnDeviceSpeechRecognizer().triggerModelDownload`.
- `MicPermissionActivity`: the trampoline. It is opened only from the chip tap, with `NEW_TASK`.
  - Finishes at once if permission is already granted.
  - Otherwise shows `VoiceDisclosureSheet` first unless `consentVoiceNoticeAt > 0`, then requests `RECORD_AUDIO` and logs `voicePermissionResult`.
  - On grant it finishes and never starts dictation. On denial it shows a rationale with "Open app settings" / "Not now".
  - Maps `EngineChoice` to `DisclosureEngineKind` (R3).
- `smartbarVoicePanel()`: renders `VoicePanel` in the smartbar main row after `PrivateLockIndicator` while voice is not Hidden. On API 33+, back dismisses it via `OnBackInvokedDispatcher.PRIORITY_OVERLAY`.

**Upstream hunks** (all `FORK(S1-voice)`, rows in `docs/FORK_PATCHES.md`):
- `KeyboardManager`: `VOICE_INPUT` → `voiceSessionOrNull()?.controller?.toggle()`, still behind `policyDecision().allowVoice`.
- `AbstractEditorInstance`: `voiceOwnsComposing` flag; `handleSelectionUpdate` skips its composing re-sync while it is set.
- `FlorisImeService`: creates the session in `onCreate`; forwards `onUpdateSelection`, `onFinishInputView` and `onDestroy`; companion `voiceSessionOrNull()`.
- `Smartbar`: one line after `PrivateLockIndicator()`.
- `AndroidManifest`: `ACCESS_NETWORK_STATE` and the activity (translucent `FlorisAppTheme.Transparent`, `excludeFromRecents`, `noHistory`, `taskAffinity=""`, `exported=false`).
- `settings.gradle.kts`, `app/build.gradle.kts`, `strings_fork.xml`.

**Core changes:**
- `SpeechSession(punctuationEnabled = true)`: when false, spoken punctuation words are kept as spoken (needed for the `spokenPunctuation` setting).
- `FeatureSettings.firstDictationAt: Long = 0L`.

## Tests

| Suite | Count | Result |
|---|---|---|
| `:ime:voice-ui:testDebugUnitTest` (`ComposingSpanControllerTest` 9, `VoiceControllerTest` 13) | 22 | pass |
| `:core:voice:testDebugUnitTest` (incl. new `spokenPunctuationOffKeepsWordsAsSpoken`) | 60 | pass |
| `:core:datastore:testDebugUnitTest` (round trip incl. `firstDictationAt`) | 4 | pass |
| `:app:testDebugUnitTest` (all, incl. new `VoiceImeSessionTest` 5, `MicPermissionActivityTest` 7; ManifestHardening, IME isolation etc. unchanged and green) | 120 | pass |
| `:app:lintDebug` | - | no new findings (10 warnings, all pre-existing upstream/baseline; my one ComposableNaming warning was fixed by renaming to `smartbarVoicePanel`) |
| `:app:assembleDebug` | - | OK |

Brief Step 2/4 were run as `:ime:voice-ui:testDebugUnitTest` (the module is Android, so `:test` is not the task name) plus the full `:app:testDebugUnitTest` rather than `--tests '*voice*'`, which is a superset.

Key behaviours covered:
- Punctuation join "hello " + ", " → "hello, ".
- No join after a host change.
- Drop never deletes host text.
- Password restart mid-partial: the partial is finished, not committed, the session ends with `field_change`, focus is abandoned, the engine is destroyed, and a late final is ignored.
- Host edit during a partial: 0 deletes.
- Stale-generation commit is discarded.
- Policy-locked toggle is a no-op.
- Permission chip launches the activity only on tap, and only once.
- `Unavailable` → offline message with download chip.
- `MIC_BUSY`.
- `first_dictation` logged once and persisted, not logged for zero words.
- Trampoline: notice before request, skip when agreed, not now, grant (finish, log, no other activity), denial rationale + settings intent, already granted, engine mapping.

## TDD evidence

- RED captured for `SpeechSession.punctuationEnabled`: the new test failed to compile (unknown parameter), then went GREEN after the change.
- Mutation check on the controller: reverting the deferred `end()` (`scope.launch { end(...) }` back to a direct call) fails `firstDictationIsLoggedOnceForASessionWithWords` (expected 1, got 0) because the last output had not been collected yet.
- First runs of `MicPermissionActivityTest` failed 3/7 on real issues in the tests' assumptions (request happens after the async consent write; Robolectric records the permission dialog as a started activity; a destroyed activity in the already-granted case). The tests were fixed, not the production code.
- Honest gap: most of `:ime:voice-ui`, `ImeTextTarget`, `VoiceImeSession` and `MicPermissionActivity` were drafted before their tests, so for those there is no strict RED-first record. `firstDictationAt` was also not RED-first.

## Fork-marker check (run after committing)

```
check-fork-markers: upstream v0.5.2; 96 modified (6 marker-exempt), 202 rename-only, 207 deleted
check-fork-markers: OK
```

## Files

New:
- `ime/voice-ui/build.gradle.kts`
- `ime/voice-ui/src/main/kotlin/com/manjul/aikeyboard/ime/voiceui/{ComposingSpanController,VoiceController,VoiceUiState,VoicePanel,VoiceChip,Waveform}.kt`
- `ime/voice-ui/src/main/res/values/strings.xml`
- `ime/voice-ui/src/test/kotlin/com/manjul/aikeyboard/ime/voiceui/{ComposingSpanControllerTest,VoiceControllerTest}.kt`
- `app/src/main/kotlin/com/manjul/aikeyboard/fork/voice/{ImeTextTarget,VoiceImeSession,MicPermissionActivity,SmartbarVoicePanel}.kt`
- `app/src/test/kotlin/com/manjul/aikeyboard/fork/voice/{VoiceImeSessionTest,MicPermissionActivityTest}.kt`

Modified:
- `settings.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/values/strings_fork.xml`
- `app/src/main/kotlin/com/manjul/aikeyboard/FlorisImeService.kt`
- `app/src/main/kotlin/com/manjul/aikeyboard/ime/keyboard/KeyboardManager.kt`
- `app/src/main/kotlin/com/manjul/aikeyboard/ime/editor/AbstractEditorInstance.kt`
- `app/src/main/kotlin/com/manjul/aikeyboard/ime/smartbar/Smartbar.kt`
- `core/voice/.../SpeechSession.kt` (+ test)
- `core/datastore/.../FeatureSettings.kt` (+ test)
- `docs/FORK_PATCHES.md`

## Self-review

- No transcript or typed text, and no package names, in logs, analytics or exceptions. The new code has no log calls at all, and analytics carry only counts, durations, reasons, booleans and error kinds.
- R21 holds: `:ime:voice-ui` has no `:app` imports. Module rules pass (the build would fail otherwise).
- Upstream hunks are small (one line in each of KeyboardManager and Smartbar; a flag plus one condition in AbstractEditorInstance; five call sites in FlorisImeService), each with a marker within 3 lines.
- Every piece of user copy is in strings.xml / strings_fork.xml, with no concatenation.
- No banned APIs.

## Concerns

1. **Not verified on a device** (no emulator): real `SpeechRecognizer` behaviour, predictive back at `PRIORITY_OVERLAY` from an IME window, `triggerModelDownload`, and how the translucent trampoline looks over other apps.
2. **`noHistory="true"` + permission dialog**: the brief asks for `noHistory`. On some OEMs a `noHistory` activity can be finished when the permission dialog covers it, which would drop the result. This is untested on a device. If it shows up, removing `noHistory` is safe because the activity finishes itself on every path.
3. **Host shifts our region**: the controller drops conservatively (finishes composing, never deletes). The test case "abc hel" plus a host insert at 0 then partial "hello" gives "Xabc helhello", which duplicates the partial. We chose never to delete host text over a clean result.
4. **Password restart**: the partial "hel" stays in the field as plain text (finished, not committed through `commitText`). Removing it would need a delete in a field we no longer trust.
5. `voiceSessionEnd.errorCode` is always null: `SpeechSession` exposes only error kinds, not raw recognizer codes.
6. The trampoline's theme is fixed at `AppTheme.AUTO` rather than the user's app theme preference (avoids loading FlorisBoard prefs in the trampoline).

## Decisions (where brief and HEAD differed, code won)

- The editor hunk is only the `voiceOwnsComposing` flag. The brief's `setComposingRegionText` / `finishOwnComposing` editor methods were not added; that logic lives in `ImeTextTarget` so the upstream hunk stays small.
- Analytics come from the existing `appContext().analytics` (there is no `Context.analytics()` accessor at HEAD).
- Added `ACCESS_NETWORK_STATE` (required by `ConnectivityManager.getNetworkCapabilities`, needed for `online`).
- `SpeechSession` got `punctuationEnabled` because the `spokenPunctuation` setting had no hook in the core session.
- `end()` is deferred with `scope.launch` because `SpeechSession` queues its last output before it goes Idle. The output collector must run first, or the commit and word count are lost.
- The engineering-standards subagent was not spawned: the contract forbids subagents, and the zd-standards MCP server failed to connect this session (outage, not a clean pass).

---

# Fix round 1

Commit: `ffe72272 fix(voice): address review for task 11` (on top of `8a532cad`; nothing amended, not pushed)

## Changes

**Important 1: our partial in a field that became sensitive (R24).**
- `TextTarget.clearComposing(expected)`. `ImeTextTarget` acts only when voice owns the region and `getTextBeforeCursor(expected.length) == expected`; the comparison is in memory and never logged. It then runs `setComposingText("", 1)` and `finishComposingText()` in one batch edit. If the guard fails, it only finishes the region; it never falls back to a delete.
- `ComposingSpanController.discard()` calls it with the current span text. A span already let go of (after a host change) is left alone.
- `VoiceController.cancel()` uses `discard()`. `drop()` is kept for host changes and `ClearComposing`.
- Behaviour change to flag: `onFinishInputView` also goes through `cancel()`, so hiding the keyboard mid-partial now removes the unfinished partial too (it used to stay as plain text). `finishInputViewStopsAndAbandonsFocus` was updated to assert this.

**Important 2: the punctuation join could delete a host character.**
- (a) `VoiceController.begin()` calls `span.startSession()`, which resets `trailingSpace`.
- (b) Each commit has an id. The first selection update matching the latest commit records the cursor. A later update with a different `newSelStart` clears `trailingSpace`. Updates for older commits that arrive late are ignored, so two commits reported late still join correctly.

**Minors**
- **Re-anchor:** a region the host shifted intact (same length via the expect queue, cursor at `candEnd`, only `candStart` moved) stays ours. The `anchor` check was removed. A moved cursor or a changed length still drops. "Xabc helhello" is now "Xabc hello".
- **`errorCode`:** `SpeechSession.lastErrorCode: Int?` (private set) is set in `handleError` and reset in `start()`. `voiceSessionEnd` gets it only when the end reason is ERROR.
- **`MicPermissionActivity`:** the rationale screen is saved in the instance state and restored after recreation. The notice is rebuilt by the normal flow, so it names the right engine.
- **Double policy check:** left as it is (defense in depth), as ruled.

## Tests (new or changed)

- `ComposingSpanControllerTest` (+7):
  - `discardRemovesOurOwnPartial`
  - `discardAfterTheHostTookTheRegionClearsNothing`
  - `hostMovingTheCursorAfterACommitBlocksThePunctuationJoin`
  - `repeatedReportsOfOurCommitKeepThePunctuationJoin`
  - `twoCommitsReportedLateStillJoin`
  - `newSessionNeverJoinsTheLastCommitOfTheOldOne`
  - `regionShiftedIntactByTheHostIsReanchored`
- `VoiceControllerTest` (+3, 1 changed):
  - `aNewSessionNeverJoinsPunctuationToTheLastSessionsCommit`
  - `errorEndCarriesTheRecognizerErrorCode`
  - `userEndCarriesNoErrorCode`
  - Changed: `cancelReleasesFocusAndLogsFieldChange` now expects `clear:hel`.
- `SpeechSessionTest` (+1): `lastErrorCodeKeepsTheRawRecognizerCodeUntilTheNextStart`.
- `VoiceImeSessionTest` (+2, 3 changed), with a real EditText input connection:
  - Password restart: the field text is `""`, `deletes == 0`, no commits.
  - New `cancelLeavesTheFieldAloneWhenTheTextBeforeTheCursorIsNoLongerOurs`: the guard fails, the text stays "abc hel", the region is finished, 0 deletes.
  - New `punctuationAfterTheHostMovedTheCursorDeletesNothing`: commit "hello ", the host moves the cursor to just after its own space with no region, then ", x". Result: 0 `deleteSurroundingText`.
  - Changed: the host-shift test now asserts "Xabc hello".
  - Changed: `finishInputView` now asserts `""` (see the behaviour change above).
- `MicPermissionActivityTest` (+1): `rationaleSurvivesRecreation` (`ActivityScenario.recreate()`).

## TDD evidence

RED was captured before the implementation; the interface stubs compiled but behaved the old way.

```
ComposingSpanControllerTest 16 fail 4   (hostMovingTheCursor... true≠false, newSession... true≠false, regionShifted... , discard... [set:hel, finish])
VoiceControllerTest 16 fail 3           (cancel... [set:hel, finish], aNewSession... "commit+join:, x ", errorEnd... expected 2 but null)
SpeechSessionTest 35 fail 1             (lastErrorCode expected 2 but null)
MicPermissionActivityTest 8 fail 1      (rationaleSurvivesRecreation: no "Microphone is off" node)
VoiceImeSessionTest 7 fail 3            (password: expected "" was "hel"; host shift: "Xabc helhello"; punctuation: deletes expected 0 was 1)
```

The first version of the `:app` punctuation test passed while still RED-pending: its cursor landed after "a", and `ImeTextTarget`'s "only a space" check hid the bug. I moved the cursor to just after a host space; it then failed with 1 delete. The guard-fail test passes both before and after the fix, by design (the "no fallback delete" invariant).

GREEN: every suite above passes after the implementation. The only extra edit was the `finishInputView` assertion, an intended consequence of R24.

## Command output (after committing)

`$G ./gradlew :ime:voice-ui:test :core:voice:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

```
BUILD SUCCESSFUL in 43s
655 actionable tasks: 212 executed, 443 up-to-date
Lint found 10 warnings (and 49 errors, 263 warnings and 2 hints filtered by baseline lint-baseline.xml)
```

Results:
- `:ime:voice-ui`: 32/32 (debug and release).
- `:core:voice`: 61/61 (debug and release).
- `:app:testDebugUnitTest`: 123/123.
- Lint: the 10 warnings are the same pre-existing ones as before; none are in `fork/voice` or `voice-ui`.

`scripts/check-fork-markers.sh`

```
check-fork-markers: upstream v0.5.2; 96 modified (6 marker-exempt), 202 rename-only, 207 deleted
check-fork-markers: OK
```

## Remaining concerns

- `clearComposing` checks the text before the cursor, not the composing span itself (InputConnection does not expose the span). It relies on the ownership flag plus that text matching. If the input connection is already a new field that happens to end in the same text and has its own composing region, that region would be cleared. That needs both an exact text match and a host-set region in a field we did not write to; I judged it acceptable.
- Concerns 1, 2 and 6 from the first report still stand (not verified on a device; `noHistory` with the permission dialog; theme fixed at AUTO). Concern 3 ("helhello"), concern 4 (password partial) and concern 5 (`errorCode`) are resolved.

Note: team-lead confirmed that removing the unfinished partial when the keyboard hides is intended (Ruling R25, extending R24). The updated `finishInputViewStopsAndAbandonsFocus` test stays.
