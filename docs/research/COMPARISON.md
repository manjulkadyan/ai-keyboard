# Voice + Translator Keyboard: two-app comparison

Two competing voice/translator keyboards, torn down side by side to choose a build target. This was a static-only pass on 2026-10-17 UTC. APK claims are tagged `[dec]` (decoded from manifest, resources or source) or `[inf]` (inferred from a tell). No APK claim is tagged `[obs]` because no device traffic was captured. The only `[obs]` tags in this file are Play listing facts, which were seen directly on the store page (`COMPETITORS.md`, `playstore/targets.md`).

- **App H**: "Voice Keyboard and Translator" (Heri Corner). Teardown in `voicetranslator_work/`.
- **App L**: "Voice Keyboard - Voice Typing" (LogiApp Tech). Teardown in `voicetyping_work/`.

**One-line read.** Both apps come from **one white-label template** built on upstream FlorisBoard (Apache-2.0). The evidence: template packages `com.example.voicetypingkeyboard_s` (H) and `…_z` (L), the same `myappsdata.site/<vendor>/databases/stickers.json` sticker host, the same 7 plain word lists, the same smartbar additions, and the same in-keyboard ad with a hard-coded deny-list of exactly 86 packages (the two lists are identical, verified 2026-10-17). Both were released on 2026-09-19 `[dec]`/`[obs]`. They differ in a handful of engineering choices, and those choices decide which one to follow.

## Side by side

