# Voice Keyboard - Voice Typing — Clone Build Plan

**Basis:** the `FEATURE_BACKLOG.md` build-order column; `REVERSE_ENGINEERING_REPORT.md` §3, §8 and §12; `report/native.md` §11 (substitutes); `report/antitamper.md` §11 (security spec); `report/monetization.md` §10. Conventions follow `/Users/manjul/StudioProjects/workspace/apk/ANDROID_FAST_CLONE_PLAYBOOK.md`: multi-module `:core:*` / `:feature:*`, `build-logic` convention plugins, a single `libs.versions.toml`, Hilt, MVVM with unidirectional `StateFlow<UiState>`, Navigation 3 for the app shell, UMP-first `:core:ads`, `:core:billing` (Play Billing 9.1.0) with server-side verification, and Remote Config kill switches.
**Assumption:** one engineer, full-time, Kotlin + Compose. Estimates are working days including tests, excluding store assets and marketing.
**Scope guard:** a **legally distinct** app. The keyboard engine comes from **upstream FlorisBoard source (Apache-2.0)**, not from this APK. No code, asset, word list, sticker, theme art, sound or string table from the target is reused. LICENSE/NOTICE and an open-source attribution screen ship from day one.

## Architectural decisions taken up front

| Decision | Choice | Why (target evidence) |
|---|---|---|
| Keyboard engine | Vendor upstream FlorisBoard as `:ime:*` modules, renamed package, upstream tag pinned | The target is itself a FlorisBoard fork. Upstream gives layouts, glide, clipboard and emoji for free. |
| Translation | **ML Kit on-device Translation** by default; optional cloud tier (Cloud Translation v3 or DeepL) **through our own proxy** | The target uses the unofficial `translate_a/single` endpoint: ToS violation, rate limits, and per-keystroke egress. |
| IME privacy rule | No network call from the IME without an explicit user action, except ML Kit model downloads. Hard-off for translate/AI/learning in `TYPE_TEXT_VARIATION_PASSWORD`, `*_WEB_PASSWORD`, `NUMBER_PASSWORD` and `IME_FLAG_NO_PERSONALIZED_LEARNING` fields | The target's translate branch has no field-type check (open item 1). |
| Logging | A Timber tree that drops everything in release; lint rule bans `Log.*` in `:ime:*` | The target logs translate URLs, voice results and host packages. |
| STT | Platform `SpeechRecognizer`, preferring `createOnDeviceSpeechRecognizer` (API 31+) when the locale is supported | Free, and the target's single-shot version is easy to beat. |
| AI | Our backend only (`POST /v1/rewrite`), App Check + Play Integrity, server-side quota | The target has none. Never put a provider key in the APK. |
| Ads | App screens only, never inside the IME window. A global frequency cap on **every** placement. No interstitial on a primary action button | The target puts an ad inside the keyboard, hides it from Google apps, and shows an uncapped Translate-button interstitial. |
| Entitlement | Play Billing 9.1.0, `queryPurchasesAsync` on cold start, server-side verification | The target has a dead `is_user_premium` flag and no billing. |
| Backup | `dataExtractionRules` exclude clipboard, user dictionary, conversation history and theme images | The target has `allowBackup=true` with no rules. |
| IME metadata | Per-locale `<subtype>` entries; `settingsActivity` points at a real exported-false settings activity | The target's settings alias does not exist. |
| Navigation | Navigation 3 for the app shell (playbook §6). FlorisBoard keeps its own internal IME navigation | The target uses Navigation-Compose 2 and ships Nav3 unused. |

### Module tree

```
app/                       :app — @HiltAndroidApp, MainActivity, NavDisplay
build-logic/               convention plugins (application, library, compose, hilt, feature)
core/common, ui, designsystem, network, database, datastore, analytics, ads, billing, remoteconfig, testing
core/translate             ML Kit Translation + cloud-proxy fallback behind one TranslateRepository
core/speech                SpeechRecognizer wrapper (IME + app), RecognizerIntent launcher
core/tts                   TextToSpeech wrapper with sentence chunker + availability checks
ime/florisboard            upstream engine (vendored, renamed)
ime/bridge                 IME ⇄ app contracts (prefs bridge, translate/AI/voice ports)
feature/onboarding, settings, translator, conversation, camera, dictionary, themes, stickers, game, paywall
backend/ (separate repo)   rewrite proxy, translate proxy, purchase verification, sticker catalog
```

---

## Sprint 0 — Skeleton and spine (5 days)

| # | Task | Days |
|---|---|---|
| 1 | Gradle scaffold per playbook: `build-logic`, `libs.versions.toml`, module tree above, `dataExtractionRules`, no cleartext, release log-stripping tree | 2 |
| 2 | Vendor upstream FlorisBoard at a pinned tag into `:ime:florisboard`; package rename; LICENSE/NOTICE + attribution screen; CI build green | 2 |
| 3 | `:core:designsystem` tokens + `AppTheme`, edge-to-edge, Nav3 host, Hilt graph, Crashlytics + Analytics wrappers | 1 |

**Exit:** the app installs, the renamed keyboard can be enabled by hand and types English.

## Sprint 1 — Keyboard foundation and activation (10 days) · build order 1–3

