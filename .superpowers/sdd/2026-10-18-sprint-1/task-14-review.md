# Task 14 review (41a62f80..283fc8b4)

Spec: issues found (see Important 1-2). Quality: needs fixes (docs-only).

Checks run: scripts/check-fork-markers.sh -> OK. All 11 cited test classes and 7 cited test names exist. FORK_PATCHES first-column paths without globs all exist at HEAD (no stale rows). README module list matches settings.gradle.kts (:app, :lib:*, :core:common/ads/datastore/privacy/analytics/voice, :ime:policy, :ime:voice-ui, :lib:whisper, :lint-rules). Plan: 67 boxes ticked, only Task 4 Step 2 unticked (matches ledger "no RED").

Important
1. docs/sprint-1-device-checklist.md item 6.2 is false against the built release manifest (app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml:24,50-52 and app/src/main/AndroidManifest.xml:18). POST_NOTIFICATIONS is still declared (upstream line kept), contradicting "not requested (R17)". WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE are merged in from WorkManager (R29), so "and nothing else new" fails. Fix: either remove POST_NOTIFICATIONS (R17 intent) or correct the item and R17 note; list the WorkManager permissions.
2. Pending-decisions "Play listing" bullet covers only ACCESS_NETWORK_STATE and INTERNET. Add WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE and POST_NOTIFICATIONS (whichever stay) for the Data safety and permissions text.
3. Exit table "no Room write" row and 2.9 are the R34 gap (already ruled; fix round must add PasswordFieldEgressTest, then update the table row, the concern text and plan Step 2 tick basis).

Minor
- Checklist omits ledger device notes: EXTRA_PREFER_OFFLINE on SYSTEM_DEFAULT is only a hint (T10); Whisper settings show no download percent (T13); WorkManager 10-min/3-attempt resume for large models (T13, partly in 5.10); clipboard slider range 0-1440 (T3). Covered: R24, R25, R27, R33, R9-R12, R15-R17, R23, R26, R29, R32, T7/T8/T10/T11/T12/T13 device lists.
- Exit table row 1 notes emoji/settings stay; PolicyChipFilterTest is accurate to that ("no smartbar action" in the TRD means no feature action).
- Pending decisions section covers OD-01/R28, R23+R29, release whisper_enabled_langs [], R30 (+R31), R13: all present.
- 3 stale lint-baseline entries (report) not cleaned; informational.

Assessment: Needs fixes (items 1-2 are factual doc errors; 3 is already ruled).
