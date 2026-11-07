# Team 4: Native, ML and Media/UX

**App:** Voice Keyboard and Translator · `com.heri.language.keyboard.voice.typing` · v1.4 (versionCode 5) · sha256 `6cb257d0…c1f4`
**Stack:** Native Kotlin. FlorisBoard fork (`dev.patrickgold.florisboard`, Apache-2.0) with a Jetpack Compose UI everywhere: the app screens and the keyboard surface itself. Heavy R8 renaming (map id `r8-map-id-53b39b15…`). minSdk 24, targetSdk 36, `extractNativeLibs="false"`.
**Pass:** STATIC-ONLY, with no device and no traffic. Tags: `[obs]` means read directly from dex, resources or binary in this pass. `[dec]` is a design decision or recommendation. `[inf]` is inferred from a tell and needs confirming at runtime (numbered open items in §12).
**Citations:** paths are relative to `voicetranslator_work/`. `src/` stands for `jadx_out/sources/` and `res/` for `jadx_out/resources/res/`.

> **Corrections to the triage brief.**
> (a) **CameraX is not used.** Its Java classes were stripped by R8. Only `Camera2Config$DefaultProvider` and `MetadataHolderService` survive, because the manifest references them. Camera capture uses the **raw `android.hardware.camera2` API** on a `TextureView` (§5.3). `[obs]`
> (b) **There is a first-party content host.** `https://myappsdata.site/heriCornerKeyboard/databases/stickers.json` feeds the sticker catalog (`src/androidx/lifecycle/r.java:663`). It is static JSON hosting, not an API, but the clone needs its own equivalent. `[obs]`
> (c) The translation helper logs the **full gtx URL, including the user's text**, to logcat (`Log.d("urlCheck___", …)` in `src/tf/b.java`, `n()`). `[obs]`
> (d) Firebase **Performance** (`FirebasePerfOkHttpClient`, `FirebasePerfUrlConnection`) and **Crashlytics** strings are present in addition to the SDKs listed in the brief. `[obs]`

---

## 1. Native `.so` roster

Only `arm64-v8a` ships (split `config.arm64_v8a.apk`). The base APK has no `lib/` entries, and there is no armeabi-v7a or x86 split in the XAPK. All four files start with ELF64 little-endian magic `7f 45 4c 46 02 01 01`, `e_machine 0xB7` (AArch64). `[obs]`

**Architecture DoD:** arm64-v8a analysed. armeabi-v7a is **absent**, so no fallback is possible. 32-bit-only devices could not install this XAPK. That is irrelevant to the clone, because every lib here is a stock AndroidX artifact. `[obs]`

| `.so` | Size | Origin artifact (META-INF version) | Java loader still in dex? | Status |
|---|---|---|---|---|
| `libandroidx.graphics.path.so` | 10 096 B | `androidx.graphics:graphics-path:1.0.1` | Yes: `androidx/graphics/path/PathIteratorPreApi34Impl` and `ConicConverter` | Live. Compose/Material shape path iteration on API < 34. `[obs]` |
| `libdatastore_shared_counter.so` | 10 360 B | `androidx.datastore:datastore-core:1.2.1` | Yes: `androidx/datastore/core/NativeSharedCounter` | Live. Multi-process DataStore version counter. `[obs]` |
| `libimage_processing_util_jni.so` | 32 544 B | `androidx.camera:camera-core:1.5.3` | **No** (`ImageProcessingUtil` is absent from both dex files) | **Orphan dead weight.** R8 stripped CameraX, but AGP still packaged the .so. `[obs]` |
| `libsurface_util_jni.so` | 4 896 B | `androidx.camera:camera-core:1.5.3` | **No** (`SurfaceUtil` absent) | **Orphan dead weight.** `[obs]` |

**First-party native code: none.** There is no vendor .so, no `libpairipcore.so`, no Flutter or RN runtime, no TFLite, ONNX, Vosk or Whisper runtime, and no JNI declarations in `src/com/example/**` or `src/dev/patrickgold/**`. The clone needs **zero NDK work**. `[obs]`

---

## 2. Bundled models and data, identified by magic bytes

`base/assets/` was checked by leading bytes, not by extension. `[obs]`

