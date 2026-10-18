# Fork patches

Every upstream FlorisBoard file we edit is listed here (TRD §2.1). Each edited hunk carries a
`FORK(<ticket>): reason` marker (`//` in Kotlin/Gradle, `#` in TOML/properties, `<!-- -->` in XML).

| File | Ticket | Why |
|---|---|---|
| `gradle/libs.versions.toml` | S0-build | The pinned jetpref snapshot `20251119T222500Z-SNAPSHOT` has expired from Sonatype. Pinned to the `0.3.0` release, which compiles against v0.5.2 unchanged |
| `gradle/libs.versions.toml` | S0-modules | Fork-only entries are appended in clearly separated `# --- AI Keyboard ---` blocks in `[versions]`, `[libraries]` and `[plugins]` (JUnit BOM/launcher, coroutines-test, javax.inject, AGP/KGP artifacts for `build-logic`, our `aikeyboard.*` plugin ids). Upstream entries are untouched; each block header carries the marker instead of each line |
| `settings.gradle.kts` | S0-modules | `pluginManagement { includeBuild("build-logic") }` for our convention plugins; `include(":core:common")`, `include(":ime:policy")` |
| `build.gradle.kts` | S0-modules | Root applies `aikeyboard.module-rules` (see "Module dependency rules" below) |
| *(global)* `app/**`, `benchmark/**`, `lib/android/.../PackageManager.kt`, `app/schemas/*`, `app/src/main/baseline-prof.txt`, `utils/generate_spellcheck_config.py` | S0-rename | Global package rename `dev.patrickgold.florisboard` -> `com.manjul.aikeyboard` (TRD §2.1 "Package and branding"). Source dirs and Room schema dirs moved with `git mv`; package declarations, imports, `namespace`/`applicationId`, manifest components, `method.xml`/`spellchecker.xml` settings activity, baseline profile and string constants rewritten mechanically. Recorded once here instead of per hunk; the NOTICE file carries the Apache-2.0 §4(b) change statement |
| `settings.gradle.kts` | S0-branding | `rootProject.name` set to `AiKeyboard` |
| `app/build.gradle.kts` | S0-branding, S0-licences | Debug/beta app names and launcher foreground point at the placeholder name and glyph; `copyLicenseAssets` task ships repo-root `LICENSE` and `NOTICE` as `assets/licenses/*.txt` |
| `app/src/main/res/mipmap-anydpi-v26/ic_app_icon_{stable,beta,debug}{,_round}.xml` | S0-branding | Adaptive icons use the neutral placeholder keyboard glyph (`drawable/ic_app_icon_keyboard_*`, new files). The FlorisBoard foreground/monochrome vectors, PNG mipmaps (minSdk 26 makes them unnecessary) and Play Store icon PNGs were deleted |
| `app/src/main/res/values/strings.xml` and `app/src/main/res/values-*/strings.xml` | S0-branding | Mechanical replacement of the word "FlorisBoard" in user-visible string text: `{app_name}` where the string is rendered through the curly-format `stringRes` (which substitutes the app name), the literal "AI Keyboard" elsewhere (`app_name`, crash dialog/notifications, theme source label). Comment attributes are untouched. Each replaced string has a marker comment above it |
| `app/src/main/res/values/strings_dont_translate.xml` | S0-rename | The KeyCode reference URL contained the old package path; it now links to a GitHub code search for `KeyCode.kt` upstream |
| `app/src/main/assets/ime/theme/org.florisboard.themes{,.my}/extension.json` | S0-branding | Theme pack titles "FlorisBoard default themes" -> "Default themes". JSON has no comment syntax, so these two files carry no inline marker; this row is the record |
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
| Deleted: `fastlane/`, `.github/`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `crowdin.yml`, `ROADMAP.md`, `AI_POLICY.md`, `LANGUAGEPACKS*.md`, `.idea/icon.svg` | S0-branding | Upstream store listings, project-process docs, CI that uses FlorisBoard secrets, and the FlorisBoard logo. `README.md` was rewritten for the fork |

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
- `:ime:policy` (pure JVM): placeholder `SensitiveFieldPolicy` contract; implemented in Sprint 1.

Layout decision: the upstream IME stays in `:app` (no `:ime` module split in Sprint 0).

## Module dependency rules

`aikeyboard.module-rules` (applied in the root `build.gradle.kts`) inspects every project-to-project
dependency, in any configuration, after all projects are evaluated, and fails the build when:

1. a project whose path starts with `:ime` reaches `:core:ads`, `:core:billing` or any `:feature:*`
   project, directly or through other projects;
2. a `:feature:*` project depends directly on another `:feature:*` project.

The rule logic is a pure function (`ModuleRules.check`) with unit tests, plus a Gradle TestKit test
that runs the plugin against a fixture build. Run them with `./gradlew :build-logic:convention:test`.

**Limitation:** because the upstream IME still lives in `:app`, and `:app` is allowed to depend on
`:core:ads`, this Gradle rule cannot by itself keep ad code out of the keyboard. That guarantee comes
from the class-reference test added together with `:core:ads` (and the process/auto-init measures in
TRD §11.1). The Gradle rule protects our `:ime:*` modules.

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

Lint status: `:app:lintDebug` loads the custom check ("Included Additional Checks", vendor AI Keyboard) but
still fails on 49 pre-existing upstream errors (40 `ImpliedQuantity` and 6 `StringFormatInvalid` in
translations, plus `WrongConstant`, `RestrictedApi`, `LocalContextConfigurationRead`). Upstream's
`lint { baseline = file("lint.xml") }` points at a lint *config* file, not a baseline, so nothing is
baselined. Creating a real baseline (or fixing those errors) is a follow-up. Lint needs the Rust Android
targets for `:lib:native`; without them, skip the CMake tasks with
`-x ':lib:native:buildCMakeDebug[<abi>][fl_native]' -x ':lib:native:configureCMakeDebug[<abi>]'` per ABI.
