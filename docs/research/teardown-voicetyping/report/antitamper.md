# Team 5: Anti-tamper and Security. Voice Keyboard - Voice Typing v1.3

Package `com.logiapps.language.keyboard.voice.typing.translator` v1.3 (versionCode 4), sha256 `6052cf1a…c6f8`. Mode: **STATIC-ONLY**. No device, mitmproxy or Frida was used. Every runtime claim is `[inf]` and is tracked in §13.
Tags: `[obs]` = read directly from a shipped artifact (decompiled source, manifest, resources, or signature block). `[dec]` = a decision or recommendation for our clone. `[inf]` = inferred.
Paths are relative to `voicetyping_work/`. Source paths are under `jadx_out/sources/`. **No extracted key was ever used against a live API.**

**Headline:**
- This app has **no protection layer at all**: no PairIP of any tier, no RASP that gates behavior, no certificate pinning, no attestation, no App Check and no string encryption [obs]. The only obstacle is R8 renaming (10684 of 12845 classes are in `defpackage/`) [obs]. A dynamic pass needs only a system-store CA on a rooted AVD. Frida is optional.
- **The real story for an IME is privacy, not anti-tamper.** The fork adds a **"translate-while-typing" mode**. In that mode, every key press appends the character to a buffer and fires a **plain HTTP GET with the whole typed buffer in the query string** to the unofficial endpoint `translate.googleapis.com/translate_a/single`. The result is then written back into the host app's text field. The full URL, including the typed text, also goes to logcat [obs]. Voice-dictation results go through the same path [obs]. None of the code I read in that branch checks the field's input type [obs]. Whether it runs inside password fields is open item #1.

---

## 1. Anti-tamper inventory

| Mechanism | Evidence | Strength | Bypass effort | Tag |
|---|---|---|---|---|
| PairIP (licensing or VM tier) | **ABSENT**. No `com/pairip/`, no `libpairipcore.so`, no VM blobs. `anti-tamper.txt` flags only the missing NSC | None | N/A | [obs] |
| App-level signature / repackaging check | **ABSENT**. No first-party `GET_SIGNATURES`/`GET_SIGNING_CERTIFICATES` use. The only hits are in Play Core (`defpackage/e2a.java:47`, `bna.java:56`), which check Google's own app-update service signer | None | N/A | [obs] |
| Root / emulator / debugger detection | **SDK-only and non-gating**: Crashlytics `CommonUtils`-style flags (`defpackage/d71.java:159-170` goldfish/ranchu, test-keys, `/system/app/Superuser.apk`, `/system/xbin/su`; `:921` `isDebuggerConnected`) feed crash-report metadata. GMS phenotype build-type reads (`nfa.java:291,394`). No first-party caller | None (telemetry only) | N/A | [obs] |
| Installer check | Only SDK `getInstallerPackageName` (`d93.java`, `en3.java`, `l6.java`: Firebase/GMS) | None | N/A | [obs] |
| Play Integrity / SafetyNet / Firebase App Check | **ABSENT**. No `com/google/firebase/appcheck`, no `play/core/integrity` | None | N/A | [obs] |
| Play SourceStamp | Signature block "Verified for SourceStamp: true". Stamp cert `3257d599…bd6d` (`base/stamp-cert-sha256`) | Advisory only, not enforced in code | Trivial | [obs] |
| Signing | v2+v3 schemes, **RSA 4096**, DN `CN=Android, O=Google Inc.`, cert SHA-256 `e1a50005…3ec1`. This is the Play App Signing (Google-managed) key | Strong key; irrelevant to tamper resistance | N/A | [obs] |
| networkSecurityConfig | **ABSENT** in manifest. No `res/xml/network_security_config.xml` | None (trusts system CAs only, API 24+ default) | Trivial with a system CA | [obs] |
| Certificate pinning | **ABSENT**. No `CertificatePinner` use. All `X509TrustManager`/`HostnameVerifier` hits are OkHttp/Conscrypt platform shims (`k46`, `pj`, `vt5`, `pe1`, …) | None | N/A | [obs] |
| String / asset encryption | **ABSENT**. Endpoints, ad unit IDs and the RC schema are plaintext in dex and resources | None | N/A | [obs] |
| Obfuscation | R8 full-mode renaming plus repackaging into `defpackage/`. Source-file attribute replaced by `r8-map-id-8d086b3b…` | Low-Medium (slows reading only) | Low | [obs] |

