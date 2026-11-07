# Team 1: Manifest & Permissions — Voice Keyboard - Voice Typing v1.3

This is a static-only pass. Tags: `[obs]` means read directly from the manifest, resources, or decompiled source. `[dec]` is a decision or recommendation for the clone. `[inf]` is an inference that needs confirmation.
Paths are relative to `ai-keyboard/voicetyping_work/`.
Sources: `jadx_out/resources/AndroidManifest.xml` (425 lines), `jadx_out/resources/res/`, `jadx_out/sources/`, `base/assets/`, `unpack/*.apk`, `unpack/manifest.json`.

## 1. Identity & provenance

| Field | Value | Tag |
|---|---|---|
| Package | `com.logiapps.language.keyboard.voice.typing.translator` | [obs] |
| Version | 1.3 (versionCode 4) | [obs] |
| SDK | minSdk 26, targetSdk 37, compileSdk 37 (codename "17"), platformBuildVersion 37 | [obs] |
| App label | `@string/app_name` = "Voice Keyboard - Voice Typing" | [obs] |
| Application class | `com.example.voicetypingkeyboard_z.diLayer.MyApplication` (Hilt: implements `fa3`/generated component) | [obs] |
| Theme | `@style/Theme.VoiceTypingKeyboard_Z` | [obs] |
| Internal namespace | `com.example.voicetypingkeyboard_z` is a template leftover. The `_z` suffix and the sticker host path `ZTechVoiceTyping` suggest a white-label studio ("ZTech") | [obs] / [inf] |
| Publisher hints | Package prefix `com.logiapps`; privacy policy at `https://sites.google.com/view/voicekeyboard-logi-policy/home` (in `defpackage/kd4.java` area); feedback email `comboduo682@gmail.com`, subject "Feedback: <app_name> 1.3" (`defpackage/ct0.java:84`) | [obs] |
| Distribution | XAPK with base + `config.arm64_v8a` + `config.xhdpi`. Manifest has `requiredSplitTypes="base__abi,base__density"`, `com.android.vending.splits` -> `@xml/splits0`, `com.android.stamp.source=https://play.google.com/store`, `STAMP_TYPE_DISTRIBUTION_APK`, `derived.apk.id=2` | [obs] |
| Total size | 13,741,089 bytes (`unpack/manifest.json`) | [obs] |
| Dex | `classes.dex` and `classes2.dex`, R8 (jadx header `r8-map-id-8d086b3b…`). 10,684 of 12,845 classes are in `defpackage/` | [obs] |
| Upstream | A fork of **FlorisBoard** (`dev.patrickgold.florisboard.*`), which is Apache-2.0. `base/assets/license/project_license.txt` holds the Apache-2.0 text (169 lines) | [obs] |
| Native libs (arm64) | `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`, `libimage_processing_util_jni.so`, `libsurface_util_jni.so` (all AndroidX/CameraX). No `libpairipcore.so`, no Flutter or RN | [obs] |

### Signing

Ran `build-tools/36.0.0/apksigner verify --print-certs -v unpack/com.logiapps...translator.apk` [obs]:

| Item | Value |
|---|---|
| Schemes | v1: no, **v2: yes, v3: yes**, v3.1: no, v4: no. SourceStamp verified |
| Signer DN | `CN=Android, OU=Android, O=Google Inc., L=Mountain View, ST=California, C=US` |
| Signer cert SHA-256 | `e1a50005ee5d6bbfb73574b4065a796aec4f733dc6d571070f51d1b3caac3ec1` |
| Signer cert SHA-1 | `22e82eb784def5a2f40b1cfe26949e12a72c5226` |
| Signer public key SHA-256 | `966dda410e6ee8d26296dd6e09924b1ddafcddfda7134408e1302e9466506358` |
| Key | RSA 4096 |
| SourceStamp cert SHA-256 | `3257d599a49d2c961a471ca9843f59d341a405884583fc087df4237b733bbd6d` (the Play Store stamp, same as in the camTranslator teardowns) |
| Stamp timestamp | 1788850518 (2026-09-08 UTC) |

