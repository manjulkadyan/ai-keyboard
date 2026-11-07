# Task 14 report: Sprint exit check and docs

Status: DONE_WITH_CONCERNS. Commit `docs: Sprint 1 exit checklist and fork patch pass` on `sprint-1` (HEAD after 41a62f80), not pushed.

## Step 1 verification
`$G ./gradlew clean test :app:lintDebug :app:assembleRelease` -> BUILD SUCCESSFUL in 7m 29s (1445 tasks). Test XML totals: 2057 tests, 0 failures, 0 errors, 0 skipped. Lint passed (note: 3 entries in `app/lint-baseline.xml` no longer match anything, informational only).
`scripts/check-16kb-alignment.sh app/build/outputs/apk/release/*.apk` -> 15 native libraries, `check-16kb-alignment: OK`.
`scripts/check-fork-markers.sh` (run after commit and after the amend) -> OK (96 modified, 6 exempt).

assembleRelease needed no signing change: it produces `app-release-unsigned.apk`. No environmental failure.

## What changed
- Created `docs/sprint-1-device-checklist.md`: device items grouped as IME core, privacy/policy, onboarding, voice (platform), voice (Whisper), packaging/permissions, each with steps, expected result and the task/ruling it came from (built from the ledger and the task 5/7/8/10/11/12/13 reports). Includes every item the brief lists, R24/R25/R27/R33, and the password-field egress test marked still required (INTERNET landed in T13, only user is the Whisper download worker, R29).
- "Pending product and ops decisions" section: OD-01, Play listing notes for ACCESS_NETWORK_STATE (R23) and INTERNET (R29), `whisper_enabled_langs = []` in release, TRD Whisper size text (q5_1 57/182 MB, R30; R31 gate), PRD section 12 enum supersets (R13), plus other open flags.
- TRD section 8 exit-criteria table mapped to existing test files (all verified to exist).
- `docs/FORK_PATCHES.md`: consistency pass. The "Our modules" section lacked `:core:privacy`, `:core:analytics`, `:core:voice`, `:lib:whisper`; added. Every edited upstream file already had rows (marker script OK).
- `README.md`: module list extended, link to the checklist.
- `docs/plans/2026-10-18-sprint-1.md`: ticked all boxes for tasks 1-14 except Task 4 Step 2 (the report says no RED run was captured).

## Concerns (exit-criteria gaps, stated plainly)
1. TRD section 8 exit text mentions a "Robolectric no-network test". No such test exists. `ManifestHardeningTest` only checks the network security config. The checklist records this and keeps the egress test (item 2.9) as the Sprint 3 gate.
2. "No Room write" is covered at the capture-gate level (`ClipboardCaptureGateTest`), not through the Room DAO or `ClipboardManager` wiring (parked since Task 3).
3. Funnel events are verified by `OnboardingViewModelTest` against a fake sink. `LogcatSink` has no unit test and is wired only when `BuildConfig.DEBUG`.
4. Release APK has no `libaikb_whisper.so` for x86 (known, load() returns 0); arm64, armeabi-v7a and x86_64 have it.
5. I amended my own just-made docs commit once (to untick Task 4 Step 2) before reporting; nothing was pushed.

## Fix round 1 (commit `3bb0d56b fix(docs): address review for task 14`)

1. **R34 egress test.** New `app/src/test/kotlin/com/manjul/aikeyboard/fork/policy/PasswordFieldEgressTest.kt` (`@Config(sdk=[34])`).
   - Script: real `ImePolicyController` (text field, restart into password field, fresh password start), key presses and backspace through a real input connection, paste through the real `ClipboardCaptureGate` in front of the real Room DAO (mirrors `ClipboardManager.onPrimaryClipChanged`), and the mic action through `VoiceImeSession`.
   - Guards: a `ProxySelector` that records every connection attempt (covers URL/OkHttp paths), WorkManager must hold no work, a Room `QueryCallback` records every INSERT/UPDATE/DELETE, and the clipboard table must be empty. Also asserts no recognizer engine is created.
   - Second test `positiveControlsProveTheDetectorsFire`: a URL connect, an unrestricted paste and an enqueued worker each trip their detector.
   - RED (temporary breaks, reverted): gate ignoring the policy -> `AssertionFailedError: Room writes: [INSERT OR ABORT INTO clipboard_history ...]`; a URL connect added to the script -> `network attempts: [https://example.invalid/, socket://example.invalid:443]`. GREEN: `:app:testDebugUnitTest --tests '*PasswordFieldEgressTest*'` BUILD SUCCESSFUL (2 tests).
   - Limits stated in the doc: raw native sockets are not covered, there is no suggestion tap (the suggestion provider is a stub in Sprint 1), `FlorisImeService` cannot run under Robolectric.
   - Checklist exit-table rows and item 2.9 now cite the test; the gap text is gone. Item 2.9 keeps the device and Sprint 3 instrumented follow-up.
2. **R35.** POST_NOTIFICATIONS stays. Item 6.2 now lists the merged release manifest exactly: RECORD_AUDIO, ACCESS_NETWORK_STATE, INTERNET, VIBRATE, POST_NOTIFICATIONS, WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE and the private DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION. The Play-listing bullet lists the same set. Finding: `SetupScreen.kt:230` is unreachable. No route reaches `SetupScreen` (`Routes.kt` setup route shows `OnboardingScreen`), so it is dead code. Reported in the checklist, no code changed.
3. **Minors added:** 4.11 (EXTRA_PREFER_OFFLINE hint, T10), 5.15 (no download %, T13), 2.10 (clipboard slider 0-1440, T3). Rows re-sorted by number.

Verification: `$G ./gradlew test :app:lintDebug` BUILD SUCCESSFUL, 2065 test cases, 0 failures; `scripts/check-fork-markers.sh` OK after the commit. Not pushed.
