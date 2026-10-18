# Fork patches

Every upstream FlorisBoard file we edit is listed here (TRD §2.1). Each edited hunk carries a
`FORK(<ticket>): reason` marker (`//` in Kotlin/Gradle, `#` in TOML/properties, `<!-- -->` in XML).

| File | Ticket | Why |
|---|---|---|
| `gradle/libs.versions.toml` | S0-build | The pinned jetpref snapshot `20251119T222500Z-SNAPSHOT` has expired from Sonatype. Pinned to the `0.3.0` release, which compiles against v0.5.2 unchanged |
| `gradle/libs.versions.toml` | S0-modules | Fork-only entries are appended in clearly separated `# --- AI Keyboard ---` blocks in `[versions]`, `[libraries]` and `[plugins]` (JUnit BOM/launcher, coroutines-test, javax.inject, AGP/KGP artifacts for `build-logic`, our `aikeyboard.*` plugin ids). Upstream entries are untouched; each block header carries the marker instead of each line |
| `gradle.properties` | S0-build | `org.gradle.tooling.parallel=true` (added by Android Studio): parallel IDE sync only, no effect on command-line builds |
| `settings.gradle.kts` | S0-modules | `pluginManagement { includeBuild("build-logic") }` for our convention plugins; `include(":core:common")`, `include(":ime:policy")` |
| `build.gradle.kts` | S0-modules | Root applies `aikeyboard.module-rules` (see "Module dependency rules" below) |
| *(global)* `app/**`, `benchmark/**`, `lib/android/.../PackageManager.kt`, `app/schemas/*`, `app/src/main/baseline-prof.txt`, `utils/generate_spellcheck_config.py` | S0-rename | Global package rename `dev.patrickgold.florisboard` -> `com.manjul.aikeyboard` (TRD §2.1 "Package and branding"). Source dirs and Room schema dirs moved with `git mv`; package declarations, imports, `namespace`/`applicationId`, manifest components, `method.xml`/`spellchecker.xml` settings activity, baseline profile and string constants rewritten mechanically. Recorded once here instead of per hunk; the NOTICE file carries the Apache-2.0 §4(b) change statement |
| `settings.gradle.kts` | S0-branding | `rootProject.name` set to `AiKeyboard` |
| `app/build.gradle.kts` | S0-branding, S0-licences | Debug/beta app names and launcher foreground point at the placeholder name and glyph; `copyLicenseAssets` task ships repo-root `LICENSE` and `NOTICE` as `assets/licenses/*.txt` |
| `app/src/main/res/mipmap-anydpi-v26/ic_app_icon_{stable,beta,debug}{,_round}.xml` | S0-branding | Adaptive icons use the neutral placeholder keyboard glyph (`drawable/ic_app_icon_keyboard_*`, new files). The FlorisBoard foreground/monochrome vectors, PNG mipmaps (minSdk 26 makes them unnecessary) and Play Store icon PNGs were deleted |
| `app/src/main/res/values/strings.xml` and `app/src/main/res/values-*/strings.xml` | S0-branding | Mechanical replacement of the word "FlorisBoard" in user-visible string text: `{app_name}` where the string is rendered through the curly-format `stringRes` (which substitutes the app name), the literal "AI Keyboard" elsewhere (`app_name`, crash dialog/notifications, theme source label). Comment attributes are untouched. Each replaced string has a marker comment above it |
| `app/src/main/res/values/strings_dont_translate.xml` | S0-rename | The KeyCode reference URL contained the old package path; it now links to a GitHub code search for `KeyCode.kt` upstream |
| `app/src/main/assets/ime/theme/org.florisboard.themes{,.my}/extension.json` | S0-branding | Theme pack titles "FlorisBoard default themes" -> "Default themes", and the "Floris " prefix dropped from theme labels ("Floris Day" -> "Day"). JSON has no comment syntax, so these two files carry no inline marker; this row is the record |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/settings/about/AboutScreen.kt` | S0-branding, S0-licences | Icon content description from resources; "Based on FlorisBoard, Apache-2.0" attribution line (string in new `values/strings_fork.xml`) |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/settings/about/ProjectLicenseScreen.kt` | S0-licences | Shows `NOTICE` then `LICENSE` from `assets/licenses/`. The old `assets/license/project_license.txt` copy of LICENSE was deleted |
| `app/src/main/kotlin/com/manjul/aikeyboard/lib/crashutility/CrashDialogActivity.kt` | S0-branding | Crash report environment line uses our app name |
| `lib/compose/src/main/kotlin/org/florisboard/lib/compose/Resources.kt` | S0-branding | Fallback `{app_name}` value is the placeholder name |
| `app/src/main/res/xml/method.xml`, `app/src/main/res/xml/spellchecker.xml`, `utils/generate_spellcheck_config.py` | S0-settings | `android:settingsActivity` now targets the exported `app.FlorisAppActivity` instead of `SettingsLauncherAlias`, which `hideAppIcon()` disables (that left the IME/spell-checker gear pointing at a disabled component). Guarded by `SettingsActivityManifestTest` |
| `settings.gradle.kts` | S0-hardening | `include(":lint-rules")` |
| `gradle/libs.versions.toml` | S0-hardening | `android-lint` (= AGP + 23), `junit4` versions and `android-lint-api/-checks/-tests`, `junit4` libraries, added inside the existing `# --- AI Keyboard ---` blocks |
| `app/build.gradle.kts` | S0-hardening | `lintChecks(projects.lintRules)`; `proguard-hardening.pro` added to the `beta` and `release` proguard files |
| `app/src/main/AndroidManifest.xml` | S0-hardening | `android:networkSecurityConfig="@xml/network_security_config"`; `android:dataExtractionRules` now points at the new `data_extraction_rules.xml` (upstream pointed it at the `full-backup-content` file, which is the wrong format for API 31+). `allowBackup="true"` kept, as TRD §6.3 requires |
| `app/src/main/res/xml/backup_rules.xml` | S0-hardening | Removed the `database/floris_user_dictionary` include: learned/user-added words are user-typed content (TRD §6.3). Only `root/jetpref_datastore` (settings) and `file/ime` (installed extensions) are backed up; clipboard databases, `no_backup/clipboard_files`, crash data and caches are excluded by omission. XML comment marker at the top of the file |
| `app/src/main/kotlin/com/manjul/aikeyboard/lib/crashutility/CrashUtility.kt` | S0-hardening | `@SuppressLint("ImeProcessTermination")` on `UncaughtExceptionHandler.uncaughtException`. **Justification:** this is the last step of the uncaught-exception handler; the process is already crashing and must terminate after the stacktrace is written (returning would leave a dead main thread). It is the only `killProcess`/`exitProcess` call in the codebase and is not reachable from normal IME or app flows |
| `settings.gradle.kts` | S0-ads | `include(":core:ads")` |
| `gradle/libs.versions.toml` | S0-ads | `google-ump` version (4.0.0, the latest release on Google Maven on 2026-10-18) and library, `aikeyboard-ad-isolation` plugin id, added inside the existing `# --- AI Keyboard ---` blocks |
| `app/build.gradle.kts` | S0-ads | Applies `aikeyboard.ad-isolation` (merged-manifest auto-init check wired into `check`); `implementation(projects.core.ads)`. Nothing in `:app` calls `:core:ads` yet |
| `lib/native/src/main/rust/CMakeLists.txt` | S0-ci | `target_link_options(fl_native PRIVATE "-Wl,-z,max-page-size=16384")`: NDK r26 links with a 4 KB max page size by default, so `libfl_native.so` LOAD segments were 4 KB aligned. Play requires 16 KB alignment (TRD §1, §11.8); enforced by `scripts/check-16kb-alignment.sh` in CI |
| `app/build.gradle.kts` | S0-ci | `lint { baseline }` now points at a real baseline, `app/lint-baseline.xml` (new file), instead of the `lint.xml` config file, so `:app:lintDebug` gates CI on new issues only |
| `app/src/main/res/values/strings.xml` and `app/src/main/res/values-*/strings.xml` | S0-branding | Second pass over the other spellings the first pass missed: "Florisboard"/"florisboardu" (`setup__grant_notification_permission__description` in `values` and 15 locales, `values-cs` `crash_dialog__title`, `values-nl` `pref__theme__source_assets`), the misspelling "FlorisBorad" (`values-ckb`), and translated app names "TastieraFloris" (`values-sq-rAL`), "ФлорисБоард" (`values-sr`) and "فلوریس بورد" (`values-fa`, 12 strings). Same rule: `{app_name}` for curly-formatted strings, "AI Keyboard" elsewhere. Guarded by `UpstreamBrandingStringsTest` (new file) |
| `app/src/main/res/values/strings_dont_translate.xml` | S0-branding | Removed `florisboard__repo_url`, `__issue_tracker_url`, `__privacy_policy_url`, `__changelog_url`, `__commit_by_hash_url` (their call sites are gone, see below). Our `privacy_policy_url` (placeholder `https://example.com/privacy`, TODO(OD-01)) lives in `strings_fork.xml` |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/settings/about/AboutScreen.kt` | S0-branding | Changelog and Repository rows (FlorisBoard GitHub) removed; Privacy policy row opens our `privacy_policy_url` |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/setup/SetupScreen.kt` | S0-branding | Footer: Privacy policy opens our `privacy_policy_url`; the Repository link (FlorisBoard GitHub) and its bullet separator are removed |
| `app/src/main/kotlin/com/manjul/aikeyboard/lib/crashutility/CrashDialogActivity.kt` | S0-branding | Crash text carries a plain version (plus commit hash in debug) instead of a link to upstream's commit/release page; the GitHub report instructions are hidden; the "Open issue tracker" button (FlorisBoard issues) is now a "Share" button (`ACTION_SEND` chooser with the crash text). Copy to clipboard is unchanged. `crash_dialog.xml` is untouched (button text set in code) |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/ext/AddonBox.kt` | S0-branding | No links to upstream's Addons Store (`BuildConfig.FLADDONS_STORE_URL`, `beta.addons.florisboard.org`): the "Visit Addons Store" button is removed, the info text is our `ext__home__info_import_only`, and `UpdateBox` ("Search for updates", which opens the store's check-updates page) renders nothing (`ADDONS_STORE_ENABLED = false`). Local extension import is unchanged. The `FLADDONS_*` build config fields in `app/build.gradle.kts` are kept (no remaining UI call site) |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/settings/typing/TypingScreen.kt` | S0-branding | Auto-space-punctuation card: feedback request and link to upstream's GitHub discussion removed; the experimental-feature warning stays |
| Deleted: `app/src/main/res/drawable/ic_app_icon_{stable,beta,debug}_{foreground,monochrome}.xml`, `app/src/main/res/mipmap-*dpi/ic_app_icon_{stable,beta,debug}{,_round}.png`, `app/src/main/ic_app_icon_{stable,preview,debug}-playstore.png` | S0-branding | FlorisBoard launcher foreground/monochrome vectors, legacy PNG mipmaps and Play Store icons (see the `mipmap-anydpi-v26` row) |
| Deleted: `app/src/main/assets/license/project_license.txt` | S0-licences | Copy of LICENSE replaced by the build-generated `assets/licenses/` (see the `ProjectLicenseScreen.kt` row) |
| `lib/snygg/src/test/kotlin/org/florisboard/lib/snygg/SnyggThemeTest.kt` | S0-ci | Upstream v0.5.2 imports `org.florisboard.lib.color.schemes.yellow{Light,Dark}Scheme`, which does not exist in `:lib:color` at that tag, so the `:lib:snygg` unit tests did not compile. The two fixed schemes are now built with `:lib:color`'s `neutralDynamicColorScheme` (yellow 500 seed); all assertions are unchanged |
| Deleted: `fastlane/`, `.github/`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `crowdin.yml`, `ROADMAP.md`, `AI_POLICY.md`, `LANGUAGEPACKS*.md`, `.idea/icon.svg` | S0-branding | Upstream store listings, project-process docs, CI that uses FlorisBoard secrets, and the FlorisBoard logo. `README.md` was rewritten for the fork |
| `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/kotlin/com/manjul/aikeyboard/{app/ext/AddonBox,app/settings/about/AboutScreen,app/settings/about/ProjectLicenseScreen,app/settings/typing/TypingScreen,app/setup/SetupScreen,lib/crashutility/CrashDialogActivity,lib/crashutility/CrashUtility}.kt`, `lib/compose/src/main/kotlin/org/florisboard/lib/compose/Resources.kt`, `lib/snygg/src/test/kotlin/org/florisboard/lib/snygg/SnyggThemeTest.kt` | S0-licences, S0-ci | Markers only, for the per-hunk marker check: the "Modified by AI Keyboard contributors, 2026" header line carries `FORK(S0-licences)`; removed imports and removed lines get a one-line `FORK(<ticket>)` comment at the deletion point; `AndroidManifest.xml` moves `android:name` onto the `<application` line and `networkSecurityConfig` next to `dataExtractionRules` so the marker sits within 3 lines of both edits. No behaviour change |
| `settings.gradle.kts` | S1-policy | `include(":core:datastore")` |
| `gradle/libs.versions.toml` | S1-policy | `androidx-datastore` 1.2.1 (latest stable on Google Maven, 2026-10-18), `robolectric` 4.17 (latest on Maven Central), `junit-vintage-engine` (version from the junit-bom), inside the `# --- AI Keyboard ---` blocks |
| `app/build.gradle.kts` | S1-policy | `implementation(projects.core.datastore)`, `implementation(projects.ime.policy)`; Robolectric, JUnit 4, junit-vintage and coroutines-test as test dependencies; applies `gradle/robolectric-offline.gradle.kts` (new file: resolves Robolectric's android-all jar through Gradle so tests need no download into the home directory) |
| `app/src/main/kotlin/com/manjul/aikeyboard/FlorisApplication.kt` | S1-policy | Lazy `featureSettingsStore` and `Context.featureSettingsStore()` accessor |
| `app/src/main/kotlin/com/manjul/aikeyboard/FlorisImeService.kt` | S1-policy | Owns an `ImePolicyController` (keyguard read on every evaluation, no forced packages until S3); calls it from `onStartInput`, `onFinishInputView`, `onFinishInput`, `onDestroy`; companion `policyController()` accessor. `onStartInputView` needs no call: the decision is made in `onStartInput`, which always precedes it |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/editor/EditorInstance.kt` | S1-policy | `isIncognitoMode` is also true when the policy decision does not allow learning; composing is off when it does not allow the suggestion strip |
| `app/src/main/AndroidManifest.xml` | S1-policy | `android:directBootAware="false"` on the IME and spell-checker services (was `true` / absent): the policy and settings store live in credential-encrypted storage. Guarded by `ManifestHardeningTest` |

## Marker-exempt files

`scripts/check-fork-markers.sh` (run in CI, `.github/workflows/pr.yml`) fails when an upstream file is
modified but not listed above, when a deleted upstream file is not listed, or when any changed hunk lacks a
`FORK(` marker. Hunks come from a zero-context diff of the rename-normalised upstream file against HEAD: a
hunk that adds or changes lines needs the marker on one of those lines or on one of the 3 lines before it;
a hunk that only deletes lines needs the marker within 3 lines of the deletion point. Only the line containing
`FORK(` counts, so in a multi-line comment put the marker on the line nearest the edit.
Files whose only change is the global package rename (content and path normalised
`dev.patrickgold.florisboard` <-> `com.manjul.aikeyboard`) pass without a row. The `*(global)*` row is
deliberately not used for matching, so any edit beyond the rename needs its own row.

The files below are listed but cannot (or need not) carry an inline marker. Keep this list short.

| File | Why no inline marker |
|---|---|
| `app/src/main/assets/ime/theme/org.florisboard.themes{,.my}/extension.json` | JSON has no comment syntax; branding-only title change recorded in the S0-branding row above |
| `README.md` | Rewritten from scratch for the fork (S0-branding); no upstream hunk is left to annotate |

## Decisions

- The `org.florisboard.lib.*` library module namespaces (`:lib:android`, `:lib:color`, `:lib:compose`,
  `:lib:kotlin`, `:lib:native`, `:lib:snygg`) are **not** renamed. They are internal module packages,
  not user-visible branding, and renaming them would make every upstream merge touch every file.
- FlorisBoard extension IDs inside assets (e.g. `org.florisboard.layouts`, `org.florisboard.themes`)
  are **not** renamed. They are data identifiers stored in user preferences and referenced across
  extension JSON; renaming them would break upstream-compatible extensions and merges.
- Third-party library coordinates `dev.patrickgold.jetpref` and `dev.patrickgold.compose.tooltip`
  are external dependencies and stay as they are.
- Upstream class names (`FlorisImeService`, `FlorisAppActivity`, `Floris*`) are kept. They are not
  user-visible and renaming them would turn every upstream merge into a conflict.
- Apache-2.0 §4(b): files whose only change is the package rename are covered by the change statement
  in `NOTICE`. Files with substantive edits also get a "Modified by AI Keyboard contributors, 2026"
  line under the upstream copyright header (where the file has one).

## Our modules and convention plugins (S0-modules)

New code lives in our own modules (TRD §2.1, §2.2). These are new files, not upstream patches:

- `build-logic/` (included build): `aikeyboard.android.library`, `aikeyboard.android.library.compose`,
  `aikeyboard.jvm.library`, `aikeyboard.module-rules`. SDK levels are read from the root
  `gradle.properties` keys `projectCompileSdk`, `projectMinSdk`, `projectTargetSdk`, so our modules
  always match the upstream ones. Java/Kotlin bytecode target 11 and JUnit 5, as upstream.
- `:core:common` (Android library): dispatcher qualifier/provider, `Outcome` result type.
- `:ime:policy` (pure JVM): `SensitiveFieldPolicy` (Sprint 1).
- `:core:datastore` (Android library, S1-policy): `FeatureSettings` and `FeatureSettingsStore` (multi-process DataStore, JSON).

Layout decision: the upstream IME stays in `:app` (no `:ime` module split in Sprint 0).

## Module dependency rules

`aikeyboard.module-rules` (applied in the root `build.gradle.kts`) inspects every project-to-project
dependency, in any configuration, after all projects are evaluated, and fails the build when:

1. `:ime` or an `:ime:*` project (not a prefix-only sibling such as `:imeTools`) reaches `:core:ads`, `:core:billing` or any `:feature:*`
   project, directly or through other projects;
2. a `:feature:*` project depends directly on another `:feature:*` project.

The rule logic is a pure function (`ModuleRules.check`) with unit tests, plus a Gradle TestKit test
that runs the plugin against a fixture build. Run them with `./gradlew :build-logic:convention:test`.

**Limitation:** because the upstream IME still lives in `:app`, and `:app` is allowed to depend on
`:core:ads`, this Gradle rule cannot by itself keep ad code out of the keyboard. That guarantee comes
from the ad-isolation checks below (S0-ads) and the process/auto-init measures in TRD §11.1. The Gradle
rule protects our `:ime:*` modules.

## Hardening baseline (S0-hardening)

New files (no markers needed), TRD §2.3 "Process termination", §6.3, §7.1:

- `lint-rules/` (`:lint-rules`, JVM, `lint-api`/`lint-checks` compileOnly): `ImeProcessTermination` (error)
  flags `android.os.Process.killProcess`, `System.exit`, `Runtime.exit`/`halt` and `kotlin.system.exitProcess`.
  Hooked into `:app` with `lintChecks`. Tests: `./gradlew :lint-rules:test` (JUnit 4, `LintDetectorTest`).
- `app/src/main/res/xml/network_security_config.xml`: cleartext off, system CAs only; `debug-overrides`
  adds user CAs (only effective in debuggable builds).
- `app/src/main/res/xml/data_extraction_rules.xml`: API 31+ `cloud-backup` and `device-transfer` with the
  same include-only list as `backup_rules.xml`.
- `app/proguard-hardening.pro`: R8 `-assumenosideeffects` for `Log.v/d/i`; `Flog.checkShouldFlog*` assumed
  `false` and `Flog.log*` side-effect free, so every `flog*` block is removed from minified builds.
- `app/src/test/.../manifest/ManifestHardeningTest.kt`: exported-component allow-list, bind permissions,
  providers not exported, FileProvider paths are `cache-path` sub-directories only, backup rules are
  include-only without databases/shared prefs/clipboard/dictionary, NSC checks.

FileProvider: upstream's `file_paths.xml` already has a single `<cache-path name="backup" path="backup-and-restore/"/>`
(used by the settings backup export), which is narrow; it is left unchanged. TRD §3.3 `cache/share/` will be
added when the first sharing feature needs it.

Lint status: `:app:lintDebug` loads the custom check ("Included Additional Checks", vendor AI Keyboard).
The 49 pre-existing upstream errors (40 `ImpliedQuantity` and 6 `StringFormatInvalid` in translations, plus
`WrongConstant`, `RestrictedApi`, `LocalContextConfigurationRead`) and the upstream warnings are recorded in
`app/lint-baseline.xml` (S0-ci); fixing them is a follow-up. Regenerate the baseline with
`./gradlew :app:updateLintBaseline` only when an upstream merge brings new findings. Lint needs the Rust
Android targets for `:lib:native`; without them, skip the CMake tasks with
`-x ':lib:native:buildCMakeDebug[<abi>][fl_native]' -x ':lib:native:configureCMakeDebug[<abi>]'` per ABI.

## Ads skeleton and IME isolation (S0-ads)

New files (no markers needed), TRD §2.2, §2.3, §4.11, §11.1:

- `:core:ads` (Android library, `aikeyboard.android.library`), app process only:
  - `consent/ConsentManager` + `UmpConsentManager` (Google UMP 4.0.0). Every call takes an `Activity`;
    constructing it touches nothing. `ConsentDebugConfig` forces a debug geography for debug builds.
  - `placement/AdPlacement`, `AdPlacementId` (lower_snake_case), `AdFormat`, `FrequencyCap`,
    `AdPlacementRegistry`. `AdPlacements.registry` is **empty**; Sprint 4 registers the PRD FR-27 placements.
  - `cap/AdCapPolicy`: pure global + per-placement cap (defaults: interstitial and app-open share
    1 per 3 min and 6 per rolling 24 h). `AdCapStateStore` is an interface; the DataStore implementation
    comes with `:core:datastore`.
  - `AdsInitializer.initialize(activity)`: UMP consent, then the `AdSdk` only if `canRequestAds()` and the
    user is not ad-free. Sprint 0 ships `NoAdSdk`; the Google Mobile Ads SDK is **not** a dependency yet.
  - Tests: `./gradlew :core:ads:test`.
- IME isolation, source level: `app/src/test/.../isolation/ImeAdIsolationTest.kt` scans the IME code
  (`com.manjul.aikeyboard.ime.*`, `...lib.*`, `FlorisImeService`, `FlorisSpellCheckerService`,
  `FlorisApplication`, and the upstream `:lib:*` modules) and fails on any reference (import or
  fully-qualified) to `com.manjul.aikeyboard.core.ads`, `com.google.android.gms.ads`,
  `com.google.android.libraries.ads` or `com.google.android.ump`. Because the IME also uses
  `com.manjul.aikeyboard.app.*` (preferences, routes), the rest of `:app` may only use ads from the
  allow-listed `com.manjul.aikeyboard.app.ads` package, which the IME code must not reference either.
  Other `:app` code may not reference `app.ads` either (a preferences file importing it would hand ad code
  to the IME transitively), except files on the `AD_HOST_CALLERS` allow-list of Activity-only callers
  (empty in Sprint 0).
- IME isolation, manifest level: `aikeyboard.ad-isolation` (build-logic, `AdIsolationPlugin`,
  `AdAutoInitRules`) registers `verify<Variant>NoAdAutoInit` on `:app` and wires it into `check`. It fails if
  the merged manifest contains `MobileAdsInitProvider` or any provider / `androidx.startup` initializer from
  the GMA, GMA next-gen, UMP or `:core:ads` packages. UMP 4.0.0 merges no provider (only the
  `com.google.android.gms.version` meta-data from `play-services-basement`).
- Exported components, merged manifest: the same plugin registers `verify<Variant>ExportedComponents`
  (also wired into `check`). It applies `ExportedComponentRules.ALLOW_LIST` (build-logic) to the merged
  manifest, so components merged in from libraries are reviewed as well; `ManifestHardeningTest` only sees
  the source manifest. Library entries: `androidx.profileinstaller.ProfileInstallReceiver` (must require
  `android.permission.DUMP`) and `androidx.compose.ui.tooling.PreviewActivity` (debuggable builds only;
  `ui-tooling` is a `debugImplementation`). Keep the `:app` entries in sync with `ManifestHardeningTest`.
- These are static checks. The runtime proof (IME-only cold start, no `com.google.android.gms.ads` class
  loaded, TRD §11.1 1.1(c)) is an instrumented test for Sprint 4, when GMA is added.

## CI (S0-ci)

New files (no markers needed), TRD §2.1, §7.2, §11.8:

- `.github/workflows/pr.yml` (pull requests and pushes to `main`, ubuntu-latest): fetches the upstream tag,
  runs `scripts/check-fork-markers.sh`, installs JDK 17 (temurin), the Android SDK and the NDK / CMake /
  build-tools pinned in `gradle/tools.versions.toml`, the pinned Rust toolchain with the four Android targets,
  then `assembleDebug`, unit tests for every module (`testDebugUnitTest` plus `test` on the JVM modules and
  `:build-logic:convention:test`), `:app:verify{Debug,Release}NoAdAutoInit`, `:app:verify{Debug,Release}ExportedComponents`, `:app:lintDebug` and the 16 KB
  alignment check on the debug APK. The IME isolation and manifest hardening tests run as `:app` unit tests.
- `scripts/check-fork-markers.sh`: see "Marker-exempt files" above. Run it locally before opening a PR.
- `scripts/check-16kb-alignment.sh <apk>...`: `zipalign -c -P 16 -v 4` on the APK and an ELF check that
  every `LOAD` segment of every `lib/<abi>/*.so` is aligned to at least 16 KB (`llvm-readelf` from the NDK).
  All ABIs are checked. TRD §11.8 also asks for the check on the release AAB; that belongs to the Main stage.
