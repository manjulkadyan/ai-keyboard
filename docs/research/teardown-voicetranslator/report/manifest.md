# Team 1: Manifest & Permissions — Voice Keyboard and Translator v1.4

Static-only pass with no device, proxy or Frida. Tags: `[obs]` means seen directly in the manifest, resources or decompiled source; `[dec]` means a decision or recommendation for the clone; `[inf]` means inferred and not verified. Paths are relative to `voicetranslator_work/`.
Sources: `jadx_out/resources/AndroidManifest.xml` (457 lines), `jadx_out/resources/res/`, `jadx_out/resources/assets/`, `jadx_out/sources/`, `unpack/*.apk`, `unpack/manifest.json`.

---

## 1. Identity & provenance

| Field | Value | Tag |
|---|---|---|
| Package | `com.heri.language.keyboard.voice.typing` | [obs] |
| Store name / label | "Voice Keyboard and Translator" (`@string/app_name`; same label for every locale in `unpack/manifest.json`) | [obs] |
| Version | 1.4 (versionCode 5) | [obs] |
| SDK | minSdk 24, targetSdk 36, compileSdk 36 (codename "16"), platformBuildVersionCode 36 | [obs] |
| Declared Application | `com.pairip.application.Application` | [obs] |
| Real Application | `com.example.voicetypingkeyboard_s.VoiceTypingKeyboard`: the PairIP class `extends VoiceTypingKeyboard` | [obs] |
| Source namespace | `com.example.voicetypingkeyboard_s` (Android Studio template package left in production) plus the `dev.patrickgold.florisboard` fork | [obs] |
| Distribution | XAPK v2: base (13.4 MB) + `config.arm64_v8a` (103 KB) + `config.xxxhdpi` (57 KB), 13.56 MB total. `requiredSplitTypes="base__abi,base__density"`, `com.android.vending.splits.required=true`, `stamp.source=https://play.google.com/store`, `STAMP_TYPE_DISTRIBUTION_APK`, `derived.apk.id=2` | [obs] |
| Dex | `classes.dex` + `classes2.dex`, R8 (`r8-map-id-53b39b15…`) | [obs] |
| Native libs (arm64) | `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`, `libimage_processing_util_jni.so` (CameraX), `libsurface_util_jni.so` (CameraX). No first-party, PairIP or ML native code | [obs] |
| Developer refs | feedback mail `arameezsc@gmail.com` (`sources/cb/f2.java:48`), privacy policy `https://sites.google.com/view/voice-keyboard-heri-policy/home` (`cb/f2.java`, case 2), share text links `play.google.com/store/apps/details?id=<pkg>` | [obs] |
| Firebase project | `voice-typing-keyboard-s` (`res/values/strings.xml`) | [obs] |
| UI framework | Single Activity + Jetpack Compose (`g3.j1` ComposeView set in `MainActivity.onCreate`) + **Navigation 3** (`d6.e` = `NavBackStack<NavKey>`, routes are `@Serializable` `AppRoutes.*`) | [obs] |

### Signing

From `apksigner verify --print-certs -v` on all three split APKs. Every split has the same signer [obs].

| Item | Value |
|---|---|
| Schemes (base) | v1: **no**, v2: yes, v3: yes, v3.1: no, v4: no; SourceStamp verified (splits also carry v1) |
| Signer DN | `CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US` |
| Signer cert SHA-256 | `bd273808b7bc6c655dfe7f0a0557e9f9b8c383aee515141a75e587eac83b0dff` |
| Signer cert SHA-1 | `ff1bdcf26c42eb5a2652fe8ae7a5a86e8bb848e7` |
| Key | RSA 4096; pubkey SHA-256 `1c37e640349dfbd9e1b3421e9f89a8e7823e984df51c29f5c58029050106ebaa` |
| SourceStamp cert SHA-256 | `3257d599a49d2c961a471ca9843f59d341a405884583fc087df4237b733bbd6d` (the standard Play Store stamp, same as the camTranslator apps; also in `base/stamp-cert-sha256`) |
| Stamp timestamp | 1782989949 (epoch seconds) |

The generic Google DN means the APK is signed by **Play App Signing**, so the developer's upload key is not visible [inf]. A clone could not, and must not, reuse this signature. Any API-key restriction based on SHA-1 would bind to `ff1bdcf2…e7` [inf].

