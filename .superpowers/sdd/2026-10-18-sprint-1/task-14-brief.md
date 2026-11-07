### Task 14: Sprint exit check and docs

**Files:**
- Modify: `docs/FORK_PATCHES.md` (consistency pass), `README.md` (new modules), `docs/plans/2026-10-18-sprint-1.md` (tick boxes)
- Create: `docs/sprint-1-device-checklist.md` — manual on-device checks this sandbox cannot run (no emulator package): IME-only cold start; lock-screen typing; voice on Pixel/Samsung/Xiaomi with the keyboard hidden mid-session (`Idle` and mic indicator off ≤ 300 ms); segmented-session behaviour; Whisper base on a 3 GB phone within the memory budget; onboarding funnel visible in Logcat (`AiKbAnalytics`); password-field egress test (instrumented, Sprint 3 gate when INTERNET-using features land).

- [ ] **Step 1: Run** `$G ./gradlew clean test :app:lintDebug :app:assembleRelease && scripts/check-fork-markers.sh && scripts/check-16kb-alignment.sh app/build/outputs/apk/release/*.apk` — Expected: PASS.
- [ ] **Step 2: Verify** the exit criteria from TRD §8 Sprint 1 against tests: password field → no smartbar action (Task 7 `PolicyChipFilterTest`), no Room write (Task 3 gate test), voice live partials (Task 11), funnel events (Task 8, Logcat sink instead of DebugView — Firebase not yet set up).
- [ ] **Step 3: Commit** `docs: Sprint 1 exit checklist and fork patch pass`.