| | **App H — Voice Keyboard and Translator** | **App L — Voice Keyboard - Voice Typing** |
|---|---|---|
| Package | `com.heri.language.keyboard.voice.typing` | `com.logiapps.language.keyboard.voice.typing.translator` |
| Version | 1.4 (code 5) | 1.3 (code 4) |
| Artifact sha256 | `6cb257d0…c303c1f4` | `6052cf1a…cf7c6f8` |
| minSdk / targetSdk | 24 / 36 | 26 / 37 |
| Installs / rating `[obs]` | 56.6K, no rating yet | 59.5K, no rating yet |
| Listing monetization `[obs]` | Ads + IAP $5.49–6.49 | Ads only |
| **Stack** | Kotlin, Compose (app + IME), **Navigation 3** (22 `@Serializable` routes), Hilt-style DI, Room 2.8.4 + DataStore, HttpURLConnection + OkHttp + Retrofit `[dec]` | Kotlin, Compose (app + IME), **Navigation-Compose 2.10** (Nav3 on the classpath but unused), Hilt 2.60.1, Room 2.8.4 (4 DBs) + DataStore, HttpURLConnection + Retrofit/OkHttp, CameraX 1.6.2 `[dec]` |
| **IME architecture** | FlorisBoard fork. `FlorisImeService` on a renamed IME+Lifecycle base (`kj/b`), whole keyboard is Compose (`wi/p`). `method.xml`: one language-neutral `AsciiCapable` subtype, `supportsInlineSuggestions=true`, `settingsActivity` → **undeclared** `SettingsLauncherAlias`. 7 keyboard languages exposed, 71 Floris presets unreachable `[dec]` | Same FlorisBoard fork and the same `method.xml` defect (dangling `SettingsLauncherAlias`, so the system gear is probably broken `[inf]`). 71 subtype presets and 156 layouts available `[dec]` |
| Smartbar additions | mic, translation-bar toggle, clipboard, stickers, settings, language switch, in-IME native ad `[dec]` | voice, translator bar, emoji, clipboard, stickers, in-IME native ad `[dec]` |
| **STT engine** | Platform `SpeechRecognizer` in the IME: **continuous loop**, auto-restart after 300 ms, 2 s silence extras, 3 s no-speech watchdog, restarts on errors 6/7/8. Partials ignored. Recognizer kept in a **static field** (leak). No prefer-offline. In-app screens use the `RecognizerIntent` dialog `[dec]` | Platform `SpeechRecognizer` in the IME: **one utterance per tap**, 5 s watchdog, partials and RMS ignored. No prefer-offline. In-app screens use `RecognizerIntent` (MAX_RESULTS 5) and refuse to start when offline `[dec]` |
| **Translation engine** | Keyless `translate.googleapis.com/translate_a/single?client=gtx`, `User-Agent: Mozilla/5.0`. No timeout, retry, cache or fallback; failure returns `""` `[dec]` | Keyless `translate_a/single?client=dict-chrome-ex` (Chrome dictionary-extension client id), `User-Agent: Mozilla/5.0`. No timeout or retry; failure returns `""` `[dec]` |
| Translate-as-you-type | Buffer in Compose state. **Cancels the previous job** before each launch and commits a result only if the buffer is unchanged (stale guard). **No debounce**: the decompile-failed body (H-1) was recovered in the 2026-10-17 verification pass and contains no `delay()`; every edit still issues a request because the blocking HTTP call is not interrupted by cancellation. Paste and voice also feed the buffer `[dec]` | Buffer in `hh8`. **No debounce and no cancel-previous**, so N keystrokes make N requests and stale responses can win `[dec]`/`[inf]` |
| TTS | Platform `TextToSpeech`. Only `LANG_MISSING_DATA` handled `[dec]` | Platform `TextToSpeech` with a sentence chunker `[dec]` |
| OCR | Play services Mobile Vision on **raw Camera2** (`TextureView` + `ImageReader`). CameraX packaged but stripped. OCR call site not recovered `[dec]`/`[inf]` | Play services Mobile Vision via dynamite, on **CameraX** `LifecycleCameraController`. Fully decompiled `[dec]` |
| Dictionary | `api.dictionaryapi.dev`, English only `[dec]` | `freedictionaryapi.com/api/v1/entries/en/{word}?translations=true`, English only `[dec]` |
| Extra content | **Offline phrasebook**: 241 phrases × ~43 locales in a bundled SQLite asset `[dec]` | **Guess-word mini game** (levels, hints, ad slots) `[dec]` |
| **AI features** | **None.** No LLM, no language ID, no bundled model (verified by magic bytes) `[dec]` | **None.** No LLM, no bundled model `[dec]` |
| Themes | Upstream Snygg plus a **typed 24-field Room `KeyboardThemeModel`** with `BackgroundTheme(type, value, opacity, blur)`. 47 bundled bitmaps, trending themes screen `[dec]` | Upstream Snygg plus **about 110 flat prefs** (`g03`) and a Room themes table. About 116 bundled skins. The renderer probably reads prefs directly `[inf]` |
| Suggestions | Prefix match over a merged map of 7 word lists, flat weight. No autocorrect, ranking, learning or next-word. The user dictionary is never consulted `[dec]` | Prefix `startsWith` over 7 lists, fixed 0.5 score. No autocorrect or bigrams. About 81% of the Arabic list is English. Hindi has no list `[dec]` |
| **Ads** | AdMob Next-Gen + UMP, no mediation. **13 interstitial placements on 1 shared unit**, fired on feature *entry*. 30 s cooldown only when `*_capping_enabled`, and every one defaults to false. App-open on every foreground. **In-IME native ad**, hidden for 86 hard-coded packages. RC fetched before every splash (3 s minimum interval) `[dec]` | AdMob Next-Gen 1.4.0 + UMP 4.0.0, no mediation. **10 interstitial placements on 1 preloaded slot**. The cooldown is on by default only for `home_word_game`. **Interstitial on every Translate press** when one is loaded. App-open with 4 h expiry. **In-IME native ad**, hidden for the same 86 hard-coded packages as H (list verified identical, plus the app's own package). RC 24 h interval `[dec]` |
| **Billing** | Play Billing **8.3.0 linked, zero first-party calls**. Premium setter is an empty method. This conflicts with the listing's IAP range (open question 4) `[dec]`/`[obs]` | **No billing library.** `is_user_premium` is never written `true` `[dec]` |
| **PairIP tier** | **Licensing only** (`com.pairip.licensecheck`, no `libpairipcore.so`, no VM blobs). Sideloading may trigger `System.exit(0)` inside the IME process `[dec]`/`[inf]` | **None** (0 VM methods, no wrapper) `[dec]` |
| Attestation / pinning / NSC | none / none / none `[dec]` | none / none / none `[dec]` |
| **Privacy posture** | With the bar on, the whole buffer goes out in the URL on every edit (keys, backspace, paste) and is also logged to logcat (`urlCheck___`). Firebase Perf traces the call. No password-field guard found (open item H-3). Host packages and the FCM token are logged. `allowBackup=true` with no rules. FileProvider exposes `external-path "."` `[dec]`/`[inf]` | Same egress: the whole buffer per keystroke in the URL and in logcat (`urlCheck___`). Voice results logged (`TextCheck----`). No password-field check (open item L-1). Clips flagged `IS_SENSITIVE` are still stored. `allowBackup=true` with no rules. FileProvider `files-path`/`cache-path`/`external-cache-path "."` `[dec]`/`[inf]` |
| Inbound integrations | none: no deep links, PROCESS_TEXT, SEND, widgets or tiles `[dec]` | none `[dec]` |
| Signing | Play App Signing, RSA 4096, v2+v3 `[dec]` | Play App Signing, RSA 4096, v2+v3, SourceStamp verified `[dec]` |
| **Code quality / decompile coverage** | 14,821 classes, 226 errors in 108 classes. **2 core-path failures** in the main run: the translation-bar dispatch (R8-merged into `androidx/lifecycle/o0`, since recovered with `jadx --comments-level debug --single-class`) and the camera OCR caller (still open). No money-path failures `[dec]` | 12,845 classes, 239 errors in 113 classes. **No core or money-path failures.** 3 first-party data/UX failures (`i1` conversation, `ki1` partial, `ad3` word-list preload) `[dec]` |
| Code-quality tells | static recognizer leak, audio focus never abandoned, 9 dead RC keys, GA4 event names over 40 characters, template package left in production `[dec]` | dangling settings alias, 3 natives configured but never read, `^~^` escape never reversed, template package left in production `[dec]` |

## Feature parity

Legend: **●** shipped and works · **◐** shipped but defective · **○** absent.

| Capability | H | L | Notes |
|---|:-:|:-:|---|
| Keyboard (layouts, glide, emoji, clipboard, one-hand) | ● | ● | Stock upstream FlorisBoard in both `[dec]` |
| Per-locale system subtypes | ◐ | ◐ | Both declare one language-neutral subtype `[dec]` |
| IME settings gear | ◐ | ◐ | Both point at an alias that does not exist `[dec]`; crash vs no-op `[inf]` |
| Voice typing in keyboard | ● continuous | ◐ single-shot | H's restart loop is the better base. Neither shows live partials `[dec]` |
| Translate-as-you-type | ◐ | ◐ | Both send keystrokes to a keyless endpoint. H cancels stale jobs; L does not `[dec]` |
| Text translator + TTS | ● | ● | H stores Room history; L has no history `[dec]` |
| Voice conversation | ● | ● | L's middle step failed to decompile `[inf]` |
| Camera translation | ◐ | ◐ | Both use deprecated Mobile Vision. L sits on CameraX, H on raw Camera2. Neither has a live overlay `[dec]` |
| Dictionary | ● en only | ● en only | Both online-only, no cache `[dec]` |
| Offline phrasebook | ● | ○ | H only `[dec]` |
| Mini game | ○ | ● | L only `[dec]` |
| Themes + customizer + key sounds | ● typed model | ● flat prefs | H's model is cleaner to port `[dec]` |
| Stickers (`commitContent` MIME chain) | ● | ● | Identical probe order in both `[dec]` |
| Suggestions / autocorrect | ◐ | ◐ | Prefix-only in both. Wrong-language, unranked results `[dec]` |
| Offline translation | ○ | ○ | Neither app has it |
| AI rewrite / grammar / reply / chat | ○ | ○ | Neither app has it |
| PROCESS_TEXT / share-to-translate | ○ | ○ | Neither app has it |
| Billing / paywall | ◐ linked, dead | ○ | Neither app can take a payment `[dec]` |
| In-IME ad with Google-app deny-list | ● | ● | Do-not-copy in both teardowns |

**Headline parity read.** The two apps are about 90% the same product. H is ahead on the things a user feels: continuous voice, cancel-previous on the translation bar, translation history, the phrasebook, a typed theme model, and Navigation 3. L is ahead on things an engineer feels: CameraX, no PairIP, full decompile coverage of the translate chain, and a sane RC fetch interval. They share every defect that matters: keystroke egress, no password guard, logcat leaks, the in-IME ad, the dead premium flag, the broken settings gear and prefix-only suggestions.

## Engine and cost architecture

| | H | L |
|---|---|---|
| STT | platform recognizer, cloud allowed | platform recognizer, cloud allowed |
| Translation | keyless `client=gtx` | keyless `client=dict-chrome-ex` |
| OCR | GMS Mobile Vision (dynamite) | GMS Mobile Vision (dynamite) |
| TTS | platform | platform |
| Dictionary | dictionaryapi.dev | freedictionaryapi.com |
| Vendor-owned backend | **none** (one static JSON on myappsdata.site) | **none** (same host, different path) |
| Bundled models | none | none |
| First-party native code | none (4 AndroidX `.so`, 2 orphaned) | none (4 AndroidX `.so`) |
| Monthly cost to the vendor | about zero `[inf]` | about zero `[inf]` |

Three points to carry into the build:

1. **Both vendors cut their engine cost to zero, and the IME pays the price.** Calling a keyless endpoint is not a strategy you can build a P&L on in any app. Inside a keyboard it also sends keystrokes to a consumer endpoint with no contract `[dec]`. The clone's default must be **on-device** (ML Kit Translation, the on-device recognizer where available), with cloud quality offered only through our own proxy. That is the privacy claim neither competitor can make.
2. **Neither vendor runs a server.** There is no auth, no quota and no entitlement authority `[dec]`. The only features that need a server in our product are AI rewrite/dictation and purchase verification. Both are Pro-tier features, so the backend is paid for by the users who need it.
3. **No AI anywhere.** The whole "AI keyboard" segment in `COMPETITORS.md` (SwiftKey, CleverType, Starnest, Wispr, Typeless) is out of these apps' reach. That segment is where the money is ($15–30/month tiers at 0.8–1.7M installs within months `[obs]`).

## Security and correctness scorecard

| | H | L | Winner |
|---|---|---|:-:|
| Keystroke egress | whole buffer per edit, no debounce; cancel-previous + stale guard | whole buffer per keystroke, no cancel | H, barely |
| Password-field guard | none found | none found | — |
| Logcat leaks | URL with text, host packages, FCM token | URL with text, voice results, dictionary words, FCM token, host packages | H, barely |
| Ads inside the IME, hidden from Google apps | yes (86) | yes (86, the identical list) | — |
| Anti-tamper risk to the IME process | PairIP licensing can `System.exit(0)` on sideload `[inf]` | none | **L** |
| Backup hygiene | no rules | no rules | — |
| FileProvider scope | `external-path "."` plus `files-path`/`cache-path "."` (broader) | `files-path`/`cache-path`/`external-cache-path "."` | L |
| Network security config / pinning | absent / none | absent / none | — |
| Secrets | Firebase client key only (`AIzaSyCd…lqfo`) | Firebase client key only (`AIzaSyBL…70kE`) | — |
| Decompile coverage of core paths | 2 core gaps | 0 core gaps | **L** |

Both are honour-system products. Neither ships a vendor credential worth notifying anyone about. Both Firebase client keys are public by design and were **never called**.

---

# Build-target recommendation

## Use **App H's product shape** as the spec base. Build on **upstream FlorisBoard**, not on either APK. Borrow App L's engineering choices.

Both apps are thin forks of the same template, so "build target" means *which app's product and data model we specify against*. The keyboard engine itself comes from upstream FlorisBoard source in either case.

**Why App H:**

1. **Its voice loop is the right base.** Continuous dictation with a 300 ms restart, 2 s silence extras and an error-aware restart policy is what "voice typing" users expect. L's single-shot tap-per-utterance flow is the weaker UX `[dec]`. We fix H's static-field leak and add live partials as composing text. Neither app does the latter.
2. **Its translation bar is closer to correct.** It cancels the previous job and also takes paste and voice input into the buffer `[dec]`. L fires one uncancelled request per keystroke. Our version adds a 400–500 ms debounce and an on-device engine.
3. **Its data model is portable.** The typed 24-field `KeyboardThemeModel` with `BackgroundTheme(type, value, opacity, blur)` maps directly onto a small `ThemeSpec` that generates a Snygg sheet. L's about 110 loose prefs would have to be redesigned first.
4. **It matches the playbook stack.** Navigation 3 with typed routes and Room translation history match `ANDROID_FAST_CLONE_PLAYBOOK.md` defaults. L ships Nav3 but doesn't use it. (Correction, verification pass 2026-10-17: the playbook's default is **minSdk 26**, which is L's value, not H's 24. Choosing 24 for wider South Asia/MENA reach `[inf]` is a deliberate deviation from the playbook, not a match.)
5. **It has more product.** The offline phrasebook and translation history are features. L's extra is a word game, which is ad inventory.
6. **It signals that IAP is the plan.** Play Billing 8.3.0 is linked and the listing shows an IAP range `[obs]`, even though no purchase code runs `[dec]`. The developer meant to add a paid tier. We actually ship one.