| # | Task | Days |
|---|---|---|
| 4 | **Core keyboard:** strip unused upstream screens, our branding, per-locale `method.xml` subtypes, a real `settingsActivity`, password/no-learning field policy in `ime/bridge`, clipboard auto-expiry + sensitive-clip masking | 5 |
| 5 | **Onboarding:** splash, app-language picker (per-app locales), step 1 (IME settings + 500 ms poll + resume recheck), step 2 (picker + default-IME check), a try-it field, one funnel event per step | 3 |
| 6 | **Settings + growth:** keyboard settings surface over FlorisBoard prefs, privacy policy, In-App Review at a happy-moment trigger, share, feedback, FCM with deep-link routing | 2 |

**Exit:** a stranger can install, enable and type. Funnel analytics are live.

## Sprint 2 — Voice, suggestions, translation engine (11 days) · build order 4–6

| # | Task | Days |
|---|---|---|
| 7 | **Voice typing:** `:core:speech`, on-device recognizer preference, partial results as composing text, RMS-driven mic animation, continuous dictation with auto-restart, spoken punctuation, permission trampoline activity | 4 |
| 8 | **Suggestions/autocorrect:** a frequency trie built offline from openly licensed frequency data for the launch locales, Damerau-Levenshtein autocorrect, a small bigram table, learned words (skipped in incognito and password fields) | 4 |
| 9 | **Translation engine + text translator:** `:core:translate` (ML Kit models, download manager, language ID), translator screen with mic, TTS (`:core:tts` with chunker), share, history and favourites in Room | 3 |

**Exit:** the core promise ("voice typing + translator keyboard") works offline once models are downloaded.

## Sprint 3 — In-keyboard translate, money, conversation (10 days) · build order 7–9

| # | Task | Days |
|---|---|---|
| 10 | **Translate-while-typing bar:** IME bar with 300–500 ms debounce, cancel-previous, `setComposingText` preview + explicit commit/undo, hard-off in sensitive fields, language pair picker | 3 |
| 11 | **Ads + consent + billing:** playbook `:core:ads` (UMP before `MobileAds.initialize`, a global cap on every placement, `onPaidEvent` revenue logging, banners/natives on app screens only), `:core:remoteconfig` with kill switches, `:core:billing` (Remove Ads / Pro subscription), paywall screen, purchase-verification endpoint | 5 |
| 12 | **Voice conversation:** two-sided mic, direction by side, Room history, auto-speak, face-to-face split mode | 2 |

**Exit:** the app earns money in a policy-safe way. The signature in-keyboard translate feature ships without keystroke egress.

## Sprint 4 — Personalization and visual translation (12 days) · build order 10–12

| # | Task | Days |
|---|---|---|
| 13 | **Themes:** typed `ThemeSpec` → generated Snygg stylesheet, gallery (colours/gradients/wallpapers/custom), editor with live preview, photo background via photo picker + crop + blur, our own key-sound set, premium packs gated on Pro | 6 |
| 14 | **Camera translator:** CameraX Preview + ImageCapture + ImageAnalysis, ML Kit Text Recognition v2, crop, translate via `:core:translate`, gallery and share-sheet ingest | 4 |
| 15 | **Dictionary:** offline Wiktionary-derived DB (CC-BY-SA attribution) for launch languages, optional online enrichment with a Room cache, TTS pronunciation, "define selection" smartbar action | 2 |

**Exit:** feature parity with the target on every screen it ships, plus offline behaviour it lacks.

## Sprint 5 — Differentiators and engagement (10 days) · build order 13–15

| # | Task | Days |
|---|---|---|
| 16 | **Stickers:** catalog on our CDN with ETag caching, our own licensed packs, download manager, IME panel with `commitContent` MIME fallback chain, premium packs on Pro | 3 |
| 17 | **AI rewrite (greenfield):** backend `/v1/rewrite` (LLM provider key server-side, App Check + Play Integrity, per-user daily quota), smartbar action with tone presets, composing preview + undo, one-time disclosure, Pro unlimited | 5 |
| 18 | **Guess-word game:** daily puzzle, levels, rewarded-ad hints, streak | 2 |

**Exit:** v1 feature-complete with one capability (AI rewrite) and one privacy story (on-device translate) the target cannot match.

## Sprint 6 — Hardening and release (6 days)

| # | Task | Days |
|---|---|---|
| 19 | Privacy audit: verify no IME network or log call in password fields (instrumented test), backup exclusion check, Data Safety form, IME disclosure copy, privacy policy | 2 |
| 20 | Performance: baseline profile for the IME, cold-start and keyboard-open latency budgets, ML Kit model size and download UX | 2 |
| 21 | Policy pre-flight (playbook "Play Policy Pitfalls"): no ads in the IME, no exit or primary-action interstitials, consent and privacy-options entry, subscription disclosure; staged rollout | 2 |

**Total:** about 64 engineer-days (roughly 13 weeks) for v1.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| ML Kit Translation quality is below Google web translate for some pairs | Cloud-proxy tier (Pro) for the translator screen; keep the IME bar on-device by default |
| On-device STT is unavailable for many locales | Fall back to the default recognizer with a clear "audio is processed by your device's speech service" disclosure |
| FlorisBoard upstream churn | Pin a tag; keep fork patches in `ime/bridge` rather than editing the engine |
| Ad revenue is lower without the in-IME ad | Pro subscription (AI rewrite quota, premium themes and stickers, no ads) plus a higher-quality app-side ad cadence; this is the intended trade-off |
| Open items 1, 2 and 4 could change the policy narrative | These affect only competitive messaging, not the clone's design, which already enforces the stricter rule |