| Asset | Size | Leading bytes | What it really is |
|---|---|---|---|
| `phrases_final.db` | 573 440 B | `53 51 4c 69 74 65 20 66 6f 72 6d 61 74 20 33 00` ("SQLite format 3") | Phrasebook DB. Tables: `topic_groups` (8 rows), `sub_topics` (8), `expressions` (241), `phonetic_maps` (1 683), `language_registry` (26). It uses a **column per locale** (`en-US`, `de`, `zh-CN` … `ru`, about 44 columns), so every phrase is pre-translated offline. Group labels: Daily, Travel, Chat, Work, Funny, … |
| `ime/dict/{english,arabic,spanish,urdu,french,indonesian,portuguese}.txt` | 131–443 KB each, 156 945 lines in total | UTF-8 text, one word per line (for example `a`, `aback`, `abacus`) | **Plain word lists with no frequencies.** The suggestion source (§6). |
| `ime/languagepack/org.florisboard.hanshapebasedbasicpack/han.sqlite3` | **0 B** | (empty) | A stub. The Han shape-based provider (`oj.a`) is registered, but its DB is empty, so Chinese shape input cannot produce candidates. `[obs]` That it is broken at runtime is `[inf]`. |
| `ime/media/emoji/root.txt` | 54 502 B, 3 794 lines | UTF-8 | FlorisBoard emoji table (loaded at `src/k5/u0.java:656`). |
| `ime/media/emoticon/emoticons.json` | 1 333 B | `{` | Floris emoticon set. |
| `ime/theme/org.florisboard.themes/stylesheets/*.json` (6) | small | JSON | Upstream Floris Day/Night (with borderless and pure-night variants), Snygg stylesheets. |
| `ime/keyboard/org.florisboard.{layouts,localization,composers,currencysets}` | 195 files | JSON | Upstream layouts (76 character layouts: qwerty, azerty, arabic, urdu_phonetic, hindi_in, …), popup mappings, composers. |
| `PublicSuffixDatabase.list` | 132 737 B | `00 02 06 02 2a 2e …` | OkHttp eTLD table, not a model. |
| `dexopt/baseline.prof(m)` | — | `pro\0 010` / `prm\0 002` | ART baseline profile. |
| `res/raw/*.mp3` (12) | — | MP3 | Key-press sound packs (§7.3). |
| `res/raw/{splash_loading_lottie,searching_word}.json` | — | JSON | Lottie animations (splash, dictionary search). |

**No ML model is bundled.** Across the merged `base/` tree there is no `TFL3` magic, no ONNX, PyTorch-zip, GGUF, Vosk `am/`/`graph/` directory, `.tflite`, `.pb` or `.bin` model. The only `.bin` is `DebugProbesKt.bin`, from kotlinx-coroutines. `[obs]`

**OCR model:** Mobile Vision `play-services-vision:20.1.3` / `-vision-common:19.1.3` is the *thin* client. It loads the `com.google.android.gms.vision.dynamite.ocr` module and `ChimeraNativeTextRecognizerCreator` from Google Play services at runtime (`src/com/google/android/gms/internal/vision/i.java:1056,1166`). Nothing is local, and there is no `com.google.mlkit` package at all. `[obs]` See open item #3 for the unresolved first-party call site.

---

## 3. On-device vs. server, per capability

| Capability | Where it runs | Engine | Evidence |
|---|---|---|---|
| Voice typing (in keyboard) | **System recognizer.** On-device or Google cloud depending on the device's recognition service. | `android.speech.SpeechRecognizer` | `src/wi/k.java:250`. No `EXTRA_PREFER_OFFLINE` is set, so the device default applies. `[obs]` Whether audio leaves the device depends on the default service. `[inf]` |
| Voice input (in-app screens) | System recognizer UI | `RecognizerIntent` (`ACTION_RECOGNIZE_SPEECH`) activity | `src/ab/l.java:304`, `src/db/d.java:37`, `src/k1/u3.java:58` `[obs]` |
| Text translation (app, keyboard bar, conversation, phrasebook fallback) | **Server**, Google, unofficial | `translate.googleapis.com/translate_a/single?client=gtx` via `HttpURLConnection` with `User-Agent: Mozilla/5.0` | `src/tf/b.java` `n(str, sl, tl)` `[obs]` |
| Phrasebook | **On-device** | Pre-translated SQLite (`phrases_final.db`) | §2 `[obs]` |
| Dictionary | **Server** | `api.dictionaryapi.dev` (Retrofit) | `src/mk/c.java:101` (per the brief) `[obs]` |
| OCR (camera translation) | **On-device, via GMS** | Mobile Vision TextRecognizer, dynamite `ocr` module | §2, §5.3. `[obs]` for the library. The call site is `[inf]`. |
| TTS (speaker buttons) | **On-device** (system TTS engine) | `android.speech.tts.TextToSpeech` | 4 instances: `src/mb/x0.java:435`, `src/mb/c0.java:439`, `src/mb/l.java:678`, `src/db/o.java:606` `[obs]` |
| Word suggestions | **On-device** | Prefix match over a merged word list | §6 `[obs]` |
| Glide typing | **On-device** | FlorisBoard stock glide classifier (`glide__*` prefs) | Prefs present `[obs]`. Classifier internals obfuscated `[inf]`. |
| Language ID / AI rewrite / LLM | **None** | — | No mlkit, langid, openai or gemini strings `[obs]` |
| Sticker catalog | **Server** (static JSON) plus a local cache | OkHttp GET to `myappsdata.site/heriCornerKeyboard/databases/stickers.json`. Packs are cached under app files, filtered to png/jpg/webp/gif. | `src/androidx/lifecycle/r.java:663-700` `[obs]` |

