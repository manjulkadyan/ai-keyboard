# Backend & API Surface: Voice Keyboard - Voice Typing

**Target:** `com.logiapps.language.keyboard.voice.typing.translator` v1.3 (versionCode 4, minSdk 26, target 37). Native Kotlin + Compose. This is a fork of FlorisBoard (Apache-2.0) with R8 obfuscation. 10684 of 12845 classes are in `defpackage/`.
**SHA-256:** `6052cf1a5a6c9eafadba994fedecc3d4f63af2eee952617b317de3fe9cf7c6f8`
**Pass type:** static-only. No request was sent to any host named here, and no extracted key was used.
**Evidence tags:** `[obs]` = read directly from decompiled code or a packaged resource. `[dec]` = a decision or recommendation for the clone. `[inf]` = an inference that is not proven statically. Every dynamic claim is `[inf]` and has a matching open item in section 12.
**Paths** are relative to `WORKDIR = voicetyping_work/`. Sources are under `jadx_out/sources/` and resources under `jadx_out/resources/`.

> **Summary.** The developer runs no backend of its own. The app calls three external HTTP services: (1) Google's **unofficial** `translate_a/single` web endpoint for every translation, including a live translate-as-you-type mode inside the keyboard; (2) the public **freedictionaryapi.com** REST API for the dictionary; (3) a static JSON sticker catalog on **myappsdata.site**. Speech-to-text uses the platform `SpeechRecognizer`/`RecognizerIntent`. TTS uses platform `TextToSpeech`. There are no LLM or AI features, no auth, no App Check and no billing. Firebase covers analytics, crash reporting, Remote Config and FCM only. `[obs]`

---

## 1. Endpoint catalog

| # | Host + path | Method | Auth | Client | Purpose | Source |
|---|---|---|---|---|---|---|
| E1 | `https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl={src}&tl={tgt}&dt=t&q={text}&ie=UTF-8&oe=UTF-8` | GET | none. Header `User-Agent: Mozilla/5.0` | `HttpURLConnection` wrapped with `FirebasePerfUrlConnection.instrument` | All text translation: the Translator screen, Voice Conversation, and in-keyboard live translate | `defpackage/my3.java:150-181` (`my3.B0`) `[obs]` |
| E2 | `https://freedictionaryapi.com/api/v1/entries/{language}/{word}?translations=true` | GET | none | Retrofit (`a32` interface) + OkHttp, Gson-style converter `ny1(2)` | Dictionary lookup. `language` is hard-coded to `"en"` | `defpackage/a32.java:10`, `defpackage/nk6.java:79-123` (base URL), `defpackage/l32.java:2713-2720` (call) `[obs]` |
| E3 | `https://myappsdata.site/ZTechVoiceTyping/databases/stickers.json` | GET | none | OkHttp (`FirebasePerfOkHttpClient.execute`) | Sticker pack catalog (`AvailableStickersModel[]`) | `defpackage/eo5.java:546-560` `[obs]` |
| E4 | `{image_List[i]}`: sticker image URLs taken from the E3 JSON | GET | none | `HttpURLConnection`, 8 s connect/read timeouts, Perf-instrumented | Downloads sticker images into a per-pack folder. A `.complete` marker file is written when done | `defpackage/eo5.java:504-542`, `:575` `[obs]`. The host is probably also myappsdata.site `[inf]` |
| E5 | Firebase: Remote Config, Installations, FCM registration (`fcmregistrations.googleapis.com`), Analytics/Measurement, Crashlytics, Perf, Sessions | SDK | Firebase API key + app ID | Firebase SDKs | Telemetry, config and push | `endpoints.txt`, `sdk-roster.txt` `[obs]` |
| E6 | AdMob / ads_mobile_sdk hosts (`googleads`, `2mdn`, `googletagservices`, `mediation.goog`) | SDK | AdMob app ID | GMA next-gen SDK | Ads. Monetization (Team 3) owns this | `endpoints.txt` `[obs]` |
| E7 | Play In-App Update, UMP consent | SDK | none | Play libs | Update prompt, GDPR consent | `sdk-roster.txt` `[obs]` |

No other first-party hosts were found. Of the URLs in `endpoints-firstparty.txt`, only E1, E2 and E3 are app-specific. The rest are SDK documentation strings and `schemas.florisboard.org` theme-schema identifiers, which are not fetched. `[obs]`

