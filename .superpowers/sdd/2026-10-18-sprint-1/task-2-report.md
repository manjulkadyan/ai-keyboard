# Task 2 report: `:core:datastore` and policy wiring

Status: DONE_WITH_CONCERNS. Commit f6b7ff6f `feat(ime): evaluate SensitiveFieldPolicy on every onStartInput`.

## Implemented
- `:core:datastore` (`aikeyboard.android.library` + kotlinx-serialization): `FeatureSettings`, `VoiceEnginePreference`, `LanguagePair`, `FeatureSettingsStore`. File `files/datastore/feature_settings.json`, `MultiProcessDataStoreFactory`, JSON serializer (ignoreUnknownKeys, corruption throws a content-free `CorruptionException`; no corruption handler is configured, see concerns). `voiceSilenceMs` clamped to 2000..5000 on write. `current` is a `MutableStateFlow` fed by a collector and set directly after each `update` (read-your-writes). Constructor takes an optional dispatcher (default IO).
- `:app` `fork/policy`: `FieldGeneration` (atomic counter), `EditorInfoMapping` (`EditorInfo?.toFieldDescriptor()`), `ImePolicyController`.
- Wiring: `FlorisApplication.featureSettingsStore` (lazy) + `Context.featureSettingsStore()`; `FlorisImeService` lazily builds the controller (keyguard = `KeyguardManager.isDeviceLocked`, read each evaluation; fail-closed `true` if the service is missing; `forcedPackages = emptySet()`), calls it from `onStartInput`, `onFinishInputView`, `onFinishInput`, `onDestroy`; companion `policyController()`. `EditorInstance`: incognito also when `!allowLearning`; composing off when `!allowSuggestionStrip`. Manifest: `directBootAware="false"` on IME and spell checker.
- `docs/FORK_PATCHES.md` rows added, `scripts/check-fork-markers.sh` OK.

## Decisions
- `translatePair` is `LanguagePair?` (`@Serializable data class LanguagePair(source, target)`), property name kept.
- Robolectric 4.17 (latest on Maven Central), androidx.datastore 1.2.1 (latest stable), `junit-vintage-engine` version from the junit-bom (5.13.3).
- Robolectric tries to download android-all into `~/.m2` and takes `~/.robolectric-download-lock`, which the sandbox blocks (`FileNotFoundException ... Operation not permitted`). New `gradle/robolectric-offline.gradle.kts` resolves `android-all-instrumented:14-robolectric-10818077-i7` through Gradle into `build/robolectric-deps` and sets `robolectric.offline`/`dependency.dir`. Applied in `:core:datastore` and `:app`. Tests use `@Config(sdk = [34])`.
- App Robolectric tests use `application = android.app.Application::class` because `FlorisApplication`'s static init loads `fl_native`.
- `onStartInputView` has no controller call: the controller API has no hook for it and the decision is made in `onStartInput`, which always precedes it. `onDestroy` calls `onFinishInput()`.
- `onFinishInput` bumps generation, sets the decision to an all-denied value with no reasons (`Locked.copy(reasons = emptySet())`, also the initial value before any field) and notifies listeners. Brief did not specify.
- `FlorisImeService.policyController()` throws `IllegalStateException` (no content in message) when no service is alive, so `EditorInstance` must only run inside the service (true today).
- Controller tests are deterministic: tests that need settings call `store.update` (read-your-writes), no waiting. `FeatureSettingsStoreTest` checks the on-disk JSON through the serializer rather than opening a second store (DataStore forbids two active stores on one file).

## TDD evidence
Honest note: RED was only partly captured. Tests were written first and the first run (before the Robolectric offline setup and `:app` dependencies existed) failed: `FeatureSettingsStoreTest > initializationError FAILED ... MavenDependencyResolver.java:111 (FileNotFoundException)`, then `ImePolicyControllerTest`/`EditorInfoMappingTest` failed with `UnsatisfiedLinkError: no fl_native` from `FlorisApplication.<clinit>` (9 of 17 failed). Those are environment failures, not behaviour RED; the production classes were written before the app tests were first run, and the manifest test was added after the manifest edit.

GREEN: `./gradlew :core:datastore:test :app:testDebugUnitTest --tests '*policy*' --tests '*ManifestHardening*'` -> BUILD SUCCESSFUL. FeatureSettingsStoreTest 3/3, ImePolicyControllerTest 7/7, EditorInfoMappingTest 2/2, ManifestHardeningTest 8/8.
Full: `./gradlew :app:testDebugUnitTest :app:lintDebug` -> BUILD SUCCESSFUL; `scripts/check-fork-markers.sh` -> OK.

