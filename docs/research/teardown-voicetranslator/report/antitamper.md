# Team 5: Anti-tamper and Security. Voice Keyboard and Translator v1.4

Static-only pass. No device, no mitmproxy, no Frida. Tags: `[obs]` means seen in decompiled code, resources or the manifest. `[dec]` means a decision or recommendation. `[inf]` means inferred and not verified. Every dynamic claim is `[inf]` and is tracked as a numbered open item in section 10.

- Package: `com.heri.language.keyboard.voice.typing`, versionCode 5, sha256 `6cb257d0…c1f4`.
- Stack: Kotlin, Compose, and a FlorisBoard fork, shrunk with R8.
- Paths are relative to `voicetranslator_work/jadx_out/sources/` unless they say otherwise.
- Sibling for contrast (read-only): `voicetyping_work` ("Voice Keyboard - Voice Typing" 1.3).

Secrets note: values are redacted to the first 8 and last 4 characters. No vendor API was called with any value extracted here.

---

## 0. Executive summary

1. **Anti-tamper is PairIP licensing only** [obs]:
   - There is no `libpairipcore.so`, no `VMRunner`/`executeVM`, no `SignatureCheck`, and no VM asset blobs.
   - The whole app is ordinary R8 DEX and nothing is virtualized.
   - The runbook line "PairIP: PRESENT" is a tier false-alarm. It does not block Frida or repackaging.