**Net:** there is nothing to bypass. All revenue, ad-gating and data-egress logic decompiles to readable (renamed) Java [obs].

---

## 2. PairIP tier and the honest coverage line

- **Tier: none.** The runbook generated no `pairip-vm-callsites.txt` because there is no `libpairipcore.so`. A grep of the sources finds no `com.pairip`, `VMRunner`, `executeVM` or `LicenseClient` [obs].
- **Coverage statement:** VM-virtualized methods = **0**. jadx decompiled 12845 classes. 113 classes failed and 239 errors were logged, but **none of the failures are in first-party packages** (`com/example/voicetypingkeyboard_z`, `dev/patrickgold/florisboard`) [obs, triage brief + `jadx-failed-classes.txt`].
- Money-path coverage (ad gating, RC schema, in-IME ad toggles), data-egress coverage (translate, dictionary, stickers) and IME input handling (`FlorisImeService`, the `o98` key dispatcher) are all **100% readable** [obs].
- The remaining blind spot is semantic, not technical. R8 renamed most of the classes, so some flows (voice conversation, TTS roles) need call-graph tracing rather than name lookup. See §13 #5.

---

## 3. RASP and runtime checks

- **No first-party RASP** [obs]. There is no first-party code path that reads root, emulator, debugger, hook-framework (Frida/Xposed/LSPosed) or signature state and then changes behavior. The two `xposed` grep hits are false positives: the words "exposed" and "underexposed" in `fn6.java:151` and `sf8.java:523`.
- The SDK checks that do exist only *report*: Crashlytics tags crashes with root and emulator state, and AdMob's own risk signals go to Google's ad servers [inf]. Expected effect on a rooted AVD: the app works normally, but **ad fill may drop or ads may be test-flagged** because of AdMob's server-side invalid-traffic heuristics [inf].
- **IME-specific gate** [obs]. `FlorisImeService.onStartInputView` (`dev/patrickgold/florisboard/FlorisImeService.java:573-625`) decides whether to show the in-keyboard native ad. It does this by matching `editorInfo.packageName` against a hard-coded exclusion list of ~85 packages. The list covers almost every Google first-party app (Gmail, Chrome, YouTube, Play Store, Maps, Messages, Docs, Gboard, Translate, Lens, Authenticator, Assistant, Dialer, Calendar, Keep, Family Link, …) and every major OEM/third-party launcher. The ad also requires `my_keyboard_native_ad_enable`, `!is_user_premium` and `PREF_CAN_USER_REQUEST_ADS`.
  - [inf] The effect is that the in-IME ad is hidden in exactly the apps a Google reviewer or a stock-device tester is most likely to type into, and shown in third-party apps (WhatsApp, Instagram, banking apps, etc.). Treat this as a **reviewer-evasion pattern and a Play policy risk** (ads in an IME, plus selective suppression).
  - [dec] Our clone must never do this. See §11.
- `is_user_premium` is read in `dt.java:49,140` and `js6.java:300` and copied into `keyboard_prefs` (`js6.java:302`). It is **never set to true anywhere**, and the app has no billing library [obs]. It is a white-label template leftover. On a rooted device, flipping it suppresses all ads, which matters only for a dynamic pass.

---

## 4. Network security

| Item | Finding | Tag |
|---|---|---|
| NSC | Absent. With targetSdk 37 the defaults apply: cleartext blocked and only system CAs trusted. A user-installed CA is **not** trusted | [obs] |
| `usesCleartextTraffic` | Not set (defaults to false). Every first-party URL is `https://` | [obs] |
| Pinning | None, in either app code or OkHttp config | [obs] |
| TLS stacks | `HttpURLConnection` (translate, wrapped by `FirebasePerfUrlConnection.instrument`, `my3.java:155`). OkHttp/Retrofit (dictionary `nk6.java:81`, stickers `eo5.java:549`, coil3 images). Cronet appears only as a GMS property file (`play-services-cronet.properties`), used by the ads SDK [inf] | [obs] |
| Spoofed UA | The translate request sets `User-Agent: Mozilla/5.0` and `client=dict-chrome-ex`, impersonating the Google Dictionary Chrome extension (`my3.java:153-157`) | [obs] |
| MITM verdict | A **system-store CA on a rooted AVD** (Magisk "Move Certificates" / AlwaysTrustUserCerts) should see all first-party traffic in clear. No Frida unpinning is needed. GMS/ads traffic may use Cronet/QUIC and needs UDP/443 blocked to force TCP fallback [inf] | [inf] |

