# com.logiapps.language.keyboard.voice.typing.translator — Reverse-Engineering Report

**Artifact:** `Voice+Keyboard+-+Voice+Typing_1.3_APKPure.xapk` (XAPK file 13,986,166 bytes; base + `config.arm64_v8a` + `config.xhdpi` splits total 13,741,089 bytes)
**SHA-256:** `6052cf1a5a6c9eafadba994fedecc3d4f63af2eee952617b317de3fe9cf7c6f8`  ·  **Analyzed (UTC):** 2026-10-17 (runbook seed 2026-10-17T12:50:03Z, integrated 2026-10-17)
**Package / version:** `com.logiapps.language.keyboard.voice.typing.translator` · versionName 1.3 / versionCode 4 · minSdk 26 / targetSdk 37 (compileSdk 37)
**Stack:** Kotlin + Jetpack Compose (app shell and IME, as in upstream FlorisBoard) · DI **Hilt 2.60.1** · DB **Room 2.8.4** (4 databases) + DataStore 1.2.1 + SharedPreferences bridge · net **HttpURLConnection** (translate, sticker images) + **Retrofit/OkHttp** (dictionary, sticker catalog), all Firebase-Perf-instrumented · **Navigation3 1.1.7** (`NavDisplay` + `NavBackStack`, 15 registered routes; the Navigation-Compose 2.10.0 artifacts are on the classpath, but no NavController/NavHost code survives R8 — corrected by the class sweep) · CameraX 1.6.2 (camera2-pipe backend) · coil3 · **Glide 4** (native-ad images, stickers) · Lottie · bundled SDK extras: Cronet Java API, WorkManager (AdMob `OfflinePingSender` only), Tink. A white-label **fork of FlorisBoard (Apache-2.0)**; the internal namespace is `com.example.voicetypingkeyboard_z`.
**Mode:** **STATIC-ONLY.** No device, mitmproxy or Frida this pass. Every runtime claim is `[inf]` and is listed as a numbered open item in §10.
**Decompilation coverage:** 12,845 classes (10,684 in R8 `defpackage/`). `jadx.log`: **"finished with errors, count: 239"**, with 113 classes in `jadx-failed-classes.txt`. (`jadx-errors.txt` is empty, but that only means the runbook captured nothing there; the log is authoritative.) **Did any failure hit a money path? No.** Ad gating (`dn3`, `js6`, `dt`, `FlorisImeService`), Remote Config sync, the translate client (`my3`), the live-translate chain (`o98`/`hh8`/`as0`/`gh8`), dictionary and stickers all decompiled. Three renamed first-party classes failed, all on data/UX paths: `defpackage/i1` (voice-conversation coroutine), `defpackage/ki1` (conversation screen, partial) and `defpackage/ad3` (suggestion word-list preload). See open items 9 and 10. **Superseded by the class sweep:** the failed list actually contains 24 first-party or mixed classes, and all of them were recovered (`coverage/recovered/`). 260 source files have at least one undecompiled method. 8 first-party methods failed without being listed, including `s.run()` (the in-keyboard banner load callbacks, read from bytecode), so "no money-path failure" needs that caveat. See `CLASS_COVERAGE.md` §3-4.
**Anti-tamper / attestation:** **None.** No PairIP (no `libpairipcore.so`, no `com/pairip`; 0 VM-protected methods), no Play Integrity / SafetyNet / App Check, no certificate pinning, **no network-security-config** (system CAs only, cleartext blocked by the target-37 default), no string encryption in first-party code (the bundled AdMob next-gen SDK obfuscates its own strings with Base64+XOR, `ads_mobile_sdk/xt3.java:21`), no first-party root/emulator/signature checks. R8 renaming is the only obstacle. Signing: v2+v3, RSA 4096, Play App Signing (generic Google DN), cert SHA-256 `e1a50005ee5d6bbfb73574b4065a796aec4f733dc6d571070f51d1b3caac3ec1`, Play SourceStamp verified.

> **Claim tags (template meaning):** `[obs]` observed in live traffic · `[dec]` decoded from manifest/resources/source · `[inf]` inferred from a tell.
> This pass captured no traffic, so **this report contains zero `[obs]` claims**. The Wave 1 team reports use `[obs]` for "read directly from code" and `[dec]` for "clone decision". In this integrated report their `[obs]` becomes `[dec]`, and their clone decisions are marked **Clone:** in prose.

---

## Quality Gate

| Gate | Status | Evidence |
|---|---|---|
| Artifact identity pinned (SHA-256, version, signer) | PASS | Header; `report/manifest.md` §1 |
| Coverage honestly stated (errors from `jadx.log`, not the seed) | PASS | 239 errors / 113 classes; first-party failures named |
| Money path fully readable | PASS | `report/antitamper.md` §2; `report/monetization.md` §2 |
| Every engine identified (STT, translate, TTS, OCR, AI) | PASS | §2, §4 |
| Every endpoint catalogued with auth + attestation | PASS | §2 (E1-E7) |
| Secrets redacted to 8+4, no vendor API called | PASS | §9 |
| Privacy egress table, IME-specific | PASS | §8 |
| Dynamic pass | NOT RUN (static-only mode by design) | §7 carries the dynamic plan; claims are `[inf]` |
| Open items have owners | PASS | §10 (22 items) |
| No verbatim code/asset/string-table copying | PASS | Short evidence quotes only |

## Definition of Done

| Item | Done? | Note |
|---|---|---|
| Artifact SHA-256, version, date in header | Yes | |
| arm64-v8a analyzed (v7a only as noted fallback) | Yes | The only ABI split shipped is arm64-v8a; all 4 `.so` files are AndroidX/CameraX |
| Decompile errors triaged; money-path failures worked around | Yes | No money-path failures; 3 first-party data-path failures are open items 9-10 |
| Manifest + backend surface + monetization complete | Yes | §2, §5, §6 |
| Flutter: Blutter run (not just `strings`) | N/A | Native Kotlin; no `libflutter.so`/`libapp.so` |
| Dynamic pass attempted; traffic captured or blockage explained | No (by mode) | STATIC-ONLY run. Nothing blocks a later pass: no pinning, no PairIP. Plan in §7 |
| Every claim tagged obs/dec/inf | Yes | |
| Caveats converted to tasks | Yes | §10 |
| Secrets redacted + disclosure note | Yes | §9 |
| Verification pass (fresh eyes) done | Yes | 2026-10-17; see "Verification pass (2026-10-17)" at the end |

---

## TL;DR — how it actually works