### 1.1 What the catalog does not contain
- No developer API server, login, user accounts or cloud sync. `[obs]`
- No OpenAI, Gemini, Anthropic, DeepL, Microsoft Translator or MyMemory endpoints, and no LLM prompt strings anywhere in the sources or resources. `[obs]`
- No cloud STT (Google Cloud Speech, Whisper, Deepgram) and no offline STT engine (Vosk, whisper.cpp). The only native libs are AndroidX, DataStore and CameraX. `[obs]`
- Remote Config does not deliver any endpoint. All E1 to E3 URLs are compiled-in literals. Every Remote Config key is ad configuration (section 7). `[obs]`

---

## 2. Auth and key provisioning

### 2.1 Credentials present (redacted 8+4)
| Item | Value (redacted) | Location | Notes |
|---|---|---|---|
| `google_api_key` | `AIzaSyBL…70kE` | `res/values/strings.xml` | Standard Firebase Android key. Used only by Firebase SDKs. `[obs]` Restriction status is unknown statically `[inf]` |
| `gcm_defaultSenderId` | `14064649…3929` | `res/values/strings.xml` | FCM sender `[obs]` |
| `google_app_id` | `1:140646…4a78` | `res/values/strings.xml` | Firebase app `[obs]` |
| Firebase project / bucket | `voice-keyboard-translator-z` / `…firebasestorage.app` | `res/values/strings.xml` | No Storage SDK call site was found. The bucket string comes from google-services.json boilerplate `[inf]` |
| AdMob app ID | `ca-app-pub-3051…~…1938` | manifest meta-data | Team 3 `[obs]` |

`secrets-keys-redacted.txt` lists only the single `AIza` key. No other key shapes were found: no `sk-`, bearer tokens, JWTs or hard-coded passwords. `[obs]`

### 2.2 Auth mechanism
- **None.** E1, E2 and E3 are anonymous. E1 spoofs a browser `User-Agent` and uses the Chrome-extension client ID `dict-chrome-ex` so that it looks like the Google Dictionary extension. `[obs]` (The intent is `[inf]`.)
- No token, refresh flow or token storage exists. `[obs]`
- **Attestation:** none. There is no Firebase App Check and no Play Integrity in `sdk-roster.txt` or the sources. `[obs]`
- **Transport:** there is no `networkSecurityConfig` and no cleartext opt-in. All endpoints are HTTPS. No certificate pinning was found for E1 to E3: there is no OkHttp `CertificatePinner` on the client built in `nk6`, and plain `HttpURLConnection` is used elsewhere. `[obs]`

### 2.3 Device / signature fingerprinting near the network code
`my3.java:130-145` computes a SHA-1 of the signing certificate. This sits in the same obfuscated utility class as `B0`, but the result is never attached to E1. It is most likely an SDK helper that R8 merged into `my3` (a Firebase/GMS cert-fingerprint helper) `[inf]`.

---

## 3. Translation engine

### 3.1 Engine identity
There is a single engine: the Google Translate unofficial web endpoint (E1). There is no ML Kit Translate (`com.google.mlkit.nl.translate` is absent), no on-device fallback and no secondary provider. `[obs]`

### 3.2 Request construction (`my3.B0(text, sl, tl)`)
1. Trim the input (`px7.d1`, Kotlin `trim`). `[obs]`
2. Replace every `&` with the placeholder `^~^`, and every `\n` with `~~`. `[obs]`
3. URL-encode the result as UTF-8 and concatenate it into the GET URL above. Unknown languages are not checked; a null code is concatenated as the literal `null` `[obs]` (`or0.java:187` can pass `0`/null when no language model is selected).
4. Write the full URL to logcat under tag `urlCheck___`. **This logs the user's text in clear** `[obs]` (`my3.java:154`).
5. Open the connection with Firebase Performance instrumentation. Perf records host/path timing; whether the query string is retained in Perf traces is `[inf]` (open item 12.6).

