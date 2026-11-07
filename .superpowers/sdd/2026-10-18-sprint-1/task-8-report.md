# Task 8 report: onboarding O1-O5

Commit 82fdf6c9 `feat(onboarding): O1–O5 flow with resume and funnel events`.

## Implemented
- `fork/onboarding/`: `OnboardingStep` (+ fork-local `DisclosureEngineKind`), `ImeStatusSource` (+ `InputMethodImeStatus`: 500 ms poll, `recheck()` for onResume), `OnboardingViewModel` (plain class, scope injected, analytics as `(AnalyticsEvent)->Unit`, subtypes as lambda, clock lambda), `OnboardingScreen`, `TryItStep`, `VoiceDisclosureSheet` (sets `consentVoiceNoticeAt` on Agree; `() -> DisclosureEngineKind` supplied by `systemDisclosureEngineKind`).
- Wiring (FORK S1-onboarding, rows in FORK_PATCHES.md): `FlorisAppActivity` (start at Home only if onboardingDone and enabled and selected; splash capped at 1 s; fork store read once with 1 s timeout), `Routes.kt` (`Setup.Screen` renders OnboardingScreen; upstream `SetupScreen.kt` untouched, unused), `HomeScreen` (card text "Select AI Keyboard as your keyboard"), manifest `RECORD_AUDIO`, `strings_fork.xml`, Turbine in `libs.versions.toml` / `app/build.gradle.kts`.

## Decisions
- `onboarding_step_view` mapping: WELCOME=ONE, ENABLE=TWO, SELECT=THREE, TRY_IT=TRY; LANGUAGES logs none (the closed analytics enum has no value; `languages_set` covers it).
- Returning user who is done but disabled/deselected re-enters at ENABLE/SELECT and goes straight to DONE after selecting (language step skipped).
- `skip()` only works from LANGUAGES/TRY_IT (no keyboard yet before that).
- Translate pair default: first chosen language -> second chosen (else en, or es if source is en).
- Language preselection: system locales mapped to MVP tags (exact, then same language); none -> en-US.

## Tests
- `OnboardingViewModelTest`, 11 tests, all green: `./gradlew --offline :app:testDebugUnitTest --tests '*onboarding*'` BUILD SUCCESSFUL. Covers fresh start/start event, ENABLE->SELECT->LANGUAGES with bucket, resume after process death at SELECT (second VM, same store), languages_set count=2 has_rtl=true + ensureSubtypes tags + stored pair, locale mapping, skip from TRY_IT, returning user DONE, returning-but-deselected, mic denial stays on TRY_IT, no `core.ads`/billing imports in the package.
- RED evidence: first run had 3 failures (skipFromTryItCompletes, languagesChosenSeedsSubtypesAndLogs, micDenialLogsAndStaysOnTryIt, `AssertionFailedError`), a real bug: pressing Get started with the IME already enabled+selected did not advance past ENABLE (no new status emission). Fixed by re-evaluating the last status after WELCOME->ENABLE. Honest caveat: I wrote the VM before running the tests, so there was no pre-implementation compile-fail RED run.
- Full verification: `:app:testDebugUnitTest --tests '*onboarding*' :app:lintDebug :app:assembleDebug` BUILD SUCCESSFUL; `scripts/check-fork-markers.sh` OK; full `:app:testDebugUnitTest` passes (no failure XML).

## Not done: OnboardingScreenTest
Compose UI test cannot run offline: BOM 2025.11.00 pins `ui-test-junit4`/`ui-test-manifest` 1.9.4, which is not in the Gradle cache (only 1.10.0 aars and 1.12.1). No fake test was written. `TryItStep` is written to be testable (mic request is a lambda invoked only from the sheet's Agree); needs the 1.9.4 artifacts online, then add `androidx-compose-ui-test-junit4` and `ui-test-manifest` to the catalog.

## Concerns
- No emulator, so activity/start-destination/permission flow and the ModalBottomSheet are compile+lint verified only.
- `FlorisAppActivity` now has an unused `observeAsState` import (left to avoid an extra hunk).

## Fix round 1 (commit 75e146d5 `fix(onboarding): address review for task 8`)
- Markers: the script compares HEAD, so my earlier OK ran before the commit (I ran it pre-commit). Now OK on the new HEAD. Restored the upstream `// testImplementation(composeBom)` comment in build.gradle.kts; replaced the deleted `isImeSetUp` line in FlorisAppActivity with a FORK comment. FORK_PATCHES row updated.
- OnboardingScreenTest added (2 tests, green): TRY_IT shows a text field and "Skip"; mic request count is 0 before Agree and exactly 1 after, consent read back from the store (777). Deps resolved online without `--offline` (ui-test-junit4 testImplementation, ui-test-manifest debugImplementation, `platform(composeBom)`).
- Re-entry logs no step_view/ime_enabled/ime_selected/onboarding_complete/onboarding_start (test `reentryLogsNoFunnelEvents`).
- Consent: `recordVoiceConsent` writes under `NonCancellable` and the sheet calls `onAgree` only after it; test `recordVoiceConsentPersistsEvenWhenCallerIsCancelled`.
- ime_selected bucket asserted ("15-30"); KDoc notes firstOpenAt timing (R16).
- Minors: unused `observeAsState` import removed, FORK imports ordered; DataStore wait in the activity uses only the remaining splash time and always falls through to `setContent` (onboarding is the fallback); `InputMethodImeStatus` polls once and shares via `shareIn`; view model lives in an `OnboardingHolder` ViewModel so rotation does not re-log step views; R17 comment added at the Routes.kt SetupScreen swap.
- Also removed duplicated catalog entries that had slipped into libs.versions.toml.

Final verification: `$G ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` tail: `BUILD SUCCESSFUL in 25s, 395 actionable tasks` (EXIT=0, lint: baseline only). Tests: OnboardingViewModelTest 13/13, OnboardingScreenTest 2/2.
`scripts/check-fork-markers.sh` tail: `check-fork-markers: upstream v0.5.2; 95 modified (6 marker-exempt), 203 rename-only, 207 deleted` / `check-fork-markers: OK`.