---

## 4. Audio capture and speech pipeline

### 4.1 Keyboard mic (FlorisImeService), continuous dictation loop `[obs]`

Entry point: `wi.k.l()` (`src/wi/k.java:225-275`).

1. Read the pref `getKeyboard().F` (the translation-bar toggle, `show_translation_bar`) to decide where results go.
2. If `RECORD_AUDIO` has not been granted, start the trampoline **`PermissionActivity`** (`FLAG_ACTIVITY_NEW_TASK`, extra `EXTRA_WAS_TRANSLATION_BAR_OPEN`) and return. An IME cannot show a runtime-permission dialog itself.
3. Request audio focus. On API 26+ this uses `AudioAttributes` usage 11 (`USAGE_ASSISTANCE_ACCESSIBILITY`) and content type 1 (`CONTENT_TYPE_SPEECH`). Below 26 it calls `requestAudioFocus(null, STREAM_MUSIC, AUDIOFOCUS_GAIN_TRANSIENT)`. The focus is never abandoned in `m()`. `[obs]` The clone should use `USAGE_ASSISTANT` (16) and abandon focus when it stops. `[dec]`
4. `SpeechRecognizer.createSpeechRecognizer(service)`, stored in the **static** `FlorisImeService.f26155t`.
5. Intent extras: `LANGUAGE_MODEL=free_form`, `PARTIAL_RESULTS=true`, `SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS=2000`, `…POSSIBLY_COMPLETE…=2000`, `LANGUAGE=<active keyboard subtype locale as a BCP-47 tag>`, `calling_package`.
6. `startListening` is posted on the main looper (`wi/h` case 0) and sets the flag `f26156u = true`.

Listener `src/wi/j.java`:
- `onReadyForSpeech` arms a **3 s no-speech watchdog** (`k.b()` posts `g3.k(4)`, which calls `wi.k.m()` to stop). `onBeginningOfSpeech` cancels it.
- `onPartialResults` is **ignored** (empty body). The UI therefore shows no live transcript, only final segments. `[obs]`
- `onResults` takes the first hypothesis:
  - Translation bar **off**: `InputConnection.commitText(text + " ")` into the host app.
  - Translation bar **on**: the text goes into the keyboard's internal source buffer (`ck.a.h/i/g`), which triggers translation (§4.3).
  - Then **auto-restart** after 300 ms (`k.a()`), so dictation is continuous until the user taps stop.
- `onError`: codes 6 (speech timeout) and 7 (no match) re-arm the watchdog and restart. Code 8 (busy) restarts. Any other code stops (`wi.k.m()`: `stopListening` + `destroy` + null the static).
- `onRmsChanged` is empty, so there is no mic level meter. `[obs]`

**Weaknesses worth not copying** `[dec]`: the static recognizer reference leaks across service instances. The 2 s silence extras are ignored by many recognizers. With no partial results the UX feels laggy. The restart loop can spin on devices that return error 7 immediately.

### 4.2 In-app voice (RecognizerIntent) `[obs]`
- Text translation screen: `src/ab/l.java:304`. `LANGUAGE` is set to the source-language code, or forced to `"en"` when a flag is set.
- Conversation screen: `src/k1/u3.java:58`. `LANGUAGE` is the speaking side's code. Results are read back in `src/c1/a.java:135` and `src/ij/i0.java:293`.
- Dictionary screen: `src/db/d.java:37-40`. **Bug:** `putExtra(LANGUAGE, Locale.getDefault())` passes a `Locale` object (Serializable) instead of a String. Recognizers read `getStringExtra`, so the extra is ignored and the device default is used. `[obs]` Its effect is `[inf]`.