The cert has the generic Google DN, so this is **Play App Signing**. The developer's upload key is not visible [inf]. No v1 `META-INF/*.RSA|SF|MF` entries exist [obs].

### Application bootstrap (`MyApplication`, 473 lines)

- [obs] `onCreate()` -> `d()` injects the Hilt graph, then `b()`. `b()` stores a WeakReference to the app, sets up two `ga2` preference stores (one regular, one device-protected), and launches coroutines on `hy2.c`. If the user is unlocked (`dn3.s(this)`) it runs the full init in `a()`. Otherwise it clears the cache dir and registers a receiver for `android.intent.action.USER_UNLOCKED`.
- [inf] This direct-boot / user-unlock split matches upstream `FlorisApplication.onCreate()`. The init pattern is inherited and was not written by this dev.
- [obs] `a()` clears the cache, starts the dictionary/extension managers (`mo2.c/d/e.m()`) and an analytics/events helper (`eventsHelper`, `n31`).

## 2. Entry points

Manifest totals [obs]: **6 activities, 14 services, 12 receivers, 4 providers**. There is no `<activity-alias>`.
First-party components (app plus FlorisBoard fork): 2 activities, 2 services, 0 receivers, 1 provider.

### 2.1 Activities

| Activity | exported | Intent filters | Mode / orientation / other | Theme | Notes |
|---|---|---|---|---|---|
| `com.example.voicetypingkeyboard_z.presentationLayer.MainActivity` | **true** | MAIN + LAUNCHER | portrait, `windowSoftInputMode=adjustNothing` | `Theme.VoiceTypingKeyboard_Z` | Single-activity Compose host (`ia1` = ComposeView, `setContent`). Hilt entry point. `attachBaseContext` applies the locale from the `pref_selected_language` pref (default "en"). `onCreate` resets `keyboardBannerPreviousTime=0` and calls `ws8.a0(this)` (FCM token/topic helper, also called from `FCMService.d()`). Overrides `onNewIntent` -> `setIntent()` only [obs] |
| `dev.patrickgold.florisboard.AudioPermissionActivity` | false | — | `launchMode=singleTask` | `@android:style/Theme.Translucent.NoTitleBar` | A trampoline the IME uses to request `RECORD_AUDIO`, because an IME cannot show runtime permission dialogs. When permanently denied it opens `APPLICATION_DETAILS_SETTINGS` (fallback `android.settings.SETTINGS`), then finishes (102 lines) [obs] |
| `com.google.android.libraries.ads.mobile.sdk.common.AdActivity` | false | — | full configChanges | `@style/AdCustomTheme` | Next-gen GMA SDK (`ads_mobile_sdk`) [obs] |
| `com.google.android.play.core.hsdp.service.HsdpShimActivity` | false | — | singleTop, excludeFromRecents | `Theme.HsdpServiceTransparent` | Play Core "hsdp" shim (in-app update / service dialogs) [obs] |
| `com.google.android.gms.common.api.GoogleApiActivity` | false | — | — | Translucent | GMS resolution [obs] |
| `com.google.android.play.core.common.PlayCoreDialogWrapperActivity` | false | — | — | — | Play In-App Update [obs] |

### 2.2 Services

