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
| `app/src/main/kotlin/com/manjul/aikeyboard/FlorisApplication.kt` | S1-policy | Lazy `featureSettingsStore`, `Context.featureSettingsStore()` accessor; loading starts in `init()` |
| `app/src/main/kotlin/com/manjul/aikeyboard/FlorisApplication.kt` | S1-analytics | Lazy `analytics` (`BatchingRelay`): `LogcatSink` in debug, `NoopSink` in release; opt-out from `featureSettingsStore`, policy from the IME service's controller |
| `settings.gradle.kts`, `app/build.gradle.kts` | S1-analytics | `include(":core:analytics")`, `implementation(projects.core.analytics)` |
| `settings.gradle.kts` | S1-voice | `include(":core:voice")` |
| `app/src/main/kotlin/com/manjul/aikeyboard/FlorisImeService.kt` | S1-policy | Creates an `ImePolicyController` in `onCreate` (restriction listener re-applies the decision to `EditorInstance`; keyguard read on every evaluation, no forced packages until S3); calls it from `onStartInput`, `onFinishInputView`, `onFinishInput`, `onDestroy`; companion `policyController()` accessor. `onStartInputView` needs no call: the decision is made in `onStartInput`, which always precedes it |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/editor/EditorInstance.kt` | S1-policy | `isIncognitoMode` is also true when the policy decision does not allow learning; composing is off when it does not allow the suggestion strip |
| `app/src/main/AndroidManifest.xml` | S1-policy | `android:directBootAware="false"` on the IME and spell-checker services (was `true` / absent): the policy and settings store live in credential-encrypted storage. Guarded by `ManifestHardeningTest`. The attributes share the `<service` line so the marker sits within 3 lines of each edit (per-hunk marker check); no behaviour change from that |
| `settings.gradle.kts` | S1-clipboard | `include(":core:privacy")` |
| `app/build.gradle.kts` | S1-clipboard | `implementation(projects.core.privacy)` |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/clipboard/ClipboardManager.kt` | S1-clipboard | Takes a `ClipboardCaptureGate` (fail-closed policy gate plus sensitivity classifier): `onPrimaryClipChanged` returns before `fromClipData`/URI clone for blocked or sensitive clips, `insertOrMoveBeginning` re-checks; `enforceExpiryDate` deletes unpinned items older than the TTL through the DAO |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/clipboard/provider/ClipboardDatabase.kt` | S1-clipboard | `deleteUnpinnedOlderThan` DAO query; `fallbackToDestructiveMigration()` removed from both clipboard databases (schema versions unchanged, so no migration is needed) |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/AppPrefs.kt` | S1-clipboard | `historyAutoCleanOldEnabled` default true, `historyAutoCleanOldAfter` default 1440 (minutes, 24 h) |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/settings/clipboard/ClipboardScreen.kt` | S1-clipboard | Auto-clean slider max 120 -> 1440 minutes so the 24 h default is reachable |
| `app/src/main/res/xml/method.xml`, `app/src/main/kotlin/com/manjul/aikeyboard/ime/core/SubtypeManager.kt` | S1-subtypes | `method.xml`: the single placeholder subtype is replaced by the 8 MVP subtypes (en-US, es-ES, fr-FR, pt-BR, ar, ur-PK, id, hi-IN; labels in `strings_fork.xml`). `SubtypeManager.ensureDefaultSubtypes(tags)` adds missing presets by primary locale, in order, idempotently; called from onboarding only, never on IME start |
| `app/src/main/assets/ime/keyboard/org.florisboard.localization/extension.json` | S1-layouts | `ur-PK` preset: `numericRow` set to `org.florisboard.layouts:persian` (Extended Arabic-Indic digits U+06F0-U+06F9, the Urdu digits; each key's long-press gives the western digit). The number-row layout is also the top row of the symbols layer, so this is the per-subtype native/western digit choice; users switch it with "Number row layout" in the subtype editor (`western_arabic` for western). `ar` and `hi-IN` already default to native digits. Characters defaults are unchanged (`urdu_phonetic`, `hindi_in`); our `org.aikeyboard.layouts` layouts need no preset entry because the subtype editor lists every indexed characters layout |
| `app/src/main/assets/ime/keyboard/org.florisboard.localization/popupMappings/ur-PK.json` | S1-layouts | Coverage gaps in `urdu_phonetic` (guarded by `ScriptCoverageTest`): its h key is Arabic heh `ه` (U+0647), so the mapping's `ہ` entry never matched; a new `ه` entry offers `ہ` (main), `ھ`, `ۂ`. `~right` (the `.` key) now starts with `۔` and `؟` |
| `app/src/main/assets/ime/keyboard/org.florisboard.localization/popupMappings/ar.json` | S1-layouts | `؛` (U+061B) added first in `~right` relevant (after the `؟` main): the Arabic characters keyboard had no Arabic semicolon |
| `app/src/main/kotlin/com/manjul/aikeyboard/lib/FlorisLocale.kt` | S1-layouts | `supportsCapitalization` is false for `ur`: Urdu has no case, and auto-shift at sentence start would otherwise type the shift layer of `urdu_standard` (our layout, which puts `ھ`, `ۓ`, `ۂ`, `آ`, ... on shift) |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/text/key/KeyCode.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/ime/text/keyboard/TextKeyData.kt` | S1-smartbar | `TOGGLE_PRIVATE_APP` (-246) key code and predefined key data (also in the internal key list) |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/keyboard/KeyboardManager.kt` | S1-smartbar | Handles `TOGGLE_PRIVATE_APP` (adds the focused editor's package to the private apps; package read on the main thread, never logged); `VOICE_INPUT` is dispatched and enabled only when the policy decision allows voice, `IME_UI_MODE_CLIPBOARD` is enabled only when it allows clipboard history; fail closed (`PolicyDecision.Locked`) without a service |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/keyboard/ComputingEvaluator.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/ime/smartbar/quickaction/QuickAction.kt` | S1-smartbar | Lock icon, name and tooltip for `TOGGLE_PRIVATE_APP` |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/smartbar/Smartbar.kt` | S1-smartbar | Renders `PrivateLockIndicator` first in the main row (nothing unless the field is sensitive); the sticky action is removed when the policy disallows it |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/smartbar/quickaction/{QuickActionsRow,QuickActionsOverflowPanel}.kt` | S1-smartbar | Dynamic actions pass through `policyFiltered()` (disallowed chips are removed, not greyed out); the overflow panel uses the same filtered list so the visible-count arithmetic stays consistent. The row treats a policy-removed sticky action as absent (same as `Smartbar.StickyAction`), so a password field shows one overflow toggle, not two |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/smartbar/quickaction/QuickActionArrangement.kt` | S1-smartbar | `Default`: Mic sticky; Emoji, Clipboard, Settings and the private-app action dynamic; upstream's other actions moved to `hiddenActions` (still available in the actions editor). Translate and AI are not present. Users still on the default pref get the new default; explicitly saved arrangements keep their order but go through the pin rules below. `Serializer.deserialize` returns `fork.smartbar.PinnedActions.normalize(...)` (S1-1.7): Mic is always the sticky action and never in the dynamic or hidden actions, and at most 3 dynamic actions count as pins (the private-app action is exempt and kept last); extra pins move to the front of `hiddenActions`. This also migrates arrangements saved before the rule. Guarded by `PinnedActionsTest` |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/smartbar/quickaction/QuickActionsEditorPanel.kt` | S1-smartbar | Pin rules in the actions editor (PRD FR-07, S1-1.7): the sticky Mic tile cannot be dragged and nothing can be dropped on it; a drop into the dynamic actions that would be a 4th pin is refused (the tile returns where it came from) and the subheader adds "Up to 3 pinned"; the dynamic subheader reads "Pinned (n/3)" (fork strings `quick_actions_editor__subheader_pinned`, `quick_actions_editor__pin_limit`); `onDispose` saves `PinnedActions.normalize(...)` |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/Routes.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/app/settings/HomeScreen.kt` | S1-smartbar | `Settings.PrivateApps` route (`settings/privacy/private-apps`) and a home entry for it |
| `app/src/main/AndroidManifest.xml` | S1-smartbar | `<queries>` entry for MAIN/LAUNCHER intents so the Private apps screen can list launchable apps on API 30+ |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/FlorisAppActivity.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/app/Routes.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/app/settings/HomeScreen.kt` | S1-onboarding | Start destination is onboarding unless `onboardingDone` and the keyboard is enabled and selected (read once before `setContent`, 1 s cap); splash held at most 1 s; `Setup.Screen` route renders `fork/onboarding/OnboardingScreen` (upstream `SetupScreen.kt` is kept, unused, untouched); Home card text "Select AI Keyboard as your keyboard" |
| `app/src/main/AndroidManifest.xml` | S1-onboarding | `RECORD_AUDIO` permission (requested only after the voice notice) |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | S1-onboarding | Turbine and Compose UI test (`ui-test-junit4`, `ui-test-manifest` as `debugImplementation`, versions from the BOM) for the onboarding tests; the commented `testImplementation(composeBom)` line is kept |
| `app/src/main/AndroidManifest.xml` | S1-voice | `<queries>` entry for `android.speech.RecognitionService` so `SpeechRecognizer.isRecognitionAvailable` sees recognition services on API 30+ |
| `settings.gradle.kts`, `app/build.gradle.kts` | S1-voice | `include(":ime:voice-ui")`; `implementation(projects.core.voice)`, `implementation(projects.ime.voiceUi)` |
| `app/src/main/AndroidManifest.xml` | S1-voice | `ACCESS_NETWORK_STATE` permission (the engine choice needs to know whether a network is up); `fork.voice.MicPermissionActivity` (translucent, `excludeFromRecents`, `taskAffinity=""`, not exported). Not `noHistory`: that drops the `RequestPermission` result (no rationale, no `voice_permission_result`); the activity finishes itself in `onStop` unless a request is in flight. Guarded by `ManifestHardeningTest` and `MicPermissionActivityTest` |
| `app/src/main/res/values/strings_fork.xml` | S1-voice | Permission-denied rationale strings for the mic trampoline |
| `app/src/main/kotlin/com/manjul/aikeyboard/FlorisImeService.kt` | S1-voice | Creates a `fork.voice.VoiceImeSession` in `onCreate` (after the policy controller); forwards `onUpdateSelection`, `onFinishInputView` (hiding the keyboard ends dictation) and `onDestroy`; companion `voiceSessionOrNull()` accessor |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/keyboard/KeyboardManager.kt` | S1-voice | `VOICE_INPUT` toggles in-keyboard voice typing instead of switching to another voice IME; the S1-smartbar policy gate is kept |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/editor/AbstractEditorInstance.kt` | S1-voice | `voiceOwnsComposing` flag: while voice owns the composing region, `handleSelectionUpdate` does not treat the region as a word the editor must re-sync |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/smartbar/Smartbar.kt` | S1-voice | After `PrivateLockIndicator`, `smartbarVoicePanel()` replaces the rest of the main row while the voice panel is open |
| `settings.gradle.kts`, `app/build.gradle.kts` | S1-whisper | `include(":lib:whisper")` (vendored whisper.cpp v1.9.5 + JNI bridge `libaikb_whisper.so`); `implementation(projects.lib.whisper)` |
| `gradle/libs.versions.toml` | S1-whisper | `androidx-work` 2.12.0 (`work-runtime`, `work-testing`) and `mockwebserver` 4.12.0 (5.x pulls `okhttp-android`, which needs compileSdk 37) inside the `# --- AI Keyboard ---` blocks |
| `app/build.gradle.kts` | S1-whisper | `WHISPER_MODEL_BASE_URL` build config field (placeholder `https://example.com/models/`, TODO(OD-01); every file is SHA-256 pinned in `WhisperModelSpec`) and `WHISPER_ENABLED_LANGS` (`"*"` = all languages in debug and beta, `""` = none in release); `implementation(libs.androidx.work.runtime)` |
| `app/src/main/AndroidManifest.xml` | S1-whisper | `INTERNET` permission, used only by the Whisper model download worker (the app had no network access before); `androidx.work.WorkManagerInitializer` removed from the startup provider, so WorkManager initialises on demand through `FlorisApplication`'s `Configuration.Provider` and never on the IME cold start. WorkManager's exported `SystemJobService` (`BIND_JOB_SERVICE`) and `DiagnosticsReceiver` (`DUMP`) are on the `ExportedComponentRules` allow-list |
| `app/src/main/kotlin/com/manjul/aikeyboard/FlorisApplication.kt` | S1-whisper | Lazy `whisperVoice` (`fork.voice.WhisperVoice`: shared whisper.cpp context, idle unloader, model manager) and `Context.whisperVoice()` accessor; `Configuration.Provider` with `ModelDownloadWorkerFactory`; `onTrimMemory` frees the loaded model (non-blocking) when Whisper was ever used |
| `app/build.gradle.kts`, `app/src/main/kotlin/com/manjul/aikeyboard/FlorisApplication.kt` | S1-privacy-logs | Debug builds enabled every Flog level, and the info/debug lines carry key labels (including password-field keystrokes), spell-check text and `EditorInfo` summaries (device check 2.8, PC-10). Flog now runs at error + warning only; `-Paikb.verboseFlog=true` (`BuildConfig.VERBOSE_FLOG`) turns full logging back on for a local developer build. Release stays silent (`isFloggingEnabled = DEBUG`) |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/nlp/han/HanShapeBasedLanguageProvider.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/ime/keyboard/KeyboardManager.kt` | S1-privacy-logs | The two error-level Flog lines that interpolated user content (composing text; the full key data of an unknown key) now log only non-content fields |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/Routes.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/app/settings/HomeScreen.kt` | S1-whisper | `Settings.Voice` route (`settings/voice`) rendering `fork/settings/VoiceSettingsScreen` and a home entry for it after Private apps |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/keyboard/KeyboardManager.kt` | S1-policy | Final review Important 1: the `IME_UI_MODE_CLIPBOARD` key handler asks `fork.clipboard.ClipboardPanelGate.onClipboardKey`, so no route (smartbar chip, Enter long-press popup `-213`, `SWITCH_TO_CLIPBOARD_CONTEXT` swipe) opens clipboard history where the policy decision denies it |
| `app/src/main/kotlin/com/manjul/aikeyboard/FlorisImeService.kt` | S1-policy, S1-voice, S1-analytics | Final review fixes. S1-policy: `ClipboardPanelGate.closeOnRestriction` on the policy controller closes an open clipboard panel on every more restrictive decision (new field, restart, late settings, R7), and `onStartInputView` keeps the panel on a restart only if the decision allows clipboard history (upstream "hide on next text field" pref still applies). S1-voice: `onStartInput` tells the voice session a new field's connection holds no region of ours (before the policy call); `onFinishInput` cancels voice before `super.onFinishInput()` so the partial is discarded, not committed (R39). S1-analytics: `onStartInputView` sends the sampled `ime_session_start` through `fork.analytics.ImeSessionStartReporter` (R36) |
| `settings.gradle.kts` | S2-translate | `include(":core:translate")` (pure-JVM translation core: engine interface, router, LRU, chunking, translator pool, bar pipeline) |
| `gradle/libs.versions.toml`, `settings.gradle.kts`, `app/build.gradle.kts`, `app/src/main/kotlin/com/manjul/aikeyboard/FlorisApplication.kt` | S2-mlkit | Catalog entries `mlkit-translate` 17.0.3, `mlkit-language-id` 17.0.6, `kotlinx-coroutines-play-services`; `include(":core:translate-mlkit")`; `implementation(projects.core.translate)`, `implementation(projects.core.translateMlkit)`; lazy `translation` holder (`fork/translate/TranslationCore`) on `FlorisApplication`, `onTrimMemory` forwards to its translator pool only if it already exists, `Context.translation()` accessor. ML Kit is not initialised at process start |
| `settings.gradle.kts` | S2-bar | `include(":ime:editing")` (pure-JVM composing-span helpers shared by voice and the translate bar: `TextTarget`, `ComposingSpanController`, `OwnWriteUndo`); `include(":ime:translate-ui")` (the in-keyboard Translate bar: controller, bar buffer, Compose bar) |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/text/key/KeyCode.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/ime/text/keyboard/TextKeyData.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/ime/smartbar/quickaction/QuickAction.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/ime/keyboard/ComputingEvaluator.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/ime/smartbar/quickaction/QuickActionArrangement.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/ime/smartbar/quickaction/QuickActionsEditorPanel.kt` | S2-bar | Translate chip: new key code `TOGGLE_TRANSLATE_BAR` (-247) and its predefined `TextKeyData`, quick-action label/tooltip (strings in `strings_fork.xml`) and `Icons.Default.Translate` icon; it leads the default dynamic actions and the actions editor refuses to drag it (fixed, never pinned or hidden, see `fork/smartbar/PinnedActions`) |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/keyboard/KeyboardManager.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/ime/editor/EditorInstance.kt` | S2-bar | While the Translate bar is open, soft and hardware keys, suggestion taps, glide words and clipboard pastes go to the bar buffer through `fork/translate/BarKeyRouter` and never reach the editor; `TOGGLE_TRANSLATE_BAR` opens or closes the bar only when the live policy decision allows translation, and the key is disabled otherwise |
| `app/src/main/kotlin/com/manjul/aikeyboard/ime/smartbar/Smartbar.kt` | S2-bar | The open Translate bar (`fork/translate/SmartbarTranslateBar`) renders above the smartbar row, in a `Column` around the upstream body |
| `app/src/main/kotlin/com/manjul/aikeyboard/FlorisImeService.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/FlorisApplication.kt`, `app/build.gradle.kts` | S2-bar | `TranslateImeSession` per service (mirrors `VoiceImeSession`): created in `onCreate`, forwarded `onStartInput`, `onStartInputView` (keeps the hidden-bar text when the keyboard returns), `onUpdateSelection`, `onFinishInputView`, `onFinishInput`, `onDestroy`; voice output goes to the bar while it is open; opening the bar cancels voice first. The translation core gets the Wi-Fi-only setting for model downloads. `implementation(projects.ime.translateUi)` |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/devtools/DevtoolsScreen.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml` | S2-translate | Task 4 day-1 latency check (TRD §8, R-03): a "Translate latency probe" row (`fork/devtools/TranslateLatencyProbe`), shown only when `BuildConfig.DEBUG`; the debug source set adds `core/translate-mlkit/src/androidTest/assets` so the probe reads the instrumented test's `latency/` corpus (debug APK only); catalog entry `androidx-test-runner` 1.6.1 for the `:core:translate-mlkit` instrumented test. `DevtoolsScreen.kt` also gets the S0-licences change-notice line |
| `app/src/main/kotlin/com/manjul/aikeyboard/FlorisApplication.kt` | S2-packs | Lazy `translateModelStore` (`:core:translate-mlkit` `TranslateModelStore` over ML Kit's `RemoteModelManager` and WorkManager) and `Context.translateModelStore()` accessor; the translation core's model presence and the bar's downloads go through it; `TranslateModelDownloadWorkerFactory` joins the Whisper factory in a `DelegatingWorkerFactory`; an activity-lifecycle callback refreshes the model states when an app screen resumes (never from the IME, never at process start) |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/Routes.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/app/settings/HomeScreen.kt` | S2-packs | `Settings.OfflineLanguages` route (`settings/offline-languages`, the keyboard's offline chip deep-links here) rendering `fork/settings/OfflineLanguagesScreen` and a home entry for it after Voice |
| `settings.gradle.kts`, `app/build.gradle.kts`, `app/src/main/kotlin/com/manjul/aikeyboard/FlorisApplication.kt` | S2-translator | `include(":core:database")`, `include(":core:tts")` and their `implementation` lines; lazy `appDatabase` (`AppDatabase`, file `app_database`, never backed up), `translationHistory` (`TranslationHistoryRepository`) and `ttsManager` (`TtsManager`) on `FlorisApplication` with `Context.translationHistory()` / `Context.ttsManager()` accessors. Nothing is opened at process start |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/Routes.kt`, `app/src/main/kotlin/com/manjul/aikeyboard/app/settings/HomeScreen.kt`, `app/src/main/res/values/strings_fork.xml` | S2-translator | `Routes.Translator` (`translator`) and `Routes.TranslatorHistory` (`translator/history`) rendering `fork/translator/TranslatorScreen` and `HistoryScreen`; a "Translate" home entry at the top of the list; `translator__*` and `history__*` strings |
| `app/src/main/AndroidManifest.xml` | S2-processtext | Exported `fork.processtext.TranslateTextActivity` (label "Translate", `FlorisAppTheme.Transparent`, `excludeFromRecents`, `taskAffinity=""`) with exactly two filters: `PROCESS_TEXT` and `SEND`, each `DEFAULT` + `text/plain`, no scheme/host/path. On the `ExportedComponentRules` allow-list with its filter set pinned (PRD FR-14 AC5); guarded by `ManifestHardeningTest` |
| `app/src/main/kotlin/com/manjul/aikeyboard/app/FlorisAppActivity.kt` | S2-routes | Incoming intents go through `fork/routes/AppIntents.classify`: `open_screen` extras and `ui://florisboard/...` / `app://...` links open only routes on the `fork/routes/RouteRegistry` allow-list (via `navController.graph.hasDeepLink` + `navigate(uri)`), anything else opens Home and logs `deeplink_unknown` without the value; extension import (`VIEW` / `SEND` with a URI) keeps working, an empty or text-only clip is ignored (upstream crashed on `getItemAt(0).uri!!`). The activity intent is replaced with a bare `ACTION_MAIN` after classification so Navigation never auto-handles the raw intent (incl. injected `deepLinkIds`). Translator text is handed over through `TranslatorHandoff` |
| `settings.gradle.kts` | S3-suggest | `include(":core:suggest")` (pure-JVM suggestion engine: `AKSD` dictionary format, Damerau-Levenshtein matcher with key-proximity costs, unigram/bigram/learned ranking, `AutocorrectPolicy`) |

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
| `app/src/main/assets/ime/keyboard/org.florisboard.localization/{extension.json,popupMappings/ur-PK.json,popupMappings/ar.json}` | JSON has no comment syntax; S1-layouts additions recorded in the rows above |
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
- `:ime:voice-ui` (Android library, Compose, S1-voice): `VoiceController`, `VoicePanel`. No `:app` types; the IME wiring is in `:app` `fork/voice`.
- `:ime:editing` (pure JVM, S2-bar): `TextTarget`, `ComposingSpanController` (moved from `:ime:voice-ui`, package rename only), `OwnWriteUndo`. Test fixtures `FakeEditor` (InputConnection-like buffer with host-side edits and a delayed selection-update queue) and `HostSafetyChecker`. New files.
- `:ime:translate-ui` (Android library, Compose, S2-bar): `TranslateBarController`, `BarUiState`, `BarBuffer` (grapheme-aware, 5,000 chars) and `BarBufferTarget`, the Compose `TranslateBar` with `PairChip`, `BarStatusChip`, `UndoChip`. No `:app` types. New files.
- `:core:privacy` (Android library, S1-clipboard): clipboard sensitivity classifier and capture gate inputs. New files.
- `:core:analytics` (Android library, S1-analytics): `EventCatalog`, limits, `BatchingRelay`, `LogcatSink` (debug) and `NoopSink`. No Firebase yet.
- `:core:voice` (Android library, S1-voice, S1-whisper): `SpeechSession`, platform recognizer engine, `SpeechEngineSelector`, audio focus, and the Whisper engine, model manager and download worker.
- `:core:translate` (pure JVM, S2-translate): `TranslationEngine`/`LanguageDetector` interfaces, `TranslationEngineRouter` (no silent cloud fallback, refuses when the field policy forbids translation), `TranslationCache` (LRU 200), `SentenceChunker`, `SourceResolver`, `ModelSizes`, `TranslatorPool`, the disabled `CloudTranslationEngine` stub (OD-05) and the bar `TranslatePipeline`. Fakes in `testFixtures`. New files.
- `:core:translate-mlkit` (Android library, S2-mlkit): `MlKitTranslationEngine` (never downloads; 5 s timeout; errors mapped by `MlKitErrors`, messages dropped), `MlKitLanguageDetector` (first 200 chars), `MlKitLanguages`, `MlKitBootstrap` (ML Kit starts on first use), `ModelPresence` / `RemoteModelPresence`, `TranslatorHandle` seam. Its manifest removes `MlKitInitProvider` (`tools:node="remove"`); `AdAutoInitRules` now also forbids any `com.google.mlkit.` provider in the merged manifest. New files.
  Task 4 adds `LatencyStats` (nearest-rank percentiles) and `LatencyCheck` (pairs, budgets, corpus parsing) to `:core:translate`, and the instrumented `TranslateLatencyTest` plus its 50-sentence corpora (`src/androidTest/assets/latency/`) to `:core:translate-mlkit`; method in `docs/translation/latency.md`. New files.
