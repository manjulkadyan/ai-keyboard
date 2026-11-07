# Backend & API Surface: Voice Keyboard and Translator

**Target:** `com.heri.language.keyboard.voice.typing` v1.4 (versionCode 5), sha256 `6cb257d0…c1f4`. Native Kotlin. Fork of FlorisBoard (Apache-2.0), Jetpack Compose, R8 (map id `53b39b15…`).
**Pass type:** STATIC-ONLY. No network call was made to any host in this document, and the extracted key was never used.
**Evidence tags:** `[obs]` means read directly from decompiled code or a packaged resource. `[dec]` means a design decision or recommendation for the clone. `[inf]` means inferred from naming, context or SDK conventions. Every `[inf]` that needs runtime proof is listed in §11.
**Paths** are relative to `WORKDIR = voicetranslator_work/`. Sources are under `jadx_out/sources/` and resources under `jadx_out/resources/`.

---

## 0. Headline

1. **No paid or AI backend.** Translation uses the unofficial, keyless Google Translate web endpoint (`client=gtx`). Speech-to-text is the platform `SpeechRecognizer` or `RecognizerIntent`. TTS is the platform `TextToSpeech`. Dictionary lookups go to the free `api.dictionaryapi.dev`. There is no LLM, no ML Kit Translate, and no cloud STT. `[obs]`
2. **One developer-owned host that triage missed:** `https://myappsdata.site/heriCornerKeyboard/databases/stickers.json`, the sticker catalog. The path segment `heriCornerKeyboard` suggests the developer ("heri") reuses this host across apps. `[obs]` / `[inf]` on the reuse.
3. **There is no auth anywhere.** No request carries an Authorization header, token or API key. The only credential in the APK is the stock Firebase `google_api_key`. `[obs]`
4. **No attestation.** There is no Firebase App Check and no Play Integrity call site in app code. PairIP only does licensing (see the anti-tamper report). `[obs]`
5. Besides the SDKs listed by triage, the APK also ships **Firebase Crashlytics, Performance Monitoring and Sessions**. Perf instruments both first-party HTTP paths (`FirebasePerfUrlConnection.instrument` and `FirebasePerfOkHttpClient.execute`). `[obs]`

---

## 1. Endpoint catalog

| # | Host | Path / query | Method | Auth | Purpose | Source | Tag |
|---|------|--------------|--------|------|---------|--------|-----|
| 1 | `translate.googleapis.com` | `/translate_a/single?client=gtx&sl={src}&tl={tgt}&dt=t&q={enc}&ie=UTF-8&oe=UTF-8` | GET | none. Header `User-Agent: Mozilla/5.0` | All text translation: keyboard translation bar, text translator, conversation, camera result [inf] | `tf/b.java:1676` (`static String n(str, sl, tl)`) | `[obs]` |
| 2 | `api.dictionaryapi.dev` | `/api/v2/entries/{language}/{word}`. `language` is always `"en"` | GET | none | Dictionary screen: definitions, phonetics, synonyms | Retrofit iface `ua/a.java`, client built in `mk/c.java:101` (case 20), called at `db/m.java:86,108` | `[obs]` |
| 3 | `myappsdata.site` | `/heriCornerKeyboard/databases/stickers.json` | GET | none | Sticker pack catalog (JSON array of `StickersModel`) | `androidx/lifecycle/r.java:663` (case 22, OkHttp) | `[obs]` |
| 4 | *dynamic*: the URLs inside `stickers.json` → `image_List` | n/a | GET | none | Download of sticker images into app-private storage | `androidx/lifecycle/r.java:622` (case 21, `URLConnection` → `FileOutputStream`) | `[obs]` URL source `[inf]` |
| 5 | `firebaseremoteconfig.googleapis.com` (+ `firebaseremoteconfigrealtime`) | `/v1/projects/…/namespaces/firebase:fetch` | POST | FIS token | 94 Remote Config keys, all of them ad placement/cadence config | SDK, `ob/e.java:129` | `[obs]` |
| 6 | `firebaseinstallations.googleapis.com` | `/v1/projects/…/installations` | POST | `x-goog-api-key` = `google_api_key` | FID registration | SDK | `[obs]` |
| 7 | FCM (`fcm.googleapis.com` via GMS) | n/a | n/a | GMS | Push. The token is only logged (`FCMHelper.java:36`), never uploaded to the developer's own server | `FCMHelper` | `[obs]` |
| 8 | `firebase-settings.crashlytics.com` | `/spi/v2/platforms/android/gmp/…` | GET | app id | Crashlytics settings and reports | SDK | `[obs]` |
| 9 | `app-measurement.com` | `/a`, `/s/d` | POST | app instance id | GA4F analytics | SDK | `[obs]` |
| 10 | Firebase Perf / datatransport | batched | POST | n/a | Perf traces for endpoints 1 to 4 | SDK | `[inf]` |
| 11 | `googleads.g.doubleclick.net`, `pagead2.googlesyndication.com`, `fundingchoicesmessages.google.com` | various | GET/POST | ad unit ids | GMA Next-Gen ads and UMP consent | SDK | `[obs]` |
| 12 | `sites.google.com/view/voice-keyboard-heri-policy/home` | n/a | browser | n/a | Privacy policy link | strings / endpoints.txt | `[obs]` |