### PairIP handoff

`sources/com/pairip/application/Application.java` overrides only `attachBaseContext()`: it calls `LicenseClient.checkLicense(context)` and then `super.attachBaseContext()` [obs]. `com/pairip/` contains only `application/` and `licensecheck/` (LicenseClient, LicenseActivity, LicenseContentProvider, RepeatedCheckMetadata, LicenseResponseHelper…) [obs]. `LicenseActivity` is declared with `exported=false` [obs]. **`LicenseContentProvider` is NOT declared in the manifest**, so the provider-based early-init path is dead code and the license check runs only from the Application wrapper [obs]. `CHECK_LICENSE` is declared outside `<application>`, as PairIP injection does [obs]. There is no `libpairipcore.so`, so this is the licensing-only PairIP variant (Team 5 owns the details) [obs].

### Application init (`VoiceTypingKeyboard`)

- Implements the `ti.b` generated-component interface and pulls an `eventsHelper` from a generated graph (`oa.e … .get()`). This is a Hilt-style DI component; no `dagger.hilt` names survive R8 [inf].
- `onCreate`: initialises the Floris singletons (`t0.f36928a/b`), launches two coroutines, and in direct-boot (locked) state defers work through `registerReceiver(…, USER_UNLOCKED)` [obs]. It deletes a stray `cache/WebView` file, and for non-main processes calls `WebView.setDataDirectorySuffix(processName)` (the multi-process WebView fix for ads) [obs].
- The App-open ad manager `pb/d.java` ("ComposeOpenAppManager") registers `ActivityLifecycleCallbacks` + `ProcessLifecycleOwner`. It reads the current Nav3 route from the main back stack and blocks app-open ads on `Splash`, `PermissionActivity`, and `AdActivity`. It caches the ad for 4 hours [obs]. Monetization owns the details.

---

## 2. Entry points

Manifest totals [obs]: **8 activities (+1 activity-alias), 14 services, 12 receivers, 4 providers**.
First-party components are listed in full. SDK components are grouped.

### 2.1 Activities

| Component | exported | Intent filters | Attributes | Theme | UI |
|---|---|---|---|---|---|
| `com.example.voicetypingkeyboard_s.MainActivity` | **true** | MAIN + LAUNCHER | portrait, `adjustNothing` | `Theme.VoiceTypingKeyboard_S` | Compose host for all 22 Nav3 routes (§7). `attachBaseContext` applies the `pref_selected_language` locale (default "en"). `onNewIntent` → `setIntent` with no extras parsed [obs] |
| `dev.patrickgold.florisboard` (**activity-alias** → MainActivity) | false | none | singleTask | `FlorisAppTheme` | Floris's in-code launcher target for settings. The alias name equals the package name [obs] |
| `dev.patrickgold.florisboard.PermissionActivity` | false (default, no filter) | none | `taskAffinity=""`, `finishOnTaskLaunch`, `excludeFromRecents`, `noHistory` | `Theme.Translucent.NoTitleBar` | Invisible trampoline. The IME starts it with `FLAG_ACTIVITY_NEW_TASK` (`wi/k.java:232`) to request **RECORD_AUDIO** from inside the keyboard. On a permanent deny it opens `APPLICATION_DETAILS_SETTINGS`, falling back to `SETTINGS`. Carries the extra `EXTRA_WAS_TRANSLATION_BAR_OPEN` so it can reopen the in-keyboard translate bar [obs] |
| `com.google.android.libraries.ads.mobile.sdk.common.AdActivity` | false | none | wide configChanges | `AdCustomTheme` | GMA Next-Gen SDK fullscreen ads [obs] |
| `com.android.billingclient.api.ProxyBillingActivity` / `…V2` | false | none | translucent | | Play Billing 8.3.0 library stubs. No first-party caller (see Team 3) [obs] |
| `com.google.android.gms.common.api.GoogleApiActivity` | false | | translucent | | GMS resolution [obs] |
| `com.google.android.play.core.common.PlayCoreDialogWrapperActivity` | false | | `Theme.PlayCore.Transparent` | | In-app update dialog [obs] |
| `com.pairip.licensecheck.LicenseActivity` | false | | | | PairIP license-fail dialog [obs] |

