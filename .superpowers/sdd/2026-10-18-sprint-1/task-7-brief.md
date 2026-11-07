### Task 7: Smartbar chips, private lock indicator, private apps

**Files:**
- Create: `app/src/main/kotlin/com/manjul/aikeyboard/fork/smartbar/{ChipId,ChipArrangement,PrivateLockIndicator}.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/fork/privacy/{PrivateAppsScreen,PrivateAppsRepository,BankingPackages}.kt`
- Modify (FORK S1-smartbar): `ime/text/key/KeyCode.kt` (`TOGGLE_PRIVATE_APP`), `ime/text/key/TextKeyData.kt`, `ime/keyboard/KeyboardManager.kt` (handle `TOGGLE_PRIVATE_APP`; voice/clipboard/emoji keys disabled per `decision`), `ime/smartbar/Smartbar.kt` (render `PrivateLockIndicator` when `decision.sensitive || reasons.isNotEmpty()`), `ime/smartbar/quickaction/QuickActionArrangement.kt` (Default = Mic sticky, Emoji, Clipboard hidden when history off, Settings; Translate/AI not present), `ime/smartbar/quickaction/QuickActionsRow.kt` (filter actions disallowed by the decision — removed, not greyed; long-press on the lock indicator or the overflow offers "Make this app private"), `app/AndroidManifest.xml` (`<queries><intent><action MAIN/><category LAUNCHER/></intent></queries>`), settings nav (Privacy → Private apps route), `strings_fork.xml`, `docs/FORK_PATCHES.md`
- Test: `app/src/test/kotlin/com/manjul/aikeyboard/fork/smartbar/{ChipArrangementTest,PolicyChipFilterTest}.kt`, `app/src/test/.../fork/privacy/PrivateAppsRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 2 `FeatureSettingsStore` (`privatePackages`, `smartbarPinned`, `smartbarOrder`), `policyController().decision`; Task 6 `EventCatalog.privacyPrivateAppAdd()`.
- Produces:
  - `enum class ChipId { MIC, TRANSLATE, AI, EMOJI, CLIPBOARD, STICKERS, SETTINGS }`, `ChipId.fixed = setOf(MIC, TRANSLATE)`.
  - `object ChipArrangement { fun pin(current: List<ChipId>, chip: ChipId): List<ChipId>  /* max 3 pins, fixed chips not pinnable */; fun reorder(order: List<ChipId>, from: Int, to: Int): List<ChipId> /* fixed chips keep their positions */; fun visible(order: List<ChipId>, registered: Set<ChipId>, decision: PolicyDecision, clipboardHistoryOn: Boolean): List<ChipId> }`.
  - `class PrivateAppsRepository(store, pm: PackageManager) { suspend fun add(pkg: String); suspend fun remove(pkg: String); fun launchableApps(): List<AppEntry>; fun suggestions(): List<AppEntry> /* BankingPackages ∩ launchable, never auto-added */ }`; `object BankingPackages { val byMarket: Map<String, Set<String>> = emptyMap() }` (TODO(RN-05)).

- [ ] **Step 1: Write failing tests.** `ChipArrangementTest`: 4th pin rejected (list unchanged); pinning MIC no-op; reorder never moves MIC/TRANSLATE; order persists through `FeatureSettingsStore` round-trip. `PolicyChipFilterTest`: password decision → visible chips exclude MIC, TRANSLATE, AI, CLIPBOARD, STICKERS; EMOJI and SETTINGS remain; unregistered TRANSLATE/AI never visible. `PrivateAppsRepositoryTest` (Robolectric): `add("com.bank")` makes `ImePolicyController` report `PRIVATE_APP` for that package on next `onStartInput`; `suggestions()` empty with the empty list; add logs `privacy_private_app_add` with no params.
- [ ] **Step 2: Run** `$G ./gradlew :app:testDebugUnitTest --tests '*smartbar*' --tests '*privacy*'` — Expected: FAIL.
- [ ] **Step 3: Implement.** The mic chip stays wired to `VOICE_INPUT` until Task 11 replaces the handler.
- [ ] **Step 4: Run** step 2 command + `$G ./gradlew :app:lintDebug && scripts/check-fork-markers.sh` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(smartbar): policy-aware chips, private lock and private apps`.