There is **no first-party API server**. Endpoint 3 is a static JSON file on a developer-owned domain. It works like a CDN and has no API behind it. `[obs]` / `[inf]` that it is static hosting (the `.json` path, a GET with no parameters, no auth).

All first-party endpoints use HTTPS. No `network_security_config` is shipped and no `CertificatePinner` is used, so the app trusts system CAs only and pins nothing. `[obs]`

### 1.1 Notes per endpoint

- **#1 gtx.** This is the endpoint the free Google web and Chrome clients use. It is not the billed Cloud Translation API and comes with no SLA and no licence. It is rate-limited per IP, and heavy use returns HTTP 429 or a CAPTCHA. `[obs]` for the URL, `[inf]` for the throttling behaviour. The URL is logged in plaintext with tag `urlCheck___` (`tf/b.java:1677`), which leaks the user's typed text to logcat in release builds. `[obs]`
- **#2 dictionaryapi.dev.** This is a community-run free API that only covers English (`"en"` is hard-coded at both call sites). It is not tied to the keyboard language. `[obs]`
- **#3 / #4 stickers.** The OkHttp client used is the app-wide singleton from the Hilt-style module `oa/d.java` case 9: connect, read and write timeouts are 60 s each, and `f33377f=true` is probably `retryOnConnectionFailure` `[inf]`. Sticker folder names are sanitized to `[A-Za-z0-9_]` before files are written (`r.java:~680`). This mitigates path traversal. `[obs]`

---

## 2. Auth and key provisioning

| Item | Value (redacted) | Location | Use | Tag |
|------|------------------|----------|-----|-----|
| Firebase / Google API key | `AIzaSyCd…lqfo` | `res/values/strings.xml` `google_api_key` | Firebase Installations, Remote Config and Analytics. Standard Firebase client key | `[obs]` / `[inf]` on restriction state |
| Firebase project | `voice-typing-keyboard-s` | `strings.xml` `project_id` | n/a | `[obs]` |
| GCM sender / app id | `98842667…2571` / `1:988426…0c9e` | `strings.xml` | FCM | `[obs]` |
| Storage bucket | `voice-typing-keyboard-s.firebasestorage.app` | `strings.xml` | No Storage SDK call site found, so unused `[inf]` | `[obs]` |
| Crashlytics mapping id | `db58f447…84d9` | `strings.xml` | Crash symbolication | `[obs]` |
| AdMob app id | `ca-app-pub-873048…~…0830` | `strings.xml` `admob_app_id` | Ads | `[obs]` |