## Files
New: `core/datastore/build.gradle.kts`, `core/datastore/src/{main,test}/.../{FeatureSettings,FeatureSettingsStore,FeatureSettingsStoreTest}.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/fork/policy/{ImePolicyController,FieldGeneration,EditorInfoMapping}.kt`, `app/src/test/kotlin/com/manjul/aikeyboard/fork/policy/{ImePolicyControllerTest,EditorInfoMappingTest}.kt`, `gradle/robolectric-offline.gradle.kts`.
Modified: `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `FlorisApplication.kt`, `FlorisImeService.kt`, `EditorInstance.kt`, `AndroidManifest.xml`, `ManifestHardeningTest.kt`, `docs/FORK_PATCHES.md`.

## Concerns
1. Corrupt file: the serializer throws `CorruptionException`, but no `ReplaceFileCorruptionHandler` is configured, so a corrupt settings file makes `update`/`flow` fail instead of resetting to defaults. Worth a follow-up (fail-open vs fail-closed choice).
2. `store.current` is defaults until the first async load finishes, so the very first field after process start could miss `privatePackages`/`incognitoManual` (fail-open window of a few ms). A blocking preload or an awaited load in `onCreate` would close it; not done to avoid blocking the main thread.
3. `lib:native` Rust issue from the memory file did not affect this task's builds.

## Fix report: fail-safe settings load (commit 8e5d4638 `fix(ime): fail-safe settings load for policy`)

Changes
- `FeatureSettingsStore`: `ReplaceFileCorruptionHandler` resets a corrupt file to `FeatureSettings()`. Test `corrupt file resets to defaults` writes garbage bytes, expects defaults from `flow`, then a working `update`.
- `ImePolicyController(store, keyguard, forcedPackages, scope)`: collects `store.flow` in the injected scope; each emission re-evaluates the saved `activeField` (null between sessions, cleared in `onFinishInput`). If the new decision `isMoreRestrictiveThan` the old one, generation is bumped and listeners fire. `FlorisImeService` passes `lifecycleScope`.
- Tests added: `settings that load after the first field lock it once` (com.bank, then `privatePackages={com.bank}`: listener once, `PRIVATE_APP`, generation bumped) and `settings emission after finishing input does not resurrect a field`. Test stores/controllers use an `UnconfinedTestDispatcher`.
- Marker check: the committed manifest in f6b7ff6f actually failed `check-fork-markers.sh` (I had run it before committing, against the previous HEAD, and it said OK). Fixed: `directBootAware="false"` now sits on the `<service` line (IME also has label and permission on one line) so the marker is within 3 lines of the edits and of the deletion. Row in FORK_PATCHES.md updated.

Behaviour-level RED (controller given the `scope` parameter but no collector yet)
`./gradlew :app:testDebugUnitTest --tests '*ImePolicyController*'`
```
ImePolicyControllerTest > settings that load after the first field lock it once FAILED
    org.opentest4j.AssertionFailedError at ImePolicyControllerTest.kt:119
9 tests completed, 1 failed
```
Line 119 is `assertEquals(1, seen.size)`: the listener never fired because nothing re-evaluated the field when the settings arrived. Expected.

GREEN: `./gradlew :core:datastore:test :app:testDebugUnitTest :app:lintDebug` -> BUILD SUCCESSFUL (ImePolicyControllerTest 9/9, FeatureSettingsStoreTest 4/4); `scripts/check-fork-markers.sh` -> OK.

Note: a settings emission that makes the field less restrictive updates `decision` but does not notify (same rule as restarts).

## Fix report 2: review findings (commit `fix(ime): address review for task 2`)

- Controller is now created eagerly in `FlorisImeService.onCreate` (`lateinit`), with a restriction listener that calls `EditorInstance.applyPolicyDecision`. `FlorisApplication.init()` touches `featureSettingsStore` so the load starts at process start. `onDestroy` no longer touches a lazy.
- `EditorInstance` keeps the upstream composing/incognito values and derives the active-state values via `PolicyStateDerivation` (new, in `fork/policy`). `applyPolicyDecision` runs from `handleStartInputView` and from the listener, so a decision that tightens after the field started updates `isIncognitoMode`/`isComposingEnabled`. Without a running service it uses `PolicyDecision.Locked` (`FlorisImeService.policyControllerOrNull()`; `policyController()` still throws).
- `libs.versions.toml`: comment next to `robolectric` names the android-all pin in `gradle/robolectric-offline.gradle.kts`.
- Tests: `PolicyStateDerivationTest` (3): unrestricted/restricted derivations, and the scenario field started on defaults for `com.bank`, then settings with `privatePackages={com.bank}`: incognito becomes true and composing false through the same listener calls the service registers. `EditorInstance` itself is not instantiated: it needs `FlorisApplication` (native library, managers, preferences), which is a heavy harness; the glue is two calls into the derivation.
- Result: `./gradlew :core:datastore:test :app:testDebugUnitTest :app:lintDebug` -> BUILD SUCCESSFUL; `scripts/check-fork-markers.sh` -> OK at HEAD (after amending the commit once to update FORK_PATCHES.md rows).
