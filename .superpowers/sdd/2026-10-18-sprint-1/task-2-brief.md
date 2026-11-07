### Task 2: `:core:datastore` feature settings and policy wiring into the IME

**Files:**
- Create: `core/datastore/build.gradle.kts`, `core/datastore/src/main/kotlin/com/manjul/aikeyboard/core/datastore/{FeatureSettings,FeatureSettingsStore}.kt`
- Create: `app/src/main/kotlin/com/manjul/aikeyboard/fork/policy/{ImePolicyController,FieldGeneration,EditorInfoMapping}.kt`
- Modify (FORK S1-policy): `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml` (datastore, kotlinx-serialization already upstream, robolectric, junit-vintage), `FlorisApplication.kt` (lazy `featureSettingsStore`), `FlorisImeService.kt` (`onStartInput`, `onStartInputView`, `onFinishInputView`, `onFinishInput`, `onDestroy` call the controller), `ime/editor/EditorInstance.kt:121-127` (incognito also true when `!decision.allowLearning`; composing off when `!decision.allowSuggestionStrip`), `AndroidManifest.xml` (`directBootAware="false"` on the IME and spell-checker services), `docs/FORK_PATCHES.md`
- Test: `core/datastore/src/test/.../FeatureSettingsStoreTest.kt`, `app/src/test/kotlin/com/manjul/aikeyboard/fork/policy/{ImePolicyControllerTest,EditorInfoMappingTest}.kt`, extend `app/src/test/.../manifest/ManifestHardeningTest.kt`

**Interfaces:**
- Consumes: Task 1 `SensitiveFieldPolicy.evaluate`, `PolicyDecision`.
- Produces:
  - `@Serializable data class FeatureSettings(val incognitoManual: Boolean = false, val privatePackages: Set<String> = emptySet(), val smartbarPinned: List<String> = emptyList(), val smartbarOrder: List<String> = emptyList(), val voiceEnginePreference: VoiceEnginePreference = VoiceEnginePreference.AUTO, val voiceSilenceMs: Int = 2000, val spokenPunctuation: Boolean = true, val onboardingStep: Int = 0, val onboardingDone: Boolean = false, val firstOpenAt: Long = 0L, val translatePair: Pair<String, String>? = null, val consentVoiceNoticeAt: Long = 0L, val analyticsOptOut: Boolean = false)`; `enum class VoiceEnginePreference { AUTO, ON_DEVICE, WHISPER }`.
  - `class FeatureSettingsStore(context: Context) { val flow: Flow<FeatureSettings>; suspend fun update(transform: (FeatureSettings) -> FeatureSettings); val current: FeatureSettings /* cached, non-blocking */ }` backed by `MultiProcessDataStoreFactory.create` with a kotlinx-serialization JSON `Serializer<FeatureSettings>`, file `files/datastore/feature_settings.json`. `voiceSilenceMs` is clamped to 2000..5000 on write.
  - `class ImePolicyController(store: FeatureSettingsStore, keyguard: () -> Boolean, forcedPackages: () -> Set<String>)` with `val decision: StateFlow<PolicyDecision>`, `val generation: FieldGeneration`, `fun onStartInput(info: EditorInfo?, restarting: Boolean)`, `fun onFinishInputView()`, `fun onFinishInput()`, and `fun addRestrictionListener(l: (PolicyDecision) -> Unit)` — listeners fire when `!restarting` or the new decision `isMoreRestrictiveThan` the previous one; later tasks (voice, smartbar) register their cleanup here.
  - `class FieldGeneration { val value: Int; fun bump(); fun isCurrent(token: Int): Boolean }` — bumped in `onStartInput(!restarting)`, on more-restrictive restart, and in `onFinishInputView` (TRD §11.2 #2.5).
  - `fun EditorInfo?.toFieldDescriptor(): FieldDescriptor` (null → `FieldDescriptor(null, 0, 0, null, null)`).
  - `FlorisImeService.policyController(): ImePolicyController` companion accessor (same weak-ref pattern as `currentInputConnection()`).

- [ ] **Step 1: Write failing tests.**
  - `FeatureSettingsStoreTest` (Robolectric): default values; `update` round-trips; `voiceSilenceMs = 9000` stored as 5000.
  - `ImePolicyControllerTest` (Robolectric): (a) text field → `decision.allowVoice`; (b) `onStartInput(text, false)` then `onStartInput(password, restarting = true)` → listener called once with a decision where `!allowVoice`, generation bumped; (c) password → text with `restarting = true` → listener not called; (d) `onStartInput(null, false)` → no crash, `Unrestricted`-equivalent for a NULL class; (e) keyguard locked → `reasons` contains `DEVICE_LOCKED`; (f) package in `privatePackages` → `PRIVATE_APP`; (g) `onFinishInputView` bumps generation.
  - `ManifestHardeningTest`: IME service `directBootAware` is false (or absent).
- [ ] **Step 2: Run** `$G ./gradlew :core:datastore:test :app:testDebugUnitTest --tests '*policy*' --tests '*ManifestHardening*'` — Expected: FAIL.
- [ ] **Step 3: Implement.** Add Robolectric (latest stable on Maven Central, record version) + `junit-vintage-engine` so JUnit4 Robolectric tests run on the JUnit5 platform. Hook the controller into the five `FlorisImeService` callbacks; `forcedPackages` returns `emptySet()` until Remote Config lands (S3).
- [ ] **Step 4: Run** the Step 2 command, then `$G ./gradlew :app:testDebugUnitTest :app:lintDebug && scripts/check-fork-markers.sh` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(ime): evaluate SensitiveFieldPolicy on every onStartInput`.

