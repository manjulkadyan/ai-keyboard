# Fork patches

Every upstream FlorisBoard file we edit is listed here (TRD §2.1). Each edited hunk carries a
`FORK(<ticket>): reason` marker (`//` in Kotlin/Gradle, `#` in TOML/properties, `<!-- -->` in XML).

| File | Ticket | Why |
|---|---|---|
| `gradle/libs.versions.toml` | S0-build | The pinned jetpref snapshot `20251119T222500Z-SNAPSHOT` has expired from Sonatype. Pinned to the `0.3.0` release, which compiles against v0.5.2 unchanged |
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