### 3.3 Response parsing (`my3.o0`, `my3.java:1132-1145`)
- The body is read fully and parsed as a JSON array. The code takes element `[0]` (the sentences array), then concatenates `[i][0]` (the translated segment) for each sentence. `[obs]`
- Afterwards it replaces `~~` with `System.lineSeparator()` to restore line breaks. `[obs]`
- **The `^~^` placeholder is never reversed.** If Google passes it through untranslated, the user sees `^~^` where `&` was. If it is mangled, `&` is lost `[obs]` on the code path. The actual output is `[inf]` (open item 12.1).
- Response shape, in words: an outer array whose first element is a list of `[translated, original, …]` tuples. Other elements (detected source language, confidence) are ignored. `[obs]` (The documented shape of this undocumented API is `[inf]`.)

### 3.4 Error handling
- Any exception (network, HTTP non-2xx raising `IOException` in `getInputStream`, or JSON) is caught. It is `printStackTrace`'d and `""` is returned. `[obs]`
- There is no retry, no backoff, no explicit timeout (it uses `HttpURLConnection` defaults, which can be infinite on some Android versions `[inf]`), and HTTP 429 is not distinguished from other errors. `[obs]`
- Callers treat `""` as the translation. In the keyboard path this **erases the previously committed translation** (section 3.6). `[obs]`

### 3.5 Limits
- A UI-side cap of 10,000 characters: string `limit_reached_maximum_characters_allowed` ("Maximum 10000 characters allowed") is shown from the Translator Compose screen `oh8.java:671/1694/2786/3867` `[obs]`. The cap is not enforced inside `B0`. Because the request is a GET, very long text will exceed practical URL length limits well before 10k characters `[inf]`.
- 103 target/source languages are hard-coded as `TranslationLanguageModel(name, code, flagImage)` lists in `defpackage/ah8.java` and `defpackage/bh8.java`. No server-side list is fetched. `[obs]`

### 3.6 Routing: the three translation entry points
| Entry | Caller | Trigger | Direction logic |
|---|---|---|---|
| Translator screen | `nh8.java:59`, which calls `B0(textState, src, tgt)` | User action. A `translate_button` interstitial placement exists, so the trigger is most likely the button press `[inf]` | Fixed src to tgt, with a swap button `[inf]` |
| Voice Conversation | `or0.java:173-187` (case 1) | After an STT result | Boolean flag (the speaker side, `isLeft`) chooses `A→B` or `B→A` `[obs]` |
| **In-keyboard live translate** | `hh8` + `as0.java:90/107/124` + `gh8` | **Every keystroke** while translate mode is on | Defaults are `hh8.c="en"` and `hh8.d="ur"`. They are overwritten from the keyboard language picker in `k32.java:70-71` `[obs]` |

**In-keyboard live translate in detail** `[obs]`:
- `o98.java:1040-1072` (the FlorisBoard key-dispatch path, patched by the fork): when the keyboard "translate" toggle is on, character keys are diverted away from the editor into a shadow buffer. `hh8.b(text)` appends to the buffer, space appends `" "`, and delete (`-7`) calls `hh8.c()`, which drops the last character.
- Each append or delete launches a coroutine (`as0` cases 2 and 3) that sends the **entire buffer** to E1. There is no debounce or cancel-previous logic.
- `gh8.invokeSuspend` then calls `deleteSurroundingText(hh8.b, 0)`, which removes the previously committed translation by its stored length, then calls `commitText(newTranslation)` and stores the new length.
- Consequences: (a) N keystrokes produce N full-buffer requests to Google. (b) The text being typed into any app (chat, email, search) is sent to Google as it is typed. (c) Out-of-order responses can commit a stale translation `[inf]`. (d) A failed request commits `""` and deletes the previous translation. (e) If the user moves the cursor, `deleteSurroundingText(len)` can delete unrelated text `[inf]`.
- `[dec]` The clone should debounce (around 300-500 ms), cancel in-flight jobs, keep the translation in a composing region (`setComposingText`) instead of delete-and-commit, and translate on-device (ML Kit) by default for privacy inside the IME.

---

## 4. Speech-to-text engine

### 4.1 Inside the IME (voice key)
- `FlorisImeService.r()` (`dev/patrickgold/florisboard/FlorisImeService.java:735-760`):
  - It checks `SpeechRecognizer.isRecognitionAvailable`. If unavailable it shows the toast "Speech not available".
  - It creates a recognizer with `createSpeechRecognizer(this)`, which uses the default system recognition service (usually Google), **not** `createOnDeviceSpeechRecognizer`.
  - Intent: `RECOGNIZE_SPEECH`, `LANGUAGE_MODEL=free_form`, `LANGUAGE=` the active keyboard subtype's locale tag, `PARTIAL_RESULTS=true`.
  - The listener is `defpackage/yy2`. `onResults` takes `results_recognition[0]` and calls `commitText(text + " ")` (`yy2.java:42-68`). `onPartialResults` and `onError` are also handled. `[obs]`