2. **No RASP in first-party code** [obs]. Every root, debugger and installer check found belongs to an SDK: Crashlytics, GMS, Firebase or PairIP.
3. **No network security config and no certificate pinning** [obs]. A mitm CA in the system store will see all traffic. Cleartext is not explicitly enabled; with targetSdk 36 it defaults to off [inf].
4. **One secret: the standard Firebase Web API key** (`AIzaSyCd…lqfo`). Abuse risk is low to medium and depends on how the key is restricted in the Firebase console (open item #5).
5. **The main privacy issue for an IME** [obs]: when the keyboard's translation bar is on (`keyboard.translationBar`), keystrokes and pasted clipboard text go into a buffer. Each change re-triggers a request to Google's **unofficial, unauthenticated** gtx endpoint, with the full buffer in the URL query string.
   - The same URL, plaintext included, is also written to logcat (`tf/b.java:1678`).
   - The request is wrapped by Firebase Performance (`FirebasePerfUrlConnection.instrument`).
6. **Voice audio** goes to the system `SpeechRecognizer`, usually Google's recognition service [inf]. `EXTRA_PREFER_OFFLINE` is not set, so cloud recognition is allowed [obs]. The app does not record raw audio itself [obs].
7. **No first-party backend.** Data goes only to Google: translate.googleapis.com, Firebase (Analytics, Crashlytics, Performance, Sessions, Remote Config, FCM, Installations) and AdMob. The one third-party host is api.dictionaryapi.dev, which receives words from the in-app dictionary screen [obs].
8. **Leaks and hygiene** [obs]:
   - `allowBackup="true"` with no backup rules, so translation history, clipboard history and the user dictionary are eligible for Auto Backup [inf].
   - The FCM token and every host app's package name are logged with `Log.d` in the release build.
   - `FileProvider` exposes `external-path "."`. It is not exported, so this is a low risk.

---

## 1. Anti-tamper inventory

| # | Mechanism | Present? | Evidence | Strength | Bypass effort |
|---|---|---|---|---|---|
| 1 | PairIP Play-licensing check | Yes | `com/pairip/application/Application.java`: `attachBaseContext` calls `LicenseClient.checkLicense(context)` and then `super` (`VoiceTypingKeyboard`) [obs] | Low. It is a Play licence gate only. | Low. Patch `checkLicense`, or replace `exitAction`. |
| 2 | PairIP VM encryption (`libpairipcore.so`) | **No** | `base/lib/arm64-v8a/` holds only `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`, `libimage_processing_util_jni.so` and `libsurface_util_jni.so`. `find -name 'libpairip*'` returns nothing. `com/pairip/` contains only `application/` and `licensecheck/` (8 files) [obs] | n/a | n/a |
| 3 | PairIP AppComponentFactory | **No** | `appComponentFactory="androidx.core.app.CoreComponentFactory"` (manifest l.83) [obs] | n/a | n/a |
| 4 | Signature / integrity self-check | **No (first-party)** | no `SignatureCheck` or `verifyIntegrity` [obs]. `pe/h.java:70` compares a Base64 cert digest and dev-keys/test-keys; it looks like GMS's Google-signature verifier, not app logic [inf] | n/a | n/a |
| 5 | Root / su detection | **SDK only** | `gg/f.java:97-98` (`/system/app/Superuser.apk`, `/system/xbin/su`, test-keys) is Crashlytics `CommonUtils`, used for crash-report metadata (`gg/*` contains Crashlytics strings) [obs/inf] | none against the user | none |
| 6 | Debugger detection | **SDK only** | `gg/f.java:52` `Debug.isDebuggerConnected()` sets a Crashlytics device-state flag [obs] | none | none |
| 7 | Installer check | PairIP and SDK | `LicenseClient.java:191` `getInstallSourceInfo`; `gg/w.java:139` and `kf/b.java:186` (Firebase/Crashlytics) [obs] | Low | Low |
| 8 | Frida / Xposed / emulator detection | **No** | grep for `frida`, `XposedBridge` and `ranchu` in the first-party and `dev/` trees finds nothing [obs] | n/a | n/a |
| 9 | `android:debuggable` | Off | attribute absent (release default) [obs] | — | — |
| 10 | Certificate pinning | **No** | no `CertificatePinner` or `.pin(` outside OkHttp internals. The only `"sha256/"` hit is in Glide/OkHttp library code (`com/bumptech/glide/e.java:465`), not a configured pinner [obs] | none | trivial |
| 11 | Network Security Config | **Absent** | no `networkSecurityConfig` attribute. `res/xml/` holds only `file_paths`, `method`, `remote_config_defaults`, `splits0` and M3 shape files [obs] | none | trivial |
| 12 | String encryption / DexGuard | **No** | URLs, ad keys, log tags and remote-config keys are all plaintext in DEX [obs] | none | n/a |

**Bottom line** [inf]: there is no meaningful client-side protection. The only live control is the Play licence check, which runs in-process and can be patched locally.

---

## 2. PairIP tier: licensing-only, with an honest coverage line

**What is present** [obs]:
- `com.pairip.application.Application extends VoiceTypingKeyboard`. Its one override calls `LicenseClient.checkLicense()` before `super.attachBaseContext`.
- `LicenseClient` binds `com.android.vending.licensing.ILicensingService` / `IBackgroundLicensingService` (`SERVICE_PACKAGE = "com.android.vending"`) and uses `TRANSACTION_CHECK_LICENSE_V2`.
- Retry and timing constants:
  - `MAX_RETRIES = 3`
  - `RETRY_DELAY_MILLIS = 1000`
  - `REPEATED_CHECK_RETRY_DELAY_MILLIS = 300000`
  - `EVENTUAL_SHUTDOWN_DELAY_MILLIS = 30000`
- On `NOT_LICENSED` it either starts `LicenseActivity` (`ActivityType.PAYWALL` with a Play `PAYWALL_INTENT`, or `ERROR_DIALOG`), or runs `exitAction`, which is `System.exit(0)` (`LicenseClient.java:57-60`).
- It skips the check in isolated processes (`LicenseClient.java:105`).
- `packageName` is hard-coded to `com.heri.language.keyboard.voice.typing` (l.53). `LicenseContentProvider` is the auto-init hook.

**IME-specific consequence** [inf]: the IME service runs in the same process as the Application, so the licence check also runs when the system binds the keyboard. A sideloaded or re-signed copy that fails licensing can call `System.exit(0)` 30 s after start, and the keyboard would disappear mid-typing. This matters for any Wave-2 sideload test (open item #6).

**What is absent** [obs]: native VM, integrity check, encrypted asset blobs, PairIP component factory. `base/assets/` holds only `dexopt/`, `ime/`, `license/`, `phrases_final.db` and `PublicSuffixDatabase.list`.

**Contrast with the sibling** [obs]: `voicetyping_work` has **no PairIP at all**. There is no `com/pairip` directory and the Application is `com.example.voicetypingkeyboard_z.diLayer.MyApplication`. Both keyboards come from the same "voicetypingkeyboard_{s,z}" template family [inf]. Only this one turned on Play's automatic licence protection, and neither paid for VM encryption.

**Decompile coverage** [obs]:
- 14821 classes, 226 decompile errors, 108 files in `jadx-failed-classes.txt`, 22 of them in `ads_mobile_sdk/*`.
- Failures near first-party code:
  - `mk/e.java`, a sibling of the dictionary Retrofit client `mk/c`. It is low impact; the endpoint and models are readable.
  - **`androidx/lifecycle/o0.java`**. Despite the package name, R8 merged app coroutine lambdas into this class. Its `invokeSuspend` body did not decompile.
- `ck/a.g()` launches `new androidx.lifecycle.o0(buffer, null, 10)` for live keyboard translation. The **debounce delay and the dispatch into `ci/s` case 1 → `tf.b.n(...)` are therefore unconfirmed** [inf] (open item #7).
- Everything else that matters for security decompiled cleanly: the network calls, licence client, manifest, speech intent and analytics wrapper.

**Honest coverage line:** about 95%+ of the security surface is readable. Nothing is hidden behind a VM. The one material gap is the live-translate debounce body in `androidx/lifecycle/o0`, which smali or a Frida hook on `tf.b.n` would settle.

---

## 3. Network security posture

| Control | State | Evidence | Assessment |
|---|---|---|---|
| NSC | absent | manifest has no attribute [obs] | Only system CAs are trusted (API 24+ default). A user CA is ignored, so Wave 2 needs the CA in the system store [inf]. |
| Cleartext | not declared | no `usesCleartextTraffic` [obs] | Defaults to blocked at targetSdk 36 [inf]. All known endpoints are HTTPS [obs]. |
| Pinning | none | section 1 #10 [obs] | mitm works once the CA is in the system store. |
| gtx client | `HttpURLConnection`, `User-Agent: Mozilla/5.0` spoofed | `tf/b.java:1675-1684` [obs] | Mimics a browser to reach a non-API endpoint. This is a ToS risk and has no SLA [dec]. |
| Retrofit/OkHttp | 60 s connect/read/write timeouts | `oa/d.java` case 9 [obs] | No interceptors or auth headers. Only used for dictionaryapi.dev [obs]. |
| Firebase Performance | network instrumentation on | `FirebasePerfUrlConnection.instrument(...)` in `tf/b.java:1679` [obs] | Perf reports host, path, latency and size. Query strings are normally stripped [inf] (open item #8). |

**Attestation chain:** there is none. No Play Integrity, SafetyNet or App Check classes are used by first-party code. No backend exists that could enforce attestation [obs].

---

## 4. Secrets inventory and abuse assessment

| Item | Redacted value | Location | Kind | Abuse assessment |
|---|---|---|---|---|
| `google_api_key` | `AIzaSyCd…lqfo` | `res/values/strings.xml:144` | Firebase auto-generated Web/Android key [inf] | Public by design. If unrestricted in GCP it could be used against enabled Google APIs on project `voice-ty…rd-s` and billed to it. No first-party code uses it directly [obs]. Risk is low to medium; check restrictions (open item #5). |
| `google_app_id` | `1:988426…0c9e` | strings.xml:145 | Firebase app id | Public identifier. |
| `gcm_defaultSenderId` | `98842667…2571` | strings.xml:138 | FCM sender | Public. The FCM server key is not in the APK [obs]. |
| `google_storage_bucket` | `voice-ty….app` | strings.xml:147 | Firebase Storage bucket | No Storage SDK is used [obs]. Only exposed if the bucket rules are open [inf]. |
| `admob_app_id` | `ca-app-p…0830` | strings.xml:21 | AdMob app id | Public. |
| Ad unit IDs (4) | `ca-app-pub-873048…9004` ×4, `…8142` ×5, `…7927` ×13, `…0666` ×1 | `res/xml/remote_config_defaults.xml` | AdMob units | Public. Can be harvested for invalid-traffic attacks against the publisher, but this is not a secret [inf]. |
| `secrets-candidates.txt` "password" hits | resource IDs `0x7f0700c2…` | dimen/drawable IDs [obs] | false positives | none |

There is no OpenAI, Gemini, Azure, DeepL or Google Cloud Translation key, because the app uses keyless gtx [obs]. The sibling app also ships exactly one Firebase key (`AIzaSyBL…70kE`) [obs].

**Abuse of the gtx endpoint itself** [dec]: the endpoint takes no key, so there is nothing to steal. The risk falls on the operator: Google can rate-limit or block `client=gtx`, which breaks the main feature for every user at once. The clone must not depend on it.

---

## 5. Obfuscation assessment

- **R8** with full renaming (`r8-map-id-53b39b15…`) and aggressive class merging. App lambdas ended up in `androidx/lifecycle/o0`, and Crashlytics and Mobile Vision code share `n0/v0` [obs]. Kept names: `com/example/voicetypingkeyboard_s/*` (22) and `dev/patrickgold/florisboard/*` (16, the manifest-referenced FlorisBoard entry points) [obs].
- **No string encryption, no reflection obfuscation, no native code from the app** [obs]. Log tags (`urlCheck___`, `showAdState__`, `AdsInter_`, `SplashDebug`, `mAds_`, `firebaseEvent___`) act as free anchors for hooks.
- **`Log.d` was not stripped** (no `-assumenosideeffects` rule) [obs]. This is both a leak (section 6) and a gift for reversing.
- Note on the triage brief: it lists `n0/v0` as OCR. It is an R8 merge of Crashlytics settings code and `gms.vision.text.Text` implementations (`n0/v0.java:27,459,1060`) [obs]. Mobile Vision OCR is still likely [inf].

---

## 6. Privacy: what leaves the device (the critical section for an IME)

### 6.1 Data-flow table

| Data | Leaves device? | Destination | Trigger | Evidence |
|---|---|---|---|---|
| **Typed keystrokes (translation-bar mode)** | **Yes**, as a cleartext query string over TLS | `translate.googleapis.com/translate_a/single?client=gtx&sl=…&tl=…&q=<buffer>` | User turns on the in-keyboard translation bar (pref `keyboard.translationBar`, `getKeyboard().F`). Then every character (`bk/t.java:1646,1658` → `ck.a.d`), backspace (`bk/s.java:89,114` → `ck.a.c`) and paste (`ij/r0.java:1061`, `o0/e.java:582` → `ck.a.e`) updates the buffer, and `ck.a.g()` cancels the previous job and launches a new one [obs]. The result is committed back over the original text (`ck/a.java:48-63`, `deleteSurroundingText` + `commitText`) [obs]. | `ck/a.java`, `ci/s.java:138`, `tf/b.java:1673-1700` |
| Typed keystrokes (normal mode) | **No** [obs/inf] | — | Keystroke routing to `ck.a` is gated on `translationBar` (`bk/t.java` ~l.1608; `ij/r0.java:1056`). Suggestions use the bundled `assets/ime/dict/*.txt` locally. No other network sink was found for key events [obs]. | — |
| **Pasted clipboard text** | Yes, only when the translation bar is on and the user pastes | gtx | paste key case `-33` → `ck.a.e(primaryClipText)` [obs] | `ij/r0.java:1056-1062` |
| Clipboard history | No network [obs]. Stored locally in `ClipboardHistoryDatabase`/`clipboard_files`. Floris auto-cleans sensitive items (`clipboard.historyAutoCleanSensitive*`) [obs] | local, Auto Backup [inf] | system clipboard read in `ab/l.java:245` (Floris) | `FlorisPreferenceModelImpl.java:27` |
| **Voice audio** | Yes, through the platform recognizer, not the app [inf] | System `SpeechRecognizer` provider, usually Google [inf] | Mic key → `wi/k.java:250` `createSpeechRecognizer`. Extras: `LANGUAGE_MODEL=free_form`, `PARTIAL_RESULTS=true`, 2000 ms silence, `calling_package` [obs]. There is no `PREFER_OFFLINE` and no `createOnDeviceSpeechRecognizer` [obs]. | `wi/k.java:252-257` |
| Recognized speech text | Yes, when the user then translates it (app screens or translation bar) [inf] | gtx | open item #4 | — |
| In-app text, phrasebook, camera-OCR translations | Yes | gtx (callers `cb/k.java:275`, `vb/k.java:91`) [obs] | user action | — |
| Dictionary lookups | Yes, the looked-up word | `api.dictionaryapi.dev` (third party, no auth) | in-app dictionary screen [obs] | `mk/c.java:101` |
| Camera frames | No upload found. OCR runs on device through `gms.vision` [inf] | — | camera translation | open item #3 |
| Analytics events | Yes | Firebase Analytics | `tb/a.a(id,name,type)` sends **constant** strings for ad and lifecycle events (`app_open_*`, `splash_*`, `ShowGDPR`) [obs]. No typed content was seen in params [obs]. | `tb/a.java`, `sb/c.java` |
| Crash reports and device state | Yes | Crashlytics (root and debugger flags in metadata) | crash [obs] | `gg/f.java` |
| Network performance traces | Yes | Firebase Performance | every gtx call [obs] | `tf/b.java:1679` |
| Host-app package name | Not sent [obs]. Logged locally and used to suppress ads | logcat | `onStartInputView` | `FlorisImeService.java:681-686` |
| Ad requests | Yes (AD_ID, device signals, consent) | AdMob next-gen SDK, including the **in-keyboard native ad** | keyboard opens in a non-allowlisted host app [obs] | `wi/v.java:76-88`, `sb/c.java` |
| FCM token | Sent to Firebase. **Also logged** | logcat | `FCMHelper.java:36` [obs] | — |

### 6.2 Specific findings

1. **Keystrokes in the URL** [obs]: the translation buffer is URL-encoded into the GET query string (`q=`), not sent as a POST body. Query strings end up in server access logs and can be captured by proxies and debugging tools. The data controller is Google's consumer-translate front end, under no contract with this developer [dec].
2. **No password-field guard seen on the translation path** [inf]. Floris's incognito handling (`gj/q.java:856`, `IME_FLAG_NO_PERSONALIZED_LEARNING` = `16777216`) covers suggestion learning. No check of `inputType` password variations was found before the `ck.a.d` routing. If the translation bar stays on in a password field, the password could be sent to gtx (open item #9; high severity if confirmed).
3. **Logcat leak** [obs]: `Log.d("urlCheck___", "callUrlAndParseResult: " + fullUrl)` writes the whole translated text to logcat on every call. Third-party apps cannot read logcat without READ_LOGS (API 16+), but it shows up in bug reports, adb and OEM log collectors [inf].
4. **Auto Backup** [obs/inf]: `allowBackup="true"` with no `fullBackupContent` or `dataExtractionRules`. Room `keyboard_theme_db` (translation history, phrasebook selections), Floris clipboard and user-dictionary DBs, and SharedPreferences are backed up to the user's Google Drive by default [inf].
5. **Ad SDK inside the IME** [obs]: a native ad renders inside the keyboard window. The ad SDK runs in the IME process, but it has no API access to key events [inf]. The hard-coded list of about 90 Google and launcher host packages that suppress the ad (`FlorisImeService.java:670-711`) looks like review or policy evasion [inf].
6. **Privacy-policy alignment**: not checked. No privacy-policy URL appeared among the first-party endpoints (open item #10, Monetization/Legal).

---

## 7. Manifest and component exposure

| Component | Exported | Protection | Note |
|---|---|---|---|
| `MainActivity` | yes (launcher) | — | Normal. |
| `FlorisImeService` | yes | `BIND_INPUT_METHOD` | Correct. Only the system can bind it [obs]. |
| `ClipboardMediaProvider` (`…provider.clipboard`) | **no**, `grantUriPermissions=true` | per-URI grants | Fine [obs]. |
| `FileProvider` (`…provider`) | no | `file_paths.xml`: `external-path "."`, `files-path "."`, `cache-path "."` | Overly broad roots. Exposure depends on which URIs the app grants [inf]. Low. |
| `FCMHelper` | no | — | Fine. |
| WorkManager `SystemJobService`, `DiagnosticsReceiver`, `ProfileInstallReceiver`, `FirebaseInstanceIdReceiver` | yes | `BIND_JOB_SERVICE`, `DUMP`, `c2dm.SEND` | Library defaults [obs]. |
| `PermissionActivity` | (Floris) | `noHistory` | — |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | signature | — | AndroidX default. |

Anomaly (from triage, confirmed) [obs]: `method.xml` `settingsActivity` points to `dev.patrickgold.florisboard.SettingsLauncherAlias`, which is not declared. The Settings link from system IME settings is probably broken [inf]. This is functional, not a security issue.

---

## 8. Dynamic battle plan (Wave 2, when a lab is available)

**Lab:** rooted AVD (API 34). Mitm CA moved into the **system** store, because without an NSC user CAs are ignored. Plain Frida is enough; nothing detects it. LSPosed is not needed.

**Install path:** a sideloaded APK triggers PairIP `NOT_LICENSED` → paywall, or `System.exit(0)` about 30 s later [inf]. Pick one of these options:
1. Install from Play on a Play-enabled AVD image. This is the cleanest option.
2. Frida-hook `com.pairip.licensecheck.LicenseClient.checkLicense` to a no-op, or replace `LicenseClient.exitAction` with an empty `Runnable`.
3. Smali-patch `com/pairip/application/Application.attachBaseContext` to drop the call and re-sign. This is safe because there is no integrity check.

**Hooks (class.method → purpose):**
| # | Hook | Purpose | Closes |
|---|---|---|---|
| H1 | `tf.b.n(String,String,String)` args/return | Confirm the per-keystroke translate cadence and debounce, and which screens call it | #7, #4 |
| H2 | `ck.a.d/e/c/g` + `EditorInfo.inputType` in `onStartInputView` | Check whether the translation bar sends text from password fields | #9 |
| H3 | `android.speech.SpeechRecognizer.startListening(Intent)` + `Settings.Secure voice_recognition_service` | Identify the recognizer provider; online or offline | #11 |
| H4 | `com.google.firebase.remoteconfig.FirebaseRemoteConfig.getString/getBoolean/getLong` | Dump live ad cadence and feature flags | #2 |
| H5 | `android.content.SharedPreferences.getBoolean("isUserSubscribed"/"is_user_premium")` | Premium gate; confirm Billing is unused | #1 |
| H6 | `com.google.android.gms.vision.text.TextRecognizer` init vs ML Kit `TextRecognition` | Which OCR path is used | #3 |
| H7 | Logcat filter `urlCheck___|mAds_|showAdState__|AdsInter_|SplashDebug|voiceTranslatorNotificationCheck___` | Free tracing without Frida | all |

**Mitm expectations** [inf]:
- `translate.googleapis.com` GET with `q=` visible.
- `api.dictionaryapi.dev` GET.
- `firebaseinstallations`, `firebaseremoteconfig`, `app-measurement.com`, `firebase-settings.crashlytics.com`, `firebaselogging-pa` (Perf).
- AdMob `googleads.g.doubleclick.net` and related.
- No unknown hosts. Any unknown host is a finding.

**Expected failure modes** [inf]:
- PairIP exit on a sideloaded install (handled above).
- GMS ad SDK traffic using Cronet/QUIC can bypass the HTTP proxy. Block UDP/443 to force TCP.
- The gtx endpoint may return 429 when hammered from the lab.

---

## 9. Security recommendations for the clone

1. **Translation backend** [dec]: replace gtx with **on-device ML Kit Translation**, which is free and keeps keystrokes local. That is a privacy feature competitors cannot match. Fall back to a paid API (Google Cloud Translation, DeepL, Azure) only through **our own proxy**, so no vendor key ships in the APK. Use POST bodies, not query strings.
2. **Never translate from sensitive fields** [dec]: turn off the translation bar, voice and clipboard capture when `inputType` is any password variation, `TYPE_TEXT_VARIATION_WEB_PASSWORD`/`VISIBLE_PASSWORD`, `TYPE_NUMBER_VARIATION_PASSWORD`, or `IME_FLAG_NO_PERSONALIZED_LEARNING`. Floris upstream provides most of the plumbing.
3. **Debounce and minimise** [dec]: translate after about 400 ms idle or on an explicit button, and send only the current sentence.
4. **Speech** [dec]: prefer `SpeechRecognizer.createOnDeviceSpeechRecognizer` (API 31+) or set `EXTRA_PREFER_OFFLINE`. Disclose cloud fallback.
5. **Logging** [dec]: strip `Log.d/v/i` with R8 `-assumenosideeffects`. Never log URLs, user text, FCM tokens or host package names.
6. **Backup** [dec]: set `dataExtractionRules`/`fullBackupContent` to exclude clipboard, translation history and the user dictionary, or set `allowBackup=false`.
7. **Firebase key** [dec]: restrict it to the app's package and SHA-1 and to the specific Firebase APIs. Turn on App Check if we add any backend.
8. **Anti-tamper** [dec]: PairIP licensing alone adds little and can kill the IME process. Prefer Play Integrity checked on our server for paid features only. Never use local `isUserSubscribed` flags as the entitlement source.
9. **Transport** [dec]: add a `network_security_config` with `cleartextTrafficPermitted=false`. Pin only our own proxy if we have one; do not pin Google hosts.
10. **Ads in the IME** [dec]: do not copy the Google-host allowlist suppression. If we show keyboard ads at all, gate them consistently and disclose them. Better, keep ads out of the IME window and in the companion app.
11. **FileProvider** [dec]: narrow `file_paths.xml` to specific subdirectories (for example `cache/share/`).
12. **Licensing** [dec]: keep FlorisBoard Apache-2.0 NOTICE and attribution (`assets/license`) when forking.

---

## 10. Open items (static-only; owners assigned)

| # | Item | Owner | How to close |
|---|---|---|---|
| 1 | Confirm no paywall or Billing flow at runtime | Monetization | H5 + UI walk |
| 2 | Live Remote Config values and ad cadence | Backend | H4 |
| 3 | OCR path: Mobile Vision vs ML Kit | Native/UX | H6 |
| 4 | Conversation mode STT → gtx → TTS chain | Backend | H1 + H3 |
| 5 | Firebase API key restrictions (package/SHA-1/API scope) | Security (T5) | Firebase console of the *clone* project only. Do not probe the target's key [dec]. |
| 6 | PairIP behaviour on sideload (paywall vs exit, and whether the IME dies) | Security (T5) | Sideload test in the lab |
| 7 | Live-translate debounce and dispatch inside failed class `androidx/lifecycle/o0` | Security (T5) | smali of `o0.invokeSuspend` case 10, or H1 timing |
| 8 | Whether Firebase Perf traces include the `q=` query (user text) | Security (T5) | mitm `firebaselogging-pa` payload |
| 9 | **Translation bar active in password fields?** (high severity if yes) | Security (T5) / Native/UX | H2 on a password EditText |
| 10 | Privacy-policy text vs actual flows (keystrokes to Google gtx, dictionaryapi.dev) | Monetization/Legal | Play listing and in-app policy URL |
| 11 | Which recognizer service handles audio (Google online vs on-device) | Native/UX | H3 |