---

## 5. Attestation chain

- None [obs]. There is no first-party backend, no auth token and no App Check. The Firebase calls (Installations, RC, FCM, Analytics, Crashlytics, Perf, Sessions) carry only the standard Firebase API key and FID [inf: standard SDK behavior].
- The third-party endpoints (`translate.googleapis.com`, `freedictionaryapi.com`, `myappsdata.site`) are unauthenticated GETs [obs].
- Consequence: nothing server-side ties a request to a genuine install. This is irrelevant to the app's security, because it has no paid entitlements to protect.

---

## 6. Secrets inventory (redacted) and abuse assessment

| Secret | Location | Value (redacted) | Abuse assessment | Tag |
|---|---|---|---|---|
| Firebase / Google API key | `res/values/strings.xml:155` `google_api_key` | `AIzaSyBL…70kE` | Standard client-side Firebase key, public by design. Realistic risks: (a) if the GCP project left it unrestricted, other enabled Google APIs could be billed to the developer; (b) Remote Config defaults could be fetched by anyone (low value, ad IDs only). **Not tested; no call made** | [obs] / risk [inf] |
| GCM sender ID | `gcm_defaultSenderId` | `14064649…3929` | Public identifier. Low risk | [obs] |
| Firebase app ID | `google_app_id` | `1:140646…4a78` | Public identifier | [obs] |
| Firebase project / bucket | `project_id` `voice-keyboard-translator-z`; `voice-keyboard-translator-z.firebasestorage.app` | n/a | Bucket name is public. Exposure depends on Storage rules: see open item #7 | [obs] |
| AdMob app ID | `strings.xml:25` `admob_app_id` | `ca-app-pub-3051…1938` | Public by design. Ad unit IDs (5 production + 1 test, Team 3) are also public. Their abuse vector is invalid-traffic attacks against the publisher, not against us | [obs] |
| FCM token | Not shipped. Generated at runtime and **written to logcat** (`utils/fcm/FCMService.java:35`) | n/a | A token in logcat or bugreports lets anyone holding the server key push to that device. Low risk | [obs] |
| `secrets-candidates.txt` hits | `api_key = 0x7f120284`, `password = 0x7f07…` (resource IDs). `token=TRBZRIRI…` is in `META-INF/…/kotlinx-serialization-core-jvm/verification.properties` | n/a | False positives: resource IDs and a build-verification token | [obs] |

There are no OpenAI, Gemini, DeepL, Azure, Google Cloud Translation or other paid-vendor keys anywhere [obs]. The fork avoided paying for translation by **scraping an unofficial Google endpoint** rather than shipping a key. That carries a ToS risk and rate-limit/IP-block fragility, not a key-leak risk [inf].

---

## 7. Exported surface, providers, backup

| Component | Exposure | Notes | Tag |
|---|---|---|---|
| `presentationLayer.MainActivity` | exported (launcher) | No deep-link filters | [obs] |
| `FlorisImeService` | exported, protected by `BIND_INPUT_METHOD` | Only the system can bind. Correct | [obs] |
| `AudioPermissionActivity` | not exported | Translucent trampoline that asks for RECORD_AUDIO from the IME | [obs] |
| `ClipboardMediaProvider` (`…provider.clipboard`) | not exported, `grantUriPermissions=true` | Serves clipboard images/videos to host apps through per-URI grants. This is the upstream FlorisBoard design | [obs] |
| `FileProvider` (`…provider`) | not exported | `xml/file_paths.xml` exposes **`files-path "."`, `cache-path "."` and `external-cache-path "."`**, i.e. the whole internal files and cache roots, including the Room DBs if any grant is mis-issued. Any URI grant bug becomes full data exposure | [obs] / risk [inf] |
| FCM `FirebaseInstanceIdReceiver` | exported, `c2dm.SEND` permission | Standard | [obs] |
| `FCMService.onMessageReceived` | not exported | Only logs "Notification is Received". No payload-driven intents or URLs, so there is no push-to-deeplink injection surface | [obs] |
| WorkManager / Firebase receivers with `DUMP` / `BIND_JOB_SERVICE` | exported but permission-guarded | Standard | [obs] |
| **IME settings alias** | `method.xml` points at `dev.patrickgold.florisboard.SettingsLauncherAlias`, but **no such `<activity-alias>` exists in the manifest** | The system "keyboard settings" gear will fail to resolve (functional bug, not security) | [obs] |
| Backup | `allowBackup="true"`, **no `fullBackupContent` or `dataExtractionRules`** | Auto Backup / device transfer will include the **clipboard history DB, user dictionary (learned words), AppDatabase (translations/conversations) and DataStore prefs**. For an IME this means typed-word and clipboard history leave the device into the user's Google Drive backup | [obs] / impact [inf] |
| `largeHeap="true"` | n/a | Not security-relevant | [obs] |

