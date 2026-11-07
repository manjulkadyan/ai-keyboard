# Task 3 report

## Implemented
- `:core:privacy` (jvm library) with `ClipSensitivityClassifier.isSensitive(text, flaggedSensitive)`: flag, bare 4-8 digit code with optional short alpha prefix, keyword sentence (code/otp/pin/...) <= 60 chars with a standalone 4-8 digit run (dates, times, URLs excluded by lookarounds), single-token password (>= 10 chars, upper+lower+digit+symbol, no `://`).
- `fork/clipboard/ClipboardCaptureGate` (injectable `() -> PolicyDecision?`, default `FlorisImeService.policyControllerOrNull()?.decision?.value`; null means fail closed). Checks `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+) and the literal key `android.content.extra.IS_SENSITIVE`.
- `ClipboardManager`: constructor takes the gate; `onPrimaryClipChanged` returns before `fromClipData` (no insert, no URI clone); `insertOrMoveBeginning` re-checks policy and sensitivity; `enforceExpiryDate` calls `deleteUnpinnedOlderThan(cutoff)` (closes URIs of expired in-memory items first).
- DAO `deleteUnpinnedOlderThan`; `fallbackToDestructiveMigration()` removed from both clipboard DBs (history v4, files v2: schema untouched, no migration needed; no lint-baseline entry existed).
- Prefs: `historyAutoCleanOldEnabled` true, `historyAutoCleanOldAfter` 1440 (upstream unit is minutes: `* 60 * 1000`). Settings slider max raised 120 -> 1440 so the default is reachable.
- FORK_PATCHES rows added; markers script OK.

## Tests
- RED: classifier stubbed to return false for non-null text: `:core:privacy:test` failed (otp-like cases [1]-[5], flagged test).
  The ClipboardCaptureGateTest was written alongside the gate, so no separate RED run for it (honest note: implementation was written before the first run).
- GREEN: `./gradlew :core:privacy:test :app:testDebugUnitTest --tests '*clipboard*'` BUILD SUCCESSFUL; ClipboardCaptureGateTest 8/8; `:app:lintDebug` and `scripts/check-fork-markers.sh` pass.

## Decisions / concerns
- `ClipboardManager` cannot be instantiated in Robolectric (needs `FlorisApplication`: native lib, managers), so the capture decision is extracted into `ClipboardCaptureGate` and tested directly; expiry is tested on the DAO query in an in-memory Room DB. The manager wiring itself (calls to gate, `enforceExpiryDate`) is not covered by a test.
- "history size <= 20" is upstream `enforceHistoryLimit` (unchanged, default limit pref); not separately tested.
- Existing installs keep their stored `historyAutoCleanOldAfter` (20 min if ever set) since only defaults changed.
- Also removed the destructive fallback from `ClipboardFilesDatabase` (banned by contract).

## Fix round 1
- `onPrimaryClipChanged`: blocked clip now sets `primaryClip = null` before returning (FORK marker). Consumers already null-safe (`commitClipboardItem`, `canBePasted`, NlpManager/KeyboardManager collectors, `?.let`).
- Classifier: keyword-path cap 60 -> 160; split 3+3 codes (`482 913`, `482-913`) count when a keyword is present. New tests: full SMS, `Your code: 482 913`, `OTP 482-913` (true); bare `482 913` and a 160+ char prose paragraph with "code" and a year (false).
- History limit: trim step extracted to `ClipboardCaptureGate.itemsOverLimit` (used by `enforceHistoryLimit`); test asserts 25 items -> 5 oldest removed, 20 kept.
- Manager-wiring test gap accepted by controller.
- Command: `./gradlew :core:privacy:test :app:testDebugUnitTest --tests '*clipboard*' :app:lintDebug` BUILD SUCCESSFUL (privacy 26/26, gate test 9/9); `check-fork-markers.sh` OK.