**What we do NOT take from H:** the keyless gtx engine, logcat logging, the in-IME ad and its 86-package deny-list, uncapped feature-entry interstitials, RC fetched before every splash, PairIP licensing (it can kill the IME process), the broad FileProvider, the dead billing stub, and any asset: phrasebook DB, `keyboard_bg*`/`keybg*` art, MP3s, string tables or language catalog.

## What to borrow from App L

| Borrow | Why it beats H's version | Cost |
|---|---|---|
| **CameraX** for the camera translator (`Preview` + `ImageCapture`, then add `ImageAnalysis`) | H uses raw Camera2 with `getCameraIdList()[0]`. CameraX is the prerequisite for a later live overlay. We pair it with ML Kit Text Recognition v2, not deprecated Mobile Vision | in plan (4–5 days) |
| **No PairIP / no anti-tamper wrapper** | A licence check that can `System.exit(0)` must not live in an IME process. Use Play Integrity server-side for Pro AI calls only | free |
| **Remote Config discipline**: 24 h interval, ad config only | H fetches before every splash with a 3 s minimum, which adds launch latency and quota | free |
| **Sentence-chunked TTS** | Long translations need chunking; H passes the whole string to `speak()` | ~0.5 day |
| **freedictionaryapi.com's `translations=true` shape** as the online enrichment layer over an offline DB | It returns cross-language glosses. dictionaryapi.dev is English-only definitions. Both are hard-coded to `en`; ours is multi-language | ~0.5 day |
| **Guess-word / daily word game**, as a retention surface with **rewarded** hints | A known pattern in the category: Goldlab's Hangman/Learn module sits at 12.2M installs `[obs]`. Keep it off the MVP critical path | 2 days (post-MVP) |
| **Narrower FileProvider roots** | H exposes `external-path "."`. Go narrower than both: `cache/share/` only | free |