- **No user accounts.** There is no Google Sign-In flow in app code (the `com.google.android.gms.signin` prefs belong to the SDK), no Firebase Auth, and no token refresh. `[obs]`
- **No attestation gates.** There is no `FirebaseAppCheck`, no `IntegrityManager`, and no `X-Android-Cert` or `X-Android-Package` headers. `[obs]`
- **No secrets need rotating.** The Firebase key is designed to be public. If it has no Android-app restriction it can be abused for FIS and RC quota, but nothing billable is exposed. `[inf]`

---

## 3. Speech-to-text engine

There are two separate STT paths. Both are on-platform. No audio bytes are sent by the app itself, but the system recognizer (usually Google's) may send audio to its own cloud. `[obs]` / `[inf]`

### 3.1 In-keyboard voice typing (IME)

- **Entry point:** `wi/k.java` `l()` (static helper on the `FlorisImeService` companion). `[obs]`
- **Permission gate:** if `RECORD_AUDIO` has not been granted, the code launches `PermissionActivity` with the extra `EXTRA_WAS_TRANSLATION_BAR_OPEN`, then returns. `[obs]`
- **Audio focus:** `AudioAttributes` usage 11 (`USAGE_MEDIA`) with content type 1 (speech) on API 26+, and a transient focus request on older APIs. `[obs]`
- **Recognizer:** `SpeechRecognizer.createSpeechRecognizer(service)` (`wi/k.java:250`), held in the static `FlorisImeService.f26155t`. `[obs]`
- **Intent extras** (`RECOGNIZE_SPEECH`): `SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS=2000`, `…POSSIBLY_COMPLETE…=2000`, `LANGUAGE_MODEL=free_form`, `PARTIAL_RESULTS=true`, `LANGUAGE=<active keyboard subtype locale, '-'-joined>`, `calling_package`. `[obs]`
- **Continuous loop:** `startListening` runs through a main-looper `Handler` that posts `wi/h` (case 0). After every `onResults`, the listener (`wi/j.java`) calls `k.b()` and `k.a(intent)` to restart. This gives continuous dictation until the user stops it. `[obs]`
- **Error policy** (`wi/j.onError`): code 6 (speech timeout) or 7 (no match) resets and restarts. Code 8 (recognizer busy) restarts. Any other code stops via `k.m()`. `[obs]`
- **Partial results:** requested but ignored, because `onPartialResults` is a no-op. Only final results are used. `[obs]`
- **Result routing** (`wi/j.onResults`): if the translation bar pref (`FlorisPreferenceModel.keyboard.F`) is on, the text goes into the translation buffer (`ck/a.h()` then `ck/a.g()`), which triggers translation (§4.2). Otherwise it is committed directly with `InputConnection.commitText(text + " ", 1)`. `[obs]`
- **Stop:** `k.m()` removes the handler callbacks, calls `stopListening()` and `destroy()`, nulls the static, and sets `f26156u=false`. `[obs]`

### 3.2 In-app screens (translator, conversation, dictionary)

- These use the system dialog through `ActivityResultLauncher` with a `RECOGNIZE_SPEECH` intent: `db/d.java:37` (locale = `Locale.getDefault()`, `EXTRA_PROMPT = R.string.speak_now`) and `ab/l.java:304` (locale = selected language code, or `"en"` when `vb.l.e()` is true). `[obs]`
- `ActivityNotFoundException` produces a toast with `voice_recognition_is_not_supported_on_this_device`. `[obs]`

**Clone guidance `[dec]`:** keep the platform `SpeechRecognizer` for v1 because it is free and works offline on many devices. Add `EXTRA_PREFER_OFFLINE` and use partial results for live preview (the competitor discards them). Consider an optional on-device engine (Vosk or whisper.cpp) as a premium differentiator.

---

## 4. Translation engine and routing

### 4.1 The single provider (`tf/b.n(text, sl, tl)`)

The flow, in words `[obs]`:
1. Trim the input (`lo.o.U0`). Replace every `&` with `^~^` and every `\n` with `~~` so that ampersands and line breaks survive the `q=` parameter, then URL-encode the result as UTF-8.
2. Build the gtx URL from §1 #1 and log it.
3. Open an `HttpURLConnection` wrapped by Firebase Perf and set `User-Agent: Mozilla/5.0`. No explicit timeouts are set, so the platform defaults apply (effectively unbounded) `[inf]`.
4. Read the whole body and parse it with `c0()`: `JSONArray(body)[0]` is the sentence array, and the code concatenates `sentence[0]` (the translated segment) for every element. Any exception yields `""`.
5. Replace `~~` with `System.lineSeparator()` and return the string.
6. **Errors:** any exception returns the empty string (`RequestConfiguration.MAX_AD_CONTENT_RATING_UNSPECIFIED` is `""`). There is no retry, no fallback provider and no caching.

**Quirk:** the `^~^` placeholder is never mapped back to `&` after translation. Google usually passes it through or mangles it, so text containing `&` may render incorrectly. `[obs]` for the missing reverse mapping, `[inf]` for the visible effect.

### 4.2 Routing and call sites

| Caller | Feature | Language source | Defaults | Tag |
|--------|---------|-----------------|----------|-----|
| `ci/s.java:138` (coroutine case 1) | **Keyboard translation bar**: the user types or speaks into an in-keyboard buffer, the translation replaces the previously committed output | `ck/a.f20550c` / `f20551d` (static, mutable) | `en` → `ur` | `[obs]` |
| `cb/k.java:275` (default branch) | Text translator screen / camera result `[inf]` | args, blank values fall back to the defaults | `en` → `ur` | `[obs]` |
| `vb/k.java:91` | **Conversation mode**: translates `TextDiscription.listeningText` | `vb.l` side-A / side-B languages | English ↔ Spanish (`vb/l` ctor) | `[obs]` |

**Keyboard translation bar lifecycle** (`ck/a.java`) `[obs]`:
- The buffer is a Compose `MutableState<String>` (`f20548a`), with the cursor in `f20549b`. Insert, delete and paste (`d`, `c`, `e`) all mutate the buffer and then call `g()`.
- `g()` **cancels the previous job** (`u1.e(null)`) and launches a new coroutine on `Dispatchers.Main + SupervisorJob`. That coroutine body (`androidx/lifecycle/o0` case 10) did not decompile, so whether it uses a debounce `delay()` is unknown (open item #5). `[obs]`
- Output commit `a(str)` runs inside `beginBatchEdit`: `deleteSurroundingText(lastLen, 0)`, then `commitText(str)`, then `lastLen = str.length`. Each new translation therefore overwrites the previous one in the host field. `[obs]`
- `f()` clears the buffer and resets `lastLen`. `[obs]`

**Online gate:** the text translator (`vb/l.k()`) checks `com.bumptech.glide.e.q(context)` (connectivity `[inf]`). When offline it shows the `internet_connection_required` toast and makes no call. It also skips the call if the input is blank or a request is already in flight (`f45039w`). `[obs]`

**Conversation mode flow** (resolves triage open item #4 to `[obs]`, except for TTS):
`RecognizerIntent` (side A or B) → `TextDiscription(listeningText, translatedText="", isFromStartSide)` is appended to `vb.l.A` → `vb/k` calls gtx on `Dispatchers.IO` → main-thread `r0` (default branch) writes the translated copy back into list slot `i`, then launches `k5/y` on IO, which **inserts a `TranslationEntity` into `translation_history`** (`k5/y.java:148`). `[obs]` Auto-speak of the result has not been confirmed: TTS is wired to buttons (§5), and whether conversation auto-plays needs a runtime check `[inf]`.

**Language catalogs** `[obs]`:
- `TranslationLanguagesModel(id, name, code, flagIcon, isRtl)` is the gtx language list.
- `vb/l.f45024f` holds 43 BCP-47 tags (en-US, zh-CN, ar-SA, pt-BR, …). They exactly match the language columns of the bundled phrasebook DB (§6.3).

**Clone guidance `[dec]`:** use ML Kit on-device Translate (free, offline, 59 languages) as the primary path, and offer a paid Cloud Translation v3 or DeepL tier for quality. Do not ship gtx. Add a 300–400 ms debounce, request de-duplication and an LRU cache in the keyboard bar.

---

## 5. Text-to-speech

- **Engine:** platform `android.speech.tts.TextToSpeech`, created per screen. Call sites: `bk/o.java:477-490`, `a2/a.java:246-255`, `ab/l.java:263-283`, `ab/o.java:205,334,342` (the dictionary word), `ab/d.java:354`. `[obs]`
- **Pattern:** `setLanguage(Locale.forLanguageTag(model.code))`, then `speak(text, QUEUE_FLUSH(0), null, null)`. `[obs]`
- **Unsupported language:** if `setLanguage` returns `-1` (LANG_MISSING_DATA), the app shows the toast `google_does_not_support_this_language`. A return of `-2` (NOT_SUPPORTED) is not handled. `[obs]`
- No speech rate or pitch is set and nothing is synthesized to a file. There is no cloud TTS (no `translate_tts` URL). Navigating away calls `stop()` when `isSpeaking()` is true (`ab/l` case 12). `[obs]`

---

## 6. Local data schema

### 6.1 Room databases

| DB file | Class | Ver | Identity hash | Tables | Origin |
|---------|-------|-----|---------------|--------|--------|
| `keyboard_theme_db` | `AppDatabase` (`oa/d.java:49`) | 1 | `46a3a014…8164` | `translation_history`, `keyboard_themes`, `keyboard_selected_languages` | first-party `[obs]` |
| `clipboard_history` [inf] | `ClipboardHistoryDatabase` | 4 | `1dd181d1…9ab5` | `clipboard_history` | Floris `[obs]` |
| `clipboard_files` | `ClipboardFilesDatabase` (`ci/s.java:148`) | 2 | `dc886df9…4a84` | `clipboard_files` | Floris `[obs]` |
| user dictionary | `FlorisUserDictionaryDatabase` | 1 | `787af4a2…b273` | `words` | Floris `[obs]` |
| `androidx.work.workdb` | WorkManager | n/a | n/a | n/a | SDK |

Builder flags on `AppDatabase` (`t.f41689p=false`, `f41690q=true`, `r=true`) most likely mean destructive fallback on migration, so a schema bump wipes history and themes `[inf]`. Hashes are from `dj/h.java:24,266,272,278`. `[obs]`

**First-party table shapes** (column names only, summarised from the Room DDL) `[obs]`:
- `translation_history`: `id` PK autoinc, `sourceText`, `translatedText`, `sourceLang`, `targetLang`, `isFromStartSide` (bool), `timestamp`. DAO: insert-or-replace, `SELECT * … ORDER BY timestamp ASC` (exposed as a Flow), delete-all. This shared table holds both text-translator and conversation history.
- `keyboard_themes`: `id` PK autoinc, `isCustom`, then about 22 styling columns. The background columns (`keyboardBackground`, `keysBackground`, `stickyKeys*`, `spaceKeysBackground`, `hotKeysBackground`, `keyPopUpBackground`, `smartBar*Background`) are TEXT, each a serialized `BackgroundTheme`. Colours are INTEGER ARGB. Sizes, radii and widths are REAL. `glideTrailColor` is also stored. Delete is by id list.
- `keyboard_selected_languages`: `id` PK, `name`, `code`, `flagIcon` (nullable drawable res id). Insert-or-replace and delete-by-id.

**Floris stock tables:** `clipboard_history` (`type`, `text`, `uri`, `creationTimestampMs`, `isPinned`, `mimeTypes`, `is_sensitive`, `is_remote_device`), `clipboard_files` (`_display_name`, `_size`, `orientation`, `mimeTypes`), and `words` (`word`, `frequency`, `locale`, `shortcut`). `[obs]`

### 6.2 Raw SQLite: phrasebook

- `assets/phrases_final.db` (573,440 B) is copied on first run to `getDatabasePath("phrases_final.db")` (`j8/b.java:348-360`) and opened read-only through `sa/b` with raw queries in `ra/f.java:47,64,81`. `[obs]`
- **Tables:** `topic_groups` (8 rows), `sub_topics` (8), `expressions` (241), `language_registry` (26), `phonetic_maps` (1,683 rows, unique on `source_text`+`lang_tag`). `[obs]`
- `expressions`, `sub_topics` and `topic_groups` each have one column per language: 43 BCP-47 columns, including en-US/en-UK/en-AU and three Arabic and three Chinese variants. The query selects `expr_id, subtopic_id, sort_order, <langColumn>`. The column name is concatenated into the SQL from `ra/f.f41781c`, which is safe only if that value comes from the fixed allow-list in `vb/l.f45024f` `[inf]`. `[obs]`
- `phonetic_maps` and `language_registry` have **no code references**, so they are dead data or reserved for a future feature. `[obs]`
- **The phrasebook is fully offline.** The `phrasebook_translation` interstitial key implies a "translate" action, but static evidence suggests a column switch rather than a gtx call `[inf]`.

### 6.3 Preferences

| Store | Type | Keys seen | Notes | Tag |
|-------|------|-----------|-------|-----|
| `voice_typing_translator_prefs_data` | Jetpack DataStore (Preferences), `nb/s.java:23` | `isUserSubscribed`, `is_user_premium`, `can_user_request_ads`, `app_open_main_show`, `pref_selected_language`, and others | Read with `runBlocking` (`nb/r.a/b`). **The setter `nb/r.c(key, bool)` is an empty method**, so `kb/g.java:122` writing `is_user_premium` is a no-op | `[obs]` |
| `voice_typing_translator_prefs_image` | DataStore | n/a | Background image or theme assets `[inf]` | `[obs]` |
| `keyboard_prefs` | SharedPreferences (`n/d.java:929`) | `is_user_premium`, `can_user_request_ads`, `keyboard_native_ad_enable`, `keyboard_native_ad_keyboard_id`, `keyboard_native_ad_button_color` | Read by `FlorisImeService:694-710` to gate the in-keyboard native ad | `[obs]` |
| Floris prefs | `FlorisPreferenceModelImpl` (DataStore/JSON, `settings_version`) | translation-bar toggle `keyboard.F`, layouts, theme | Stock Floris | `[obs]` |

**Premium consequence:** nothing writes `isUserSubscribed`, and the only write of `is_user_premium` to DataStore is a stub. The premium flag is therefore effectively always `false`. This strengthens the Monetization team's finding that Billing is vestigial. `[obs]` / `[inf]`

### 6.4 Files

- Sticker images go to `va/e.f44974a/<sanitizedName>/*.{png,jpg,webp,gif}` in app-private storage `[obs]` (exact dir `[inf]`). A pack is marked "offline" (`isOffLine=true` via `copy`) when local files exist. `[obs]`
- `StickersModel` fields: `id`, `name`, `image_List[]`, `file_size`, `isOffLine`, `isPremium`. `isPremium` exists in the remote JSON, but premium can never become true (§6.3). `[obs]` / `[inf]`

---

## 7. Request lifecycle and threading

| Path | Client | Thread | Timeouts | Retry | Cache | Tag |
|------|--------|--------|----------|-------|-------|-----|
| gtx | `HttpURLConnection` + Perf | IO coroutine (`vb/k` on IO; `ci/s` is launched from `ck/a` scope on Main, and it must hop to IO or it would throw `NetworkOnMainThreadException` `[inf]`) | platform default | none | none | `[obs]` |
| dictionary | Retrofit 2 + Gson (`ei.n`), new default `OkHttpClient` per build (`mk/c` case 20) | `Call.enqueue` (`.b(new e0(...))`) | OkHttp default 10 s | none | none | `[obs]` |
| stickers JSON | shared OkHttp singleton (`oa/d` case 9) + Perf | IO | 60/60/60 s | `retryOnConnectionFailure` `[inf]` | file-presence check only | `[obs]` |
| sticker images | `URLConnection` + Perf | IO | platform default | none | on disk, permanent | `[obs]` |
| RC fetch | Firebase SDK | SDK | n/a | n/a | n/a | `[obs]` |

The splash screen waits **3 s** if the user is subscribed or offline and **12 s** otherwise, while the RC fetch and the splash interstitial load (`kb/g.java:125`). `[obs]`

---

## 8. Remote Config surface

- `res/xml/remote_config_defaults.xml` contains 94 keys. **Every key is ad-related**: `*_banner_*`, `*_native_*`, `*_inter_{id,enabled,capping,load_next}`, `app_open_*` and `keyboard_native_ad_*`. `[obs]`
- There is **no backend switch**: no translation-provider flag, no API-key pool, no kill switch, and no forced-update key. The provider (gtx) cannot be changed without an app update. `[obs]`
- RC is fetched at splash and parsed through a custom XML defaults reader in `ob/e.java:165-183`. `[obs]`

---

## 9. AI / LLM features

**None.** There are no strings for `openai`, `gemini`, `generativelanguage`, `anthropic`, `api-inference`, `chat/completions` or `generateContent` in the dex or resources, and no prompt templates exist. Triage's grep was re-checked against endpoints.txt. `[obs]`
**Clone opportunity `[dec]`:** "AI rewrite / tone / grammar" in the smartbar is a clear differentiator. Proxy any LLM call through your own backend with App Check, and never embed a provider key.

---

## 10. Third-party backend identification

| Backend | Role | Evidence chain | Confidence |
|---------|------|----------------|-----------|
| Google Translate web (gtx) | Sole translation engine | literal URL `tf/b.java:1676`, `client=gtx`, UA spoof, `JSONArray[0][i][0]` parse matches the gtx response shape | **99%** |
| Free Dictionary API (dictionaryapi.dev) | English dictionary | Retrofit base URL, `@GET api/v2/entries/{language}/{word}`, models match the public schema (word/phonetics/meanings/definitions/synonyms/antonyms) | **99%** |
| Developer static host `myappsdata.site` | Sticker catalog + assets | literal URL, GET with no auth, path `/heriCornerKeyboard/databases/` suggests a shared multi-app asset bucket | **95%** that it is static hosting, **60%** that it is shared with another "heri" app `[inf]` |
| Firebase (`voice-typing-keyboard-s`) | RC, FCM, Analytics, Crashlytics, Perf, Installations | `strings.xml` ids + SDK classes under `com/google/firebase/{remoteconfig,messaging,crashlytics,perf,sessions}` | **99%** |
| Google platform speech (via `SpeechRecognizer`) | STT | `createSpeechRecognizer` without a component name → device default service, usually Google's `[inf]` | **90%** |
| Device TTS engine | TTS | platform API only | **99%** |
| Google Mobile Vision (`gms.vision.text`) | OCR for camera translation | `xd/a.java implements vision.text.Text`, `com/google/android/gms/vision/*`, no `com/google/mlkit` | **85%** (code path not traced, Native team owns this) |
| Own API server | none | no non-Google, non-dictionary hosts except the static sticker file | **95% none** |

---

## 11. Open items (static-only; each needs a runtime check)

| # | Item | Why | Owner |
|---|------|-----|-------|
| 2 | Real RC values (ad cadence, capping) | defaults only | Backend (from triage) |
| 4 | Conversation: does the translated turn auto-play TTS? | the STT→gtx→Room path is `[obs]`, auto-speak is not | Backend |
| 5 | Keyboard translation-bar debounce | `androidx/lifecycle/o0` case 10 failed to decompile. Rerun jadx with `--show-bad-code` or read the smali | Backend |
| 6 | `stickers.json` live shape and the image host(s) | the URLs inside the JSON are runtime data | Backend |
| 7 | Whether `cb/k` default-branch caller is the camera flow or the text screen | the obfuscated Compose call graph | Backend / Native |
| 8 | Is the `google_api_key` Android-restricted? | cannot test without calling the API, which is forbidden | Backend (owner sanity check only on clone's own key) |
| 9 | gtx throttling behaviour under keyboard typing load (429 / CAPTCHA HTML causing `""` output) | runtime only | Backend |
| 10 | Which Room DB file name the Floris clipboard history uses | `ClipboardHistoryDatabase` builder not traced | Backend (low priority) |

Resolved from triage: #4 is now mostly `[obs]` (§4.2). Supporting evidence for #1 (no paywall): the DataStore premium writer is a no-op (§6.3).

---

## 12. Clone backend recommendations `[dec]`

1. **Translation:** ML Kit on-device as the default, with an optional paid cloud tier through your own proxy. Debounce, cache, and handle `&` and newlines correctly.
2. **STT:** platform recognizer plus partial results, with an optional offline engine. Keep the restart-on-silence loop but cap it so the mic does not stay on indefinitely.
3. **TTS:** platform TTS. Handle `LANG_NOT_SUPPORTED` and offer an "install voice data" intent.
4. **Dictionary:** dictionaryapi.dev is acceptable for prototyping only. For multi-language support, bundle Wiktionary-derived data or license a dictionary.
5. **Stickers:** serve from your own CDN with a signed manifest, versioning and checksums. Never hot-link a competitor's host.
6. **Backend control plane:** add RC keys for the translation provider, a kill switch and min-version, which the competitor lacks.
7. **Privacy:** remove plaintext logging of user text (the competitor logs it under `urlCheck___`), and do not log the FCM token.
8. **Attestation:** use App Check (Play Integrity) on any proxy you run.

---

## Appendix: key files

- `jadx_out/sources/tf/b.java:593` (`c0` parser), `:1673-1705` (`n` gtx call)
- `jadx_out/sources/ck/a.java` (keyboard translation buffer and commit)
- `jadx_out/sources/ci/s.java:138`, `cb/k.java:265-275`, `vb/k.java:60-95`, `vb/l.java` (translator / conversation VM)
- `jadx_out/sources/wi/k.java:226-290` (IME STT), `wi/j.java` (RecognitionListener), `wi/h.java`
- `jadx_out/sources/db/d.java:37`, `ab/l.java:261-310` (in-app STT and TTS)
- `jadx_out/sources/ua/a.java`, `mk/c.java:96-140`, `db/m.java:86,108` (dictionary)
- `jadx_out/sources/androidx/lifecycle/r.java:622-720` (stickers)
- `jadx_out/sources/dj/h.java` (Room open helpers), `oa/d.java:40-70` (DI module: DB, OkHttp)
- `jadx_out/sources/k5/y.java:148` (history insert), `j8/b.java:346-375` (phrasebook copy), `ra/f.java` (phrasebook queries)
- `jadx_out/sources/nb/r.java`, `nb/s.java` (DataStore wrapper, no-op setter), `n/d.java:929` (`keyboard_prefs`)
- `jadx_out/resources/assets/phrases_final.db`, `jadx_out/resources/res/xml/remote_config_defaults.xml`, `jadx_out/resources/res/values/strings.xml`