---

## 8. Obfuscation profile

- R8 with aggressive repackaging. 10684 of 12845 classes are in `defpackage/`, and the AdMob next-gen SDK keeps its own `ads_mobile_sdk/` package [obs].
- Kept names: `FlorisImeService`, `AudioPermissionActivity`, `ClipboardMediaProvider`, the Room DB classes, the preference model, and all `dataLayer.models.*` (kept by Gson/kotlinx-serialization reflection) [obs].
- **No string encryption**: endpoints, log tags, RC keys and the package-name allowlist are all plaintext [obs].
- No reflection-based hiding and no dynamic dex loading in first-party code. The `Class.forName` hits are Firebase and GMS internals [obs].
- **887 `Log.d` call sites** survive in release, including first-party tags that carry user data. See §9 [obs].
- Practical read cost: low. `grep -I` on log tags (`urlCheck___`, `TextCheck----`, `DictionaryLog`, `showAdState__`, `interAds_`) leads straight to each feature's obfuscated class [obs].

---

## 9. Privacy: what leaves the device (the critical section for an IME)

### 9.1 Data-flow table

| Data | Trigger | Destination | Transport | Evidence | Tag |
|---|---|---|---|---|---|
| **Keystrokes, typed in the IME while "translate mode" is ON** | Every printable key, space and backspace | `translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl=..&tl=..&dt=t&q=<FULL BUFFER>` | HTTPS GET, text in the **URL query** | Key dispatcher `defpackage/o98.java:1040-1071`: when pref `keyboard.m` (translate toggle) is true, it calls `hh8.b(char)` / `hh8.c()`. `hh8.java` appends to the buffer and launches coroutine `as0`, which calls `my3.B0(buffer, src, tgt)` (`my3.java:150`). `gh8.java:60-68` then `deleteSurroundingText` + `commitText(translation)` into the host field | [obs] |
| Default language pair | n/a | `hh8.c="en"`, `hh8.d="ur"`; changed through `k32.java:70` | n/a | `hh8.java` | [obs] |
| **Voice dictation result** (translate mode ON) | `onResults` | Same translate endpoint | HTTPS GET | `defpackage/yy2.java:55-60` sets the buffer to the recognized text, then `as0` translates it | [obs] |
| Voice dictation result (translate mode OFF) | `onResults` | Committed locally | n/a | `yy2.java:62-64` | [obs] |
| **Raw microphone audio** | Mic key in IME | Platform `SpeechRecognizer.createSpeechRecognizer` (`FlorisImeService.java:747-751`), `free_form`, partial results, no `EXTRA_PREFER_OFFLINE` | Through the system recognition service. On most devices that is Google's, which may process audio in the cloud | [obs] call / [inf] destination |
| Text Translator / Camera Translator / Conversation text | In-app screens | Same translate endpoint | HTTPS GET | Callers `or0.java:181,187` (camera/OCR flow), `nh8.java:59` | [obs] |
| Camera frames | Camera Translator | **On-device** GMS Vision TextRecognizer (`ny1.java`, around line 630) | Local | Triage brief | [obs] |
| Dictionary lookups (single words) | Dictionary screen | `https://freedictionaryapi.com/` (Retrofit, `nk6.java:81`; `l32.java:2716` `d()`) | HTTPS | Log line "Attempting API … for: <word>" | [obs] |
| Sticker catalog / images | Sticker panel | `myappsdata.site/ZTechVoiceTyping/databases/stickers.json` + image hosts | HTTPS | `eo5.java:549` | [obs] |
| **Host-app package name** | Every `onStartInputView` | Logcat (`mAds_`, `showAdState__`). The in-IME AdMob request also implies host-app context reaches Google ads [inf] | n/a | `FlorisImeService.java:584-589` | [obs] / [inf] |
| Clipboard contents | System clipboard change | **Local only**: `ClipboardHistoryDatabase` + `ClipboardFilesDatabase`. No network sink found. Exposed through Auto Backup (§7) | n/a | `g1.java:1240-1346` | [obs] |
| Learned words | Typing (non-incognito) | Local `FlorisUserDictionaryDatabase`. Backed up via Auto Backup | n/a | Room DB classes | [obs] |
| Analytics | Ad lifecycle | Firebase Analytics `select_content`-style events with `item_id/item_name/content_type`. All 14 call sites pass **ad event names only** (for example `splash_show_failed`, `<screen>_native_ad_requested`). No typed text | Firebase | `defpackage/z8.java`, callers `xg.java:329`, `bt.java:38`, `at.java:36` | [obs] |
| Network-request telemetry | Every translate call | Firebase Performance (`FirebasePerfUrlConnection.instrument`, `my3.java:155`) | Firebase | Whether the **query string** (which holds the typed text) is kept or stripped by Perf before upload is unverified. Open item #3 | [obs] call / [inf] content |
| Ads / attribution | App and IME | AdMob next-gen SDK, AD_ID, AdServices attribution, UMP consent | Google | Manifest | [obs] |