**Do not borrow L's** per-keystroke uncancelled dispatch, its single-shot recognizer, its Translate-button interstitial on every press, or its flat theme prefs.

## Gaps vs competitors (from `COMPETITORS.md`)

| Gap | Who owns it today | Our angle | MVP? |
|---|---|---|:-:|
| **AI rewrite / grammar / tone in the keyboard** | SwiftKey (free with Copilot), CleverType, Starnest (GPT-4o), Grammarly `[obs]` | Smartbar action through our `/v1/rewrite` proxy with a server-side free quota, then Pro. Never runs in sensitive fields | ● |
| **AI-polished voice plus translation in one step** ("speak Urdu/Hindi, send clean English/Arabic") | Nobody. Wispr/Typeless polish but don't translate, at $15–30/month. The clones translate raw ASR with no cleanup `[obs]`/`[inf]` | Dictate → LLM cleanup → translate → composing preview with undo. **The headline differentiator** | ● |
| **Offline, privacy-first voice and translation** | Only one 100K-install app claims offline translation `[obs]`. Both targets declare that they share voice recordings `[obs]` | ML Kit on-device translation in the IME by default, on-device recognizer preferred, "your typing never leaves the phone" | ● |
| **Regional / low-resource depth** (Urdu, Hindi, Arabic, Pashto, Bengali) and **Roman Urdu / Hinglish transliteration** | The clone vendors have the users but no AI. The AI keyboards stop at about 40 languages and are English-centric `[inf]` | Real per-locale subtypes and frequency dictionaries, plus a transliteration layer | ◐ (subtypes and dictionaries in MVP, transliteration in v1.1) |
| **Typing quality** (autocorrect, ranking, next word) | Gboard and SwiftKey. The targets are prefix-only `[dec]`, and the big voice clones sit at 3.7–3.9 stars `[obs]` | Frequency dictionaries + Damerau-Levenshtein + bigrams | ● (minimal) |
| **Two-way chat translation** (translate incoming messages) | CleverType reads messages through Accessibility for AI reply `[obs]` | High Play-policy risk for an IME plus an Accessibility service. **Defer** pending a policy review | ○ |
| **Mid-price tier** ($2–4/month or regional pricing) between ad clones and $15–30 AI subs | Nobody `[inf]` | Free ad tier + low-price Pro (remove ads, AI quota, cloud translation, premium themes) | ● |
| **Stability as a ranking lever** | Clones with crash/ANR changelogs `[obs]` | Baseline profile, IME latency budget, no ad SDK in the IME process path | ● |