### 4.3 In-keyboard live translation bar `[obs]`/`[inf]`
`src/ck/a.java` keeps a Compose state buffer (`f20548a` text plus a cursor `b1`) and source/target defaults `"en"` → `"ur"`. These are overwritten from prefs at `src/androidx/lifecycle/r.java:809-812`.
- Key presses, paste and voice results are edited into this buffer, not into the host field (`d()` insert char, `c()` backspace, `e()` paste).
- Every edit cancels the previous job and launches a coroutine (`androidx.lifecycle.o0` case 10). Jadx failed to decompile it (`jadx-failed-classes.txt`), so any debounce delay is `[inf]`. A sibling path, `ci/s` case 1, calls `tf.b.n(text, ck.a.f20550c, ck.a.f20551d)`.
- The result is written to the host with `ck.a.a()`: in a batch edit, `deleteSurroundingText(previousLen, 0)` then `commitText(translation)`. The host field therefore always shows the **current full translation**, replaced in place. `[obs]`
- The suggestion strip follows the buffer, not the host text, while the bar is open (`src/pj/d.java:195-206`). `[obs]`

**Risk** `[inf]`: every keystroke can trigger a gtx request, so the risk of a 429 or IP block is high (backend team). Also, `deleteSurroundingText(prevLen)` assumes the cursor never moved. If the user taps elsewhere in the host field, unrelated text gets deleted. The clone should debounce for 400–600 ms and use `setComposingText`, which the IME framework scopes safely. `[dec]`

### 4.4 TTS `[obs]`
System `TextToSpeech` with `setLanguage(Locale.forLanguageTag(code))`. If `setLanguage` returns -1 (missing data), the app shows the toast `google_does_not_support_this_language`. One path (`src/bk/o.java:477-490`) strips all non-letter/digit characters before speaking. No voice or rate selection.

---

## 5. Camera and media pipeline

### 5.1 Libraries present vs. used
- CameraX 1.5.3 (core/camera2/lifecycle/view/video) is declared in META-INF, but the **dex holds only 2 CameraX class names**, so it is effectively dead. `[obs]`
- `play-services-vision` is live as a library. Glide is used for images. `[obs]`

### 5.2 Gallery and system camera
- `android.media.action.IMAGE_CAPTURE` is fired after the CAMERA permission check (`src/ab/g.java:295`). `[obs]`
- `image/*` pickers (`src/androidx/lifecycle/r.java:783`, `src/j8/b.java:297`) feed **`ImageCropScreen(imageUri)`** (route `AppRoutes.ImageCropScreen`, `src/ab/e0.java`). It uses a custom crop view (`src/nb/i.java`, `src/nb/k.java`, `getMinCropSize`). `[obs]` Its use for custom keyboard-background images (`upload_image_action_button`, `BackgroundTheme.IMAGE`) and possibly for camera-translation crops is `[inf]`.

