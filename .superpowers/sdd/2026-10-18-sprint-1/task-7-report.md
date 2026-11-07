# Task 7 report: smartbar chips, private lock, private apps

Status: DONE_WITH_CONCERNS. One commit on `sprint-1` (`feat(smartbar): policy-aware chips, private lock and private apps`).

## Implemented
- `fork/smartbar/ChipId.kt`: enum, `fixed`, `registered` = {MIC, EMOJI, CLIPBOARD, SETTINGS} (no stickers upstream, so STICKERS is not registered), `keyCode` mapping to upstream keys (MIC -> VOICE_INPUT, EMOJI -> IME_UI_MODE_MEDIA, CLIPBOARD -> IME_UI_MODE_CLIPBOARD, SETTINGS -> SETTINGS), `parse` drops unknown persisted names.
- `ChipArrangement`: `pin` (max 3, fixed and duplicates rejected), `reorder` (slot-based, fixed chips never move, out-of-range or fixed-target is a no-op), `visible`, `allowed`. Mapping: MIC=allowVoice, TRANSLATE=allowTranslate, AI=allowAi, CLIPBOARD=allowClipboardHistory && history pref, STICKERS=allowCloud, EMOJI/SETTINGS always.
- `PolicyQuickActions.kt`: `rememberPolicyDecision()` (fail closed to `Locked` with no service), `policyFiltered()` for action lists. Chips are removed, not greyed.
- `PrivateLockIndicator`: shown when `sensitive || reasons.isNotEmpty()`; long-press fires `TOGGLE_PRIVATE_APP` unless the app is already private.
- `KeyCode.TOGGLE_PRIVATE_APP` (-246) + `TextKeyData`; `KeyboardManager` handles it (package read on main thread from `editorInstance.activeInfo`, never logged), blocks VOICE_INPUT dispatch/enable unless `allowVoice`, disables clipboard key unless `allowClipboardHistory`.
- The "Make this app private" overflow entry is the `TOGGLE_PRIVATE_APP` quick action (last dynamic action, so it sits in the overflow panel); hidden once the app is private.
- `QuickActionArrangement.Default`: Mic sticky; Emoji, Clipboard, Settings, private-app dynamic; upstream's other actions moved to `hiddenActions`.
- `PrivateAppsRepository(store, pm, log)` (`log` is the analytics hook; defaults to no-op), `BankingPackages` (empty, TODO(RN-05)), `PrivateApps.kt` (context factory wiring `analytics`, `makeAppPrivate`), `PrivateAppsScreen` (launchable apps with switches, "Suggested" section shown only when non-empty).
- Route `Settings.PrivateApps` (`settings/privacy/private-apps`), a home entry, manifest `<queries>` MAIN/LAUNCHER, strings in `strings_fork.xml`, FORK_PATCHES rows.

## Tests
- RED: `./gradlew :app:testDebugUnitTest --tests '*smartbar*' --tests '*privacy*'` failed at `compileDebugUnitTestKotlin` with `Unresolved reference 'PrivateAppsRepository'` / `'ChipId'` (expected, nothing implemented).
- GREEN: same command BUILD SUCCESSFUL; ChipArrangementTest 9, PolicyChipFilterTest 5, PrivateAppsRepositoryTest 5, 0 failures.
- Full: `:app:testDebugUnitTest :app:lintDebug` BUILD SUCCESSFUL (lint: 9 warnings, baseline-filtered). `scripts/check-fork-markers.sh`: OK.

## Decisions and deviations
- Settings nav has no Privacy parent screen; the home entry is titled "Private apps" and routes directly (path is under `settings/privacy/`).
- No TextKeyData hook beyond the predefined key; no STICKERS chip (upstream has none).
- "Overflow entry" is the quick action rather than a custom menu item.
- Repository signature gained a third optional `log` parameter (for the analytics event, testable without the relay).
- Default arrangement change drops several upstream actions from the visible bar for new installs only.

## Concerns
- Clipboard chip is hidden by default because upstream `clipboard.historyEnabled` defaults to false (per brief).
- UI (lock indicator layout, settings screen) is compile- and lint-verified only; no emulator, so not visually checked.
- `PrivateLockIndicator` is placed first in the smartbar main row and narrows the center area by one button width while sensitive.

## Fix round 1
- New `PolicyQuickActionsTest` (7 tests): `decisionOrLocked(null)` fail-closed, Locked / PRIVATE_APP / Unrestricted (history on and off), `TOGGLE_PRIVATE_APP` branch, non-chip actions, `ChipId.forKeyCode`.
- `decisionOrLocked` extracted to `PolicyQuickActions.kt`; used by `KeyboardManager.policyDecision()`. `rememberPolicyDecision` already uses the same `FailClosed` Locked flow.
- `PolicyChipFilterTest` password decision now comes from `SensitiveFieldPolicy.evaluate` on a password `FieldDescriptor`.
- `PrivateAppsRepository.add` logs only when the package was newly added (test `repeat add logs nothing`); `privatePackages` is now `Flow<Set<String>>`.
- Privacy-parent wording fixed in Routes, HomeScreen, PrivateAppsScreen, manifest comment, strings comment; FORK_PATCHES arrangement row reworded; `Lock` import moved into order in ComputingEvaluator.
- Verified: `:app:testDebugUnitTest :app:lintDebug` BUILD SUCCESSFUL; `scripts/check-fork-markers.sh` result below.