- `s()` stops listening and destroys the recognizer. `RECORD_AUDIO` is requested via `AudioPermissionActivity`, because an IME cannot show runtime permission dialogs. `[obs]`
- Whether audio goes to a cloud service or stays on-device depends on the device's recognition service `[inf]`. The app adds no offline-preference extra. `[obs]`

### 4.2 Inside the app (Translator, Conversation, Dictionary mic buttons)
- `dn3.C(context, launcher, langTag)` (`defpackage/dn3.java:162-200`) launches the system `RecognizerIntent` UI through an ActivityResult launcher with `PROMPT="Speak now..."`, `MAX_RESULTS=5`, `PARTIAL_RESULTS=true` and `LANGUAGE=` the selected language code (default `en-US`). `[obs]`
- It is gated on network connectivity (`ws8.N`). If offline it shows the toast `no_internet_connection_please_turn_on_internet_to_use_microphone`. There is a 1-second double-tap guard. `[obs]`
- Result handlers read `android.speech.extra.RESULTS[0]` in `kl.java:84` (translator text field), `le0.java:112` (conversation), and `i32.java:61` (dictionary). `[obs]`

---

## 5. Text-to-speech

- There is one engine: platform `android.speech.tts.TextToSpeech`. It is created in `vr6.java:76` (case 1) through the Compose helper `s65.v()` (`s65.java:423-438`), with a no-op `OnInitListener` (`as6`). The default engine is used and no engine package is pinned. `[obs]`
- Speak sites:
  - `gi1.java:40-60` handles conversation bubbles. It calls `setLanguage(Locale.forLanguageTag(code))` for the bubble's side (default `en`), then `speak(text, QUEUE_FLUSH)`. `[obs]`
  - `us1.P()` (`us1.java:365-393`) handles long text. It splits on `TextToSpeech.getMaxSpeechInputLength()`, preferring to break at `. ! ? \n`, then queues the chunks as `utterance_0..n` with `QUEUE_ADD` after the first. `[obs]`
  - `j5.java:340-343` is a single speak of a state value. It most likely backs the dictionary pronunciation or the translator's speak button `[inf]`.
- **Correction to the triage brief:** `fw9.java` is a `WebViewClient` subclass inside the ads SDK, not TTS. `[obs]`
- There is no cloud TTS (no `translate_tts` URL and no Cloud TTS host). `[obs]`

---

## 6. AI / LLM features

**None.** There are no LLM endpoints, no SDKs (OpenAI, Gemini or `generativeai`, Anthropic, Vertex), no prompt templates, and no rewrite/grammar/chat strings. The "AI" in the app's positioning amounts to platform STT plus the Google Translate web endpoint. `[obs]`
`[dec]` The clone can add an AI rewrite feature as a differentiator. It has no counterpart here to copy.

---

## 7. Remote Config (backend-relevant view)

- Defaults: `res/xml/remote_config_defaults.xml` (543 lines). Every key is ad configuration: `*_admob_id`/`*_Id`, `*_is_enabled`, `*_should_load`, `*_capping_enabled`, banner shape flags, `native_type`, `cta_color`, `app_open_main_show`, `my_keyboard_native_ad_{enable,keyboard_id,button_color,time=600000}`. `[obs]`
- Fetch/activate is in `js6` (`vr6.java:79-95` logs `remoteConfigLogs`). `js6.java:188` iterates over the 10 interstitial placements. `js6.java:290-302` copies the keyboard-ad keys plus `PREF_CAN_USER_REQUEST_ADS` and `is_user_premium` into the IME-process SharedPreferences `keyboard_prefs`, so that `FlorisImeService` can read them synchronously (`FlorisImeService.java:597`). `[obs]`
- No feature flags, kill switches or endpoint overrides exist. Translation and dictionary behaviour cannot be changed remotely. `[obs]` Team 3 owns the monetization detail.

---

## 8. Request lifecycle and data flow