"Voice Keyboard - Voice Typing" is **upstream FlorisBoard with a monetized white-label app shell**. It contains **no AI, no bundled ML model and no first-party backend** `[dec]`. Voice typing is the platform `SpeechRecognizer` (one utterance per tap, 5 s watchdog). Every translation (text screen, camera OCR, conversation and the in-keyboard bar) goes to Google's **unofficial, keyless `translate_a/single?client=dict-chrome-ex` endpoint** with a spoofed browser UA `[dec]`. The most counter-intuitive finding is privacy-critical: with the **translate-while-typing toggle on, every keystroke sends the whole typed buffer, in a URL, to Google and to logcat**. No password-field check was found on that branch (open item 1) `[dec]`/`[inf]`. The business model is **ads only**: AdMob, no billing, a dead `is_user_premium` flag. The strongest tell is a **native ad rendered inside the keyboard while users type in other apps, hidden for a hard-coded list of 86 Google apps and launchers** (verified count; the list is identical to App H's). That reads as reviewer evasion, a Play-policy risk the clone must not copy `[dec]`/`[inf]`.

---

## 1. Tooling used

- `re-analyze` runbook (the `apk-teardown` skill): XAPK unpack, split merge, `jadx` with an isolated home dir (`jadxhome/`), producing `jadx_out/`, `jadx.log` (239 errors), `jadx-failed-classes.txt`, `endpoints*.txt`, `sdk-roster.txt`, `secrets-*.txt` and `anti-tamper.txt` `[dec]`.
- `apksigner verify --print-certs -v` (build-tools 36.0.0) for signing `[dec]`.
- `grep -rI` (binary-safe) across `jadx_out/sources` and `resources`; magic-byte checks on assets and `.so` (ELF64 AArch64); a Python profile of the `ime/dict/*.txt` word lists `[dec]`.
- No apktool/baksmali fallback yet for `i1`, `ki1`, `ad3` (open items 9 and 10). No mitmproxy or Frida: static-only mode.

## 2. Backend surface

The developer runs **no server of its own**. All app-specific traffic goes to three keyless public hosts `[dec]`.

| # | Function | Request | Response | Auth / attestation | Tag |
|---|---|---|---|---|---|
| E1 | All translation (text screen, IME bar, camera, conversation) | `GET https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl={src}&tl={tgt}&dt=t&q={text}&ie=UTF-8&oe=UTF-8`, header `User-Agent: Mozilla/5.0`. `&` is replaced by `^~^` and `\n` by `~~` before URL-encoding (`my3.B0`, `my3.java:150-181`) | Nested JSON array; the code joins `[0][i][0]` and restores `~~` to line breaks (`my3.o0`, `:1132`). `^~^` is never reversed | None; spoofs the Chrome dictionary-extension client ID | `[dec]` (shape at runtime `[inf]`, item 3) |
| E2 | Dictionary | Retrofit `GET https://freedictionaryapi.com/api/v1/entries/{language}/{word}?translations=true`, with `language` hard-coded `en` (`a32.java:10`, `nk6.java:79-123`, `l32.java:2713`) | `DictionaryAPIResponse{word, entries[], source}`, mapped to `UnifiedDictionaryResult` (`xt3.java:626-697`) | None | `[dec]` |
| E3 | Sticker catalog | `GET https://myappsdata.site/ZTechVoiceTyping/databases/stickers.json` via OkHttp (`eo5.java:546-560`) | `AvailableStickersModel[]{id,name,file_size,image_List,isPremium,isOffLine}` | None | `[dec]` |
| E4 | Sticker images | `GET {image_List[i]}`, 8 s timeouts, `.complete` marker file (`eo5.java:504-542`) | Image bytes | None | `[dec]`; host `[inf]` (item 13) |
| E5 | Firebase: Remote Config, Installations, FCM, Analytics, Crashlytics, Perf, Sessions | SDK | SDK | Firebase API key + app ID | `[dec]` |
| E6 | AdMob next-gen 1.4.0 + UMP 4.0.0 | SDK | SDK | AdMob app ID | `[dec]` |
| E7 | Play In-App Update (2.1.0) | SDK | SDK | — | `[dec]` |

- **Engine attribution.** E1 is Google's undocumented web translate endpoint (99%: URL literal, browser UA, nested-array parser). The top alternative, the paid Cloud Translation API, is refuted: there is no key, no `v2`/`v3` path and no `translation.googleapis.com` host `[dec]`. There is no ML Kit Translate, DeepL, Microsoft, MyMemory or LLM endpoint anywhere in the sources `[dec]`.
- **Error handling.** E1 catches everything and returns `""`. There is no retry and no timeout (no connect or read timeout is set at all), and HTTP 429 is not distinguished. Input is trimmed before sending, so a trailing space is lost. A CAPTCHA or HTML reply becomes `""` `[dec]`. In the IME path, `""` overwrites the previous translation (§3.2). E3 uses the Hilt OkHttp client with 60 s timeouts (`hq1.java:261-268`) `[dec]`.
- **Transport.** HTTPS only, no pinning, no `CertificatePinner`, no App Check or Play Integrity `[dec]`.
- **Remote Config** (90 keys, 24 h minimum fetch, 60 s fetch timeout) carries **only ad configuration**. It has no endpoint, feature flag or kill switch `[dec]`. A fetch is triggered from 10 screens (connectivity-guarded, once per instance), not only from splash. A failure is non-fatal and the defaults are used `[dec]`.

## 3. Full lifecycle (capture → result)

### 3.1 IME voice typing
1. The user taps the smartbar mic. If `RECORD_AUDIO` is missing, `jy2.java:34` opens `AudioPermissionActivity` (translucent, singleTask), which requests the permission and calls back into `FlorisImeService.r()` after a grant `[dec]`.
2. `FlorisImeService.r()` (`:735-760`): `isRecognitionAvailable`, then `createSpeechRecognizer(this)` (default service, **not** on-device), `LANGUAGE_MODEL_FREE_FORM`, `LANGUAGE` = active subtype locale tag, `PARTIAL_RESULTS=true` `[dec]`.
3. `yy2.onReadyForSpeech` sets the listening flag and arms a 5,000 ms watchdog (`p0` case 21). `onPartialResults` and `onRmsChanged` are empty `[dec]`.
4. `yy2.onResults` takes `results_recognition[0]`. If `showTranslatorBar` is on, it **replaces** the live-translate buffer (`hh8.a.setValue(text)`, `yy2.java:59`) and runs `as0` → `my3.B0(text, hh8.c, hh8.d)` → `gh8` commit. `gh8` deletes the previous translation's length, so earlier translated output is lost. `hh8.c`/`hh8.d` mirror the prefs `translatorSourceLangCode`/`translatorTargetLangCode` (default en/ur; `y88.java:556-563`, `k32.java:64-71`). Otherwise it calls `commitText(text + " ", 1)`. The result is logged to tag `TextCheck----` `[dec]`.

