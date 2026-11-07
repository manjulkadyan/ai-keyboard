# Final review fix report (Sprint 1)

Implementer: final-fix. Branch `sprint-1`, base 3bb0d56b. Commit `dd4da202 fix: address final review for sprint 1` (not pushed).
Minor 2 was left untouched, as the brief asked; it is parked for Sprint 2.

## What changed

| Finding | Fix | Files |
|---|---|---|
| Important 1 (clipboard history in sensitive fields) | New `fork/clipboard/ClipboardPanelGate`. The `IME_UI_MODE_CLIPBOARD` handler in KeyboardManager now calls `ClipboardPanelGate.onClipboardKey(mode, policyDecision())`, which opens the panel only when `allowClipboardHistory` is true. The chip, the Enter popup (-213) and the `SWITCH_TO_CLIPBOARD_CONTEXT` swipe all end in that handler. `closeOnRestriction` is installed on the policy controller in `FlorisImeService.onCreate`: every reported decision with `!allowClipboardHistory` sets the mode back to TEXT. That covers a new field, a more restrictive restart and late settings (R7). `onStartInputView` now uses `ClipboardPanelGate.onStartInputView`: this is upstream's rule, plus "never keep CLIPBOARD when the decision denies history", so `restarting=true` can no longer keep the panel open. | KeyboardManager.kt (FORK(S1-policy)), FlorisImeService.kt (FORK(S1-policy)), ClipboardPanelGate.kt (new), ImePolicyController.kt (`NoField` made public) |
| Important 2 / R36 | New `fork/analytics/ImeSessionStartReporter`. It holds one `SessionSampler(System::currentTimeMillis)` per service and logs `EventCatalog.imeSessionStart(subtypeTag, decision.sensitive)`. It is called from `onStartInputView` after the null check (the decision was already set in `onStartInput`) and goes through the existing `BatchingRelay`. A sensitive field sends `is_sensitive=true`, which is exactly what the minimal policy lets through. | FlorisImeService.kt (FORK(S1-analytics)), ImeSessionStartReporter.kt (new) |
| Minor 1 | The `VoiceImeSession` `caps` parameter is now `() -> RecognizerCapabilities`, wrapped in a private `LazyCapabilities`, so `whisperVoice` is created on the first capability question (the first mic use) and not at IME onCreate. `FlorisApplication.onTrimMemory`'s `isInitialized()` therefore means something again. | VoiceImeSession.kt |
| Minor 3 | `BatchingRelay.flush` no longer re-applies the policy. The filter runs only in `log()`, against the decision current at that moment. Opt-out is still re-checked at flush. KDoc updated. | BatchingRelay.kt |
| Minor 4 | `ImeTextTarget.forgetOwnedRegion()` is called through `VoiceImeSession.onStartInput(restarting)` when `!restarting`, from `FlorisImeService.onStartInput` before the policy call. That order matters because the policy's restriction cancels voice, and the cancel must not act on the new connection. | ImeTextTarget.kt, VoiceImeSession.kt, FlorisImeService.kt (FORK(S1-voice)) |
| Minor 5 / R39 | `VoiceImeSession.onFinishInput()` cancels with `field_change` and is called before `super.onFinishInput()`. | VoiceImeSession.kt, FlorisImeService.kt (FORK(S1-voice)) |
| Minor 6 / R37 | `isAllowedBy`: when `decision.sensitive`, only `IME_UI_MODE_MEDIA` (emoji), `SETTINGS` and `TOGGLE_PRIVATE_APP` pass. Everything else is hidden, including `InsertText` actions. The private-app action is still hidden when the app is already private. | PolicyQuickActions.kt |
| Minor 7 / R38 | `WhisperIdleUnloader.onTrimMemory` frees on `RUNNING_LOW`, `RUNNING_CRITICAL` and `>= BACKGROUND`, and no longer on `UI_HIDDEN` or `RUNNING_MODERATE`. KDoc updated. | WhisperIdleUnloader.kt |
| Docs | Added FORK_PATCHES rows for KeyboardManager (S1-policy) and FlorisImeService (S1-policy, S1-voice, S1-analytics). Device checklist: 2.1 now describes the R37 allow-list; new 2.11 (clipboard routes in sensitive fields, and the panel closing) and 2.12 (R39 field change); 5.9 covers UI_HIDDEN (R38); the TRD-exit mapping row names PolicyQuickActionsTest. | docs/FORK_PATCHES.md, docs/sprint-1-device-checklist.md |

## Tests and TDD evidence