| Service | exported | Permission / filter | Notes |
|---|---|---|---|
| **`dev.patrickgold.florisboard.FlorisImeService`** | **true** | `permission=BIND_INPUT_METHOD`; filter `android.view.InputMethod`; meta `android.view.im` -> `@xml/method`; `directBootAware=false` | The IME (see §3) [obs] |
| `com.example.voicetypingkeyboard_z.utils.fcm.FCMService` | false | `com.google.firebase.MESSAGING_EVENT` | Extends `FirebaseMessagingService`. `c()` (onMessageReceived) only logs "VoiceTyping Notification is Received" with tag `voiceTypingFCMCheck___`. `d()` (onNewToken) logs the token and calls `ws8.a0()`. No data-payload routing, so pushes rely on the SDK's automatic notification display [obs] |
| `com.google.firebase.messaging.FirebaseMessagingService` | false | MESSAGING_EVENT prio -500 | SDK default [obs] |
| `com.google.firebase.components.ComponentDiscoveryService` | false | — | Registrars: Perf, Messaging, Analytics connector, Remote Config, ABT, Crashlytics, Sessions, Installations, Common, Datatransport [obs] |
| `com.google.firebase.sessions.SessionLifecycleService` | false (disabled) | — | [obs] |
| `com.google.android.gms.measurement.AppMeasurementService` / `AppMeasurementJobService` | false | JobService: BIND_JOB_SERVICE | Analytics [obs] |
| `androidx.work.impl.background.systemalarm.SystemAlarmService` | false | — | WorkManager [obs] |
| `androidx.work.impl.background.systemjob.SystemJobService` | **true** | BIND_JOB_SERVICE | Standard WorkManager. Only the system can bind [obs] |
| `androidx.work.impl.foreground.SystemForegroundService` | false | — | Accounts for the `FOREGROUND_SERVICE` permission. No `foregroundServiceType` is declared [obs] |
| `androidx.camera.core.impl.MetadataHolderService` | false (disabled) | — | CameraX Camera2 config [obs] |
| `androidx.room.MultiInstanceInvalidationService` | false | — | Room [obs] |
| `com.google.android.datatransport.runtime.backends.TransportBackendDiscovery` / `JobInfoSchedulerService` | false | — | Firelog transport [obs] |

### 2.3 Receivers (all SDK)

| Receiver | exported | Notes |
|---|---|---|
| `com.google.firebase.iid.FirebaseInstanceIdReceiver` | **true** | `permission=com.google.android.c2dm.permission.SEND`, action `c2dm.intent.RECEIVE` [obs] |
| `androidx.work.impl.diagnostics.DiagnosticsReceiver` | **true** | `permission=android.permission.DUMP` [obs] |
| `androidx.profileinstaller.ProfileInstallReceiver` | **true** | `permission=android.permission.DUMP` [obs] |
| `com.google.android.gms.measurement.AppMeasurementReceiver` | false | [obs] |
| WorkManager: `ForceStopRunnable$BroadcastReceiver`, 4x `ConstraintProxy$*`, `RescheduleReceiver` (BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED), `ConstraintProxyUpdateReceiver` | false | Standard [obs] |
| `AlarmManagerSchedulerBroadcastReceiver` | false | datatransport [obs] |

Runtime-registered receivers [obs]:
- `FlorisImeService.onCreate()` registers `android.intent.action.WALLPAPER_CHANGED` (`FlorisImeService.java:393`), used for wallpaper-based theming.
- `MyApplication` registers `USER_UNLOCKED`.
- The custom `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature level) is the AndroidX `ContextCompat.registerReceiver(..., NOT_EXPORTED)` shim.

### 2.4 Providers

| Provider | Authority | exported | grantUriPermissions | Notes |
|---|---|---|---|---|
| `dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider` | `<pkg>.provider.clipboard` | false | **true** | Serves clipboard-history images/media to target apps through per-URI grants. Backed by `ClipboardFilesDatabase` [obs]. [inf] Used through `commitContent` / rich-content insertion. |
| `androidx.core.content.FileProvider` | `<pkg>.provider` | false | true | `@xml/file_paths`: `cache-path "."`, `files-path "."`, `external-cache-path "."`, `cache-path "backup-and-restore/"`. The roots are broad (the whole files and cache dirs), but access is only through explicit grants [obs]. [inf] Used for sharing stickers/images and FlorisBoard backup export. |
| `androidx.startup.InitializationProvider` | `<pkg>.androidx-startup` | false | — | Initializers: WorkManager, ProcessLifecycle, ProfileInstaller [obs] |
| `com.google.firebase.provider.FirebaseInitProvider` | `<pkg>.firebaseinitprovider` | false | — | initOrder 100, directBootAware [obs] |

### 2.5 Exported attack surface summary

Only 6 components are exported [obs]:
- The launcher activity.
- The IME (system-only via BIND_INPUT_METHOD).
- FirebaseInstanceIdReceiver (c2dm SEND).
- SystemJobService (BIND_JOB_SERVICE).
- Two DUMP-guarded receivers.

No component accepts external data from arbitrary apps [obs]. [dec] The clone can match this minimal surface.

## 3. IME service & `method.xml`

### 3.1 `res/xml/method.xml` [obs]

```xml
<input-method android:settingsActivity="dev.patrickgold.florisboard.SettingsLauncherAlias"
    android:supportsSwitchingToNextInputMethod="true"
    android:supportsInlineSuggestions="true">
    <subtype android:label="@string/app_name" android:imeSubtypeMode="keyboard"
        android:imeSubtypeExtraValue="AsciiCapable"
        android:overridesImplicitlyEnabledSubtype="true" android:isAsciiCapable="true"/>