### 9.2 Logcat leaks of user content (release build) [obs]

- `my3.java:154`, tag `urlCheck___`: logs the **full translate URL including the typed text**.
- `yy2.java`, tag `TextCheck----`: logs **every voice-recognition result**.
- `l32.java:2716`, tag `DictionaryLog`: logs the looked-up word.
- `FCMService.java:35`: logs the FCM token.
- `FlorisImeService.java:584-619`: logs the host app package and the ad decision on every field focus.
- [inf] Since Android 4.1, third-party apps cannot read logcat without `READ_LOGS`. The exposure is to bugreports, OEM log collectors, ADB-connected tooling and device-farm logs. It is still a real leak of keystroke-level content.

### 9.3 Sensitive-field handling (open)

- [obs] The translate-mode branch in `o98.java:1048-1071` checks only the pref and the key type. It contains no `inputType` password or `IME_FLAG_NO_PERSONALIZED_LEARNING` check.
- [inf] Upstream FlorisBoard does treat password fields specially for suggestions and learning, but I did not confirm that the translate toggle is reset or suppressed there. If it is not, **passwords typed with translate mode on are sent to Google in a URL and logged to logcat**. This is open item #1, highest priority.
- [obs] The clipboard code reads `android.content.extra.IS_SENSITIVE` and stores the flag per item (`g1.java:1247`, passed into `i31` at `:1346`). Sensitive clips are therefore still **stored** in the history DB, probably masked in the UI [inf].
- [obs] The decompiled condition reads `if (SDK_INT >= 33 || desc == null …) z = false`, which taken literally *ignores* the flag on API 33+. This may be a jadx/R8 condition artifact. Open item #4.

### 9.4 Privacy verdict

- The base FlorisBoard engine is local-first and clean [inf, upstream reputation + no network sinks found in the IME core].
- **The fork's additions make it a keystroke-egress keyboard whenever translate mode is on**: character-by-character full-buffer uploads to an unofficial endpoint with a spoofed client ID, plus logcat copies.
- Combined with in-IME ads gated by host package and reviewer-app suppression, this app would struggle under Play's IME, User Data and Ads policies [inf].

---

## 10. Dynamic battle plan (for a future rooted-AVD wave)

**Lab:** rooted AVD (API 34+, Google Play image so the system SpeechRecognizer exists), Magisk with the mitmproxy CA moved into the system store, UDP/443 blocked (forces ads/GMS off QUIC), and Frida as an optional extra. No PairIP or RASP bypass module is needed [inf].