New or changed tests:
- `app/.../fork/clipboard/ClipboardPanelGateTest.kt` (Robolectric, sdk 34; real `ImePolicyController` and `FeatureSettingsStore`):
  - the Enter popup code is read from `popupMappings/default.json` (-213), and the swipe's `TextKeyData.IME_UI_MODE_CLIPBOARD` is used; both leave a password field in TEXT;
  - a normal field still opens the panel;
  - an open panel closes on a restrictive restart and stays closed through onStartInputView;
  - onStartInputView closes a panel the decision denies, with no callback involved;
  - the panel closes when the app is made private after settings load (R7);
  - the upstream keep-open pref still holds across a normal field change (the `NoField` callback is ignored);
  - MEDIA mode is untouched.
- `app/.../fork/analytics/ImeSessionStartReporterTest.kt` (reporter feeding a real `BatchingRelay` that reads the same decision):
  - a normal field logs `is_sensitive=false`;
  - a password field's event passes the minimal policy;
  - the event is sampled once per 10 minutes.
- `VoiceImeSessionTest`:
  - `capabilitiesAreResolvedOnFirstVoiceUseNotAtCreation` (Minor 1);
  - `fieldChangeDiscardsThePartialBeforeTheFrameworkFinishesComposing` (R39; replays the service order: session.onFinishInput, then the framework's `finishComposingText`, then policy.onFinishInput);
  - `newFieldConnectionIsNeverTreatedAsOurs` (Minor 4: the new field's text equals our partial and is not deleted, and its composing region is kept).
  - The Rig now takes a caps provider and a switchable connection. `PasswordFieldEgressTest` was updated for the new `caps` type.
- `PolicyQuickActionsTest`: `sensitive fields show only emoji, settings and the private-app action` (Locked and password decisions; undo, redo, paste and InsertText are hidden) and `non chip actions stay in normal fields`. This replaces `non chip actions are never filtered`.
- `BatchingRelayTest`: `policyIsReappliedAtFlush` (which locked in the old behaviour) was replaced by `normalFieldEventSurvivesAFlushInASensitiveField` and `sensitiveFieldEventStaysFilteredAfterLeavingTheField`.
- `WhisperIdleUnloaderTest`: `onlyRunningLowAndAboveFree` became `onlyRunningLowCriticalAndBackgroundOrAboveFree` (MODERATE and UI_HIDDEN do not free; LOW, CRITICAL, BACKGROUND, MODERATE and COMPLETE do).

RED, core modules:
`$G ./gradlew :core:analytics:testDebugUnitTest :core:voice:testDebugUnitTest --continue -q` -> 2 failures, both expected:
- `BatchingRelayTest.normalFieldEventSurvivesAFlushInASensitiveField`: `expected <[AnalyticsEvent(name=voice_session_start, ...)]> but was <[]>`. This is the flush-time filter dropping the event.
- `WhisperIdleUnloaderTest.onlyRunningLowCriticalAndBackgroundOrAboveFree`: `expected <0> but was <1>`. UI_HIDDEN freed the model.
- `sensitiveFieldEventStaysFilteredAfterLeavingTheField` passed already. It is a regression guard: the log-time filter existed before.

RED, app. To get assertion-level RED instead of compile errors, I first added API stubs that reproduce today's behaviour: the gate always opens and never closes; the reporter is a no-op; `onStartInput`/`onFinishInput` are no-ops; caps are resolved eagerly.
`$G ./gradlew :app:testDebugUnitTest --tests '*ClipboardPanelGateTest*' --tests '*ImeSessionStartReporterTest*' --tests '*VoiceImeSessionTest*' --tests '*PolicyQuickActionsTest*' --tests '*PasswordFieldEgressTest*' --continue` -> 31 tests, 12 failed, all expected:
```
ImeSessionStartReporterTest | sensitiveFieldSessionStartPassesTheMinimalPolicy | expected: <[AnalyticsEvent(name=ime_session_start, params={subtype=hi-IN, is_sensitive=true})]> but was: <[]>
ImeSessionStartReporterTest | normalFieldLogsANonSensitiveSessionStart | expected: <[...is_sensitive=false})]> but was: <[]>
ImeSessionStartReporterTest | sampledOncePerTenMinutes | expected two events but was: <[]>
ClipboardPanelGateTest | open panel closes when the app is made private after settings load | expected: <TEXT> but was: <CLIPBOARD>
ClipboardPanelGateTest | start input view closes a panel the decision denies even without a restriction callback | expected: <TEXT> but was: <CLIPBOARD>
ClipboardPanelGateTest | enter popup and swipe leave a password field in text mode | expected: <TEXT> but was: <CLIPBOARD>
ClipboardPanelGateTest | open panel closes on a restrictive restart | expected: <TEXT> but was: <CLIPBOARD>
ClipboardPanelGateTest | media mode is left alone by restrictions | expected: <TEXT> but was: <CLIPBOARD>
VoiceImeSessionTest | fieldChangeDiscardsThePartialBeforeTheFrameworkFinishesComposing | expected: <abc > but was: <abc hel>
VoiceImeSessionTest | newFieldConnectionIsNeverTreatedAsOurs | Expected value to be false.
VoiceImeSessionTest | capabilitiesAreResolvedOnFirstVoiceUseNotAtCreation | the IME's onCreate must not create the Whisper pieces ==> expected: <0> but was: <1>
PolicyQuickActionsTest | sensitive fields show only emoji, settings and the private-app action | Expected value to be false.
```
`normal field still opens the panel` and `upstream keep-open pref still holds` passed under the stubs, as they should: they are regression guards.

GREEN, focused: the same app tests plus `PolicyChipFilterTest`, `ImePolicyControllerTest`, `:core:analytics` and `:core:voice` -> app 45/0 failed, core:analytics 19/0, core:voice 95/0.

Full verification: `$G ./gradlew clean test :app:lintDebug :app:assembleRelease --continue` -> exit 0. Across all modules' test results: 2124 tests, 0 failures or errors. `lintDebug` passed and `assembleRelease` produced `app-release-unsigned.apk`.
- `scripts/check-16kb-alignment.sh app/build/outputs/apk/release/*.apk` -> `15 native libraries checked`, `check-16kb-alignment: OK`.
- After the full run I made two changes:
  - A KDoc-only edit to WhisperIdleUnloader's class comment (it no longer says "at least RUNNING_LOW"); `:core:voice:testDebugUnitTest` re-run -> OK.
  - `@OptIn(ExperimentalCoroutinesApi::class)` on ImeSessionStartReporterTest to silence a new compiler warning; that test re-run -> BUILD SUCCESSFUL.
- After the commit, `scripts/check-fork-markers.sh` -> `96 modified (6 marker-exempt), 202 rename-only, 207 deleted`, `check-fork-markers: OK`.

## Decisions

- **Testability.** Neither FlorisImeService nor KeyboardManager can run under Robolectric. So the policy logic lives in fork helpers (`ClipboardPanelGate`, `ImeSessionStartReporter`, the new `VoiceImeSession` methods), and the upstream hunks are one-line calls into them. The route test proves that both the popup entry (read from the real asset) and the swipe key data carry code `-213`, which is the gated handler. It does not call KeyboardManager's private swipe mapping.
- **NoField.** `onFinishInput` reports `NoField` on every field switch, and closing the panel on it would kill upstream's "hide on next text field = off" pref. `afterRestriction` therefore ignores `NoField`; the next field's own decision closes the panel if needed. This is why `ImePolicyController.NoField` is now public.
- **is_sensitive = `decision.sensitive`.** Under R4 it is equivalent to `analyticsMinimal`, so the pass-through always holds. The event is sent on restarts too; the sampler limits it to 1 per 10 minutes.
- **Minor 4.** The reset happens only when `!restarting`. A restart is the same field and connection, and Review Focus 1 (R24) needs the cancel on a restrictive restart to remove our partial, which only works while `owned` is still true.
- **Minor 1.** The capabilities are resolved on the first selector query, which is the first mic use. That still creates `WhisperVoice` on the first dictation even when the language gate is empty; constructing it is cheap (models and WorkManager stay lazy inside).
- **R37.** In sensitive fields, `InsertText` quick actions are hidden too: they are not on the allow-list.

## Self-review

- Every upstream hunk has a FORK marker within the allowed lines; the checker passes.
- No user content is logged. The reporter sends only a language tag and a boolean.
- Listener order in onCreate: editor policy, then clipboard gate, then voice (registered in the VoiceImeSession init). They are independent.
- The voice cancel can now run twice on a field change (our early cancel, then the policy's NoField). The second is a no-op: the R39 test asserts exactly one `voice_session_end`.
- `.kotlin/` (untracked build cache) was left out of the commit.

## Concerns

- R39 and the clipboard routes are covered by JVM tests of the helpers, not by running FlorisImeService or KeyboardManager. On-device confirmation is in device checklist items 2.11 and 2.12.