### 8.1 Dictionary (E2), end to end `[obs]`
1. `j32.java:140-160` normalizes the query: it keeps only letters (`Character.isLetter`) and lowercases with `Locale.ROOT`.
2. `ws8.N(context)` is the network check. **If offline, the search text is cleared** (log "No network, clearing search text") and the UI moves to a no-network state. There is **no local fallback**.
3. `l32.d(word, cb)` calls `a32.a("en", word, translations=true).enqueue(...)` (Retrofit `q`).
4. `xt3.java:626-697` (case 29) handles the response. On non-2xx or a null body it logs "API failed" and returns `null`. On success it maps `DictionaryAPIResponse{word, entries[], source{url, license}}` into `UnifiedDictionaryResult{word, pronunciation (first non-blank Pronunciation.text), definitionsByPart (map partOfSpeech→definitions), examples, synonyms, antonyms (sets aggregated from entries and senses, recursive via ec3.l), sourceUrl, translations: List<WordTranslation{languageCode, languageName, word}>}`.
5. On transport failure (`xt3.java:916-919`) it logs "API Error" and returns `null`.
- This resolves triage open item 4: **the call is API-only.** "Local first" was not found; the only local word store is FlorisBoard's user dictionary, which feeds suggestions and is not used by the Dictionary screen. `[obs]`
- The OkHttp client `is5` is built with no visible interceptors or timeouts, so OkHttp defaults apply (10 s connect/read/write) `[inf]`.

### 8.2 Voice Conversation `[obs]` + `[inf]`
1. The user taps the mic on side A or B. `ki1.java:840-851` picks that side's language code (default `en-US`) and launches the RecognizerIntent from section 4.2.
2. `le0.java:105-118` takes the recognized text and launches the `i1` coroutine (case 2) with the two language states. `i1` is listed in `jadx-failed-classes.txt`, so its body did not decompile `[obs]`.
3. Translation through E1 with direction set by the side (`or0` case 1) is `[inf]`, based on the signature match (`li5, String, bu7, bu7`).
4. `li1.java:100` inserts `ConversationMessage(text, isLeft, translatedText, timestamp)` into Room `conversation_messages`. The history is read with `SELECT * FROM conversation_messages ORDER BY timestamp ASC` and cleared with `DELETE FROM conversation_messages` (`dd0.java:190/210`).
5. Tapping a bubble runs `gi1` TTS in the bubble's language.
- Open item 12.3 covers confirming steps 2 and 3.

### 8.3 Stickers (E3/E4) `[obs]`
- The app fetches the catalog JSON and parses it into `AvailableStickersModel[]` with fields `id, name, file_size, image_List, isPremium, isOffLine`.
- Each pack's folder name is `name` with non-alphanumeric characters stripped. If the folder has a `.complete` marker and image files (png/jpg/webp/gif), the pack is marked offline-available and given local paths.
- The catalog is fetched fresh on each load, with no visible ETag or caching. On failure it returns `null` silently.
- `isPremium` exists in the model, but there is no billing SDK. The premium gate is likely ad-based or unused `[inf]` (Team 3).

### 8.4 What leaves the device
| Data | Destination | When |
|---|---|---|
| Typed text (in-keyboard translate mode), Translator input, conversation utterances | translate.googleapis.com | Every keystroke or translate action `[obs]` |
| Dictionary words | freedictionaryapi.com | Each search `[obs]` |
| Microphone audio | The system speech recognition service (usually Google) | Voice key or mic button `[inf]` |
| IP/UA only | myappsdata.site | Sticker screen `[obs]` |
| Telemetry, ad ID, FCM token | Firebase / AdMob | Continuous `[obs]` |

Voice-typed text is committed to the editor locally and is **not** translated unless translate mode is on. `[obs]` FCM token handling (`FCMService.onNewToken`) only logs the token. It is not uploaded to any developer server. `[obs]`

---

## 9. Local schema