| Step | Action | Hook / observation | Expected failure mode |
|---|---|---|---|
| 1 | Install the splits (`unpack/*.apk`) with `adb install-multiple` and enable the IME in Settings | `adb shell ime list -a` | The IME settings gear is broken (missing alias, §7). Use the app's own UI |
| 2 | Start `adb logcat -s urlCheck___ TextCheck---- DictionaryLog showAdState__ mAds_ interAds_ voiceTypingFCMCheck___` | The app's own logs reveal translate URLs, voice results and ad decisions with **no Frida needed** | None |
| 3 | Toggle translate mode, then type in a **third-party** app's normal field, a password field (`inputType=textPassword`), and a `IME_FLAG_NO_PERSONALIZED_LEARNING` field | mitmproxy: count GETs per keystroke and check whether `q=` holds the password | Closes open item #1. Translate may IP-throttle (HTTP 429) after a burst |
| 4 | Repeat step 3 inside Chrome/Gmail (excluded packages) | Confirm the native ad is suppressed there and shown in third-party apps | Closes open item #2 |
| 5 | Voice dictation with translate mode on and off | mitmproxy (translate GETs), `SpeechRecognizer` destination (Frida: hook `SpeechRecognizer.startListening`, log the bound service component) | Confirms whether recognition is online (Google) or on-device |
| 6 | Firebase Perf | mitmproxy on `firebaselogging-pa.googleapis.com` / Perf transport. Decode the protobuf and grep for `q=` | Closes open item #3. The payload is batched and may need `adb shell setprop log.tag.FirebasePerformance DEBUG` |
| 7 | Clipboard | Copy text from a password manager (which sets IS_SENSITIVE) on API 33+ and API 30. Pull `databases/clipboard_history*` via `run-as`/root | Closes open item #4 |
| 8 | Backup | `adb shell bmgr backupnow <pkg>`, then inspect the backup transport payload, or run `adb backup` on an older API | Confirms clipboard/dictionary DBs are included |
| 9 | Remote Config | Frida-hook `FirebaseRemoteConfig.getAll()` (find it via the `ju2`/`hu2` callers), or read `files/frc_*` with root | Gives actual ad cadence and in-IME refresh values for Team 3 |
| 10 | FileProvider | `adb shell content read --uri content://…provider/my_files/…` from a shell UID with no grant | Should be denied. Confirms no grant leaks |

Classes to hook if Frida is used: `defpackage.my3.B0(String,String,String)` (translate egress), `defpackage.hh8.b/c` (buffer), `defpackage.yy2.onResults`, `dev.patrickgold.florisboard.FlorisImeService.onStartInputView`, and `defpackage.z8.a` (analytics).

---

## 11. Clone security specification (buildable)

Our clone builds on **upstream FlorisBoard (Apache-2.0) with attribution**, not on this fork. The rules below fix every issue above.

1. **[dec] Translation: never upload keystrokes.**
   - Default to **ML Kit on-device translation** (downloaded language packs) for the in-keyboard mode, so typed text never leaves the device.
   - Any cloud translation (a paid Google Cloud Translation or Gemini call through our own backend proxy) runs **only on an explicit "Translate" tap on committed text**, never per keystroke. Debounce, send in the POST body, never in the URL, and use TLS.
   - Never call `translate_a/single` or spoof a client ID or User-Agent.
2. **[dec] Hard-disable in sensitive fields.** In `onStartInputView`, if the field is a password variation (`TYPE_TEXT_VARIATION_PASSWORD`, `VISIBLE_PASSWORD`, `WEB_PASSWORD`, `NUMBER_PASSWORD`) or sets `IME_FLAG_NO_PERSONALIZED_LEARNING`:
   - Force translate mode off, disable voice, suggestions learning and clipboard capture, and hide any network-backed UI.
   - Unit-test this with a matrix of EditorInfo fixtures.
3. **[dec] No ads inside the IME surface.** Ads and upsells live only in the companion app. There is no host-package allowlist or denylist logic, and no behavior that differs for Google apps.
4. **[dec] Zero user-content logging in release.**
   - R8 rule `-assumenosideeffects class android.util.Log { *; }` for d/v/i.
   - A lint rule bans string concatenation of user text into any log call.
   - Crashlytics custom keys must never hold text.
5. **[dec] Voice:** prefer `SpeechRecognizer.createOnDeviceSpeechRecognizer` (API 31+) or `EXTRA_PREFER_OFFLINE`. Disclose a cloud fallback in the privacy policy and the Data Safety form.
6. **[dec] Backup:** ship `dataExtractionRules` (API 31+) and `fullBackupContent` (older APIs) that exclude `clipboard_history*`, `clipboard_files*`, the user dictionary DB, the conversation/translation DB and `files/datastore/*keyboard*`. If we want cross-device sync of settings only, include just the theme/settings DataStore.
7. **[dec] Clipboard:**
   - Honor `ClipDescription.EXTRA_IS_SENSITIVE` on **all** API levels and **do not persist** sensitive clips.
   - Auto-expire history (upstream supports this; default 1 hour).
   - Exclude clipboard history from backup.