### 5.3 Camera translation screen (raw Camera2) `[obs]`
- `src/mb/h.java` (a `TextureView.SurfaceTextureListener`): `CameraManager.getCameraIdList()[0]` (blindly the first camera, usually the back one). It reads `SENSOR_ORIENTATION` and picks the **largest JPEG output size**. It creates `ImageReader(w, h, JPEG=256, maxImages=2)` and calls `openCamera`.
- Shutter (`src/ab/l.java` case 7): `createCaptureRequest(TEMPLATE_STILL_CAPTURE)` targets the ImageReader surface. It sets `CONTROL_AE_MODE=ON`, `FLASH_MODE` OFF or SINGLE from a `mb.d0` toggle (flash_on/flash_off drawables), and `CONTROL_AF_MODE=4` (continuous picture). `JPEG_ORIENTATION` is computed from the sensor and display rotation.
- `onImageAvailable` runs a background thread (`src/b/t1.java` case 5) that writes the JPEG bytes to `cacheDir/CamTrans_<ts>.jpg` and returns the `Uri` on the UI thread.
- OCR runs next through Mobile Vision (`TextRecognizer.detect`, renamed into the merged class `mj.s0.E()`, which builds a grey-scale frame with a luma conversion of 0.299/0.587/0.114). `[obs]` That the camera screen calls it is `[inf]`, because the caller appears to sit in a decompile-failed coroutine (open item #3). Recognized text then goes to gtx, and an empty result shows the toast `no_text_found`. `[inf]`
- **No live preview overlay or AR text replacement.** This is a capture-then-translate flow. `[inf]`

---

## 6. Suggestion and prediction engine `[obs]`

FlorisBoard's NLP manager (`src/nj/e0.java:83`) registers two providers: `org.florisboard.nlp.providers.latin` → `pj.d`, and `org.florisboard.nlp.providers.han.shape` → `oj.a` (dead, see §2).

The **Latin provider is a rewrite, not upstream's**. Upstream FlorisBoard uses a frequency JSON. This one works as follows:
- **Load** (`src/k5/u0.java:819-850`): on first use, all **7 word lists are concatenated into a single `Map<String,Int>`**, every weight = 1, with no per-language separation. A user typing English can be offered Urdu, Arabic or French words that share the prefix. Arabic and Urdu entries cannot prefix-match Latin input, but French, Spanish, Portuguese and Indonesian entries mix freely with English.
- **Query** (`src/pj/d.java:174-262`): take the current word (the composing text, or the trailing letters/apostrophes of the text before the cursor, or of the translation-bar buffer). Lowercase it. Filter `keySet()` with `startsWith(prefix)`. Take the first *N* **in insertion order** (english.txt comes first, so results are alphabetical within English). Emit candidates with a fixed confidence of 0.5.
- There is **no autocorrect, no ranking, no next-word prediction, no learning, and no edit-distance matching.** The prefs `suggestion__enable_floris_user_dictionary` and `…system_user_dictionary` exist, and `FlorisUserDictionaryDatabase` (Room) is present, but `pj.d` never consults them. `[obs]` Whether the user dictionary feeds the strip some other way is `[inf]`.
- **Inline autofill suggestions** are supported (`onCreateInlineSuggestionsRequest`, `supportsInlineSuggestions=true` in `method.xml`, gated on the smartbar and suggestions prefs). `[obs]`
- There is **no `SpellCheckerService`** in the manifest. `[obs]`

**Clone substitute** `[dec]`: one language-scoped dictionary per subtype with unigram frequencies (FlorisBoard upstream's `ime/dict` format, AOSP LatinIME `.dict` wordlists, or HeliBoard dictionaries, all Apache-2.0 or compatible). Add Damerau-Levenshtein autocorrect plus a bigram next-word model, which is still all on-device.

---

## 7. Keyboard view rendering and themes

### 7.1 IME surface architecture `[obs]`
- `FlorisImeService` extends `kj.b`, an abstract `InputMethodService` that implements `LifecycleOwner`, `ViewModelStoreOwner` and `SavedStateRegistryOwner`. `b()` attaches those owners to the IME window's `decorView` so that Compose can run inside the IME window (`src/kj/b.java:83-93`).
- `onCreateInputView()` returns `wi.p`, a subclass of `g3.a` (the renamed Compose `AbstractComposeView`, `extends ViewGroup`). Its `Content()` calls `FlorisImeService.d(…)`, so **the whole keyboard (keys, smartbar, emoji, clipboard, translation bar, sticker panel, native ad) is Compose.** There is no legacy `KeyboardView`. `onCreateCandidatesView()` returns null.
- `onCreateExtractTextView()` wraps the system extract EditText in `wi.o` (fullscreen landscape mode, governed by `keyboard__landscape_input_ui_mode`).
- The window is edge-to-edge (`up.g.J(window,false)`). `onComputeInsets` is overridden for touchable regions. A `WALLPAPER_CHANGED` receiver re-tints the keyboard.
- `onStartInputView` runs the **host-package ad allow/deny list** (about 90 Google apps and launchers, log tag `showAdState__`) before deciding whether to show the in-keyboard native ad. Monetization covers the policy angle, and the clone must not copy this. `[dec]`
- Hand-off from the IME to the app: `getLaunchIntentForPackage` + `putExtra("open_screen", "add stickers screen" | "Keyboard Languages Screen" | "settings")` (`src/wi/k.java:200-207`).

### 7.2 Theme system: two layers `[obs]`
1. **FlorisBoard Snygg** (stock): `theme__mode`, `theme__day_theme_id`/`night_theme_id`, sunrise/sunset scheduling, the `snyggTheme` key, and 6 upstream stylesheets.
2. **The app's own overlay**, the Room entity `KeyboardThemeModel`, with 24 fields: `keyboardBackground`, `keysBackground`, `stickyKeysBackground`, `spaceKeysBackground`, `hotKeysBackground`, `keyPopUpBackground`, `smartBarBackground` and `smartBarKeysBackground` (each a `BackgroundTheme`), plus colors (`keysTextColor`, `keysBorderColor`, `stickyKeysIconColor`, `hotKeysIconColor`, `smartbarIconsColor`, `glideTrailColor`), and dimensions (`keysTextSize`, `keysBorderRadius`/`Width`, `smartbarBorderRadius`, …), `isCustom`.
   - `BackgroundTheme(type, value, opacity, blur)` with `type ∈ {COLOR, GRADIENT, IMAGE, DRAWABLE, NONE}` (enum `pk.a`).
   - Prefs mirror it: `keyboard__bg_theme_type/value`, `keyboard__bg__opacity__value`, `keyboard__bg__blur__value`, `keyboard__btn_theme_*`, `smartbar__bg__*`, `smartbar__icons__*`.
   - Blur uses Compose `RenderEffect.createBlurEffect` (API 31+). On older devices it presumably falls back to no blur. `[inf]`
   - Bundled art: 32 `keyboard_bg0..31` backgrounds and 15 `keybg0..14` key skins (WebP), a color-picker asset set, and a "Trending themes" screen.
   - Custom photo background: picker, then ImageCropScreen, then `BackgroundTheme.IMAGE`. `[inf]`

### 7.3 Input feedback `[obs]`
- `src/vj/a.java`: a `SoundPool(maxStreams=1)` preloads 12 MP3s (drop, clicking, gaming, mechanical, tick, water-splash, pop, classic-type, turbo, tactile, creamy, retro). Prefs: `key_press_sound_enabled/value/volume`.
- **Bug:** sounds are keyed by their **localized display name** (`context.getString(R.string.drop_sound)` …). If the app language changes, the stored value no longer matches and playback silently falls back to "Clicking". `[obs]` The user impact is `[inf]`.
- Floris haptic prefs (`input_feedback__haptic_*`) are stock.

### 7.4 Other in-keyboard features (stock Floris unless noted)
Clipboard history and media (`ClipboardHistoryDatabase`, `ClipboardFilesDatabase`, `ClipboardMediaProvider`), emoji with history, emoticons, incognito, one-handed mode, number row, glide typing with a trail, and physical-keyboard support. **App-specific:** the stickers panel, which commits stickers via `InputConnectionCompat.commitContent`. It probes the host's MIME types in this order: `image/webp.wasticker`, jpeg, png, webp, gif. With no match it falls back to share or copy (`src/wi/k.java:68-120`, FileProvider authority `<pkg>.provider`). The translation bar and voice key are also app-specific. `[obs]`

---

## 8. UI architecture `[obs]`

- **Single activity, Compose, Navigation-Compose 2.9.6 on the classpath, but routing actually runs on **Navigation 3** (`d6/e` is a `@Serializable` snapshot-state `List<NavKey>` back stack used by `kb/*` and `pb/d`; corrected in verification pass 2026-10-17), with type-safe `@Serializable` routes**, Hilt (`hilt-navigation-compose 1.3.0`), Material3 1.4.0, Compose UI 1.10.1, Room 2.8.4, DataStore 1.2.1, WorkManager 2.7.0. Font: Inter (5 TTFs). 134 `values-*` locale directories (mostly library strings). The app has its own `AppLanguageSelectionScreen`.
- `MainActivity` (328 lines) hosts `setContent`. `PermissionActivity` is a mic-permission trampoline for the IME.
- Route set (`com.example.voicetypingkeyboard_s.ui.navigation.AppRoutes.*`, 22 routes):
  `Splash`, `AppLanguageSelectionScreen`, `KeyboardSetupScreen`, `KeyboardTestScreen`, `MainScreen`, `TextTranslationScreen`, `LanguageTranslatorScreen`, `LanguageSelectionScreenForTranslation`, `ConversationScreen`, `CameraTranslationScreen`, `ImageCropScreen`, `DictionaryScreen`, `PhrasebookCategoriesScreen`, `PhrasesListScreen`, `CustomizeKeyboardScreen`, `KeyboardThemeSelectionScreen`, `KeyboardThemeApplyScreen`, `TrendingThemesScreen`, `KeyboardStickersScreen`, `AddStickersToKeyboardScreen`, `KeyboardLanguageSelectionScreen`, `SettingScreen`.
- Navigation lambdas visible in composable signatures: `navigateToKeyboardSetup`, `navigateToApply`, `navigateToTrendingScreen`, `navigateToCustomizationScreen`, `navigateToCustomizeScreen`, `navigateToTestScreen`, `navigateToMainScreen` (`src/kb/a.java:96-98`, `src/cb/n.java`, `src/cb/y0.java`, `src/eb/s.java:105`).
- Ad plumbing is threaded through almost every screen (`inScreenAds_`, `AdsInter_`, `appOpenAds_` log tags). The app-open manager reads the current route. Monetization owns this.

---

## 9. Screen-by-screen UX flow (static reconstruction)

1. **Splash** (`AppRoutes.Splash`, `kb/g`): Lottie `splash_loading_lottie`, UMP/GDPR (`ShowGDPRScreen`), splash interstitial. The wait is 3 s or 12 s according to the brief. `[obs]`/`[inf]`
2. **App language selection**, first run. `[inf]`
3. **Keyboard setup (enable onboarding)**, `ab/f.java`, "Step 1 of 2 / Step 2 of 2":
   - Step 1, *Enable Voice Typing Keyboard*: opens `Settings.ACTION_INPUT_METHOD_SETTINGS` with `:settings:fragment_args_key = <pkg>/dev.patrickgold.florisboard.FlorisImeService` (plus `:settings:show_fragment_args`) to deep-highlight the row (`src/cb/x.java:31-50`). Illustration `enablescreenimage`.
   - Step 2, *Select Voice Typing Keyboard*: `InputMethodManager.showInputMethodPicker()`. Illustration `selectscreenimage`.
   - State detection is **polled every 500 ms** in a coroutine (`src/ok/a.java:88-140`). On API 34+ it uses `getEnabledInputMethodList()` / `getCurrentInputMethodInfo()`. Below 34 it reads `Settings.Secure` `enabled_input_methods` / `default_input_method` and compares the component with `dev.patrickgold.florisboard.FlorisImeService`. Banner ad on this screen.
4. **Keyboard test** ("Try Your Keyboard…", `try_screen_native` ad). `[obs]` strings and keys.
5. **Main/home**: cards for Text translation, Language translator, Conversation, Camera translation, Dictionary, Phrasebook, Customize keyboard, Keyboard stickers, Themes (drawables `*_translationicon`, `main_screen_card_icon`, `trending_card_bg`). Each card is interstitial-gated (`*_card` remote-config keys).
6. **Text translation**: source/target pickers with flags (about 110 `lang_flag_*`), mic (RecognizerIntent), paste from clipboard (debounced; toast `no_text_found` when empty), speaker (TTS), copy, share, swap. History goes to Room `TranslationEntity`.
7. **Conversation**: two-sided mic (RecognizerIntent per side) → gtx → TTS (`speaker_conversation_screen`, `clear_conversation`). Open item #4.
8. **Camera translation**: CAMERA permission dialog (`enable_camera_dialog_icon`), TextureView preview, flash toggle, shutter, gallery (`gallery_selection`), crop, OCR, translate.
9. **Dictionary**: search field, `searching_word` Lottie, dictionaryapi.dev result (meanings, definitions, phonetics), TTS, empty state "Try another word or check the spelling."
10. **Phrasebook**: categories (topic_groups) → phrases (expressions) from the local SQLite in about 44 locales, with TTS.
11. **Customize keyboard**: tabs `keystab`, `texttab`, `soundtab`, `stickytab`, `topbartab`, `backgroundtab` (drawables). Color picker, gradient, image, opacity, blur. Leave-without-saving warning (`warning_customizationscreen_leave`). Apply → `KeyboardThemeApplyScreen` (native ad).
12. **Trending themes / theme selection** → apply.
13. **Keyboard stickers / add stickers**: catalog from `myappsdata.site` JSON, with download per pack (`can_t_download_stickers`). The `StickersModel.isPremium` flag exists, but no paywall was found (Monetization item #1).
14. **Settings**: app language, keyboard languages (`KeyboardSelectedLanguageEntity`), layout, auto-correction (*sic*: no autocorrect engine exists, see §6), glide typing, key sound, popup on key-press, suggestions, clipboard, feedback, rate app, share, privacy policy (drawables `*_setting_screen`).
15. **Inside the keyboard**: smartbar (mic, translation bar toggle, clipboard, sticker, settings, language switch), the native ad strip (`KeyboardNativeBanner`, cooldown logic), the emoji/clipboard/sticker panels, and a mic-permission trampoline.

---

## 10. Clone substitutes (legally distinct build) `[dec]`

| Area | Winner's approach | Clone substitute |
|---|---|---|
| IME base | Fork of FlorisBoard with Compose | **Fork FlorisBoard upstream** (Apache-2.0). Keep `LICENSE`/`NOTICE`, credit it in the app's About screen, and rename the package away from `dev.patrickgold.*`. Fix the `method.xml` `settingsActivity` (it points to an undeclared `SettingsLauncherAlias`). |
| Speech (keyboard) | `SpeechRecognizer`, static, partials ignored | `SpeechRecognizer` held in a service-scoped holder. **Show partials** with `setComposingText`. RMS waveform. On API 33+ check `isOnDeviceRecognitionAvailable` and offer `createOnDeviceSpeechRecognizer`. Optional offline pack: Vosk (Apache-2.0) or whisper.cpp (MIT, adds about 40–75 MB per model, NDK). |
| Translation | Unofficial gtx scrape on every keystroke | **ML Kit on-device Translation** (free, about 30 MB per language pair, 59 languages) as the default, with an optional paid cloud API (Google Cloud Translation v3, DeepL, Azure) behind your own proxy. Debounce for about 500 ms, cache, and never log the text. |
| OCR | Mobile Vision (deprecated) on a raw Camera2 capture | **ML Kit Text Recognition v2** (bundled Latin 16.x, or the unbundled GMS variant for size) plus **CameraX** `PreviewView` + `ImageCapture`/`ImageAnalysis`. Add a live overlay mode as a differentiator. |
| TTS | System TTS | System TTS with an engine/voice picker and a missing-data install intent (`ACTION_INSTALL_TTS_DATA`). |
| Suggestions | Merged flat wordlist, prefix match, no ranking | Per-locale frequency dictionaries (upstream Floris or AOSP-licensed wordlists), autocorrect, bigrams, and a user dictionary hookup. |
| Phrasebook | Pre-translated SQLite, 241 phrases × 44 locales | Write your own phrase set (do **not** reuse `phrases_final.db`) and pre-translate it with ML Kit or a paid API at build time. |
| Dictionary | dictionaryapi.dev (free, English only) | Same API or Wiktionary/Wordnik. Cache results and show attribution. |
| Themes | Snygg plus a 24-field overlay, 47 bundled bitmaps | Keep Snygg (upstream) and add a smaller own overlay. Commission or generate **original** backgrounds, and do not reuse `keyboard_bg*`/`keybg*`. |
| Key sounds | 12 MP3s, keyed by localized name | Own sound assets (CC0 or commissioned), keyed by **stable IDs**. |
| Stickers | Remote JSON on a shared host | Own CDN plus a manifest, or drop the feature. Use `commitContent` with the same MIME probe order (that is a platform pattern, not copyrightable). |
| Camera | Raw Camera2, `cameraIdList[0]`, largest JPEG | CameraX with lens selection, resolution capped at about 12 MP for OCR, EXIF-correct rotation. |
| Native | 4 AndroidX .so files (2 orphaned) | Nothing to build. Drop camera-core unless you use CameraX. Ship arm64-v8a plus armeabi-v7a via App Bundle. |

---

## 11. Notable defects and anti-patterns seen (do not copy)

1. Full translation URL **with user text logged** to logcat (`tf/b.java`, tag `urlCheck___`). `[obs]`
2. Suggestion dictionary mixes 7 languages with flat weights, so suggestions are the wrong language and badly ordered. `[obs]`
3. `han.sqlite3` is a 0-byte stub, so Han shape input is non-functional. `[obs]` (impact `[inf]`)
4. `db/d.java` passes a `Locale` object as `EXTRA_LANGUAGE`. `[obs]`
5. Key sounds are keyed by localized label. `[obs]`
6. `deleteSurroundingText(prevLen)` live-replace in the host field can delete user text if the cursor moves. `[inf]`
7. Static `SpeechRecognizer` / `Handler` fields on the service class (leak, threading). `[obs]`
8. The settings link in `method.xml` points to an undeclared alias. `[obs]`
9. Two orphaned CameraX .so files and the whole CameraX dependency are unused. `[obs]`
10. The "Auto correction" settings screen exists with no autocorrect engine behind it. `[obs]` The drawable is `auto_correction_setting_screen`. Whether it toggles anything is `[inf]`.

---

## 12. Open items (static-only, need runtime confirmation)

| # | Owner | Item |
|---|---|---|
| 3 | Native/UX (this team) | **OCR call site.** The Mobile Vision library is confirmed live (dynamite `ocr`, `mj.s0.E()` detect path), and ML Kit is confirmed absent. The first-party caller that turns `CamTrans_*.jpg` into text was **not located**. Candidates are decompile-failed coroutines (`androidx/lifecycle/o0`, `p0`, `n0/v0`, `va/e`). Confirm at runtime with a hook on `TextRecognizer.detect` or `SparseArray<TextBlock>` producers, or by watching whether GMS downloads the `ocr` module. |
| 4 | Backend | Conversation-mode flow (RecognizerIntent → gtx → TTS), plus the turn-taking and auto-speak behaviour. |
| 5 | Native/UX | Whether `SpeechRecognizer` audio goes to the cloud (the device-default service, Google app). Check on a device with network disabled. |
| 6 | Native/UX | The debounce interval (if any) of the in-keyboard translation bar (`androidx/lifecycle/o0` case 10 failed to decompile). Measure the request rate per keystroke with mitmproxy. |
| 7 | Native/UX | Whether the Floris or system user dictionary is ever surfaced in the suggestion strip, given that `pj.d` ignores it. |
| 8 | Native/UX | Whether ImageCropScreen serves camera translation, custom backgrounds, or both. |
| 9 | Native/UX | Whether the blur fallback below API 31 looks acceptable, and the theme render cost on low-end devices. |