### 9.1 Room databases `[obs]`
| DB (file) | Version | Tables / columns | Origin |
|---|---|---|---|
| `AppDatabase` (`keyboard_themes_db`, `hq1.java:248`) | 2 (`rs.java:18`) | `keyboard_themes`: around 45 columns of colors (TEXT), sizes and opacities (REAL), e.g. `keyBoardBg, keyBgColor, keysTextColor, keyTextSize, keyRadius, smartBar*, sticky*, popup*, glideColor, glideWidth, *Opacity, keyboardBackgroundBlur`. `conversation_messages(id PK AI, text, isLeft INT, translatedText, timestamp INT)` | Fork additions (DAOs `zf4` themes, `zh1` conversation) |
| `ClipboardHistoryDatabase` | 4 (`rs.java:254`) | `clipboard_history(_id, type, text, uri, creationTimestampMs, isPinned, mimeTypes, is_sensitive, is_remote_device)` | Upstream FlorisBoard |
| `ClipboardFilesDatabase` | 1 | `clipboard_files(_id, _display_name, _size, orientation, mimeTypes)` | Upstream FlorisBoard |
| `FlorisUserDictionaryDatabase` | 2 | `words(_id, word, frequency, locale, shortcut)` | Upstream FlorisBoard |

Migration strategy: the `DROP TABLE IF EXISTS` paths in `rs.java:60-70` indicate destructive fallback for `AppDatabase` `[inf]`.

### 9.2 Preferences `[obs]`
| Store | Keys (examples) | Purpose |
|---|---|---|
| DataStore `voice_typing_voice_keyboard_prefs_data` (`zz4.java:7`) | `is_user_premium`, `PREF_CAN_USER_REQUEST_ADS`, `pref_selected_language` (app UI locale, default `en`), `app_open_main_show` | App-process settings, wrapped by `f05` |
| DataStore `voice_typing_voice_keyboard_prefs_image` (`zz4.java:8`) | theme/wallpaper image data `[inf]` | Custom keyboard backgrounds |
| SharedPreferences `keyboard_prefs` (`q91.java:49`) | `is_user_premium`, `isBannerFirstAd`, `my_keyboard_native_ad_enable/_time/_keyboard_id/_button_color`, `keyboardBannerPreviousTime`, `PREF_CAN_USER_REQUEST_ADS` | Bridge for the IME process (ads plus consent) |
| `jetpref_datastore` | FlorisBoard JetPref settings (subtypes, theme, gestures, etc.) | Upstream |

`is_user_premium` is read in five places, but nothing in the app sets it to `true`. No billing flow exists, so the flag is dead or reserved for a future version `[inf]`.

---

## 10. Third-party backend identification

| Service | Confidence | Evidence chain |
|---|---|---|
| Google Translate unofficial web API (`translate_a/single`, `client=dict-chrome-ex`) | 99% | URL literal in `my3.B0`, browser UA spoof, nested-array parser `o0`. Not the paid Cloud Translation API (no key, no `v2/v3` path). `[obs]` |
| Free Dictionary API (freedictionaryapi.com, `api/v1/entries`) | 98% | Retrofit base URL, `@GET("api/v1/entries/{language}/{word}")` with `@Query("translations")`, DTOs match `entries/senses/pronunciations/source.license`. `[obs]` Note that this is a different service from `api.dictionaryapi.dev` |
| myappsdata.site (developer-controlled static file host, "ZTech") | 85% | Path `ZTechVoiceTyping/databases/stickers.json`. The host name is generic across apps. Ownership by the white-label developer is `[inf]` |
| Platform SpeechRecognizer → Google speech services | 90% | `createSpeechRecognizer` default service plus RecognizerIntent. The provider depends on the device `[inf]` |
| Platform TextToSpeech (Google TTS engine on most devices) | 90% | `new TextToSpeech(ctx, listener)` with no engine argument `[obs]`/`[inf]` |
| Firebase (Analytics, Crashlytics, Perf, Remote Config + ABT, FCM, Installations, Sessions) | 98% | SDK roster, `strings.xml` config, `remoteConfigLogs` call sites `[obs]` |
| Google AdMob (next-gen `ads_mobile_sdk`) + UMP | 98% | Ad unit IDs, SDK classes `[obs]` |
| GMS Vision TextRecognizer (on-device OCR, camera translator) | 90% | `ny1.java` ChimeraNativeTextRecognizerCreator (Team 4). No network OCR `[obs]` |
| ML Kit Translate / any LLM / any first-party API | 0% | Not present `[obs]` |

---

## 11. Clone recommendations `[dec]`