8. **[dec] FileProvider:** scope paths to dedicated subdirectories (`cache/share/`, `files/stickers/`), never `"."`.
9. **[dec] Network:**
   - Ship an NSC with `cleartextTrafficPermitted="false"`.
   - Pin our own proxy domain with a backup pin and an expiry.
   - Leave third-party SDK domains unpinned.
10. **[dec] Keys:** no paid-vendor keys in the APK. Restrict the Firebase key in GCP to the Android app (package + SHA-256) and the Firebase APIs. Enable **App Check (Play Integrity)** on our proxy and on Firebase services.
11. **[dec] Protection layer:** PairIP or similar is not worth buying for a free, ads-only keyboard because there is no entitlement to protect. If we add premium, verify purchases server-side (RTDN + `purchases.subscriptionsv2`) and gate entitlements on the server, not with a local `is_user_premium` boolean.
12. **[dec] Manifest hygiene:**
    - Declare the settings alias that `method.xml` references.
    - Keep `directBootAware=false` (as here) so no unlocked-only data is touched before the user unlocks.
    - Set `allowBackup` deliberately, with the rules from item 6.

---

## 12. Contrast: sibling keyboard (`com.heri.language.keyboard.voice.typing`, `voicetranslator_work`)

| Dimension | This app (logiapps, v1.3) | Sibling (heri, v1.4) | Tag |
|---|---|---|---|
| IME base | FlorisBoard fork | Also FlorisBoard (`dev/patrickgold/florisboard` present) | [obs] |
| PairIP | **None** | **Licensing-only tier**: `com/pairip/{application,licensecheck}`, **no `libpairipcore.so`**, so no VM encryption. It does not block Frida or detect repackaging beyond the Play license check | [obs] |
| NSC | Absent | Absent | [obs] |
| Firebase key | `AIzaSyBL…70kE` | `AIzaSyCd…lqfo` | [obs] |
| Unofficial translate endpoint | `translate.googleapis.com` (`my3`) | Strings referencing it in `kj/b.java`, `tf/b.java` [obs]; whether it uses per-keystroke egress is not assessed here | [obs] / [inf] |
| Native libs | AndroidX, DataStore and CameraX only | The same four libs | [obs] |

[inf] Both apps come from the same white-label template family, with near-identical lib sets and the same FlorisBoard base. Neither has protection worth engineering around. The sibling's PairIP license check only matters for sideloaded installs and is hookable at `LicenseClient.checkLicense`.

---

## 13. Open items (static-only; each needs dynamic confirmation)

| # | Item | Owner | Why it matters |
|---|---|---|---|
| 1 | Does translate mode stay active and upload text in **password / no-learning fields**? Battle plan step 3 | Team 5 (Security) + Wave-2 Driver | Possible credential exfiltration to a third party and to logcat. Highest priority |
| 2 | Confirm in-IME native ad rendering, its 10-minute refresh, and suppression only in the 86 (verified count) excluded (Google/launcher) packages | Team 3 (Monetization) | Policy-risk evidence and the clone "never do" list |
| 3 | Does Firebase Performance upload the translate URL **with** the `q=` query string? | Team 5 + Team 2 (Backend) | Second keystroke-egress sink, this time to the developer's Firebase project |
| 4 | Clipboard `IS_SENSITIVE` handling on API 33+ (is the decompiled condition really inverted?) and whether sensitive clips persist | Team 4 (Native/UX) | Password-manager clips could be stored in plaintext and backed up |
| 5 | Trace the voice-conversation flow (STT, then translate, then TTS via `vr6`/`fw9`) and confirm the recognizer service (online or on-device) | Team 4 (Native/UX) | Audio egress disclosure |
| 6 | Confirm Auto Backup payload contents (clipboard, user dictionary, AppDatabase) | Team 5 | Off-device copy of typing history |
| 7 | Firebase Storage bucket rules for `voice-keyboard-translator-z.firebasestorage.app` (read-only check from an unauthenticated client; **do not** use the extracted key against other APIs) | Team 2 (Backend), legal sign-off first | Possible public data exposure. Static analysis found no Storage SDK usage in-app, so likely unused [inf] |
| 8 | Ad fill or behavior changes on a rooted/emulated device (AdMob server-side IVT) | Wave-2 Hooker | Affects the reliability of the dynamic monetization capture |
| 9 | Diff against upstream FlorisBoard to list every fork-added network or log call in the IME process | Team 1 (Manifest) / Team 5 | Completes the IME egress inventory |