</input-method>
```

- **Single system-visible subtype** with no `imeSubtypeLocale` and the label set to the app name [obs]. Language switching happens *inside* the keyboard through FlorisBoard's own subtype manager (`select_subtype_panel__header`, quick action `language_switch`), not through Android IME subtypes [obs].
- **Dangling settingsActivity** [obs]: `dev.patrickgold.florisboard.SettingsLauncherAlias` does not exist in the manifest (no `<activity-alias>`, and `grep -r SettingsLauncherAlias jadx_out/sources` returns nothing). Upstream FlorisBoard declares an activity-alias with this name. The fork removed the alias but kept the reference.
  - [inf] Tapping the gear icon in system "On-screen keyboard" settings will fail or do nothing.
  - [dec] The clone should point `settingsActivity` at a real activity.
- `supportsInlineSuggestions=true`. `FlorisImeService` implements `onCreateInlineSuggestionsRequest` (line 421) and `onInlineSuggestionsResponse` (line 493), which shows autofill chips in the smartbar (`snygg__rule_element__inline_autofill_chip`) [obs].

### 3.2 `FlorisImeService` (857 lines) [obs]

- `extends InputMethodService`. Overrides: `onCreate`, `onCreateInputView` (Compose), `onCreateCandidatesView`, `onCreateExtractTextView`, `onComputeInsets`, `onEvaluateFullscreenMode`, `onEvaluateInputViewShown`, `onStartInput`, `onStartInputView`, `onFinishInput(View)`, `onUpdateSelection`, `onUpdateExtractingVisibility`, `onWindowShown/Hidden`, `onKeyDown/Up`, `onConfigurationChanged`, `getTextForImeAction`.
- **Voice typing**: `r()` at lines 735-760:
  - Checks `SpeechRecognizer.isRecognitionAvailable`, then `createSpeechRecognizer(this)` with the `RECOGNIZE_SPEECH` intent.
  - Extras: `LANGUAGE_MODEL=free_form`, `LANGUAGE=<active subtype locale tag>`, `PARTIAL_RESULTS=true`.
  - `s()` stops and destroys the recognizer.
  - Permission is checked in `jy2.java:34` via `checkSelfPermission(RECORD_AUDIO)`. If it is missing, the IME opens `AudioPermissionActivity` [obs].
  - [inf] Offline vs. online recognition depends on the device's recognition service (usually Google).
- **In-keyboard ad gate** in `onStartInputView`, lines 576-625:
  - Runs only when `UserManager.isUserUnlocked()`.
  - Reads `editorInfo.packageName` and **suppresses the keyboard banner/native ad** when the host app is one of 86 hard-coded packages (verified count; plus the app's own package): this app itself, most Google apps (Search, Gmail, Chrome, YouTube, Play Store, Maps, Messages, Docs, Translate, Gboard, Assistant, Contacts, Dialer, Calendar, Keep, permission controller, …) and ~35 OEM or third-party launchers.
  - Otherwise the ad shows only if `my_keyboard_native_ad_enable && !is_user_premium && PREF_CAN_USER_REQUEST_ADS` (the last flag comes from UMP consent).
  - The result goes into the static flow `FlorisImeService.x`, with log tag `showAdState__` [obs].
  - [inf] Excluding Google-owned surfaces looks designed to keep the in-IME ads away from Google review and Play policy enforcement. This is a policy-risk signal. [dec] The clone must NOT ship ads inside the IME window.
  - `is_user_premium` is read even though no billing library exists (see the Monetization report). [inf] It is a dead flag left over from a template.
- **Wallpaper theming**: `WALLPAPER_CHANGED` receiver (line 393) [obs].

### 3.3 Keyboard languages, layouts and assets (FlorisBoard extensions) [obs]

| Asset | Content |
|---|---|
| `base/assets/ime/keyboard/org.florisboard.localization/extension.json` | **71 `subtypePresets`**, e.g. en-US/UK/CA/AU, de-DE/AT/CH (+neobone), fr-FR/CA/CH, it, es-ES/US/419, pt-PT/BR, nordics, id, fa (3), ar, az, hu, eo, hr, hy (3), ru, rue, uk, el, ro, pl, bg, tr, iw-IL, ckb, sr, sl, lv, ku, ca, IPA, sk, cs, ko (2), lt, ja-JP-jis, th-kd, vi-VN, ig-NG, hoc, udm, ur-PK, zh-CN-zhengma, bn-BD, hi-IN, ast, ta-lk, kab |
| `.../org.florisboard.localization/popupMappings/` | 57 locale popup maps (ar … vi-VN) |
| `.../org.florisboard.layouts/layouts/` | 156 layout JSONs across `characters`, `charactersMod`, `numeric`, `numericAdvanced`, `numericRow`, `phone`, `phone2`, `symbols`, `symbols2`, `symbols2Mod`, `symbolsMod` |
| `.../org.florisboard.composers`, `org.florisboard.currencysets` | Upstream composers (e.g. Hangul, Kana) and currency sets |
| `base/assets/ime/languagepack/` | `org.florisboard.languagepack`, `org.florisboard.hanshapebasedbasicpack` |
| `base/assets/ime/dict/` | **Fork addition**: plain-text suggestion lists for `english_data`, `spanish`, `portuguese`, `french`, `indonesian`, `arabic`, `urdu`. Only 7 languages get word suggestions [obs]. [inf] These replace upstream's (minimal) NLP. |
| `base/assets/ime/media/emoji/root.txt`, `media/emoticon` | Upstream emoji/emoticon data |
| `base/assets/ime/theme/org.florisboard.themes` | Upstream Snygg stylesheets |

[dec] All of the above is upstream Apache-2.0 material, except the `dict/*.txt` lists. The clone should take these from upstream FlorisBoard directly, keep the NOTICE/attribution, and source its own word-frequency lists (e.g. from openly licensed corpora). It should not take this APK's `dict/` files.

## 4. Permission catalog

| Permission | Category | Protection | Source / use | Tag |
|---|---|---|---|---|
| `RECORD_AUDIO` | Microphone | **dangerous** | Voice typing in the IME (via `AudioPermissionActivity`), plus voice translator/conversation in the app (`le0.java:255`, `di1.java:51`, `r60.java:166`, `ki1.java:832`) | [obs] |
| `CAMERA` (+ `uses-feature android.hardware.camera required=false`) | Camera | **dangerous** | Camera Translator (CameraX + GMS Vision OCR). Checked in `en0.java:187`, `r60.java:491`, `fqa.java:451`; rationale in `le0.java:178` | [obs] |
| `POST_NOTIFICATIONS` | Notifications | **dangerous** (API 33+) | FCM pushes. Requested in `pr0.java:307-309`, with a rationale string "Enable notifications to never miss important updates" (`mh3.java:79`) | [obs] |
| `READ_BASIC_PHONE_STATE` | Phone | normal (API 33+) | Merged from the GMA/measurement SDK (`jha.java:1792` checks READ_PHONE_STATE or READ_BASIC_PHONE_STATE). It is not used first-party | [obs] / [inf] |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Network | normal | Translate, dictionary, stickers, ads, Firebase | [obs] |
| `VIBRATE` | Haptics | normal | Key-press haptics (FlorisBoard) | [obs] |
| `WAKE_LOCK`, `FOREGROUND_SERVICE` | System | normal | WorkManager / Firebase | [obs] |
| `com.google.android.gms.permission.AD_ID` | Ads | normal | AdMob | [obs] |
| `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_AD_ID` | Ads (Privacy Sandbox) | normal | GMA / measurement | [obs] |
| `com.google.android.c2dm.permission.RECEIVE` | Push | normal | FCM | [obs] |
| `com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE` | Attribution | normal | Install Referrer (via measurement) | [obs] |
| `<pkg>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Internal | signature | AndroidX | [obs] |

Permissions that are **not** requested [obs]:
- `SYSTEM_ALERT_WINDOW` (no overlay).
- `BIND_ACCESSIBILITY_SERVICE`.
- Storage or media read (images come from the Photo Picker or gallery intent).
- Contacts, location.
- `QUERY_ALL_PACKAGES`.
- `REQUEST_INSTALL_PACKAGES`.
- billing (`com.android.vending.BILLING`).

[dec] The clone needs only RECORD_AUDIO, CAMERA (if it has a camera translator), POST_NOTIFICATIONS (optional), INTERNET and VIBRATE as first-party permissions.

### `<queries>` (package visibility) [obs]

| Query | Likely owner |
|---|---|
| `android.view.InputMethod` intent | FlorisBoard: checks whether its IME is enabled or selected |
| `android.service.textservice.SpellCheckerService` | FlorisBoard spell-check settings |
| `VIEW` BROWSABLE `https`, `CustomTabsService` | Opening the privacy URL / ad clicks |
| `INSERT vnd.android.cursor.dir/event`, `VIEW sms:`, `DIAL tel:` | GMA SDK MRAID (`mraid_allow_calendar_event` strings) [inf] |
| package `com.android.vending` | In-app update / rate / share |

## 5. Deep links, PROCESS_TEXT, SEND

- **Deep links: none** [obs]. No `VIEW` intent filter with a scheme or host, no App Links, no `autoVerify`.
- **No inbound `PROCESS_TEXT` or `SEND` filters** [obs]. The app cannot be a share target and does not appear in the text-selection "Translate" menu.
  - The `PROCESS_TEXT` code in `fb8.java:73-91`, `g91.java:64` and `nx5.java:69` is the AndroidX/Compose text-toolbar helper. It queries *other* apps' PROCESS_TEXT activities; it is not an entry point.
  - [dec] Adding an inbound `PROCESS_TEXT` "Translate with <clone>" activity would be a cheap differentiator.
- **Outbound intents** [obs]:
  - `SEND text/plain` "Share translation" (`ag7.java:32-35`).
  - Share App with a Play URL (`ct0.java:75-79`, `f32.java:34-37`).
  - Rate via `market://details?id=` (`f32.java:56`).
  - Feedback via `mailto:` (`ct0.java:84`).
  - `android.settings.INPUT_METHOD_SETTINGS` (`ct0.java:63`).
  - `InputMethodManager.showInputMethodPicker()` (`ct0.java:57`, `se4.java:841`, `uh.java:244/268`).
- **Notification deep links**: none in code. FCMService ignores payload data [obs]. [inf] Taps open the launcher activity by default.

## 6. Widgets, shortcuts, tiles, other surfaces

None [obs]:
- No `appwidget-provider` in `res/xml/` (the files are `file_paths`, `method`, `remote_config_defaults`, `splits0`, plus 4 Material3 shape-state XMLs).
- No `<receiver>` with `APPWIDGET_UPDATE`.
- No `shortcuts.xml`, no QS `TileService`, no `SpellCheckerService` declared, no Autofill service.

## 7. Backup, data, misc manifest flags

| Flag | Value | Implication | Tag |
|---|---|---|---|
| `allowBackup` | true | No `fullBackupContent` or `dataExtractionRules`, so auto-backup includes everything. [inf] That covers the Room DBs (`ClipboardHistoryDatabase`, `ClipboardFilesDatabase`, `FlorisUserDictionaryDatabase`, `AppDatabase`), so **clipboard history may be backed up to Google Drive**. [dec] The clone should exclude clipboard and history DBs from backup | [obs] / [inf] |
| `largeHeap` | true | Image/wallpaper themes and stickers | [obs] |
| `hardwareAccelerated` | true | — | [obs] |
| `supportsRtl` | true | Arabic/Urdu/Farsi supported | [obs] |
| `extractNativeLibs` | false | Libraries are loaded uncompressed from the APK | [obs] |
| `networkSecurityConfig` / `usesCleartextTraffic` | absent | Default for target 37: cleartext is blocked and only system CAs are trusted | [obs] |
| Ads meta | `OPTIMIZE_INITIALIZATION=true`, `OPTIMIZE_AD_LOADING=true`, `APPLICATION_ID=@string/admob_app_id` (`ca-app-pub-3051…~…1938`) | — | [obs] |

## 8. Screen inventory (Compose navigation)

The app is a single-activity Compose app. Of 38 `res/layout` XMLs, only the ad layouts are first-party (`native_*_admob`/`*_shimmer`, `banner_ad_shimmer_layout`, `layout_ad_container`, `layout_ad_loading`, `native_banner_small_compose_*`) [obs].

Navigation uses type-safe `@Serializable` route classes that implement `vk5` [obs]. The route names come from their `toString()`:

| # | Route class (file) | Args | Screen (from strings and models) | Tag |
|---|---|---|---|---|
| 1 | `SplashMainScreenRoute` (`p65`) | — | Splash. Interstitial `splash` + app-open ad; "App update is required" in-app-update gate; UMP consent | [obs] / [inf] |
| 2 | `AppLanguageScreenRoute` (`b65`) | — | App UI language picker (`app_language`, `AppLanguage`). 30 localized `values-*` (ar, bn, cs, de, el, es, fa, fil, fr, hi, in, it, ja, ko, mr, ms, nl, pl, pt, ro, ru, sv, ta, te, th, tr, uk, ur, vi, zh) | [obs] |
| 3 | `KeyboardEnableScreenRoute` (`j65`) | — | 2-step onboarding ("Step 1 Of 2 / 2 Of 2", "Enable Keyboard", "Select Keyboard") -> `INPUT_METHOD_SETTINGS` and the IME picker. Banner + native `enable` placements | [obs] |
| 4 | `BottomNavHomeScreenRoute` (`c65`) | — | Home with bottom nav. Cards for Voice Keyboard, Text Translator, Camera Translator, Conversation, Dictionary, Guess-Word game, Stickers, Themes. Interstitials `home_*` | [obs] / [inf] |
| 5 | `TranslationScreenRoute` (`r65`) | — | Text translator (10000-char limit, Translate button interstitial, share translation). [inf] The voice-conversation UI (`ConversationMessage`, "Delete Conversation History?") is probably a mode of this screen or of Home | [obs] / [inf] |
| 6 | `LanguageMainScreenRoute` (`m65`) | — | Translation source/target language selector (`TranslationLanguageModel`, "All Languages", "No Languages Found") | [inf] |
| 7 | `CameraScreenRoute` (`d65`) | — | Camera Translator: CameraX capture or gallery, "Please capture clear image", "Retake", back/front camera fallback | [obs] |
| 8 | `DictionaryScreenRoute` (`h65`) | — | Dictionary: definitions, examples, synonyms/antonyms, pronunciation ("Finding the meaning…") | [obs] |
| 9 | `GuessWordGameScreenRoute` (`i65`) | — | "Find the Hidden Word" levels, hints, Next/Previous level. Native `guess_word` placement | [obs] |
| 10 | `StickerScreenRoute` (`q65`) | — | Sticker catalog (remote JSON), "All Stickers", "Add Stickers" | [obs] |
| 11 | `AddStickerScreenRoute(assetModel)` (`x55`) | assetModel | Sticker pack detail and add ("Adding(%d%%)", "All Sticker Added!") | [obs] |
| 12 | `KeyBoardThemeScreenRoute` (`id0`) | — | Theme gallery: Colors / Gradient / Wallpaper (Backgrounds) / Custom tabs (`KeyboardTheme`, `KeyBoardThemeModel`) | [obs] |
| 13 | `CustomizeKeyBoardScreenRoute(themeId, categoryName)` (`g65`) | themeId, categoryName | Theme editor: live preview, key border/corner/radius/opacity, key/popup styles, key sounds (`SoundItemModel`: Air, Bubble, Piano, Pop, Snap, …), color picker. Interstitials `save_theme`, `leave_theme` ("Leave Without Saving") | [obs] |
| 14 | `AppKeyboardCustomThemeCropViewRoute(imageUri)` (`a65`) | imageUri | Crop a picked photo into a keyboard background ("Crop Image") | [obs] |
| 15 | `SettingMainScreenRoute` (`o65`) | — | Settings: Keys & Input, Layout & Size (keyboard height, one-hand mode, number row), Auto suggestions, Auto correction, Glide typing/trail, Smart Bar, Clipboard, Keyboard Sound, Privacy Policy, Rate Us, Share App, Feedback. Native `settings` | [obs] |
| 16 | `KeyboardLanguageScreenRoute` (`k65`) | — | Add or remove keyboard languages ("Add Language to Keyboard?", "Current Languages", "Available Languages") | [obs] |
| 17 | `KeyboardTranslationBarLanguageScreenRoute` (`l65`) | — | Language picker for the **in-keyboard translation bar** (type, then translate inside the IME). [inf] The bar calls the same Google `translate_a/single` endpoint | [obs] / [inf] |

Screens inside the IME (Compose inside `FlorisImeService`, upstream FlorisBoard plus fork additions) [obs]:
- Main keyboard.
- Smartbar with quick actions.
- Clipboard history.
- Emoji/emoticon panel.
- One-handed panel.
- Subtype selector.
- Voice-input state.
- Fork additions: the translation bar, a sticker panel (`ime/sticker/AvailableStickersModel`), and the native ad slot (`KeyboardMainNativeAdModel`).

## 9. Clone takeaways [dec]

1. Base the clone on **upstream FlorisBoard** (Apache-2.0). Keep `LICENSE`/NOTICE and attribution, rename the package, and write our own app shell, home, translator, camera, dictionary, game and theme UX. Do not copy this fork's code or `dict/` lists.
2. Declare a real `settingsActivity` in `method.xml` (this app's is broken). Consider per-locale `<subtype>` entries so languages show in the system picker.
3. Keep the exported surface minimal: launcher + IME. Add a `PROCESS_TEXT` translate activity and a `SEND text/plain` target, which this app lacks.
4. No ads inside the IME window. Add `dataExtractionRules` that exclude clipboard and user-dictionary DBs from backup.
5. Voice typing can stay on the platform `SpeechRecognizer` (zero cost). For quality and offline use, consider an on-device engine as a differentiator.

## 10. Open items

| # | Item | Owner |
|---|---|---|
| M1 | Confirm on a device that the IME settings gear (dangling `SettingsLauncherAlias`) crashes or no-ops [inf] | Native/UX |
| M2 | Confirm the in-IME ad suppression list works at runtime, and how the native ad renders in eligible host apps (with `PREF_CAN_USER_REQUEST_ADS` after consent) | Monetization |
| M3 | Map which bottom-nav tabs exist in `BottomNavHomeScreenRoute` and where the Voice Conversation UI lives (route not named) [inf] | Native/UX |
| M4 | Diff the fork against upstream FlorisBoard (matching upstream tag) to isolate added classes, assets and prefs. This is triage open item 5 | Manifest (this team), with Native/UX |
| M5 | Verify what auto-backup actually contains (`adb shell bmgr backupnow` in a dynamic pass) and whether clipboard DBs are included [inf] | Anti-tamper / Backend |
| M6 | Confirm FCM push payloads and tap behavior (no data handling in `FCMService`) [inf] | Backend |
