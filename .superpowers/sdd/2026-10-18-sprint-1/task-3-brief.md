### Task 3: Clipboard sensitivity, TTL and policy gating

**Files:**
- Create: `core/privacy/build.gradle.kts` (`aikeyboard.jvm.library`), `core/privacy/src/main/kotlin/com/manjul/aikeyboard/core/privacy/ClipSensitivityClassifier.kt`
- Modify (FORK S1-clipboard): `ime/clipboard/ClipboardManager.kt` (`onPrimaryClipChanged`, `insertOrMoveBeginning`, `enforceExpiryDate`), `ime/clipboard/provider/ClipboardDatabase.kt` (drop `fallbackToDestructiveMigration()`; add `@Query("DELETE FROM clipboard_history WHERE isPinned = 0 AND creationTimestampMs < :cutoff") fun deleteUnpinnedOlderThan(cutoff: Long)` — use the real column names), `app/AppPrefs.kt` (`historyAutoCleanOldEnabled` default true, `historyAutoCleanOldAfter` default 1440 minutes), `settings.gradle.kts`, `app/build.gradle.kts`, `docs/FORK_PATCHES.md`
- Test: `core/privacy/src/test/.../ClipSensitivityClassifierTest.kt`, `app/src/test/kotlin/com/manjul/aikeyboard/ime/clipboard/ClipboardCaptureGateTest.kt`

**Interfaces:**
- Consumes: Task 2 `FlorisImeService.policyController().decision`.
- Produces: `object ClipSensitivityClassifier { fun isSensitive(text: String?, flaggedSensitive: Boolean): Boolean }`.

- [ ] **Step 1: Write failing tests.**
  - `ClipSensitivityClassifierTest`: `flaggedSensitive = true` → true; `"482913"`, `"Your code is 482913"`, `"G-482913"` (4–8 digit OTP, optionally with a short prefix) → true; `"Tr0ub4dor&3xK!"` (single token, ≥ 10 chars, has upper, lower, digit and symbol) → true; `"hello world"`, `"call me at 5"`, `"https://example.com"`, `"2026-10-18"` → false; `null` → false.
  - `ClipboardCaptureGateTest` (Robolectric, history enabled): a clip with `EXTRA_IS_SENSITIVE` is not inserted; an OTP text is not inserted; any clip while `decision.allowClipboardHistory == false` is not inserted; a normal clip is inserted; a 25 h old unpinned item is deleted by `enforceExpiryDate`, a pinned one is kept; history size stays ≤ 20.
- [ ] **Step 2: Run** `$G ./gradlew :core:privacy:test :app:testDebugUnitTest --tests '*clipboard*'` — Expected: FAIL.
- [ ] **Step 3: Implement.** In `onPrimaryClipChanged`, return before `ClipboardItem.fromClipData` when the policy disallows history or the classifier says sensitive (no insert, no URI clone). Keep upstream masking for already-stored items.
- [ ] **Step 4: Run** Step 2 command plus `$G ./gradlew :app:lintDebug && scripts/check-fork-markers.sh` — Expected: PASS (the existing destructive-migration lint rule now has nothing to baseline; remove its baseline entry if one exists).
- [ ] **Step 5: Commit** `feat(clipboard): never store sensitive clips; 24 h TTL; policy gate`.