Only one component is publicly launchable from outside: MainActivity, via LAUNCHER [obs].

### 2.2 Services

| Component | exported | Permission / filter | Notes |
|---|---|---|---|
| **`dev.patrickgold.florisboard.FlorisImeService`** | **true** | `BIND_INPUT_METHOD`; action `android.view.InputMethod`; meta `android.view.im` → `@xml/method` | The keyboard. `directBootAware=false`. See §3 [obs] |
| `com.example.voicetypingkeyboard_s.utils.fcmHelper.FCMHelper` | false | `com.google.firebase.MESSAGING_EVENT` | First-party FCM service, outranks the default Firebase service (priority −500) [obs] |
| `com.google.firebase.messaging.FirebaseMessagingService` | false | MESSAGING_EVENT, prio −500 | SDK [obs] |
| `com.google.firebase.components.ComponentDiscoveryService` | false | — | Registrars: **Perf**, **Messaging**, Analytics connector, **Crashlytics**, **Sessions**, **RemoteConfig**, Installations, ABT, Datatransport [obs]. Note: the orchestrator's SDK roster leaves out Crashlytics and Performance Monitoring |
| `androidx.work.impl.background.systemjob.SystemJobService` | **true** | `BIND_JOB_SERVICE` | WorkManager. Exported, but only the system can bind [obs] |
| WorkManager `SystemAlarmService`, `SystemForegroundService` | false | — | `FOREGROUND_SERVICE` comes from WorkManager. No `foregroundServiceType` is declared and no first-party FGS exists [obs] |
| GMS measurement `AppMeasurementService` / `AppMeasurementJobService` | false | | Analytics [obs] |
| `com.google.firebase.sessions.SessionLifecycleService` | false (`enabled=false`) | | [obs] |
| `androidx.camera.core.impl.MetadataHolderService` | false (`enabled=false`) | | CameraX config holder [obs] |
| `androidx.room.MultiInstanceInvalidationService` | false | | Room [obs] |
| Datatransport `TransportBackendDiscovery`, `JobInfoSchedulerService` | false | | Firelog/CCT [obs] |

### 2.3 Receivers

| Component | exported | Guard | Notes |
|---|---|---|---|
| `com.google.firebase.iid.FirebaseInstanceIdReceiver` | **true** | `c2dm.permission.SEND` | FCM [obs] |
| `androidx.work.impl.diagnostics.DiagnosticsReceiver` | **true** | `android.permission.DUMP` | Shell-only [obs] |
| `androidx.profileinstaller.ProfileInstallReceiver` | **true** | `DUMP` | Baseline profiles (`assets/dexopt/`) [obs] |
| `AppMeasurementReceiver` | false | | [obs] |
| WorkManager `ForceStopRunnable$BroadcastReceiver`, 4× `ConstraintProxy$*` (disabled), `RescheduleReceiver` (disabled; BOOT_COMPLETED/TIME_SET), `ConstraintProxyUpdateReceiver` | false | | [obs] |
| Datatransport `AlarmManagerSchedulerBroadcastReceiver` | false | | [obs] |