### 3.2 IME translate-while-typing (the fork's signature feature)
1. `o98.java:1040-1072` (the patched FlorisBoard key dispatch): with the toggle on, character keys go to buffer `hh8.b(text)`, space appends `" "` and delete (`-7`) calls `hh8.c()` `[dec]`.
2. Each change launches coroutine `as0` (cases 2 and 3), which sends **the entire buffer** to E1. There is no debounce and no cancel-previous `[dec]`.
3. `gh8.invokeSuspend` calls `deleteSurroundingText(prevLen, 0)` and then `commitText(newTranslation)`, and stores the new length `[dec]`.
4. Consequences: N keystrokes cause N requests; out-of-order responses can commit a stale translation `[inf]`; a failure erases the prior translation `[dec]`; moving the cursor can make `deleteSurroundingText` remove unrelated text `[inf]`.
5. Toggle lifecycle `[dec]`:
   - `onWindowShown` resets `showTranslatorBar` to OFF every time the keyboard window appears (`FlorisImeService.java:712-722` → `c1`). The toggle stays on across field changes while the keyboard stays visible.
   - The toggle clears buffer `hh8.a` but not the committed length `hh8.b` (`cu0.java:214`), so the next commit can delete that many characters of user text.
   - The buffer is not cleared in `onStartInput`/`onFinishInput`.
   - The chain never reads the password variation, the incognito bit or `NO_PERSONALIZED_LEARNING`.

### 3.3 Text translator
`TranslationScreenRoute`, then source/target from the 103-entry hard-coded list (`ah8`/`bh8`), then the Translate press. That press shows the `translate_button` interstitial if one is loaded (`oh8.java:5111-5361`). Then `nh8.java:59` → `my3.B0`, then the result, then speak (TTS `gi1`/`us1.P` chunker) or share (`ag7.java`, SEND text/plain) `[dec]`. The mic uses `dn3.C` → system RecognizerIntent (`MAX_RESULTS 5`, `PROMPT`), and refuses to start offline `[dec]`.

### 3.4 Camera translator
CameraX `LifecycleCameraController` (`or0.java:98-160`; back camera, front fallback), then capture or gallery, then crop, then GMS Vision `TextRecognizer` via dynamite (`ny1.java:628-657`; an empty result shows `please_capture_clear_image`), then `my3.B0` (`or0.java:174-187`), then the text result. There is no live overlay `[dec]`/`[inf]`. There is also a second capture path: a system camera intent writes `externalCacheDir/temp_image_<ts>.jpg`, and nothing deletes these files (`zb3.n`, `p6:163`) `[dec]`. The camera language picker modes are unreachable (`ch8.java:24-41` only sets modes 0-3), so camera translation is fixed at en→es `[dec]`.

### 3.5 Voice conversation
Left/right mic (`ki1.java:840-851`) launches RecognizerIntent in that side's language. Then `le0.java:105-118` launches `i1` (failed to decompile), which translates via E1 with the direction chosen by `isLeft` (`or0` case 1) `[inf]`. Then `li1.java:100` inserts into Room `conversation_messages`, and tapping a bubble runs `gi1` TTS `[dec]`. **Class sweep update:** the middle step is now `[dec]` from `coverage/recovered/i1.fallback.java:2802-2876`: `or0` → `my3.B0` → `li1` insert. A failed translation is stored as `""`, and no TTS call is made in this step. Rows keep no language pair. The screen is tab 1 of `TranslationScreenRoute` (`nk5:114-127`).

### 3.6 Dictionary
`j32` normalises the query (letters only, lowercased). If offline it clears the search (no local fallback). Otherwise `l32.d` calls E2, then `xt3` maps the response, then the UI shows it, with TTS pronunciation via `j5` `[dec]`.

### 3.7 Stickers
E3 catalog, then E4 image download into a per-pack folder with a `.complete` marker. The IME sticker panel sends via `commitContent`, trying `image/webp.wasticker`, then jpeg, png, webp, gif (`uh.java:60-88`) `[dec]`. The compat-PNG fallback is dead code (`xy.java:390` looks for `image/*` in a list that never contains it), so the fallback always opens a "Share Sticker" chooser. `isPremium` is never read, so every pack is free `[dec]`.

## 4. On-device vs. server-side

| Capability | Where it runs | Tag |
|---|---|---|
| Keyboard, 71 subtype presets, 156 layouts, glide, emoji, clipboard, one-hand | On-device (FlorisBoard) | `[dec]` |
| Word suggestions | On-device: prefix `startsWith` over 7 plain word lists (`am4.java:142-215`), fixed 0.5 score, no bigrams. The only autocorrect is an opt-in "Auto correction" toggle (`enableAutoSuggestion`, default off), which commits the top prefix match on space/punctuation (`se4.java:510-514`) | `[dec]` |
| Voice typing / app mic | Device's default RecognitionService. No `EXTRA_PREFER_OFFLINE`. Whether it actually runs on-device or in the cloud is `[inf]` (item 6) | `[dec]` call / `[inf]` location |
| Translation (all 4 entry points) | **Server**: E1 | `[dec]` |
| Dictionary | **Server**: E2 | `[dec]` |
| OCR | On-device inside Google Play services (dynamite Vision); the model is hosted and managed by GMS | `[dec]` call / `[inf]` model |
| TTS | On-device system TTS, created per screen in a DisposableEffect (`vr6` case 1 via `s65.v`; shut down on dispose; there is no splash pre-warm). Speakers are `gi1`, `us1.P`, `j5` | `[dec]` |
| Stickers | **Server** catalog + images | `[dec]` |
| AI rewrite / chat / LLM | **Absent** | `[dec]` |

**Bundled models: none.** No `.tflite`/`.onnx`/`.pb`/Whisper/Vosk files and no TFL3 or ONNX magic in any asset. The only "model-like" file is `DebugProbesKt.bin` (the coroutines debug agent) `[dec]`. The four native libraries are AndroidX/CameraX: `libimage_processing_util_jni.so`, `libsurface_util_jni.so`, `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so` `[dec]`. **Clone:** no NDK work is needed.

## 5. Monetization & growth