1. **Translation:** use ML Kit on-device Translate as the default, especially inside the IME, so typed text never leaves the device. Offer a paid Cloud Translation v3 (or DeepL) fallback through our own proxy, with App Check. Do not use `translate_a/single`: it violates Google's ToS, is rate-limited, and is a store-review and privacy liability.
2. **Live keyboard translate:** use debounce, cancel-previous, and composing-region commit. Never delete committed text by a remembered length.
3. **Dictionary:** freedictionaryapi.com is public, but check its license and rate limits. Prefer bundling an offline Wiktionary-derived DB, with the API as an optional enrichment, and add a cache (Room) keyed by word.
4. **STT:** use `createOnDeviceSpeechRecognizer` (API 31+) when available, falling back to the default service, and expose the choice in settings.
5. **TTS:** keep platform TTS, add an `OnInitListener` that checks the result (the original ignores init failure), and check `isLanguageAvailable`.
6. **Never log** user text or URLs containing it (the original logs to the `urlCheck___` tag).
7. **Base:** use upstream FlorisBoard under Apache-2.0 with attribution. The fork's additions (translate buffer, ads in IME, sticker host) must be reimplemented, not copied.

---

## 12. Open items (static-only; each needs a dynamic or deeper pass)

**12.1: E1 request and response shape plus `&` handling.** Owner: **Backend (dynamic pass).** Confirm the JSON array shape, the 429 and CAPTCHA behaviour under per-keystroke load, and whether `^~^` survives in the output.
**12.2: Remote Config live values and capping.** Owner: **Monetization (dynamic pass).** Includes in-IME native ad rendering cadence (`my_keyboard_native_ad_time`).
**12.3: Conversation chain.** Owner: **Native/UX + Backend.** `i1` (case 2) failed to decompile. Confirm STT → `or0` case 1 → `li1` insert → `gi1` TTS, either with an alternate decompiler (`--show-bad-code`, or baksmali on `i1`) or dynamically.
**12.4: Dictionary ordering.** Owner: **Backend.** RESOLVED statically: API-only, network-gated, no local fallback (section 8.1). A dynamic check of the offline UX is optional.
**12.5: Fork diff.** Owner: **Manifest.** Diff against upstream FlorisBoard to list all patched classes (confirmed so far: the `o98` key dispatch, `FlorisImeService.r()` voice, sticker module, in-IME ads).
**12.6: Firebase Perf URL capture.** Owner: **Backend (dynamic pass).** Determine whether Perf network traces include E1's query string, which contains user text.
**12.7: Sticker image host and `isPremium` gating.** Owner: **Backend + Monetization (dynamic pass).** Capture `stickers.json` to identify the image CDN and how premium packs unlock without billing.
**12.8: Firebase API key restrictions.** Owner: **Anti-tamper/Security.** Determine whether `AIzaSyBL…70kE` is Android-package-restricted. Do not test it with live calls; check through the project owner only.

---

## Appendix: key files

| File | Role |
|---|---|
| `jadx_out/sources/defpackage/my3.java` (`B0`:150, `o0`:1132) | Translate request and parser |
| `jadx_out/sources/defpackage/hh8.java`, `as0.java`, `gh8.java`, `o98.java:1040-1072`, `k32.java:60-72` | In-keyboard live translate |
| `jadx_out/sources/defpackage/or0.java:170-187`, `nh8.java:59` | Translate callers (conversation, translator screen) |
| `jadx_out/sources/defpackage/a32.java`, `nk6.java:79-123`, `l32.java:2713`, `xt3.java:620-700,916`, `j32.java:140-180` | Dictionary API |
| `jadx_out/sources/defpackage/eo5.java:504-600` | Sticker catalog and image download |
| `jadx_out/sources/dev/patrickgold/florisboard/FlorisImeService.java:735-775`, `defpackage/yy2.java` | IME voice typing |
| `jadx_out/sources/defpackage/dn3.java:162-200`, `kl.java:84`, `le0.java:105-118`, `ki1.java:840-851` | In-app RecognizerIntent |
| `jadx_out/sources/defpackage/vr6.java:72-77`, `s65.java:423`, `gi1.java:40-60`, `us1.java:365-393`, `j5.java:340` | TTS |
| `jadx_out/sources/defpackage/rs.java`, `dd0.java`, `li1.java:100`, `hq1.java:248` | Room schema and DAOs |
| `jadx_out/sources/defpackage/zz4.java`, `f05.java`, `q91.java`, `js6.java:188,290-302` | DataStore, SharedPreferences, Remote Config sync |
| `jadx_out/resources/res/xml/remote_config_defaults.xml`, `res/values/strings.xml` | RC defaults, Firebase config |