There are no first-party manifest receivers. The only first-party runtime receiver is the `USER_UNLOCKED` one in the Application class [obs]. The custom permission `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature) is the AndroidX `ContextCompat.registerReceiver(RECEIVER_NOT_EXPORTED)` shim [obs].

### 2.4 Providers

| Authority | Class | exported | grantUri | Notes |
|---|---|---|---|---|
| `…voice.typing.provider` | `androidx.core.content.FileProvider` | false | true | `@xml/file_paths`: **`external-path "."`**, `files-path "."`, `cache-path "."`, `cache-path backup-and-restore/`. The whole external storage root is shareable by URI grant. The scope is wider than needed, for sharing camera crops and Floris backups [obs]. Clone: narrow to `cache-path images/` [dec] |
| `…voice.typing.provider.clipboard` | `dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider` | false | true | Stock Floris: serves clipboard images and video so they can be committed via `commitContent` [obs] |
| `…voice.typing.androidx-startup` | `androidx.startup.InitializationProvider` | false | | Initialisers: WorkManager, ProcessLifecycle, OkHttp `PlatformInitializer`, ProfileInstaller [obs] |
| `…voice.typing.firebaseinitprovider` | `FirebaseInitProvider` | false | | initOrder 100, directBootAware [obs] |

### 2.5 Deep links / share targets / text actions

| Surface | Present? | Evidence |
|---|---|---|
| Custom scheme / App Links (`VIEW` + `BROWSABLE` filter) | **No** | No `<data>` inside any `<intent-filter>`, no `autoVerify` [obs] |
| `ACTION_SEND` / `SEND_MULTIPLE` share target | **No** | No filter. Outbound only: "Share App" chooser in `cb/f2.java` [obs] |
| `ACTION_PROCESS_TEXT` (text-selection "Translate" menu) | **No filter** | The only PROCESS_TEXT code is Compose/AppCompat text-toolbar library code (`g5/g`, `t0/h1`, `u0/a`) that *queries* other apps [obs]. This is a cheap growth surface the competitor lacks. Clone: add a PROCESS_TEXT activity "Translate with …" [dec] |
| App widgets / shortcuts / tiles / quick settings | **No** | No `appwidget-provider`, no `shortcuts.xml`, no TileService [obs] |
| Notification deep links | FCM only | `FCMHelper` builds notifications [inf]. Payload keys are a Team 2 item |

### 2.6 `<queries>` (package visibility)

`android.view.InputMethod` and `SpellCheckerService` (Floris: lists IMEs and spell checkers), `VIEW https BROWSABLE`, `CustomTabsService`, calendar `INSERT`, `VIEW sms:`, `DIAL tel:` (GMA MRAID), `InAppBillingService.BIND`, `BillingOverrideService.BIND` (Billing lib) [obs].

**Missing:** there is no `<queries>` entry for `android.speech.RecognitionService` or `android.speech.action.RECOGNIZE_SPEECH` [obs]. The Dictionary screen gates its mic on `intent.resolveActivity(pm) == null` (`sources/db/d.java:41`). On API 30+ that call can return null when the recognizer package is not visible, which would show "No speech recognizer installed" [inf]. The IME's `SpeechRecognizer.createSpeechRecognizer` path (`wi/k.java:250`) binds a service and is affected less [inf]. → **Open item M-1**.

---

## 3. IME service & `method.xml`

`res/xml/method.xml` [obs]:

| Attribute | Value |
|---|---|
| `settingsActivity` | `dev.patrickgold.florisboard.SettingsLauncherAlias`, **not declared** in the manifest and not referenced in code. The "Settings" gear in the system IME settings will fail or no-op [obs/inf]. Upstream FlorisBoard declares this alias; the fork renamed its alias to `dev.patrickgold.florisboard` and forgot to update this attribute [inf] |
| `supportsSwitchingToNextInputMethod` | true (globe/language key may switch IME) |
| `supportsInlineSuggestions` | true. Autofill inline suggestions: `onCreateInlineSuggestionsRequest` / `onInlineSuggestionsResponse` are implemented in `FlorisImeService.java:415/579` [obs] |
| Subtypes | **1**: label `@string/app_name`, `imeSubtypeMode="keyboard"`, `imeSubtypeExtraValue="AsciiCapable"`, `isAsciiCapable=true`, `overridesImplicitlyEnabledSubtype=true`. No `imeSubtypeLocale` and no `languageTag`, so the system sees one language-neutral subtype. Language switching is handled internally by Floris [obs] |

**Language data actually shipped** [obs]:
- Keyboard languages offered in the app picker (`cd/d.java:790-797`): **7**. English, Spanish, French, Portuguese, Arabic, Urdu, Indonesian (ids 1–6 and 8; id 7 is skipped). These match the 7 word lists in `assets/ime/dict/*.txt` [obs].
- The Floris localization extension (`assets/ime/keyboard/org.florisboard.localization/extension.json`) still carries **71 subtype presets** and 55 popup mappings, plus 76 character layouts. This is upstream data, mostly unreachable from the 7-language picker [obs/inf].
- Translation target languages (`r6/y.java`): **103** `TranslationLanguagesModel` entries [obs].
- App UI languages (`ya/a.java`): **31** (en, hi, es, pt, ar, bn, fr, ru, ur, id, de, ja, zh-CN, ko, tr, vi, it, th, pl, mr, te, ta, nl, tl, ms, fa, uk, ro, sv, el, cs). Resource translations exist for about 80 `values-*` locales, with 267 of the 510 base strings translated [obs].

**Service class shape** [obs]: `FlorisImeService` (839 lines) extends `kj/b` (abstract `InputMethodService` + `LifecycleOwner`). It overrides `onCreate/onCreateInputView/onCreateCandidatesView/onCreateExtractTextView/onStartInput/onStartInputView/onFinishInput(View)/onUpdateSelection/onComputeInsets/onEvaluateFullscreenMode/onWindowShown/Hidden/onKeyDown/Up/onInlineSuggestions*`.

**Host-app ad suppression** (`FlorisImeService.java:683`) [obs]: `onStartInputView` logs `editorInfo.packageName` under the tag `mAds_`, then checks it against a hard-coded list of **86 package names**. The list holds the app's own package, about 48 `com.google.android.*` apps (Search/GSA, Talk, Nexus launcher…), Chrome (stable/beta/dev), Play Store (`com.android.vending`), Snapseed, Settings Intelligence, and about 30 OEM or third-party launchers (Samsung, MIUI, Huawei, Oppo/ColorOS, Realme, Vivo, OnePlus, Sony, LG, Motorola, Asus, ZUI, Microsoft, Nova, Lawnchair…). For listed hosts the in-keyboard banner/native ad is hidden. For other hosts it shows only if `keyboard_native_ad_enable && !is_user_premium && can_user_request_ads` [obs]. Hiding ads from Google-owned surfaces such as Play and Search looks like review evasion [inf]. **Do not copy** [dec].

**Privacy-relevant IME behaviour** [obs]: the IME logs every focused host package to logcat (`Log.d("mAds_", "packageName: …")`). The `allowBackup="true"` attribute has no `fullBackupContent` and no `dataExtractionRules`, so Floris clipboard history, user dictionary and emoji history DBs are eligible for cloud backup and device-to-device transfer [inf]. Clone: exclude the clipboard and user-dictionary DBs from backup and do not log host packages [dec].

---

## 4. Permission catalog

All declared permissions (manifest lines 15–71 and 456; identical to the XAPK `permissions` list) [obs].

| Permission | Category | Protection | Requested by / used for | Tag |
|---|---|---|---|---|
| `RECORD_AUDIO` | Microphone | **dangerous** | Voice typing in the IME (`wi/k.java` → `PermissionActivity`). Mic in Conversation (`mb/c0` left/right mic), Text translator, Dictionary (`db/d`). All voice goes through the platform `SpeechRecognizer` / `RECOGNIZE_SPEECH` | [obs] |
| `CAMERA` | Camera | **dangerous** | Camera Translation route (CameraX). Rationale string "Allow camera access to scan and translate text." `uses-feature android.hardware.camera required=false` | [obs] |
| `POST_NOTIFICATIONS` | Notifications | **dangerous** (API 33+) | FCM push. In-app rationale "Allow Notification Access to Stay Updated with Alerts"; rationale check in `j0/b1.java:141` | [obs] |
| `READ_BASIC_PHONE_STATE` | Phone | normal (API 33+) | Ads SDK device signals (`b/tf.java:3450` checks READ_PHONE_STATE or READ_BASIC_PHONE_STATE) | [inf] |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Network | normal | gtx translate, dictionaryapi.dev, sticker packs, ads, Firebase. Many offline guards ("Turn On Internet") | [obs] |
| `VIBRATE` | Haptics | normal | Floris key haptics | [obs] |
| `WAKE_LOCK`, `FOREGROUND_SERVICE` | System | normal | WorkManager / FCM | [obs] |
| `com.android.vending.BILLING` | Billing | normal | Billing lib 8.3.0 merged. No first-party purchase flow (Team 3 open item #1) | [obs] |
| `com.android.vending.CHECK_LICENSE` | Licensing | normal | PairIP LicenseClient | [obs] |
| `com.google.android.gms.permission.AD_ID` | Ads | normal | GMA | [obs] |
| `ACCESS_ADSERVICES_TOPICS` / `_AD_ID` / `_ATTRIBUTION` | Ads (Privacy Sandbox) | normal | GMA; `uses-library android.ext.adservices required=false` | [obs] |
| `c2dm.permission.RECEIVE` | Push | normal | FCM | [obs] |
| `finsky…BIND_GET_INSTALL_REFERRER_SERVICE` | Attribution | normal | Install Referrer (Firebase Analytics) | [obs] |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Internal | signature | AndroidX receiver shim | [obs] |

**Absent** [obs]: no `SYSTEM_ALERT_WINDOW` (overlay), no storage or media permissions (`READ_MEDIA_IMAGES`: gallery import uses `GET_CONTENT` / the photo picker via `i/b.java`), no location (even though `play-services-location.properties` and `places-placereport` are bundled transitively), no contacts, no `QUERY_ALL_PACKAGES`, no `BIND_ACCESSIBILITY_SERVICE`, no `REQUEST_INSTALL_PACKAGES`, no `USE_FULL_SCREEN_INTENT`, no `SCHEDULE_EXACT_ALARM`, no `RECEIVE_BOOT_COMPLETED` (the WorkManager reschedule receiver is disabled). This is a clean, minimal footprint for a keyboard [obs].

**Runtime request surfaces** [obs]:
1. IME mic: the translucent `PermissionActivity` is started from the keyboard window.
2. In-app Compose permission screens with strings "Allow Microphone Permission", "Enable Camera Access", "Allow Notifications". `c1/a.java:216` checks the rationale for CAMERA.
3. Keyboard enable flow (KeyboardSetupScreen, "Step 1 of 2 / Step 2 of 2"): `cb/x.java` opens `android.settings.INPUT_METHOD_SETTINGS` (enable) and then `InputMethodManager.showInputMethodPicker()` (select). Enablement state is read via `getEnabledInputMethodList()` and `Settings.Secure enabled_input_methods/default_input_method` (`ok/a.java:92-129`).

---

## 5. Min/target SDK analysis

| Aspect | Implication | Tag |
|---|---|---|
| minSdk 24 (Android 7.0) | Covers about 99% of devices. Floris itself needs 24+ for direct boot (the `USER_UNLOCKED` path) | [obs/inf] |
| targetSdk 36 | Meets the 2026 Play requirement. Edge-to-edge is enforced: `MainActivity` sets up insets manually (`aVar.s(...)` system bar styling) | [obs] |
| API 30+ package visibility | `<queries>` covers IMEs, browsers, billing and MRAID, but **not speech recognition** (§2.6, M-1) | [obs/inf] |
| API 33+ | `POST_NOTIFICATIONS` runtime prompt present; per-app language handled manually through `pref_selected_language` and `AppCompatDelegate` (not `localeConfig`) | [obs] |
| API 34+ FGS types | Not applicable: there is no first-party foreground service. The WorkManager FGS is declared without a type, but the app never starts expedited FGS work [inf] | [inf] |
| API 35 | `pb/d.java` has a `SDK_INT >= 35` branch for `AdActivity` insets handling | [obs] |
| Portrait lock | `screenOrientation="portrait"` on MainActivity. On Android 16 (target 36), large-screen devices ignore orientation locks [inf] | [obs/inf] |
| `largeHeap=true`, `extractNativeLibs=false`, `hardwareAccelerated=true` | Standard. largeHeap is likely for theme bitmaps and camera images [inf] | [obs] |
| No `networkSecurityConfig` | Cleartext is blocked by default (target ≥28), only system CAs are trusted, and there is no pinning | [obs] |

---

## 6. Layout inventory (XML)

Only **33 XML layouts**, all of them ad or library scaffolding, because the app UI is 100% Compose [obs]:
- Ads: `banner_ad_shimmer_layout`, `layout_ad_container`, `layout_ad_loading`, `native_banner_small_compose_{admob,shimer}`, `native_{first,second}_banner_{admob,shimmer}`, `native_third_medium_media_{admob,shimmer}`, `native_fourth_media_{admob,shimmer}`. That is 4 native templates plus a small one, each with a shimmer placeholder, hosted in Compose through `AndroidView` [inf].
- The other 20 are AppCompat/Material dialog and search defaults.

The keyboard itself is Floris Compose UI themed by "Snygg" stylesheets (`snygg__rule_element__*` strings, `assets/ime/theme/org.florisboard.themes`) [obs].

---

## 7. Screen inventory (Navigation 3 routes)

Serial names come from `sources/a1/m.java:59-93` + `ab/*` (`@Serializable` objects/classes implementing `ab.a1 : NavKey`). There are **22 routes** and all are hosted in `MainActivity` [obs]. Composable owners were identified by named lambda parameters (`k.f(x, "onXClick")`), so they are [inf] where marked.

| # | Route (`AppRoutes.*`) | Args | Composable pkg | Purpose / key UI | Tag |
|---|---|---|---|---|---|
| 1 | `Splash` (`ab/x0`) | — | `kb/g` | Splash + UMP consent + splash interstitial; "Preparing your keyboard…". App-open ads are blocked on this route | [obs] |
| 2 | `KeyboardSetupScreen` (`i0`) | — | `cb/x`, `cb/y0` | 2-step enable/select keyboard, "Enable Voice Typing Keyboard", enable-screen banner | [obs] |
| 3 | `MainScreen` (`r0`) | — | `kb/a`, `lb/a` | Home: themes carousel, cards for Translator, Customize Keyboard, Stickers, Test Keyboard, Settings; main banner/native; `navigateToTrendingScreen/Apply/KeyboardSetup` | [obs] |
| 4 | `LanguageTranslatorScreen` (`q0`) | — | `mb/l` | Translator hub: Text Translation, Camera Translation, Conversation, Phrasebook, Dictionary cards | [obs] |
| 5 | `TextTranslationScreen` (`y0`) | — | `mb/*` | Source/target pickers, swap, mic, paste, copy, read-aloud (TTS), "Type or speak here to translate" | [obs] |
| 6 | `LanguageSelectionScreenForTranslation` (`p0`) | — | `mb/*` | Searchable list of 103 languages ("All Languages / Recently Picked") | [obs] |
| 7 | `CameraTranslationScreen` (`z`) | — | `mb/l` (`onCaptureClick/onGalleryClick/onFlashModeSelected`) | Camera2 capture (raw `mb/h`, `TextureView` + `ImageReader`; CameraX is packaged but stripped; corrected in verification pass 2026-10-17) + gallery import + flash. "Please capture English text." suggests OCR source is English-only (Mobile Vision Latin) [inf] | [obs/inf] |
| 8 | `ImageCropScreen` (`g0`) | `imageUri: String` | `jb/*` (`onCropDone`, `overlayViewState`) | Crop before OCR; result "Image Translation" | [obs] |
| 9 | `ConversationScreen` (`b0`) | — | `mb/c0` (`onLeftMicClick/onRightMicClick`) | Two-sided talk & translate ("Talk & Translate"), clear/delete conversations | [obs] |
| 10 | `PhrasebookCategoriesScreen` (`s0`) | — | `mb/*` (`onCategoryClick`) | Topic groups from `assets/phrases_final.db` (travel, food, shopping, emergency, daily, …) | [obs] |
| 11 | `PhrasesListScreen` (`v0`) | `groupId: Int`, `categoryName: String` | `mb/*` | Phrases with translate/speak | [obs] |
| 12 | `DictionaryScreen` (`d0`) | — | `db/*` | Single-word lookup (dictionaryapi.dev): definition, example, phonetic, noun/verb; "Enter single word only" | [obs] |
| 13 | `CustomizeKeyboardScreen` (`c0`) | — | `cb/n`, `eb/*`, `fb/*` | Theme editor: keys, hot/sticky keys, pop-up, space bar, icons, background colour/gradient/image (crop), opacity, outlines, key radius, spacing, height, sounds (Creamy/Drop/Pop/Tick/Mechanical/Tactile/Retro/Turbo), volume; "Leave without saving?" | [obs] |
| 14 | `KeyboardThemeSelectionScreen` (`o0`) | `initialTab: Int` | `kb/*` | Tabs: Colors / Gradients / Images / Custom ("Create Theme", "No Custom Themes Yet!") | [obs] |
| 15 | `TrendingThemesScreen` (`z0`) | — | `kb/*`, `lb/*` | "Trending Themes" grid | [obs] |
| 16 | `KeyboardThemeApplyScreen` (`l0`) | — | `cb/n` | Preview + Apply, theme_apply native | [obs] |
| 17 | `KeyboardTestScreen` (`k0`) | — | `eb/s` | "Try Your Keyboard..." text field, try-screen native | [obs] |
| 18 | `KeyboardStickersScreen` (`j0`) | — | `cb/n` (`onStickerPackClick/onDownloadPackClick`) | Remote sticker packs (internet required) | [obs] |
| 19 | `AddStickersToKeyboardScreen` (`x`) | `stickersModel: StickersModel` | `cb/n` | Pack preview → "Add to keyboard" | [obs] |
| 20 | `KeyboardLanguageSelectionScreen` (`h0`) | — | `cb/m2` | Add/remove among the 7 keyboard languages | [obs] |
| 21 | `AppLanguageSelectionScreen` (`y`) | — | `cb/m2` | 31 UI languages | [obs] |
| 22 | `SettingScreen` (`w0`) | — | `cb/f2` (actions) | Keyboard toggles (auto-correction, glide typing, suggestions, smart bar, pop-up on keypress, one-handed, clipboard), app language, Rate App, Share App, Feedback (mailto), Privacy Policy, exit | [obs] |

Non-route surfaces [obs]: rate dialog (`nb/f` `onRateAppClick`, "Enjoying the App?"), in-app update dialog ("Update available" / "App update is required…"), exit dialog ("Tap back again to exit"), UMP/GDPR form (`ShowGDPRScreen`), and inside the IME: Floris smartbar, clipboard history, emoji/emoticon panel, stickers tab, translation bar (`EXTRA_WAS_TRANSLATION_BAR_OPEN`), voice-input overlay ("Speak now..."), and the keyboard native ad slot.

**Primary flow** [inf]: Splash → (keyboard not enabled/selected ? KeyboardSetup : Main) → Main hub → LanguageTranslator hub → feature screens. This matches the `navigateToKeyboardSetup` / `navigateToMainScreen` callbacks on the Splash and Setup composables.

---

## 8. Anomalies & clone notes

1. `method.xml` `settingsActivity` points to the undeclared `SettingsLauncherAlias` [obs], so the system IME settings gear is broken. Clone: point it at a declared alias [dec].
2. `LicenseContentProvider` class is shipped but not declared [obs]. This is harmless dead code.
3. No speech-recognition `<queries>` while `resolveActivity` gates the mic [obs]. A possible bug in the Dictionary mic on API 30+ [inf].
4. FileProvider exposes `external-path "."` [obs]. This is an over-broad grant scope.
5. Template package `com.example.voicetypingkeyboard_s` ships in production [obs].
6. Host-package ad-suppression list (86 entries) and host-package logging [obs]. Do not replicate [dec].
7. Crashlytics, Firebase Performance and Sessions are present in the registrars [obs] but missing from the orchestrator roster. Team 3/5 should add them.
8. No inbound integrations (no deep links, PROCESS_TEXT, SEND, widgets) [obs]. The clone can win here by adding PROCESS_TEXT "Translate", SEND text/image → translate, and a Quick Settings tile for camera translate [dec].
9. FlorisBoard Apache-2.0: `assets/license/project_license.txt` (11 KB) is present [obs]. The clone must keep NOTICE/attribution if it forks Floris [dec].

## 9. Open items (static-only)

| # | Item | Owner |
|---|---|---|
| M-1 | Confirm on an API 30+ device that the Dictionary and Text-translator mic works without a `RecognitionService` / `RECOGNIZE_SPEECH` `<queries>` entry; confirm IME `SpeechRecognizer` binding succeeds | Native/UX |
| M-2 | Confirm the system "Input method settings" gear fails (undeclared `SettingsLauncherAlias`) | Native/UX |
| M-3 | Confirm the Splash → Setup vs Main routing condition (IME enabled+selected check in `ok/a.java`) | Native/UX |
| M-4 | FCM payload keys and whether notifications deep-link into a specific route (`FCMHelper`) | Backend |
| M-5 | Check whether Auto Backup actually uploads the Floris clipboard/user-dictionary DBs (no backup rules) | Anti-tamper/Privacy |