- **Model:** ads only. AdMob next-gen `ads_mobile_sdk` 1.4.0 + UMP 4.0.0, no mediation, no MMP, no `onPaidEvent` revenue logging, **no Play Billing, no paywall** `[dec]`.
- **Units:** 4 production units (interstitial, banner, app-open, native) plus the app ID `ca-app-pub-3051…~…1938`, all delivered through Remote Config defaults. The `ca-app-pub-0000…` string is UMP's placeholder text, not a unit `[dec]`.
- **Interstitials:** 10 placements share one preloaded slot (`dn3.A`): splash, home_word_game, home_main_card, home_sticker_click, home_translation_click, home_dictionary_click, theme_click, save_theme, leave_theme, translate_button. The 30 s cooldown applies only where `capping_enabled=true`, which by default is only `home_word_game`. Each show is preceded by a 1 s "loading ad" dialog. **The Translate button shows an interstitial on every press when one is loaded** `[dec]`; the real frequency is `[inf]` (item 15).
- **Banners:** 4 (home, customization, enable, translation); collapsible/100 dp variants are controlled from RC. **Natives:** 3 live (home, guess-word, settings) + 3 configured but never read. **App-open:** 4 h expiry, skipped on splash, AdActivity and the permission trampoline `[dec]`.
- **In-keyboard native ad** (`FlorisImeService.onStartInputView:570-625`; render in `n21.java:1040-1180`, layout `native_banner_small_compose_admob`). It shows only if the host package is not this app and **not on the hard-coded deny-list**, and `my_keyboard_native_ad_enable && !is_user_premium && PREF_CAN_USER_REQUEST_ADS`. It refreshes every 10 min (values under 1000 are read as minutes, `n21.java:1092`) `[dec]`. The team counts differed (Team 3: 86, Team 5: ~85, Team 1: ~90); the verification pass counted **exactly 86 package literals** (plus the app's own package) at `FlorisImeService.java:586`, and the list is identical, entry for entry, to App H's (`FlorisImeService.java:683` there). The list covers about 55 Google apps, about 29 launchers, `com.android.settings.intelligence` and `com.sh.smart.caller` `[dec]`. Its intent is `[inf]` (reviewer evasion). **Clone:** never ship ads inside the IME window.
- **Premium flag:** `is_user_premium` is read in every ad gate and synced to the IME through SharedPreferences `keyboard_prefs` (`js6.java:290-302`), but **nothing writes `true`** `[dec]`. Billing was probably stripped from the template `[inf]`. (The setter `f05.c` looks empty in jadx, but its bytecode performs a runBlocking DataStore write, so the flags do persist. They just never become `true`.)
- **Forced in-app update** `[dec]`:
  - The Home tab (not splash) calls Play Core `requestUpdateInfo` (`fv0.java:1138-1159`) and launches the flow when an update is available (`c85.java:46-90`).
  - Declining shows "App update is required to use the app" (`tm.java:158`). Its only choices are Update or exit (`finishAffinity`, `ct0.java:52`).
  - IMMEDIATE vs FLEXIBLE `[inf]`.
- **Splash:** 12 s timeout when online (3 s offline or premium), then RC fetch, then the UMP gate, then the splash interstitial (2 s after it loads) `[dec]`. Caveats `[dec]`:
  - The timer restarts in full on every resume.
  - A failed splash-interstitial show requests no new ad.
  - Once consent has been shown, UMP is never queried again.
  - App-open ads have no cooldown (`dt.h` is written but never read).
- **Growth hooks:** 2-step enable-keyboard onboarding; "Rate us" always opens `market://` (no star filter, no In-App Review API); share-app link; one FCM topic `voice_typing_voice_keyboard_app`; feedback `mailto` `[dec]`. Analytics has about 50 events, almost all ad telemetry plus one `screen_view`, with no feature or funnel events `[dec]`.

## 6. AndroidManifest highlights

- **Components:** 6 activities, 14 services, 12 receivers, 4 providers. **Exported (6):** launcher `MainActivity`; `FlorisImeService` (BIND_INPUT_METHOD); `FirebaseInstanceIdReceiver` (c2dm SEND); `SystemJobService`; two DUMP-guarded receivers. No deep-link intent-filters. However:
  - The exported `MainActivity` routes on the `open_screen` string extra (`theme`/`settings`/`sticker`/`keyboard_Language`, sent by the IME; `r79.java:230-260`).
  - The value `language` maps to an **unregistered** route (`l65` = KeyboardTranslationBarLanguageScreenRoute), which would hit `kq4` "Unknown screen" `[dec]`/crash `[inf]`.
  - `MainActivity.onDestroy` calls `Process.killProcess` (`MainActivity.java:384`), which also kills the IME running in the same process `[dec]`/`[inf]`.

  There are also no PROCESS_TEXT/SEND inbound filters, no widgets, tiles, overlay, accessibility, spell-checker or autofill service `[dec]`.
- **IME:** `res/xml/method.xml` declares one `AsciiCapable` subtype with no locale and `supportsInlineSuggestions=true`. `settingsActivity` points to `dev.patrickgold.florisboard.SettingsLauncherAlias`, **which does not exist**: the integrator's grep found it only in `method.xml`. Team 4's note that it "opens MainActivity" is wrong. The gear icon is probably broken `[dec]`/`[inf]` (item 18).
- **Permissions:** dangerous `RECORD_AUDIO`, `CAMERA`, `POST_NOTIFICATIONS`. `READ_BASIC_PHONE_STATE` comes from an SDK. Plus INTERNET, VIBRATE, AD_ID, AdServices, FCM and install referrer. No storage, contacts, location, QUERY_ALL_PACKAGES or BILLING `[dec]`.
- **Providers:** `ClipboardMediaProvider` (not exported, grantUriPermissions); `FileProvider` with broad roots (`files-path "."`, `cache-path "."`, `external-cache-path "."`), grant-only `[dec]`.
- **Flags:** `allowBackup=true` with **no backup rules**, so clipboard history, the user dictionary and the themes/conversation DB go into Auto Backup `[dec]`/`[inf]` (item 8). Clipboard history is off by default (`sz2.java:32`), and clipboard media lives in `noBackupFilesDir` `[dec]`. `keyboard_themes_db` uses destructive migration fallback (`hq1.java:248-250`) `[dec]`. Also `largeHeap=true`, `extractNativeLibs=false`, no NSC.
- **Signing:** see the header. The publisher's privacy policy is on sites.google.com and the feedback address is a Gmail account. The white-label tells are the `com.example…_z` namespace and the `ZTechVoiceTyping` path `[dec]`/`[inf]`.

## 7. Dynamic-analysis findings

**Not run (STATIC-ONLY mode).** No traffic was captured, so `traffic/` is empty for this pass. Nothing would block a dynamic pass: there is no pinning, PairIP, Play Integrity or root check, and a system-store CA on a rooted AVD is enough `[dec]`. There are no prompts to dump because the app has no LLM `[dec]`. Planned money-path and privacy captures, in priority order (Team 5 §10 has the full 10-step plan):
1. Translate mode in a password field: does any E1 request fire? (item 1)
2. Per-keystroke E1 burst: response shape, 429/CAPTCHA, `^~^` round-trip (item 3)
3. Firebase Perf upload: does it contain the E1 query string? (item 2)
4. In-IME ad rendering and suppression across a Google app, a launcher and WhatsApp (item 4)
5. Live RC values and interstitial cadence on the Translate button (items 14 and 15)
6. `stickers.json` capture: image host and `isPremium` behaviour (item 13)

## 8. Privacy bottom line — what leaves the device

| Data | Destination | When | Tag |
|---|---|---|---|
| **Keystrokes typed into any app** (full buffer, in the URL) | translate.googleapis.com | Every key press while the IME translate toggle is on | `[dec]` |
| Voice-typed text | translate.googleapis.com | Only when the translator bar is on | `[dec]` |
| Translator / camera-OCR / conversation text | translate.googleapis.com | Each translate action | `[dec]` |
| Microphone audio | Device RecognitionService (usually Google) | Mic key / mic buttons | `[inf]` (item 6) |
| Dictionary words | freedictionaryapi.com | Each search | `[dec]` |
| IP / UA | myappsdata.site | Sticker screen | `[dec]` |
| Translate URL incl. text (possibly) | Developer's Firebase Perf project | Each E1 call | `[inf]` (item 2) |
| Ad ID, FCM token, telemetry, host-app package (ad decision) | Firebase / AdMob | Continuous | `[dec]` |
| Clipboard history (only if enabled; off by default), learned words, themes, conversations | Google Drive Auto Backup | Backup window | `[inf]` (item 8) |
| AppSet ID, Privacy Sandbox attribution (`registerSource`/`registerTrigger`), Firebase heartbeat, Analytics→Crashlytics breadcrumbs, GMS Vision Clearcut usage logs, `Build.HOST` | Google SDK backends | Continuous / per use | `[dec]` (class sweep) |

- **Logcat leaks in the release build** `[dec]`: full translate URL with text (`urlCheck___`, `my3.java:154`), every voice result (`TextCheck----`), dictionary words (`DictionaryLog`), FCM token, and the host package plus ad decision on every field focus.
- **Sensitive fields:** the translate branch checks no `inputType`/password or `IME_FLAG_NO_PERSONALIZED_LEARNING` flag `[dec]`. Whether upstream FlorisBoard resets the toggle in password fields is unconfirmed (item 1, highest priority). Clips marked `IS_SENSITIVE` are still stored. The decompiled condition that looked inverted on API 33+ is a jadx artefact: `g1.fallback.java:2217-2250` shows a correct check (item 7, closed statically).
- No biometrics, location or contacts. Voice audio is a biometric-adjacent flag and is handled by the system recognizer `[dec]`.

## 9. Secrets found

| Item | Value (redacted 8+4) | Location | Live/exploitable? |
|---|---|---|---|
| Firebase Android API key | `AIzaSyBL…70kE` | `res/values/strings.xml` (`google_api_key`) | A standard client key; whether it is restricted is unknown (item 20). **Not called.** |
| FCM sender ID | `14064649…3929` | `strings.xml` | Public identifier |
| Firebase app ID | `1:140646…4a78` | `strings.xml` | Public identifier |
| Firebase project / bucket | `voice-keyboard-translator-z` / `…firebasestorage.app` | `strings.xml` | No Storage SDK call site; boilerplate `[inf]` (item 21) |
| AdMob app ID | `ca-app-pub-3051…~…1938` | manifest meta-data, `o7.java` | Public by design |

No paid-vendor keys, no `sk-`/bearer/JWT and no hard-coded passwords (`secrets-keys-redacted.txt` lists only the single `AIza` key) `[dec]`. The app avoids paying for translation by using the unofficial endpoint instead of a key. **Disclosure:** no exploitable credential was found, so no vendor notification is required. No extracted key was used against any API. Full values live only in the local workdir (`secrets-candidates.txt`), which is not shared.

## 10. Open items / caveats

| # | Item | Owner | How to close |
|---|---|---|---|
| 1 | Does translate-while-typing stay active and upload/log text in **password / no-learning fields**? Highest priority. **Static (class sweep):** the chain never reads the password, incognito or no-learning signals. The toggle resets to OFF on every `onWindowShown`, but it survives field changes while the keyboard stays visible `[dec]` | Team 5 (Security) + Wave-2 driver | Rooted AVD + mitm; type into a password field with the toggle on; `adb logcat -s urlCheck___` |
| 2 | Does Firebase Performance upload E1 URLs **including** the `q=` query string? | Team 2 (Backend) + Team 5 | Capture `firebaselogging`/Perf batches during translation |
| 3 | E1 response shape, 429/CAPTCHA under per-keystroke load, `^~^` round-trip | Team 2 (Backend) | Dynamic capture; compare input with `&` against output |
| 4 | Confirm in-IME native ad rendering, position, 10-min refresh and suppression in deny-listed packages; (count reconciled by the verification pass: exactly 86) | Team 3 (Monetization) | Device test across a Google app, a launcher and a messenger; count done statically (86) |
| 5 | Is the in-IME ad shown in password fields? **Static answer (class sweep):** the ad gate has no password-field test (it needs only an unlocked user and network) `[dec]` | Team 3 + Team 5 | Device test to confirm |
| 6 | Which RecognitionService runs, and online vs on-device behaviour | Team 4 (Native/UX), dynamic | `settings get secure voice_recognition_service`; airplane-mode test |
| 7 | Clipboard `IS_SENSITIVE` handling on API 33+. **Closed statically:** it is a jadx artefact (`g1.fallback.java:2217-2250`); sensitive clips are still stored | Team 4 | Device test only to confirm storage of sensitive clips |
| 8 | Auto Backup payload (clipboard, user dictionary, `keyboard_themes_db`) | Team 5 | `adb shell bmgr backupnow` + inspect the transport |
| 9 | Voice-conversation chain. **Mostly closed (class sweep):** `or0` → `my3.B0` → `li1` recovered from `i1.fallback.java:2802-2876`; the screen is tab 1 of `TranslationScreenRoute` | Team 4 + Team 2 | Device walk only |
| 10 | Suggestion preload format (`ad3` case 5 failed): rank vs constant values | Team 4 | baksmali; look for the `ime/dict/` const-string consumer |
| 11 | Does the key renderer read `g03` prefs directly or generate a Snygg sheet? **Closed:** it reads them directly (`x98.java:153-205`, `c86`, `bd4`); no sheet is generated `[dec]` | Team 4 | — |
| 12 | Is GMS Vision OCR still operational on current Play services (deprecated API)? | Team 4, dynamic | Run the camera translator; `logcat -s Vision` |
| 13 | Sticker image CDN. (`isPremium` is never read, so nothing is gated `[dec]`) | Team 2 + Team 3 | Capture `stickers.json` |
| 14 | Live Remote Config values, server-side A/B tests | Team 3 | Dynamic RC fetch capture |
| 15 | Real Translate-button interstitial frequency. **Answered statically:** in-app update is forced on the Home tab; first run goes splash → Home or KeyboardEnable; the notification prompt shows once per process on Home | Team 3 | Device walk-through for frequency |
| 16 | Identify `com.sh.smart.caller` on the deny-list | Team 3 | Play Store lookup |
| 17 | FCM payloads and tap behaviour (FCMService ignores data) | Team 2 | Send a test push via the console of our own clone project; observe the target only passively |
| 18 | Settings gear (dangling `SettingsLauncherAlias`): crash or no-op? | Team 4 | Device: Settings → On-screen keyboard → gear |
| 19 | Diff the fork against upstream FlorisBoard (tag around v0.5.x) to list every added class, asset, pref, network call and log call | Team 1 (Manifest) + Team 4 + Team 5 | Tree diff of `dev/patrickgold/florisboard/*` and `assets/ime` |
| 20 | Is Firebase key `AIzaSyBL…70kE` restricted? | Team 5, through the project owner only | No live test against Google APIs |
| 21 | Firebase Storage bucket rules | Team 2, legal sign-off first | Read-only, unauthenticated check only if approved |
| 22 | Supply-chain check that the 4 `.so` files match the upstream AndroidX AARs | Team 5 | `shasum -a 256` against the AAR contents |

**Notes on team conflicts the integrator resolved:** (a) `SettingsLauncherAlias` is missing; Team 1 and Team 5 are right and Team 4 §8 is wrong. (b) `fw9` is an ads `WebViewClient`, not TTS, and `vr6` only pre-warms TTS (Teams 2 and 4 agree). (c) ~~Navigation is Navigation-Compose 2.10.0; Navigation3 is on the classpath but unused `[inf]`.~~ **Corrected by the class sweep:** Navigation3 `NavDisplay`/`NavBackStack` is the navigation system (`ed0.java:176-470`, `cc3.java:2010`). Navigation-Compose is on the classpath but unused. (b) also needs correcting: `vr6` does not pre-warm TTS on splash; TTS is per screen.

## 11. Key files (decompiled)

Paths are relative to `jadx_out/sources/`; `dp/` = `defpackage/`.

| Concern | File(s) |
|---|---|
| IME service, voice, ad gate | `dev/patrickgold/florisboard/FlorisImeService.java` (`r()` 735-760, `onStartInputView` 570-625), `dp/yy2.java`, `dp/jy2.java:34` |
| Mic permission trampoline | `dev/patrickgold/florisboard/AudioPermissionActivity.java` |
| Translate client + parser | `dp/my3.java` (`B0`:150, `o0`:1132) |
| Live translate chain | `dp/o98.java:1040-1072`, `dp/hh8.java`, `dp/as0.java`, `dp/gh8.java`, `dp/k32.java:60-72` |
| Translator / conversation / camera | `dp/oh8.java`, `dp/nh8.java:59`, `dp/ki1.java`, `dp/le0.java`, `dp/i1.java` (failed), `dp/or0.java`, `dp/ny1.java:628` |
| Language lists | `dp/ah8.java`, `dp/bh8.java` |
| In-app STT | `dp/dn3.java:162-200` |
| TTS | `dp/vr6.java:76`, `dp/gi1.java:40-60`, `dp/us1.java:365-393`, `dp/j5.java:340` |
| Dictionary | `dp/a32.java`, `dp/nk6.java`, `dp/l32.java`, `dp/xt3.java`, `dp/j32.java` |
| Stickers | `dp/eo5.java:504-600`, `dp/uh.java:60-88` |
| Suggestions | `dp/am4.java`, `dp/go5.java:45`, `dp/ad3.java` (failed) |
| Themes | `dp/g03.java:152-296` (141 prefs), `dp/o03.java`, `dp/x98.java`/`dp/c86.java`/`dp/bd4.java` (renderers), `dp/oc8.java` (apply; recovered), `dp/eo1.java` (48 presets), `dp/se4.java` (FlorisBoard KeyboardManager; its constructor builds the SoundPool) |
| Ads + RC + consent | `dp/dn3.java`, `dp/js6.java`, `dp/dt.java`, `dp/o7.java`, `dp/ak5.java`, `dp/n21.java:1040-1180`, `dp/wy2.java`, `dp/ds7.java`, `dp/fs7.java` |
| Room / prefs | `dp/rs.java`, `dp/dd0.java`, `dp/li1.java`, `dp/hq1.java`, `dp/zz4.java`, `dp/q91.java` |
| Onboarding | `dp/zt3.java:78-100`, `dp/hr0.java:31-38`, `dp/ct0.java` |
| In-app update / open_screen routing | `dp/fv0.java:1138-1159`, `dp/c85.java:46-90`, `dp/tm.java:158`; `dp/r79.java:230-260`, `dp/ed0.java:176-470`, `dp/uh.java:161-181` |
| Manifest / IME metadata / RC defaults | `jadx_out/resources/AndroidManifest.xml`, `res/xml/method.xml`, `res/xml/remote_config_defaults.xml`, `res/xml/file_paths.xml` |

## 12. Feature table (clone scoring)

D = Difficulty, I = User Impact, M = Monetization, each 1-5. Difficulty assumes the clone starts from upstream FlorisBoard. Scores were assigned by the integrator. The ranked backlog is in `FEATURE_BACKLOG.md` and the machine-readable form in `features.json`.

| Feature | Mechanics (call chain summary) | D | I | M | Dependencies | Differentiation ideas |
|---|---|:-:|:-:|:-:|---|---|
| Core keyboard (FlorisBoard base) | `FlorisImeService` → Compose `sy2` input view; 71 presets, 156 layouts, glide, emoji, clipboard (Room + `ClipboardMediaProvider`), in-keyboard language switch `[dec]` | 3 | 5 | 3 | none (foundation) | Per-locale system subtypes; clipboard excluded from backup + auto-expiry; a working settings gear and a visible privacy badge |
| Onboarding (enable/select) + app language | `ct0` → INPUT_METHOD_SETTINGS; `zt3` 500 ms poll; `showInputMethodPicker` → `hr0` default-IME check `[dec]` | 1 | 4 | 2 | Core keyboard | Funnel analytics per step; try-it-here text field before leaving onboarding; resume-safe recheck |
| Voice typing (IME) | `r()` → `createSpeechRecognizer` → `yy2.onResults` → `commitText(r[0]+" ")`; 5 s watchdog; partials ignored `[dec]` | 2 | 5 | 3 | Core keyboard, permission trampoline | Live partials as composing text + RMS waveform; continuous dictation + spoken punctuation; on-device recognizer preference |
| Translate-while-typing bar | `o98` → `hh8` buffer → `as0` → E1 per keystroke → `gh8` delete+commit `[dec]` | 3 | 4 | 3 | Core keyboard, translation engine | ML Kit on-device (keystrokes never leave the phone); debounce + composing preview; hard-off in password fields |
| Text translator | `oh8` UI → `nh8` → `my3.B0` → TTS/share; 103 hard-coded langs `[dec]` | 2 | 4 | 4 | Translation engine | Auto language detect; history + favourites; PROCESS_TEXT "Translate" entry from any app |
| Voice conversation | `ki1` mic → RecognizerIntent → `i1` → E1 → Room `conversation_messages` → `gi1` TTS `[dec]`/`[inf]` | 3 | 4 | 2 | Text translator, TTS | Face-to-face split screen; auto-speak; offline mode |
| Camera translator | CameraX → crop → GMS Vision OCR → `my3.B0` → text result `[dec]` | 3 | 4 | 3 | Translation engine | ML Kit OCR v2 multi-script; live overlay (v2); share-to-app image ingest |
| Dictionary | `j32` → network gate → Retrofit E2 → `xt3` map → `j5` TTS `[dec]` | 2 | 3 | 2 | Network core, TTS | Offline DB + cache; multi-language; "define selection" from the IME |
| Stickers | E3 catalog → E4 images (`.complete` marker) → IME `commitContent` MIME chain `[dec]` | 2 | 3 | 2 | Core keyboard, our CDN | Own licensed art; premium packs on Pro; GIF search |
| Keyboard themes + editor + sounds | 141 flat prefs (`g03`), read directly by the renderer; Room `keyboard_themes` (44 cols + id, destructive fallback); 48 presets; 5 key groups incl. Sticky; crop; 12-item key-sound picker (SoundPool, maxStreams 1). Key-skin setters have no callers `[inf]` `[dec]` | 4 | 4 | 4 | Core keyboard | Typed ThemeSpec → Snygg sheet; share/import theme codes; premium theme packs |
| Word suggestions / autocorrect | `am4` prefix match over 7 lists, fixed 0.5 score; opt-in "Auto correction" only commits the top prefix match on space `[dec]` | 3 | 4 | 1 | Core keyboard | Frequency trie + Damerau-Levenshtein autocorrect; bigrams + learned words; Hindi and real Arabic lists |
| Guess-word game | Level grid, hints, native ad, capped interstitial `[dec]` | 2 | 2 | 3 | Ads stack | Daily puzzle + streak; rewarded hint; shareable result |
| Settings + rate/share/feedback + push | Settings route; `market://` rate; mailto; FCM log-only `[dec]` | 1 | 2 | 2 | Core keyboard | In-App Review at a happy moment; push deep links; privacy dashboard |
| App-side ads + UMP + RC | `dn3`/`js6`/`dt`/`ak5`; 10 interstitials on 1 slot, 4 banners, 3 natives, app-open `[dec]` | 2 | 1 | 5 | All screens | Global cap on every placement; `onPaidEvent` revenue logging; Remove-Ads/Pro via Play Billing |
| AI rewrite (greenfield, absent in target) | New: smartbar action → our proxy → LLM → composing preview + undo | 3 | 4 | 5 | Core keyboard, backend, billing | Tone presets; translate-and-polish; server-side free quota → Pro |

---

_Report generated from reverse engineering of the artifact above for the purpose of studying **structure and mechanics**. No source code or assets were copied or reused. No PHI encountered (third-party consumer app, user-supplied data)._

---

## Verification pass (2026-10-17)

Fresh-eyes, static-only check of this report, the team reports, `features.json`/`FEATURE_BACKLOG.md`/`BUILD_PLAN.md` and `../COMPARISON.md` against `jadx_out/`. No device, no network, and no extracted key was called.

**What I tried to refute, and the result**
- *Keyless unofficial Google endpoint with a spoofed UA*: **held.** `my3.java:153` builds `translate_a/single?client=dict-chrome-ex…`, with the `&`→`^~^` and `\n`→`~~` swaps; `:154` logs the full URL to `urlCheck___`; `:155` wraps the call in `FirebasePerfUrlConnection.instrument`; `:158` sets `User-Agent: Mozilla/5.0` `[dec]`. `my3` is the only file with a `translate_a` literal.
- *No debounce and no cancel-previous on translate-while-typing*: **held.** `o98.java:1048-1070` sends keys to `hh8.b`/`hh8.c`. `hh8.java:20,29` fire `ev3.w(…, new as0(…))` without keeping or cancelling a Job. The branch checks the toggle pref and never `inputType` `[dec]`.
- *Single-shot voice with a 5 s watchdog*: **held.** `FlorisImeService.java:747` calls `createSpeechRecognizer(this)`. `yy2.java:38,70` post the 5000 ms watchdog, and `:65` calls `commitText(str+" ")` with no restart `[dec]`.
- *No AI, no bundled model, no ML Kit/LLM/Whisper/Vosk/DeepL/MyMemory/Microsoft*: **held.** There is no `com/google/mlkit`. The only keyword hits are false positives (`deeplink`, `getMyMemoryState`) `[dec]`.
- *Ads only; the premium flag is dead*: **held.** There is no `billingclient` package and no BILLING permission. Every `is_user_premium` write (`js6.java:302`, `fs7.java:93,100`) copies a value it has just read, and the setter `f05.c` is empty `[dec]`. *(Class-sweep correction: `f05.c` is only empty in jadx. Its bytecode does a runBlocking DataStore write. The conclusion still holds.)*
- *Dangling `SettingsLauncherAlias`*: **held.** It appears only in `method.xml:3`, not in the manifest.
- Artifact SHA-256, minSdk 26/targetSdk 37, `jadx.log` count 239, 113 failed classes, 12,845 classes and arm64-v8a-only `.so` files: **all re-confirmed.**

**Corrections made**
1. **Deny-list count:** "about 85-90" is now **exactly 86** package literals (plus the app's own package) at `FlorisImeService.java:586`. The list is **identical** to App H's list at `FlorisImeService.java:683` (sorted diff is empty). Breakdown: 51 `com.google.*`, 23 with "launcher" in the name, and 12 others such as Chrome, Play Store, Snapseed, Lawnchair, MIUI/Sony homes, `com.sh.smart.caller` and `is.shortcut`. Updated in the TL;DR, §5, open item 4, `report/antitamper.md`, `report/manifest.md` and `features.json`.
2. **FileProvider roots:** added the missing `external-cache-path "."` (from `res/xml/file_paths.xml`) to §6, `report/antitamper.md` and COMPARISON.
3. **Artifact size:** 13,741,089 bytes is the total of the three split APKs. The XAPK file itself is 13,986,166 bytes, and the SHA-256 is of the XAPK. The header now says so.
4. **Redaction:** `report/monetization.md` printed the full AdMob app ID and all four unit IDs. They are now redacted (`ca-app-pub-3051…/…NNNN`), the same way §9 does it.
5. **DoD:** "Verification pass" is changed from Partial to Yes.

**Secrets and placeholder scan (all .md/.json deliverables in `ai-keyboard/`).** Patterns: `AIza[0-9A-Za-z_-]{35}`, `AQ\.[A-Za-z0-9_-]{20,}`, `sk-[A-Za-z0-9]{20,}`, full `ca-app-pub-`, long hex/base64, `{{` and `TODO`. After the fixes, nothing unredacted remains. The only long hex strings are artifact and signing-certificate SHA digests, which are public. `AIzaSyBL…70kE` matches the real key's first 8 and last 4 characters. There are no `{{` or `TODO` markers.

**Open-item owners.** Every row in §10 (items 1-22) and in each `report/*.md` open-items section has an owner.

**Residual risks.** Everything at runtime is still `[inf]`, including password-field behaviour (item 1, highest priority), whether Perf payloads carry `q=` (item 2), in-IME ad rendering, and live RC cadence. `i1`/`ki1`/`ad3` have still not been recovered. They could be recovered the same way the verification pass recovered App H's `o0`: `jadx --comments-level debug --single-class <cls> base/classes*.dex`.

---

## Exhaustive class sweep (2026-10-17)

Every one of the 3,459 classes the inventory classified as first-party (`coverage/class_inventory.tsv`) was read in 36 chunks (`coverage/sweep/*.md`), together with 138 recovered `.badcode`/`.fallback` files. Coverage is **100%**. Three classes were missing from their sweeps (`lw7`, `t69`, `w25`); the consolidator read them, and all three are library. About 879 classes are really first-party: roughly 267 app, 432 FlorisBoard and 180 R8-merged mixed. The other ~2,580 were library code over-included by the graph closure. Full details are in `CLASS_COVERAGE.md`: totals, method, gap list, recovered classes, the feature → classes map, and the de-duplicated findings and corrections with verification status. Static only; no key was called; IDs are redacted to 8+4.

**Corrections applied in this report** (each re-verified against source or `dexdump` bytecode unless marked):
1. **Navigation:** Navigation3 (`NavDisplay`/`NavBackStack`, 15 routes), not Navigation-Compose. Header and §10(c) updated.
2. **In-app update:** the trigger is the Home tab, and the update is **forced** (an exit-only decline dialog). §5 and item 15 updated.
3. **Autocorrect:** an opt-in "Auto correction" exists. It commits the top prefix match on space. §4 and §12 updated.
4. **`f05.c`:** not empty in bytecode (runBlocking DataStore write). The "premium is dead" conclusion still holds. §5 and the verification-pass note updated.
5. **AdMob test-device ID:** `B3EEABB8…9ECB` is the AdMob SDK's emulator constant, not a developer device. `report/monetization.md:34` now redacts it.
6. **TTS:** created per screen; there is no splash pre-warm. §4 and §10(b) updated.
7. **Voice in translate mode:** a voice result replaces the live-translate buffer. Translator-bar languages are persisted prefs, mirrored into `hh8.c/d`. §3.1 updated.
8. **Live-translate lifecycle:** the toggle resets on `onWindowShown`; the stale `hh8.b` length can delete user text; the chain has no password or incognito check. §3.2 and item 1 updated.
9. **Decompile failures:** 24 first-party/mixed classes failed, not 3, and all are recovered. Another 8 first-party methods failed without being listed, including `s.run()` (in-keyboard banner callbacks). Header updated.
10. **String obfuscation:** "No string encryption" applies to first-party code only; the AdMob SDK obfuscates its strings. Glide, Cronet and WorkManager were added to the stack.
11. **Open items:** 7, 9 and 11 are closed statically; 5, 13 and 15 have static answers.
12. **Smaller corrections:**
    - Room destructive fallback is `[dec]`.
    - E3 uses 60 s timeouts.
    - Clipboard history is off by default, and its media is excluded from backup.
    - Theme numbers corrected: 48 presets, 44 columns + id, 141 `g03` prefs.
    - The exported `MainActivity` accepts the `open_screen` extra. Its `language` value points at an unregistered route.
    - `MainActivity.onDestroy` kills the process.
    - Camera translation is fixed at en→es; a second camera path leaves temp files behind.
    - Conversation chain recovered.
    - The sticker compat path is dead.
    - SDK egress rows added to §8.

**Top new findings for the clone:**
- Forced update with exit-only.
- Keyboard → app routing via `open_screen` (the clone should use typed, validated deep links).
- The live-translate stale-length text-loss bug, and the voice result that overwrites the buffer.
- No cooldown between app-open ads; the splash timer restarts on resume.
- The keyboard-language picker offers only 7 languages (Hindi is mapped but hidden), so most of the 71 subtype presets are unreachable from the app UI.
- The theme editor has a Sticky-key group, 17 colour targets and corner presets 0/12/25.
- Key sounds play with `maxStreams=1`.
- Many inherited FlorisBoard prefs are never read (no devtools mode, no hide-icon switch).

**New features in `features.json`:** `in-app-update`, `open-screen-intent-routing`, `urdu-shift-layout`. `FEATURE_BACKLOG.md` has a scored addendum for them.