- `:core:translate-mlkit` offline packs (S2-packs): `RemoteModels` / `MlKitRemoteModels`, `TranslateModelStore` (states, Wi-Fi-only downloads, RN-06 Stop via `translateStopped`, English pivot kept), `TranslateModelDownloadWorker` (unique work `translate-model-<lang>`, input data = the language tag only), `ModelUiState`. New files.
- `:core:database` (Android library, Room + KSP, S2-translator): `AppDatabase` (version 1, schema exported to `core/database/schemas/`, multi-instance invalidation, no destructive fallback), `TranslationHistoryEntity`/`Dao`, `TranslationHistoryRepository` (one row per edit session, search, favourites, trims to 500 oldest non-favourites first). `DestructiveMigrationBanTest` fails if any main source calls `fallbackToDestructiveMigration`. New files.
- `:core:tts` (Android library, S2-translator): `TtsManager` (one ref-counted `TextToSpeech` per process, `QUEUE_ADD`, progress via `UtteranceProgressListener`, availability and install-voice intent), `TtsChunker` (sentence chunks under the engine limit, nothing dropped), `TtsState`. New files.
- `:app` `fork/translator` (S2-translator): `TranslatorScreen`, `TranslatorViewModel` (600 ms pipeline, one history row and one `translate_done` per edit session, shared pair), `TranslatorUiState`, `HistoryScreen`/`HistoryViewModel`, `LanguagePickerSheet`, `TranslatorMic` (Sprint 1 `SpeechSession`, platform recognizer only) and `TranslatorErrors`. Nothing from `core.ads`. New files.
- `:app` `fork/translate` (S2-bar, S2-mlkit, S2-packs): `TranslateImeSession` (the bar's IME wiring, mirrors `VoiceImeSession`), `BarKeyRouter`, `SmartbarTranslateBar`, `LowTierDevice` (decision 5), `TranslationCore` (lazy translation holder) and `TranslateModels`; `fork/settings` `OfflineLanguagesScreen`/`OfflineLanguagesViewModel` (S2-packs); `fork/devtools` `TranslateLatencyProbe` (S2-translate, debug only). New files.
- `:app` `fork/processtext` (S2-processtext): `ProcessTextInput` (intent parsing, 5,000-char cap), `TranslateSheetViewModel`, `TranslateSheet` (Compose), `TranslateTextActivity`. `:app` `fork/routes` (S2-routes): `RouteRegistry` (route allow-list), `AppIntents` (intent classifier, `TranslatorHandoff`). New files.
- `:lib:whisper` (Android library, native, S1-whisper): vendored whisper.cpp v1.9.5 (`third_party/`, see `WHISPER_UPSTREAM.md`) and the 16 KB-aligned JNI bridge `WhisperNative`.
- `:core:suggest` (pure JVM, S3-suggest): `DictionaryFormat` (`AKSD` v1: sorted word table with log-scaled frequency bytes, optional bigram table), `SuggestDictionary` (sorted array walked as an implicit trie), `DamerauLevenshtein` (OSA, bounded), `KeyProximity` (QWERTY for en/es/fr/pt/id, row strings for ar/ur/hi copied from the shipped layouts), `SuggestEngine`, `AutocorrectPolicy`, `LearnedWords`. Test fixtures `InMemoryLearnedWords`, `TestDictionaries`.

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
  `ui-tooling` is a `debugImplementation`), `androidx.activity.ComponentActivity` (debuggable builds only; the
  Compose test host from `ui-test-manifest`, a `debugImplementation`), and WorkManager's
  `androidx.work.impl.background.systemjob.SystemJobService` (must require `android.permission.BIND_JOB_SERVICE`)
  and `androidx.work.impl.diagnostics.DiagnosticsReceiver` (must require `android.permission.DUMP`). Keep the `:app` entries in sync with `ManifestHardeningTest`.
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