## Merged feature set: ranked for the MVP (input to the PRD)

Ranked by dependency and by value for this market, merging both backlogs and the competitor gaps. "From" names the origin. ◆ marks something **neither app has**. Scores are `(I×2+M)/D` from each app's `features.json` where the feature exists (H / L).

| # | Feature | From | Score H / L | Why it is in the MVP |
|---:|---|---|:-:|---|
| 1 | **Core keyboard** from upstream FlorisBoard: real per-locale subtypes (start with en, es, fr, pt, ar, ur, id, **hi**), a working settings activity, a **sensitive-field policy object** (password variants + `IME_FLAG_NO_PERSONALIZED_LEARNING` → no translate, voice, AI, learning or clipboard history), clipboard auto-expiry | both, fixed | 3.25 / 4.33 | The product. Fixes the broken gear and the missing password guard in both apps by design |
| 2 | **Onboarding**: 2-step enable/select with lifecycle-aware polling, try-it field, one funnel event per step, splash ≤3 s with no forced ad wait | both | 10.0 / 10.0 | Activation decides everything. Neither app measures its funnel |
| 3 | **Voice typing in the IME**: continuous loop, **live partials as composing text**, RMS waveform, on-device recognizer preferred (API 31+), spoken punctuation, permission trampoline | H's loop + ◆ partials | 6.5 / 6.5 | The promise in the name. Neither app shows live text |
| 4 | ◆ **On-device translation core** (ML Kit Translation + language ID, model download manager) behind one `Translator` interface, with an optional cloud tier through **our proxy** | ◆ neither | — | Replaces the keyless endpoints. Everything below depends on it |
| 5 | **Text translator** with mic, paste, swap, TTS (chunked), share, Room history + favourites | H (history) + L (chunker) | 5.5 / 6.0 | Table stakes and the main ad-carrying screen |
| 6 | **Translate-as-you-type bar**, on-device: 400–500 ms debounce, cancel-previous, LRU cache, `setComposingText` preview + explicit commit/undo, hard-off in sensitive fields | H's shape, ◆ on-device | 4.67 / 3.67 | The category's signature feature, turned from a liability into the privacy claim |
| 7 | ◆ **PROCESS_TEXT + SEND "Translate"** entry from any app | ◆ neither | 8.0 / — | One activity over #4. Puts the app in every text-selection menu |
| 8 | **Suggestions / autocorrect, minimal**: per-locale frequency dictionaries, Damerau-Levenshtein, user-dictionary hookup that respects incognito | ◆ (both prefix-only) | 2.25 / 3.0 | The ratio under-ranks it. Retention depends on it |
| 9 | ◆ **AI rewrite in the smartbar** (grammar, formal, casual, shorten, translate-and-polish) via `/v1/rewrite`, App Check + Play Integrity, server-side daily free quota | ◆ neither | 4.33 / 4.33 | Moves the product into the AI-keyboard segment. The Pro upsell |
| 10 | ◆ **AI voice polish + translate** ("dictate → clean → translate") reusing #3, #4 and #9 | ◆ neither, and no competitor combines them | — | The headline differentiator (`COMPETITORS.md` gap 1) |
| 11 | **App-side ads**: UMP first, one unit per placement, a persisted global cap on **every** placement, `onPaidEvent` revenue logging, **no ads inside the IME**, no interstitial on a primary action | both, corrected | 3.5 / 3.5 | Free-tier revenue without the account-level policy risk |
| 12 | **Pro subscription**: remove ads, AI quota, cloud translation tier, premium themes. `queryPurchasesAsync` on cold start, server-side verification, mid-price/regional pricing | ◆ (H's billing is dead, L has none) | — | Neither competitor can take money. This is the mid-price gap |
| 13 | **Themes + customizer + key sounds**: `ThemeSpec` → Snygg sheet, live preview, photo background with crop/blur, **original** art | H's model | 4.0 / 3.0 | Store-screenshot driver and premium content. The **MVP cut line** sits below this row |
| 14 | Voice conversation (two-sided, in-app recognizer, auto-speak, face-to-face) | both | 3.67 / 3.33 | Reuses #3–5; cheap once the core exists |
| 15 | Camera translator: CameraX + ML Kit Text Recognition v2, gallery and share ingest, block overlay | L's camera stack | 3.67 / 3.67 | Category parity. `ImageAnalysis` sets up a later live mode |
| 16 | Dictionary: offline Wiktionary-derived DB (CC-BY-SA) + online enrichment + "define selection" | both, ◆ offline | 6.0 / 4.0 | Small, ad-carrying |
| 17 | Offline phrasebook with **our own** authored phrase set | H | 4.0 / — | Offline value for travel users. Cheap once #4 exists |
| 18 | Stickers from our own CDN manifest and licensed packs, `commitContent` MIME chain | both | 4.0 / 4.0 | Needs content operations first |
| 19 | ◆ Roman Urdu / Hinglish transliteration | ◆ neither | — | Regional-depth wedge (`COMPETITORS.md` gap 3) |
| 20 | Daily word game with rewarded hints | L | — / 3.5 | Retention and rewarded inventory. Not core |

**Explicitly out of v1:** two-way chat translation through Accessibility (policy review first), live camera overlay (v2, after #15), AI chat/assistant inside the keyboard, and every item on either app's do-not-copy list: the in-IME ad and deny-list, keyless endpoints, text logging, backup without rules, and reuse of any asset.

## Merged estimate

| | Days | Source |
|---|---:|---|
| Skeleton, fork, hardening, ads skeleton | 5 | H plan Sprint 0 |
| Core keyboard, onboarding, voice typing (#1–3) | 10 | H plan Sprint 1 |
| Translation core, translator, bar, PROCESS_TEXT (#4–7) | 10 | H plan Sprint 2 |
| Minimal suggestions/autocorrect (#8) | 4 | L plan Sprint 2 task 8 |
| AI rewrite backend + smartbar (#9) | 5 | L plan Sprint 5 task 17 |
| AI voice polish + translate (#10) | 3 | estimate on top of #3/#4/#9 `[inf]` |
| Ads placements + Pro billing + paywall (#11–12) | 6 | H plan Sprint 5 tasks 17–18 |
| Themes + customizer (#13) | 5 | H plan Sprint 3 task 11 |
| **MVP (#1–13)** | **48** | ~10 weeks for one engineer |
| Conversation, camera, dictionary, phrasebook, stickers (#14–18) | 13 | H plan Sprints 3–4 |
| Transliteration + word game (#19–20) | 5 | `[inf]` 3 + L plan task 18 (2) |
| Hardening, privacy audit, QA matrix, release | 6 | L plan Sprint 6 |
| **Full v1** | **72** | ~14–15 weeks |

Add 25–30% for CI, store review, IME device-fleet bugs (OEM keyboards, RTL, low-RAM) and ML Kit model-download UX: **about 3 months to the MVP and 4.5 months to full v1** on one engineer. The schedule risks are ML Kit translation latency on low-end devices (validate on day 1 of #6, target under 150 ms per debounced request) and LLM unit cost for #9–10 (enforced server-side from day one).

## Open questions the teardown cannot answer

| # | Question | Blocks | Owner |
|---:|---|---|---|
| 1 | Does either app's translation bar fire inside password fields? (H open item 3, L open item 1) | Nothing we build: our policy object designs it out. It matters for competitive and privacy messaging | user (rooted AVD + mitmproxy) |
| 2 | Do the Firebase Perf traces carry the `q=` text? (H 18, L 2) | Messaging only | user (dynamic capture) |
| 3 | ~~H's translation-bar debounce (failed class `androidx/lifecycle/o0` case 10)~~ **Closed 2026-10-17:** no debounce; cancel-previous + stale guard | Nothing: we debounce at 400–500 ms regardless | integrator (done, static) |
| 4 | Why the H listing shows IAP $5.49–6.49 when no billing code runs. Is a server-side flag or a later build involved? | Pricing benchmark only | user (Play listing over time + device walk) |
| 5 | Live RC ad cadence in both apps (H 4, L 14–15) | Nothing: we tune our own caps from our own data | user (one RC capture per app) |
| 6 | Cloud vs on-device recognizer behaviour per device (H 6, L 6) | Disclosure copy for our default-recognizer fallback | user (airplane-mode test) |
| 7 | ML Kit translation quality for ur/hi/ar/ps/bn compared with the cloud tier | Decides which pairs default to the cloud tier in Pro | product + engineer (our own eval, Sprint 2) |
| 8 | Play policy position on AI features and on Accessibility-based reply in an IME | Scope of #9–10 copy; the "out of v1" item | product/legal |
| 9 | Base-plan ids, regional prices and trial length for Pro | **Not recoverable from any APK.** Our own Play Console decision | product |

---

_Both teardowns study **structure and mechanics** to build a legally distinct product. The keyboard engine comes from upstream FlorisBoard source under Apache-2.0 with NOTICE retained, never from either APK. No source code, assets, word lists, phrase databases, theme art, sounds, string tables or language catalogs from either app are reused. Extracted secrets appear only redacted to the first 8 + last 4 characters, and no vendor API was called with any extracted value._

_Verification pass (2026-10-17):_ I tried to refute the recommendation against source and could not. H's continuous voice loop, cancel-previous translation bar, Nav3, translation history and vestigial Billing 8.3.0 are all confirmed. L has no translation-history table (Room tables: clipboard, conversation_messages, keyboard_themes, words). Corrections applied in this file: H's translation-bar debounce is now resolved (none; cancel-previous plus a stale guard), L's deny-list is exactly 86 and identical to H's, L's FileProvider roots now include `external-cache-path`, and the minSdk 24 "playbook match" claim is fixed (the playbook uses 26). Details are in each app's `REVERSE_ENGINEERING_REPORT.md`, under "Verification pass (2026-10-17)".
