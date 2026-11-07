# com.heri.language.keyboard.voice.typing — Reverse-Engineering Report

**App:** "Voice Keyboard and Translator"
**Artifact:** `Voice+Keyboard+and+Translator_1.4_APKPure.xapk` (XAPK v2: base 13.4 MB + `config.arm64_v8a` + `config.xxxhdpi`)
**SHA-256:** `6cb257d00f89ae872fca48d5e3bf9aa7798685e758b3000b8d04a3cef303c1f4`  ·  **Analyzed (UTC):** 2026-10-17T12:50:09Z → integrated 2026-10-17
**Package / version:** `com.heri.language.keyboard.voice.typing` · versionName 1.4 / versionCode 5 · minSdk 24 / targetSdk 36 (compileSdk 36)
**Stack:** Native Kotlin · **FlorisBoard fork** (`dev.patrickgold.florisboard`, Apache-2.0) · Jetpack Compose everywhere (app screens *and* the keyboard surface) · Navigation 3 with 22 `@Serializable` routes · DI Hilt-style generated graph (names stripped by R8) · DB Room 2.8.4 + DataStore 1.2.1 + one raw SQLite asset · net raw `HttpURLConnection` (translation, sticker images) + OkHttp (stickers JSON) + Retrofit/Gson (dictionary) · R8 map id `53b39b15…`
**Mode:** **STATIC-ONLY.** No device, no mitmproxy, no Frida. There are **zero `[obs]` claims** in this report; everything is `[dec]` or `[inf]`, and every runtime question is a numbered open item in §10.
**Decompilation coverage:** 14,821 classes; jadx 1.5.6 finished with **226 errors across 108 classes** (`jadx-failed-classes.txt`, 22 of them `ads_mobile_sdk/*`). **Did a failure hit a money/core path? Yes, two:** `androidx/lifecycle/o0` (R8 merged the app's coroutine lambdas here; holds the keyboard translation-bar dispatch; recovered in the verification pass with `jadx --comments-level debug --single-class`: no debounce) and the OCR caller of the camera flow (resolved in the exhaustive class sweep: `a2/b` case 6 → recovered `b0/f`). Neither is a revenue path: the ad engine, Remote Config wiring and (absent) billing decompiled cleanly. `[dec]`
**Anti-tamper / attestation:** PairIP **licensing-only** (`com.pairip.licensecheck`, no `libpairipcore.so`, no VM blobs, stock `CoreComponentFactory`). The runbook's "PairIP: PRESENT" is a tier false alarm. No Play Integrity, no App Check, no network-security-config, no certificate pinning, no string encryption, debug logging not stripped. `[dec]`

> **Claim tags (template semantics):** `[obs]` observed in live traffic (none this pass) · `[dec]` decoded from manifest/resources/source · `[inf]` inferred from a tell.
> Wave 1 team reports (`report/*.md`) used `[obs]` for "read directly from source" and `[dec]` for "clone decision". In this integrated report those source reads are re-tagged `[dec]`, and clone decisions are written as **Rec.** lines rather than tagged claims.

---

## Quality Gate

| Check | Result |
|---|---|
| `[inf]` claims on money-path items? | Yes, accepted as limitation: production Remote Config values (ad cadence, capping), keyboard-ad layout, and "no paywall at runtime". All three are open items 4, 16, 17. The *structure* of monetization is fully `[dec]`. |
| Dynamic analysis blocked / static-only? | Static-only by mode. §7 reads "Skipped — AVD lab not available" and lists what that leaves unproven. |
| `[partial]` features from Wave 3? | No Wave 3 per-feature pipeline ran. None left: the `keyboard-translation-bar` gap was closed by the verification pass and the `camera-translation` OCR-caller gap by the exhaustive class sweep (2026-10-17). |
| DoD items satisfied? | See table below; 2 items are N/A, 1 pending. |
| Secrets leak-check | Only redacted forms (`AIzaSyCd…lqfo`, first 8 + last 4) appear in deliverables. No vendor API was called with any extracted value. |

## Definition of Done

| # | Item | Status | Note |
|---|---|---|---|
| 1 | Artifact SHA-256, version, date in header | Done | header |
| 2 | arm64-v8a analyzed (v7a only as noted fallback) | Done | arm64 split merged over base; **no armeabi-v7a split exists**, so no fallback was possible (`report/native.md` §1) |
| 3 | Decompile errors triaged; money-path failures worked around | Mostly | triaged (header); translation-bar gap worked around in the verification pass (open item 1 closed); OCR caller found in the class sweep (item 2 closed) |
| 4 | All 5 static analysis reports complete | Done | `report/{manifest,backend,monetization,native,antitamper}.md` |
| 5 | Flutter: Blutter run | N/A | native Kotlin, not Flutter |
| 6 | Dynamic pass attempted; traffic captured or blockage explained | N/A (mode) | STATIC-ONLY; `traffic/` empty; Wave 2 battle plan in `report/antitamper.md` §8 |
| 7 | Every claim tagged | Done | `[dec]`/`[inf]` only; no `[obs]` claimed |
| 8 | Backlog prioritized with clear ranking formula | Done | `FEATURE_BACKLOG.md` |
| 9 | Caveats converted to tasks with owners | Done | §10, 24 items |
| 10 | Secrets redacted + disclosure note | Done | §9 |
| 11 | Build plan has sprint-by-sprint roadmap | Done | `BUILD_PLAN.md` |
| 12 | Scaffold project compiles | N/A this pass | no Wave 3 scaffold was produced; Sprint 0 of `BUILD_PLAN.md` creates it |
| 13 | Verification pass (fresh eyes) | Done | 2026-10-17, see "Verification pass (2026-10-17)" at the end |

---

## TL;DR — how it actually works

This is a **lightly reskinned FlorisBoard** with a Compose companion app on top, and it has **no backend of its own and no AI**. Every translation in the product — the in-keyboard translate-as-you-type bar, the text screen, conversation mode and camera results — goes to the **keyless, unofficial Google Translate web endpoint** (`translate.googleapis.com/translate_a/single?client=gtx`) with a spoofed `Mozilla/5.0` user agent, no timeout, no retry, no cache and no fallback; any failure silently yields an empty string. `[dec]`

The most counter-intuitive finding is the privacy cost of that design inside a keyboard: with the translation bar on, **every keystroke, backspace and paste re-sends the whole buffer as a GET query string** to that endpoint, the same URL (user text included) is **written to logcat** under tag `urlCheck___`, and the call is traced by Firebase Performance. No password-field guard exists on any translation-bar entry point (keys, backspace, suggestion taps, paste) `[dec]`; whether text actually leaves from a password field is unproven at runtime (open item 3). Speech is the platform `SpeechRecognizer`/`RecognizerIntent`, TTS is platform `TextToSpeech`, OCR is Play services Mobile Vision, and the dictionary is the free `api.dictionaryapi.dev` (English only). `[dec]`

The business model is **ads only, one network** (AdMob Next-Gen + UMP, no mediation, no MMP): 13 interstitial placements fired on *feature entry* with no cooldown by default plus app-open on foreground (both probably never shown, because the empty DataStore setter leaves `can_user_request_ads` false for app-side ads; see §5), and — the strongest tell — a **native ad drawn inside the keyboard while users type in other apps**, hidden on a hard-coded list of **86 host packages** (Google apps, Chrome, Play Store, launchers, but also a caller-ID app and Snapseed; one entry is truncated and never matches). Play Billing 8.3.0 is linked but never called, and the premium flag's setter is an empty method, so premium is permanently off. `[dec]`

---

## 1. Tooling used

jadx 1.5.6 (`/opt/homebrew/bin/jadx`) with `JAVA_OPTS="-Duser.home=<work>/jadxhome"`, resources decoded; arm64-v8a split merged over base by `re-analyze.sh`. `apksigner verify --print-certs -v` on all three splits. SQLite inspection of `assets/phrases_final.db`; magic-byte scan of every asset for ML models (none). No Blutter (not Flutter). No smali/baksmali fallback run yet — that is open items 1–2. No mitmproxy, no Frida, no rooted AVD: the dynamic pass is **unavailable in this mode**, not skipped by choice. `[dec]`

## 2. Backend surface

There is **no first-party API server** and **no auth on any request**. The only developer-owned host serves a static JSON file. `[dec]`

| # | Function | Request | Response / use | Auth | Source | Tag |
|---|---|---|---|---|---|---|
| 1 | All text translation | `GET translate.googleapis.com/translate_a/single?client=gtx&sl={src}&tl={tgt}&dt=t&q={enc}&ie=UTF-8&oe=UTF-8`, header `User-Agent: Mozilla/5.0` | gtx nested JSON; code concatenates `[0][i][0]` | none | `tf/b.java:1673-1705` (`n(str, sl, tl)`), parser `c0()` at `:593` | `[dec]` |
| 2 | Dictionary | `GET api.dictionaryapi.dev/api/v2/entries/en/{word}` (language hard-coded `"en"`) | meanings, definitions, phonetics, synonyms | none | Retrofit `ua/a.java`, client `mk/c.java:101`, calls `db/m.java:86,108` | `[dec]` |
| 3 | Sticker catalog | `GET https://myappsdata.site/heriCornerKeyboard/databases/stickers.json` | JSON array of `StickersModel(id, name, image_List[], file_size, isOffLine, isPremium)` | none | `androidx/lifecycle/r.java:663` (OkHttp singleton, 60 s timeouts) | `[dec]` |
| 4 | Sticker images | `GET` each URL in `image_List` | saved to app-private storage, folder name sanitised to `[A-Za-z0-9_]` | none | `androidx/lifecycle/r.java:622` | `[dec]`; hosts `[inf]` |
| 5 | Remote Config | Firebase fetch, `minimumFetchInterval = 3 s`, before every splash | 94 keys, **all ad config** | FIS token | `ob/e.java:118-208` | `[dec]` |
| 6 | Firebase Installations / Analytics / Crashlytics / Performance / Sessions / FCM | SDK | SDK | `x-goog-api-key` / app id | registrars in manifest | `[dec]` |
| 7 | AdMob + UMP | `googleads.g.doubleclick.net`, `pagead2.googlesyndication.com`, `fundingchoicesmessages.google.com` | ads, consent | ad unit ids | SDK | `[dec]` |
| 8 | OCR model | Play services dynamite module `com.google.android.gms.vision.dynamite.ocr` | downloaded by GMS, not the app | GMS | `gms/internal/vision/i.java:1056,1166` | `[dec]` |

**Engine identification.** gtx is the sole translation engine at **99%** confidence: literal URL, `client=gtx`, UA spoof, and a parser that matches the gtx response shape. The top alternative, the billed Cloud Translation API v2/v3, is refuted: no `key=` parameter, no `/language/translate/v2` path, no OAuth, no API key other than the Firebase one, and no Remote Config switch to another provider. ML Kit Translate is refuted by the absence of any `com.google.mlkit` package. `[dec]`

**Quirks.** `&` is swapped to `^~^` and `\n` to `~~` before encoding; `~~` is restored but `^~^` never is `[dec]`, so text with ampersands may render wrongly `[inf]`. No explicit timeouts on gtx `[dec]`; heavy per-keystroke use risks HTTP 429/CAPTCHA, which the code turns into an empty translation `[inf]`.

**Attestation:** none. No `FirebaseAppCheck`, no `IntegrityManager`, no `X-Android-Cert` headers. `[dec]`

## 3. Full lifecycle (capture → result)

### 3.1 Keyboard translate-as-you-type (the signature feature)
1. User enables the translation bar in the smartbar (Floris pref `keyboard.F`); turning it on is refused offline (`ab/l.java:378`), and `onWindowHidden` turns it off again (`FlorisImeService.java:781-784`), so it is per session. `[dec]`
2. Key press (`bk/t.java:1646,1658` → `ck/a.d`), backspace (`bk/s.java:89,114` → `ck/a.c`) paste (`ij/r0.java:1061` → `ck/a.e`, skipping the locked-device check) or a suggestion tap (`ij/r0.java:181`) edits a Compose `MutableState<String>` buffer instead of the host field. Voice results land here too (`wi/j.onResults` → `ck/a.h/g`). None of these paths checks for a password field. While the bar is on, word suggestions use the bar buffer as their prefix (`pj/d.java:195-198`). `[dec]`
3. `ck/a.g()` cancels the previous job and launches `androidx/lifecycle/o0` case 10 on `Dispatchers.Main + SupervisorJob`. That body failed in the main jadx run; the verification pass recovered it with `jadx --comments-level debug --single-class androidx.lifecycle.o0` (packed-switch case 10 → label `L495`). It contains **no `delay()` (no debounce)**: empty buffer → `ck/a.a("")`; otherwise `withContext(Dispatchers.IO) { ci/s case 1 → tf/b.n(buffer) }`, then commit only if the job is still active **and** `ck/a.b()` still equals the snapshot it translated (stale-result guard) `[dec]`. Cancellation is cooperative and the blocking `HttpURLConnection` call is not interrupted, so every edit still issues a request `[dec]`/`[inf]` (open item 1, closed statically).
4. `ci/s.java:138` calls `tf/b.n(buffer, ck.a.f20550c, ck.a.f20551d)` (defaults `en` → `ur`, then set from Compose state passed in from `xj/d.java:288` at `androidx/lifecycle/r.java:801-822`, not from prefs). URL logged to logcat (`tf/b.java:1678`). `[dec]`
5. `ck/a.a(str)` inside `beginBatchEdit`: `deleteSurroundingText(lastLen, 0)` → `commitText(str)` → `lastLen = str.length`. The host field always shows the latest full translation `[dec]`; if the cursor moved, unrelated text can be deleted `[inf]`.

### 3.2 Voice typing in the keyboard
1. Mic key → `wi/k.l()` (`wi/k.java:225-275`). Missing `RECORD_AUDIO` → start translucent `PermissionActivity` with `FLAG_ACTIVITY_NEW_TASK` and `EXTRA_WAS_TRANSLATION_BAR_OPEN`, return. `[dec]`
2. Audio focus (usage 11, content type speech; never abandoned). `SpeechRecognizer.createSpeechRecognizer(service)` stored in a **static** field (`wi/k.java:250`). Extras: `free_form`, `PARTIAL_RESULTS=true`, 2000 ms complete/possibly-complete silence, `LANGUAGE` = keyboard subtype locale. No `EXTRA_PREFER_OFFLINE`. `[dec]`
3. `wi/j`: `onReadyForSpeech` arms a 3 s no-speech watchdog; `onPartialResults` and `onRmsChanged` are empty; `onResults` commits `text + " "` (or routes to 3.1 step 2); auto-restart after 300 ms; errors 6/7/8 restart, others stop. `[dec]`

### 3.3 Text translator / conversation
1. `MainScreen` card → interstitial (`cj/o.java`) → `LanguageTranslatorScreen` hub → `TextTranslationScreen`. `[dec]`
2. Input by typing, paste or `RecognizerIntent` (`ab/l.java:304`). Online gate `com.bumptech.glide.e.q(context)`; blank/in-flight guard (`vb/l.k()`). `[dec]`
3. Translate button → interstitial (`mb/n0.java:82`) → gtx (`cb/k.java:275`) → result shown; **no history insert** (only conversation writes `translation_history`); empty result → "No translation received.", failure → raw `"Error: " + message` (`androidx/lifecycle/r0.java:117-150`). Speaker → `TextToSpeech.setLanguage` + `speak(QUEUE_FLUSH)`; only `LANG_MISSING_DATA` is handled. `[dec]`
4. Conversation: side A/B `RecognizerIntent` (`k1/u3.java:58`) → `TextDiscription` appended to `vb/l.A` → gtx on IO (`vb/k.java:91`) → list slot updated → `k5/y.java:148` inserts into `translation_history`. Auto-speak unconfirmed `[inf]` (open item 5).

### 3.4 Camera translation
1. Camera card: if CAMERA is already granted → interstitial → `CameraTranslationScreen` (`cb/b.java:69-71`); otherwise permission dialog first, interstitial after the grant (`c1/a.java:236-239`). `[dec]`
2. `mb/h.java` opens `getCameraIdList()[0]`, largest JPEG size, `ImageReader(JPEG, 2)` on a `TextureView`. Shutter (`ab/l.java` case 7): `TEMPLATE_STILL_CAPTURE`, AE on, flash off/single, continuous AF. `[dec]`
3. `b/t1.java` case 5 writes `cacheDir/CamTrans_<ts>.jpg` (wiped on next app start); gallery images use the same screen. Cropping uses the screen's own `nb.k` overlay rect; `ImageCropScreen` is only for keyboard backgrounds (`ab/i.java:51`). `[dec]`
4. `mb/l.java:332` → `cb/g1` → `a2/b` case 6 (network gate) → recovered `b0/f` (decode, ARGB_8888 copy, crop to overlay, GMS `TextRecognizer` wrapped as `mj/s0`, blocks joined with `\n`) → `eb/p.java:135-149`: blank → `please_capture_english_text` toast; else `vb/l.k` gtx. OCR not ready → `please_wait`; exception → raw `"Error: "+msg`. `[dec]` (open item 2 closed)

### 3.5 Stickers
`KeyboardStickersScreen` → GET `stickers.json` → pack preview → download images → keyboard sticker panel → `InputConnectionCompat.commitContent` with MIME probe order `image/webp.wasticker`, jpeg, png, webp, gif; fallback share/copy via FileProvider (`wi/k.java:68-120`). `[dec]`

## 4. On-device vs. server-side

| Capability | Runs | Engine | Tag |
|---|---|---|---|
| Keystrokes, layouts, emoji, clipboard, glide | device | FlorisBoard stock | `[dec]` |
| Word suggestions | device | custom prefix match over 7 merged word lists, weight 1, confidence 0.5; no edit-distance correction, ranking or next-word; opt-in "Auto correction" (`enableAutoSuggestion`, default off) only auto-commits the top candidate; smartbar Autocorrect action is a placeholder toast | `[dec]` |
| Voice typing / in-app voice | system recognizer; cloud allowed (no prefer-offline) | `SpeechRecognizer` / `RecognizerIntent` | `[dec]`; cloud vs local per device `[inf]` |
| Translation (all surfaces) | **server**, Google unofficial | gtx | `[dec]` |
| Phrasebook | device | pre-translated SQLite, 241 phrases × ~43 locale columns | `[dec]` |
| Dictionary | server | dictionaryapi.dev | `[dec]` |
| OCR | device via GMS | Mobile Vision dynamite `ocr` | `[dec]` (caller `b0/f`) |
| TTS | device | platform `TextToSpeech` | `[dec]` |
| Language ID / AI rewrite / LLM | **none** | — | `[dec]` |

**Bundled models:** none. Magic-byte scan of every asset found no TFLite, ONNX, GGUF, Vosk or Whisper data; the only `.bin` is kotlinx-coroutines' `DebugProbesKt.bin`. Verified by bytes, not filenames; nothing was loaded at runtime. `ime/languagepack/.../han.sqlite3` is **0 bytes**. `[dec]`
**Native code:** four AndroidX `.so` files, arm64-v8a only; two (`libimage_processing_util_jni`, `libsurface_util_jni`) are orphans because R8 stripped CameraX. No first-party native code. `[dec]`

## 5. Monetization & growth

**Model: ads only, single network.** `[dec]`

| Element | Finding | Tag |
|---|---|---|
| SDKs | AdMob Next-Gen (sole renderer), UMP, Firebase RC/Analytics/Crashlytics/Perf/Sessions/FCM. **No** mediation, MMP, RevenueCat, rewarded ads. Triage roster missed Crashlytics and Perf. | `[dec]` |
| Units | Publisher `pub-8730…7007`: 1 banner, 1 native, 1 app-open, **1 interstitial shared by all 13 placements**, all ids only in `remote_config_defaults.xml`. | `[dec]` |
| Interstitials | 13 placements (`ob/e.java:241`): splash, apply/leave customization, translator card, text translation, camera, conversation, dictionary, phrasebook, translate_button, customize keyboard (+card), sticker card. Splash interstitial after 2,000 ms (`kb/a.java:796`). **Probably never shown:** `ob/e.a()` reads `can_user_request_ads` from DataStore, which the empty `nb/r.c` never writes, so it defaults to false and gates interstitials, app-open, natives and banners `[dec]` (runtime `[inf]`, open item 4). One static `InterstitialAd` (`rb/g.java`). Cooldown hard-coded 30 s on one global timestamp (`rb/g.java:90`) and only when `*_capping_enabled`, **default false**. No ad ready → nothing shown, load for next time; "loading ad" dialog before show. No paid-event revenue logging. | `[dec]` |
| Splash | 12 s wait when online and not subscribed, else 3 s; RC fetched before every splash. | `[dec]` |
| App-open | foreground return, 4 h cache, shown 200 ms after load, blocked on Splash/PermissionActivity/AdActivity; gated by the same `can_user_request_ads` flag, and RC `app_open_main_id` is never persisted, so the unit id is `""` (`ob/e.java:331-335`, `pb/d.java:74`). | `[dec]` |
| Banners / natives | banner on main, customization, enable screens; native on customization, theme apply, try screens; 9 RC keys dead (`splash_screen_*`, `main_screen_native_*`). | `[dec]` |
| **In-keyboard native ad** | Shown while typing in third-party apps if `keyboard_native_ad_enable && !is_user_premium && can_user_request_ads`; reads the separate `n.d` SharedPreferences copy of `can_user_request_ads`, so it is the one ad that works; cooldown RC `keyboard_native_ad_time` (600,000 ms) but reset to 0 on every `MainActivity` create and started even on load failure; needs user-unlocked + network; AdMob is initialised inside the IME process if needed; binds headline/body/CTA/icon only (no `MediaView`); suppressed for **86 hard-coded host packages** (`FlorisImeService.java:683`, not Google-only, one truncated entry). Looks designed to avoid policy review `[inf]`. | `[dec]` |
| Billing | Play Billing 8.3.0 merged, **zero first-party calls**; no SKU, paywall, price string, free-tier cap. `isUserSubscribed` never written; DataStore setter `nb/r.c` is empty. | `[dec]` |
| Analytics | 14 fixed + ~12 templated events, ad pipeline only; no product events; some names exceed GA4's 40-char limit and are likely dropped `[inf]`. | `[dec]` |
| Growth | 2-step enable onboarding (settings deep-highlight + IME picker; ContentObserver below API 34, 500 ms `ok/a` poll on API 34+), carries banner + native; custom rating dialog → always market link, Settings only; share; mailto feedback; FCM topics `voice_typing_translator_app` / unsubscribe `_test_app` + notification pre-prompt (payloads only logged); Play Core in-app update (`kb/a.java:936-956`). No referral, no In-App Review. | `[dec]` |

## 6. AndroidManifest highlights

- **Components:** 8 activities + 1 alias, 14 services, 12 receivers, 4 providers. Only `MainActivity` (LAUNCHER) is publicly launchable; `FlorisImeService` exported behind `BIND_INPUT_METHOD`. `[dec]`
- **Application:** declared `com.pairip.application.Application` wrapping `com.example.voicetypingkeyboard_s.VoiceTypingKeyboard` (template package left in production). `[dec]`
- **method.xml:** one language-neutral `AsciiCapable` subtype, `supportsInlineSuggestions=true`, `settingsActivity` → `SettingsLauncherAlias`, **which is not declared** — the system IME Settings gear is probably broken `[dec]`/`[inf]`.
- **Languages:** 7 keyboard languages (en, es, fr, pt, ar, ur, id), 103 translation languages, 31 app UI languages; 71 unreachable Floris subtype presets ship. `[dec]`
- **Permissions:** dangerous = `RECORD_AUDIO`, `CAMERA`, `POST_NOTIFICATIONS`. No overlay, storage, location, accessibility, `QUERY_ALL_PACKAGES`, boot receiver. Clean footprint. `[dec]`
- **Inbound integrations:** no deep links, `PROCESS_TEXT`, `SEND` target, widgets, shortcuts or tiles; the exported `MainActivity` does accept an `open_screen` extra (`ab/m.java:36-56`: `add stickers screen`, `Keyboard Languages Screen`, `settings`, dead `language`), so any app can open those screens. Closing `MainActivity` calls `killProcess`, which also kills the IME (same process). `[dec]`
- **`<queries>`:** no `RecognitionService`/`RECOGNIZE_SPEECH` entry while the Dictionary mic is gated on `resolveActivity` (`db/d.java:41`) — may fail on API 30+ `[inf]`.
- **Providers:** FileProvider exposes `external-path "."` (too broad, not exported); `LicenseContentProvider` class shipped but undeclared (dead). `[dec]`
- **Backup:** `allowBackup=true`, no `fullBackupContent`/`dataExtractionRules` → `keyboard_theme_db` (translation history), `clipboard_history` and `clipboard_files` DBs, DataStores and the JetPref prefs file eligible for Auto Backup `[inf]`; clipboard media sits in the no-backup dir; the Floris user-dictionary DB is never created `[dec]`.
- **Signing:** Play App Signing (generic Google DN), RSA 4096, v2+v3 on base; cert SHA-256 `bd273808…3b0dff`, SHA-1 `ff1bdcf2…48e7`; standard Play SourceStamp `3257d599…`; all splits same signer. `[dec]`

## 7. Dynamic-analysis findings

**Skipped — AVD lab not available (STATIC-ONLY mode).** `traffic/` is empty. Consequences, stated rather than guessed past:
1. Production Remote Config values (ad enables, capping, unit overrides) are unknown; §5 describes code defaults only (open item 4).
2. Statically there is no debounce (open item 1, closed by the verification pass); the real request rate per keystroke and gtx throttling under that load are still unmeasured (open item 14).
3. Whether the system recognizer sends audio to the cloud on a given device is unproven (open item 6).
4. Whether the translation bar fires inside password fields is unproven and is the highest-severity unknown (open item 3).

There are **no prompt templates** to dump: the app contains no LLM call. The Wave 2 plan (rooted API 34 AVD, CA in system store because no NSC, plain Frida, PairIP bypass by Play install or `LicenseClient.checkLicense` hook, hooks H1–H7 on `tf.b.n`, `ck.a.*`, `SpeechRecognizer.startListening`, RC getters, prefs, `TextRecognizer`) is in `report/antitamper.md` §8. `[dec]`

## 8. Privacy bottom line — what leaves the device

| Data | Leaves? | To whom | Tag |
|---|---|---|---|
| **Typed text with translation bar on** (keystrokes, backspaces, pastes) | Yes, whole buffer per edit, in the URL query string | Google gtx (consumer front end, no contract) + Firebase Perf trace | `[dec]` |
| Typed text, translation bar off | No network sink found | — | `[dec]` |
| Voice audio | Via system recognizer, cloud allowed | device's recognition service, usually Google | `[inf]` |
| In-app / conversation / camera-OCR text | Yes | gtx | `[dec]` |
| Dictionary words | Yes | api.dictionaryapi.dev (third party) | `[dec]` |
| Camera frames | No upload found; OCR via GMS on device | — | `[inf]` |
| Clipboard history | No network; Room `clipboard_history` (history off by default, 20 items), Auto Backup eligible | Google Drive backup | `[dec]`; backup `[inf]` |
| Analytics | constant ad/lifecycle strings, no typed text | Firebase Analytics | `[dec]` |
| Crash, perf, ad requests (AD_ID, consent, device signals), FCM token | Yes | Crashlytics, Perf, AdMob (including from inside the IME), FCM | `[dec]` |
| Local logcat | full gtx URL with user text (`urlCheck___`), every host package (`mAds_`), full FCM token, RC fetch values (`newFetchValue___`), sticker list (`checkData____`), `suggestionCheck___`, `Vibrator` per keypress | adb / bug reports / OEM collectors | `[dec]` |

Biometric: none. Location: none (location libs bundled transitively, no permission). Voice: yes, via platform recognizer. **Password fields:** no guard on any translation-bar entry point `[dec]`; runtime egress unproven (open item 3).

## 9. Secrets found

| Item | Redacted | Location | Assessment |
|---|---|---|---|
| Firebase / Google API key | `AIzaSyCd…lqfo` | `res/values/strings.xml:144` (`google_api_key`) | Standard Firebase client key, public by design; abuse limited to quota on project `voice-ty…rd-s` if unrestricted `[inf]`. **Not called.** |
| Firebase app id / sender | `1:988426…0c9e` / `98842667…2571` | `strings.xml` | public identifiers `[dec]` |
| Storage bucket | `voice-ty….app` | `strings.xml` | no Storage SDK call site `[dec]` |
| AdMob app id + 4 unit ids | `ca-app-p…0830`; units `…9004`, `…8142`, `…7927`, `…0666` | `strings.xml:21`, `remote_config_defaults.xml` | public by nature `[dec]` |

There is **no translation, speech or AI vendor key** because the endpoint needs none. `[dec]`
**Disclosure note:** nothing here is a confidential credential, so no vendor notification is warranted. No extracted value was used against any API; key-restriction checks (open item 13) are to be done only on the clone's own Firebase project. No full values are retained in any deliverable.

## 10. Open items / caveats

| # | Item | Owner | Resolving action |
|---|---|---|---|
| 1 | ~~Translation-bar debounce and dispatch in failed class `androidx/lifecycle/o0` case 10~~ **CLOSED (verification pass 2026-10-17):** no debounce; cancel-previous + buffer-equality stale guard; request on IO per edit (see §3.1 step 3) | Integrator (static) | Done via `jadx --comments-level debug --single-class`; optional Wave 2 hook H1 to measure timing |
| 2 | ~~Camera OCR call site~~ **CLOSED (class sweep 2026-10-17):** `mb/l:332` → `cb/g1` → `a2/b` c6 → `b0/f` (recovered) → `eb/p` | Native/UX | done statically |
| 3 | **Does the translation bar send text from password fields?** (high severity if yes) | Security (T5) + Native/UX | Wave 2 hook H2 on a password `EditText` |
| 4 | Production Remote Config values and real ad cadence | Backend + Monetization | one capture of the RC fetch / hook H4 |
| 5 | Conversation mode auto-speaks the translation? | Backend | runtime walk-through |
| 6 | Recognizer: cloud vs on-device audio; Dictionary mic on API 30+ without `<queries>` (M-1) | Native/UX | airplane-mode test; API 30+ device |
| 7 | System IME Settings gear broken by undeclared `SettingsLauncherAlias` (M-2) | Native/UX | tap gear on device |
| 8 | ~~Splash → Setup vs Main routing~~ **CLOSED:** Main only if IME enabled **and** selected (`cb/o2.java:118-120`) | Native/UX | done statically |
| 9 | FCM: topics found (`u/b.java:422`, `u/c.java:407`); `onMessageReceived` only logs, so no custom payload keys or deep links in code. Remaining: what the console sends (default notification only) | Backend | send test push |
| 10 | Auto Backup really includes `clipboard_history` / `clipboard_files` / `keyboard_theme_db` (user-dictionary DB never created) (M-5) | Security (T5) | `bmgr backupnow` + inspect |
| 11 | Live shape of `stickers.json` and image hosts | Backend | one GET in the lab (not from production clients) |
| 12 | ~~`cb/k` default-branch caller~~ **CLOSED:** text screen; camera uses `eb/p` → `vb/l.k` | Backend + Native/UX | done statically |
| 13 | Firebase key restriction model | Security (T5) | configure and verify on the **clone's own** project only |
| 14 | gtx throttling under typing load (429/CAPTCHA → empty output) | Backend | lab measurement |
| 15 | ~~Floris clipboard history DB file name~~ **CLOSED:** `clipboard_history` (`o0.fallback.java:1411-1417`); media metadata `clipboard_files` (`ci/s.java:148`) | Backend (low) | done statically |
| 16 | No paywall / purchase flow at runtime | Monetization | UI walk + hook H5 |
| 17 | Keyboard native ad position/size/close affordance; native types 1 vs 2; any non-package hide rule | Native/UX | screenshots in lab |
| 18 | Crashlytics/Perf actually collecting; do Perf traces carry the `q=` text? | Security (T5) | mitm `firebaselogging-pa` payload |
| 19 | PairIP on sideload: paywall vs `System.exit(0)` killing the IME mid-typing | Security (T5) | sideload test |
| 20 | Does the user dictionary ever reach the suggestion strip? | Native/UX | type a user word on device |
| 21 | ~~ImageCropScreen use~~ **CLOSED:** keyboard backgrounds only (`ab/i.java:51`) | Native/UX | done statically |
| 22 | Blur fallback below API 31 and theme render cost on low-end devices | Clone engineer | test on API 28–30 device in clone |
| 23 | Privacy policy text vs actual flows (keystrokes to gtx, dictionaryapi.dev) | Monetization/Legal | read the Sites policy page |
| 24 | ~~Verification pass (fresh eyes) over all deliverables~~ **DONE 2026-10-17** | Integrator | see end of report |

## 11. Key files (decompiled)

Paths under `jadx_out/sources/` unless noted.

| Concern | File |
|---|---|
| IME service, ad eligibility, 86-package list | `dev/patrickgold/florisboard/FlorisImeService.java` (`:415/579` inline suggestions, `:672-717` ad gate, `:683` list) |
| IME base (InputMethodService + LifecycleOwner) | `kj/b.java` (lifecycle wiring in `onCreate`/`onWindowShown`/`onWindowHidden`; `:83-93` is owner-tag setup) |
| Keyboard voice | `wi/k.java:225-290`, `wi/j.java`, `wi/h.java` |
| Translation bar buffer and commit | `ck/a.java`; dispatch `ci/s.java:138`; body `androidx/lifecycle/o0.java` case 10 (recover with `jadx --comments-level debug --single-class androidx.lifecycle.o0`) |
| gtx client + parser | `tf/b.java:593`, `:1673-1705` |
| Translator / conversation VM | `vb/l.java`, `vb/k.java:60-95`, `cb/k.java:265-275`, `k5/y.java:148` |
| In-app STT / TTS | `ab/l.java:261-310`, `db/d.java:37-41`, `k1/u3.java:58`, `mb/x0.java:435`, `mb/c0.java:439`, `mb/l.java:678` |
| Camera2 + capture + OCR | `mb/h.java`, `ab/l.java` case 7, `b/t1.java` case 5; caller `mb/l.java:332` → `a2/b.java:192-207` → `b0/f` (recovered, `coverage/recovered/`) → `eb/p.java:135-149`; Mobile Vision `mj/s0.java` |
| Dictionary | `ua/a.java`, `mk/c.java:96-140`, `db/m.java:86,108` |
| Stickers | `androidx/lifecycle/r.java:622-720`; commit `wi/k.java:68-120` |
| Suggestions | `pj/d.java:174-262`, `k5/u0.java:819-850`, `nj/e0.java:83` |
| Themes / sounds | `pk/a.java` (background enum), `vj/a.java` (SoundPool), `cb/n.java`, `eb/s.java` |
| Room / DI / prefs | `dj/h.java`, `oa/d.java:40-70`, `nb/r.java` + `nb/s.java` (no-op setter), `n/d.java:929` |
| Phrasebook | `j8/b.java:346-375`, `ra/f.java`; asset `resources/assets/phrases_final.db` |
| Ads | `ob/e.java` (RC/ad state), `rb/g.java` (interstitial engine, `:90` cooldown), `pb/d.java` (app-open), `nb/f.java`, `sb/c.java`, `cb/n2.java:485-530` (keyboard ad refresh), `kb/a.java` + `kb/g.java` (splash) |
| Analytics | `tb/a.java` |
| Onboarding / settings | `cb/x.java:31-50`, `ok/a.java:88-140`, `ab/f.java`, `cb/f2.java` |
| PairIP | `com/pairip/application/Application.java`, `com/pairip/licensecheck/LicenseClient.java:53-60,105` |
| Routes | `a1/m.java:59-93`, `ab/*` |
| Manifest / config | `resources/AndroidManifest.xml`, `resources/res/xml/{method,remote_config_defaults,file_paths}.xml`, `resources/res/values/strings.xml` |

## 12. Feature table

Scores 1–5 (D = difficulty, I = user impact, M = monetization). Full descriptions in `features.json`; ranking in `FEATURE_BACKLOG.md`.

| # | Feature | Mechanics (call chain summary) | D | I | M | Depends on | Differentiation ideas |
|---|---|---|:-:|:-:|:-:|---|---|
| 1 | **Core keyboard (IME)** | `FlorisImeService` (`kj/b` IME+Lifecycle owner) → `wi/p` ComposeView → Floris layouts/Snygg/emoji/clipboard Room; 7-language picker over `assets/ime/dict` `[dec]` | 4 | 5 | 3 | — | clean upstream fork with real per-locale subtypes and fixed settings alias; sensitive-field mode; backup exclusions |
| 2 | **Voice typing in keyboard** | mic → `wi/k.l()` → `PermissionActivity` if needed → static `SpeechRecognizer` → `wi/j.onResults` → `commitText` / `ck/a` → restart 300 ms `[dec]` | 2 | 5 | 3 | 1 | live partials + waveform; on-device recognizer; premium offline engine |
| 3 | **Translate-as-you-type bar** | key/paste/voice → `ck/a` buffer → cancel+launch `o0` c10 (no debounce, recovered in verification pass) → `ci/s` on IO → `tf/b.n` gtx → stale guard → `deleteSurroundingText`+`commitText` `[dec]` | 3 | 5 | 4 | 1, 4 | on-device ML Kit, debounce + cache; `setComposingText`; premium cloud tier |
| 4 | **Text translator** | hub card → interstitial → `TextTranslationScreen` → RecognizerIntent/paste → online gate → `cb/k` gtx → result (not saved to history); TTS `[dec]` | 2 | 4 | 3 | — | offline packs; history management; TTS data install |
| 5 | **Conversation mode** | side mic `k1/u3` → `vb/l.A` → `vb/k` gtx (IO) → list update → `k5/y` history insert `[dec]`; auto-speak `[inf]` | 3 | 4 | 3 | 4 | hands-free turn-taking; face-to-face split; transcript share |
| 6 | **Camera translation** | Camera2 `mb/h` → capture `ab/l` c7 → `CamTrans_*.jpg` → `mb/l` → `a2/b` c6 → `b0/f` overlay crop + Mobile Vision → `eb/p` → gtx `[dec]` | 3 | 4 | 3 | 4 | CameraX + ML Kit v2 overlay; live mode; non-Latin scripts |
| 7 | **Offline phrasebook** | categories → `ra/f` raw query on `phrases_final.db` column per locale → TTS `[dec]` | 2 | 3 | 2 | 4 | own phrase set; romanisation; insert from keyboard |
| 8 | **Dictionary** | `DictionaryScreen` → Retrofit `ua/a` → dictionaryapi.dev `en` → TTS `[dec]` | 1 | 2 | 2 | — | cache + offline WordNet; multi-language; long-press definition in keyboard |
| 9 | **Themes + customizer + sounds** | theme screens → 24-field Room `KeyboardThemeModel` + `BackgroundTheme(type,value,opacity,blur)` → Floris prefs → IME render; `vj/a` SoundPool `[dec]` | 3 | 4 | 4 | 1 | original art + live preview; premium packs; stable sound ids |
| 10 | **Sticker packs** | `stickers.json` (myappsdata.site) → download → sticker panel → `commitContent` MIME probe → share fallback `[dec]` | 2 | 3 | 2 | 1 | own signed CDN manifest; photo-to-sticker; licensed GIF search |
| 11 | **Suggestions / autocorrect** | `pj/d` prefix match over merged map (`k5/u0`), confidence 0.5; opt-in auto-commit of top candidate (`enableAutoSuggestion`, default off); no edit-distance correction `[dec]` | 4 | 4 | 1 | 1 | per-locale frequency dicts + real autocorrect; bigram prediction (emoji `:` suggestions already exist) |
| 12 | **Onboarding + settings + growth** | Splash (UMP, RC, 3/12 s) → `cb/x` enable/select with `ok/a` 500 ms poll → test screen; Settings `cb/f2` `[dec]` | 1 | 4 | 2 | — | short splash + auto-advancing guide; In-App Review; product funnel analytics |
| 13 | **Ad stack** | `ob/e` RC → `rb/g` interstitial engine, `pb/d` app-open, `nb/f`/`sb/c` banner/native, `cb/n2` in-keyboard native `[dec]` | 2 | 1 | 5 | 12 | unit per placement + RC cooldowns; no ads in IME; remove-ads subscription |
| 14 | **PROCESS_TEXT / share-to-translate** (greenfield) | absent in winner `[dec]`; new activity → translation core | 1 | 3 | 2 | 4 | in-place replace when allowed; QS tile; widget |
| 15 | **AI rewrite in smartbar** (greenfield) | absent in winner `[dec]`; smartbar action → own `/v1/rewrite` proxy | 3 | 4 | 5 | 1, 13 | dictate-then-polish; server-side quota + subscription; never in sensitive fields |
| 16 | **Emoji panel** (NEW, sweep) | Floris emoji palette (`mj/*`, `lj/*`): categories, skin tones, recents (hidden while device locked, `lj/l.java:309`), `:` emoji suggestions (`mj/p0`) `[dec]` | 2 | 4 | 1 | 1 | emoji search + kitchen; per-app recents |
| 17 | **Clipboard suggestions + history** (NEW, sweep) | Floris clipboard: Room `clipboard_history` (off by default, 20 items), smartbar clip suggestion with email/URL/phone extraction and sensitive-clip masking (`nj/x.java:66-86`, `nj/i.java:37`) `[dec]` | 2 | 3 | 1 | 1 | pinned snippets; auto-expiry; exclude from backup |
| 18 | **Smartbar quick actions** (NEW, sweep) | custom row of up to 3 actions + fixed Sticker/Language/Translate buttons, default voice/clipboard/settings (`uj/l.java:29-39`); Autocorrect action is a placeholder toast (`gq/h.java:67-72`) `[dec]` | 2 | 3 | 2 | 1 | user-arranged toolbar; AI action slot |
| 19 | **Color picker** (NEW, sweep) | `za/*` bottom sheet: wheel + bitmap sampler (tolerance 25), 8 recent colors (`color_picker__recent_colors`) `[dec]` | 1 | 2 | 1 | 9 | eyedropper from wallpaper; palette extraction |
| 20 | **Push notifications** (NEW, sweep) | FCM topic subscribe `voice_typing_translator_app` (`u/b.java:422`) from `MainActivity:193` via `cd/d`; `onMessageReceived` only logs; POST_NOTIFICATIONS pre-prompt on API 33+ `[dec]` | 1 | 2 | 2 | 12 | segmented, deep-linked re-engagement; opt-in per topic |
| 21 | **In-app update** (NEW, sweep) | Play Core `AppUpdateInfo` (`kb/a.java:936-956`) → `ij/i0` c7 starts flow when available; callers `gb/t.java:190`, `bk/o.java:430` `[dec]` | 1 | 2 | 1 | 12 | flexible update with RC-driven min version |

**Rec.** (cross-cutting, from all five teams): do not ship gtx; do not log user text; no ads inside the IME; narrow FileProvider; add backup rules and an NSC; keep FlorisBoard Apache-2.0 NOTICE; write original phrase set, art and sounds — do not reuse `phrases_final.db`, `keyboard_bg*`, `keybg*` or the MP3s.

---

_Report produced by reverse engineering the artifact above to study **structure and mechanics** for a legally distinct clone. No source code, assets, string tables or language catalogs were copied or reused. No PHI encountered (third-party consumer app, user-supplied data)._

---

## Verification pass (2026-10-17)

Fresh-eyes, static-only check of this report, the team reports, `features.json`/`FEATURE_BACKLOG.md`/`BUILD_PLAN.md` and `../COMPARISON.md` against `jadx_out/`. No device, no network, and no extracted key was called.

**What I tried to refute, and the result**
- *Keyless gtx endpoint with a spoofed UA, logged and Perf-traced*: **held.** `tf/b.java:1673-1682` (`client=gtx`, `urlCheck___` log, `FirebasePerfUrlConnection.instrument`, `User-Agent: Mozilla/5.0`) `[dec]`. `tf/b` is the only `translate_a` call site.
- *Continuous voice loop*: **held.** `wi/k.java:250` stores the recognizer in the static field `FlorisImeService.f26155t`. `:252-253` set 2000 ms silence extras. `wi/k.a` restarts after 300 ms, and `wi/k.b` runs a 3000 ms watchdog. `wi/j.onResults` always restarts; `onError` restarts on 6/7/8 and stops otherwise `[dec]`.
- *Cancel-previous on the translation bar*: **held.** `ck/a.java:123-129` calls `u1.e(null)` on the old Job and then launches `o0(…,10)` `[dec]`.
- *86-package in-IME ad deny-list*: **held.** There are exactly 86 literals at `FlorisImeService.java:683`, plus the app's own package. The list is identical to App L's.
- *Billing linked but dead*: **held, and stronger than stated.** R8 left only `ProxyBillingActivity`/`V2` under `com/android/billingclient`. The `BillingClient` API itself is stripped, so no first-party call is possible. The setter `nb/r.c` is empty `[dec]`.
- *PairIP licensing only*: **held.** There is no `libpairipcore.so`. `LicenseClient.java:57-60,525` keeps the `System.exit(0)` exit action, scheduled after 30 s `[dec]`.
- *Raw Camera2, not CameraX*: **held.** `mb/h.java` uses `getCameraIdList`/`ImageReader`/`CameraDevice`, and no first-party code imports `androidx.camera`.
- *Navigation 3*: **held.** `d6/e` is a `@Serializable` snapshot-state `List<NavKey>` back stack used by `kb/*` and `pb/d`.
- *No AI, no ML Kit, no bundled model*: **held.**
- SHA-256, minSdk 24/targetSdk 36, 226 errors, 108 failed classes, 14,821 classes and arm64-v8a-only `.so` files: **re-confirmed.**

**Corrections made**
1. **Open item 1 is closed statically.** The failed class `androidx/lifecycle/o0` was re-decompiled with `jadx --comments-level debug --single-class androidx.lifecycle.o0 base/classes*.dex`. In the packed switch, case 10 goes to `L495`, and that body has **no `delay()`, so there is no debounce**. An empty buffer commits `""`. Otherwise it runs `withContext(Dispatchers.IO){ ci/s case 1 → tf/b.n }` and commits only if the coroutine is still active and `ck/a.b()` still equals the translated snapshot (a stale guard). The blocking `HttpURLConnection` call is not interrupted by cancellation, so every edit still issues a request. Updated in the header, Quality Gate, §3.1, §7, §10, §11, §12, DoD 3/13, `features.json`, `FEATURE_BACKLOG.md`, `BUILD_PLAN.md` and COMPARISON. Open item 2 (the OCR caller) is not in `o0` and stays open.
2. **Team-report inconsistencies fixed:** `report/native.md` said routing used Navigation-Compose 2.9.6; it uses Nav3. `report/manifest.md` row 7 said "CameraX capture"; it is raw Camera2.
3. **Redaction:** `report/monetization.md` printed the full publisher ID, AdMob app ID and unit IDs, including in headings. §5 of this report printed the full publisher ID. `report/backend.md` printed the full FCM sender ID. All are now redacted to the 8+4 / last-4 form.
4. **COMPARISON corrections:** H's debounce is now resolved. App L's deny-list is exactly 86 and identical to H's. L's FileProvider gains `external-cache-path`. The claim that minSdk 24 "matches playbook defaults" was wrong: `ANDROID_FAST_CLONE_PLAYBOOK.md` uses minSdk 26. The recommendation itself still stands.
5. **DoD:** item 13 is Done; open item 24 is Done.

**Secrets and placeholder scan.** Same pattern set as App L's pass. After the fixes there are no unredacted keys. `AIzaSyCd…lqfo` matches the real key's first 8 and last 4 characters. The only long hex strings are public SHA digests. There are no `{{` or `TODO` markers.

**Open-item owners.** Every row in §10 (1-24), in each `report/*.md` open-items table and in COMPARISON's open questions (1-9) has an owner.

**Residual risks.** These are still unproven at runtime: password-field egress (item 3, highest severity), Perf `q=` capture (18), production RC cadence (4), the PairIP sideload kill (19), and whether app-side ads are really gated off (4). The OCR call site (2) was closed statically by the class sweep. Request rate under typing load and gtx 429 behaviour (14) are unmeasured.

---

## Exhaustive class sweep (2026-10-17)

Every first-party and FlorisBoard class was read: **1,254 / 1,254 (100%)**, 488 first-party plus 766 Floris, in 12 chunks, including the 25 jadx-failed app/Floris classes recovered as badcode and fallback copies. 13,567 library classes were not swept (by design); a marker scan found two library-bucket classes holding app code (`u/b`, `u/c`, FCM topics). Method, per-bucket totals, gaps, the feature → classes map and all findings with evidence: `CLASS_COVERAGE.md`. Static only; no device, network or vendor API was used.

**Reclassification after reading:** 416 app, 543 Floris, 188 mixed R8 lambda holders, 107 actually library (false positives of "when unsure, first-party").

**Top new findings** (all `[dec]` unless marked)
1. **No password-field guard on any translation-bar entry point.** This covers key presses, backspace, suggestion taps and paste. Glide typing and suggestions do check for password fields. Open item 3 is now a confirmed static gap; runtime egress is still unproven.
2. **App-side ads are probably never shown.** The empty `nb/r.c` leaves `can_user_request_ads` unset in DataStore, and the reader defaults it to false. That gates interstitials, the splash interstitial, app-open, natives and banners. Only the in-keyboard native ad, which reads the `n.d` SharedPreferences copy, works. Runtime confirmation depends on open item 4 `[inf]`.
3. **The keyboard-ad cooldown is reset on every app open** and also starts when an ad fails to load. AdMob is initialised inside the IME process. The 86-entry deny list is not Google-only, and one entry is truncated.
4. **Closing the app kills the keyboard.** `MainActivity.onDestroy` and the double-back exit call `killProcess`, and the IME runs in the same process.
5. **The camera OCR chain was found** (`mb/l` → `a2/b` c6 → `b0/f` → `eb/p`), and `ImageCropScreen` is used for keyboard backgrounds only. Camera backgrounds return only the thumbnail, which is upscaled to 800 px.
6. **Text and camera translations are not saved to history.** Only conversation mode writes `translation_history`.
7. **Storage map:** `keyboard_theme_db`, `clipboard_history`, `clipboard_files`, the JetPref prefs file and two DataStores. The Floris user-dictionary DB is never created. `cacheDir` is wiped on every start, and background JPEGs are never cleaned up.
8. **`MainActivity` accepts an `open_screen` extra** (stickers, keyboard languages, settings) from any app.
9. **More release logging:** host packages, the full FCM token, Remote Config values, the sticker list, suggestion debugging and per-key vibrator logs.
10. **Undocumented features:**
    - opt-in auto-commit "Auto correction" (the smartbar Autocorrect action is only a placeholder)
    - clipboard suggestions with sensitive-clip masking
    - emoji `:` suggestions
    - a custom smartbar row
    - a color picker
    - Play Core in-app update
    - FCM topics with no payload handling
    - Urdu mode

**Corrections applied in this report**
- **Header and Quality Gate:** the camera gap is closed.
- **§3.1:**
  - the bar resets when the keyboard is hidden and refuses to turn on offline
  - suggestion taps are a fourth input path
  - paste skips the locked-device check
  - languages come from Compose state, not prefs
- **§3.3:** text translations are not inserted into history.
- **§3.4:**
  - the permission and interstitial order depends on whether CAMERA is already granted
  - cropping uses the screen's own overlay, not `ImageCropScreen`
  - the OCR chain is documented, and an empty result shows `please_capture_english_text`
- **§4:**
  - autocorrect is described precisely
  - the OCR caller is now `[dec]`
- **§5:**
  - the 13 placements are listed, including splash and translate_button
  - the splash interstitial waits 2,000 ms
  - app-side ads are gated off
  - the app-open unit id is never persisted
  - the 10-minute keyboard-ad cooldown is reset on app open
  - the 500 ms poll runs only on API 34+
  - in-app update was added
- **§6:**
  - inbound `open_screen`
  - `killProcess`
  - backup file list
- **§8:**
  - clipboard is now `[dec]`
  - logcat tags
  - password guard is now `[dec]`
- **§10:**
  - items 2, 8, 12, 15 and 21 are closed
  - item 9 is narrowed
  - item 10 is re-scoped
- **§11:** the `kj/b` and camera rows.
- **§12:**
  - rows 4, 6 and 11 corrected
  - new rows 16-21: emoji panel, clipboard suggestions, smartbar quick actions, color picker, push notifications, in-app update
- **Elsewhere:**
  - `features.json` now has 21 features
  - `FEATURE_BACKLOG.md` is re-ranked
  - **Rejected sweep claim:** that clipboard history is never persisted. The recovered `androidx/lifecycle/o0` builds `clipboard_history`.

**Gaps that remain:**
- 17 method-level decompile failures, none of them on the translation egress path `[inf]`
- library classes with no marker strings
- the runtime questions in open items 3, 4, 6, 18 and 19
