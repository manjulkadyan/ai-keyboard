# Voice Keyboard and Translator — Clone Build Plan

**Basis:** `FEATURE_BACKLOG.md` (build-order column), `REVERSE_ENGINEERING_REPORT.md` §12, `report/native.md` §10 (clone substitutes), `report/monetization.md` §12, `report/antitamper.md` §9 (security spec), and `/Users/manjul/StudioProjects/workspace/apk/ANDROID_FAST_CLONE_PLAYBOOK.md` (template repo, convention plugins, `:core:*` / `:feature:*` modules, Navigation 3, Hilt, MVVM with a single `StateFlow<UiState>`, UMP-first `:core:ads`, `:core:billing`).
**Assumption:** one engineer, full-time, Kotlin + Compose + M3. Estimates are working days including unit tests, excluding store assets and marketing.
**Scope guard:** a **legally distinct** app with the same mechanics. FlorisBoard is forked from **upstream** (Apache-2.0, keep `LICENSE`/`NOTICE`, credit in About) — never from the winner's APK. No code, art, sounds, phrase DB, language catalog or string table from the winner is reused.

## Architectural decisions taken up front

| Decision | Choice | Why |
|---|---|---|
| IME base | Fork upstream FlorisBoard as `:ime` (Android library), rename `dev.patrickgold.*`, declare the settings alias that `method.xml` points to | The winner proves the fork is viable; its broken `SettingsLauncherAlias` shows what to check. |
| Translation engine | **ML Kit on-device Translation** behind a `Translator` interface in `:core:translation`; optional paid cloud tier (Cloud Translation v3 or DeepL) only via **our own proxy**, POST bodies | The winner's keyless gtx scrape has no SLA, risks 429s and sends every keystroke in a URL. On-device is the marketing line: "your typing never leaves the phone". |
| Speech | Platform `SpeechRecognizer` in a service-scoped holder; partial results via `setComposingText`; `createOnDeviceSpeechRecognizer` / `EXTRA_PREFER_OFFLINE` on API 31+ | Free, matches the winner, fixes its static leak and missing live text. |
| OCR / camera | CameraX (`Preview` + `ImageCapture` + `ImageAnalysis`) + ML Kit Text Recognition v2 | Mobile Vision is deprecated; `ImageAnalysis` enables a later live mode. |
| Sensitive fields | Translation bar, voice, clipboard history and AI rewrite disabled for every password variation and `IME_FLAG_NO_PERSONALIZED_LEARNING` | Closes the winner's highest-severity unknown (open item 3) by design. |
| Ads | `:core:ads` per playbook: UMP first, one unit per placement, RC-tunable cooldowns with capping on by default, **no ads inside the IME window** | Avoids the winner's policy exposure and gives per-placement eCPM. |
| Billing | `:core:billing` (Play Billing 8.3.0 per playbook catalog), `queryPurchasesAsync` on every cold start, server-side validation once AI rewrite exists | The winner's premium flag is a no-op; ours must be real. |
| Logging / backup / transport | R8 `-assumenosideeffects` for `Log.d/v/i`; `dataExtractionRules` excluding clipboard, history, user dictionary; NSC with cleartext off; FileProvider scoped to `cache/share/` | Each is a winner defect from Team 5. |
| Anti-tamper | No PairIP; Play Integrity checked server-side for paid AI features only | PairIP's `System.exit(0)` can kill an IME process mid-typing. |
| Navigation / DI / data | Navigation 3, Hilt, Room, Preferences DataStore, Retrofit + OkHttp, versions from the playbook catalog | Playbook defaults. |

---

## Sprint 0 — Template and spine (5 days)

| # | Task | Days |
|---|---|---|
| 1 | Clone the playbook template repo, rename package/app id, convention plugins, version catalog; modules `:app`, `:core:{common,ui,designsystem,network,database,datastore,ads,billing,analytics,remoteconfig}` | 1.5 |
| 2 | Import upstream FlorisBoard as `:ime`, rename package, keep NOTICE, fix `method.xml` settings alias, build green | 2 |
| 3 | Hardening baseline: backup rules, NSC, R8 log stripping, scoped FileProvider, no speculative permissions | 0.5 |
| 4 | `:core:ads` skeleton: UMP consent flow + RC defaults schema (one unit per placement), no placements yet | 1 |

**Exit:** app and keyboard install; keyboard types in any app with stock behaviour.

## Sprint 1 — Keyboard you can enable and talk to (10 days) · build order 1–3

| # | Feature | Task | Days |
|---|---|---|---|
| 5 | ime-core-keyboard | Per-locale subtypes (start with the winner's 7 languages: en, es, fr, pt, ar, ur, id) with our own frequency dictionaries; clipboard, emoji, glide from upstream; sensitive-field policy object used by every later feature | 4 |
| 6 | onboarding-settings | Splash (no forced ad wait, ≤3 s), 2-step enable/select with settings-row highlight and lifecycle-aware polling, test-keyboard screen, Settings shell, app-language picker via per-app locales, product analytics events | 3 |
| 7 | voice-typing-ime | Mic key, translucent permission trampoline, service-scoped recognizer, partials as composing text, waveform, restart loop with a hard cap, abandon audio focus on stop | 3 |

**Exit:** a user can install, enable, select and dictate into any app. **Risk:** subtype/dictionary packaging in the fork; timebox task 5 at 4 days and ship with upstream dictionaries if needed.

## Sprint 2 — Translation core and the signature feature (10 days) · build order 4–6

| # | Feature | Task | Days |
|---|---|---|---|
| 8 | text-translator | `:core:translation` (ML Kit models, download manager UI, language list), `:feature:translator` screen with mic, paste, copy, share, swap, TTS with `LANG_NOT_SUPPORTED` + install-data intent, Room history | 4 |
| 9 | keyboard-translation-bar | Smartbar toggle, internal buffer, 400–500 ms debounce + LRU cache, write-back with `setComposingText`/`finishComposingText`, disabled in sensitive fields, source auto-detect via ML Kit language ID | 4 |
| 10 | process-text-share-translate | `ACTION_PROCESS_TEXT` activity (replace-in-place when not read-only), `ACTION_SEND` text target, result bottom sheet | 2 |

**Exit:** translate-as-you-type works offline once models are downloaded. **Validate on day 1:** typing latency with ML Kit on a low-end device (target under 150 ms per debounced request).

## Sprint 3 — Companion translation suite (9 days) · build order 7–9

| # | Feature | Task | Days |
|---|---|---|---|
| 11 | keyboard-themes-customizer | Theme model (backgrounds COLOR/GRADIENT/IMAGE with opacity and blur, key colours, radius, spacing), editor with live keyboard preview, original art set, key sounds keyed by stable ids, apply into `:ime` | 5 |
| 12 | conversation-mode | Two-sided screen using in-app `SpeechRecognizer` (no system dialog), auto-speak toggle, face-to-face split, history | 2.5 |
| 13 | offline-phrasebook | Our own phrase set (authored, pre-translated at build time), Room prepopulated asset, categories, TTS, romanisation | 1.5 |

**Exit:** home hub reaches parity with the winner except camera, stickers, dictionary.

## Sprint 4 — Camera, dictionary, stickers (9 days) · build order 10–12

| # | Feature | Task | Days |
|---|---|---|---|
| 14 | camera-translation | CameraX capture + gallery photo picker + crop, ML Kit Text Recognition v2 (Latin + Devanagari + CJK), block-level overlay of translated text, share | 5 |
| 15 | dictionary | Retrofit client with on-disk cache and attribution, long-press-to-define from the suggestion strip | 1.5 |
| 16 | sticker-packs | Own CDN manifest (versioned, checksummed), download manager, sticker panel with `commitContent` MIME probing and share fallback | 2.5 |

**Exit:** full functional parity with the winner, plus offline translation and PROCESS_TEXT.

## Sprint 5 — Monetization and the premium wedge (10 days) · build order 13–14

| # | Feature | Task | Days |
|---|---|---|---|
| 17 | ad-monetization | Wire placements: banner on home/customize/onboarding, natives on theme apply/test screens, interstitials on feature entry with RC cooldown (capping on by default), app-open with route blocklist; none in the IME | 3 |
| 18 | ad-monetization (billing) | `:core:billing` subscription "Pro" (remove ads, premium themes, cloud translation tier, AI rewrite quota), paywall screen, restore on cold start | 3 |
| 19 | ai-rewrite-smartbar | Backend `/v1/rewrite` proxy with App Check + Play Integrity and server-side daily quota; smartbar action (grammar, formal, casual, shorten, translate-and-polish) with undo; blocked in sensitive fields | 4 |

**Exit:** revenue paths live; premium has a reason to exist that the winner cannot match.

## Sprint 6 — Suggestions quality and release hardening (9 days) · build order 15

| # | Feature | Task | Days |
|---|---|---|---|
| 20 | suggestions-autocorrect | Damerau-Levenshtein autocorrect over per-locale frequency dictionaries, bigram next-word, user-dictionary learning that respects incognito | 5 |
| 21 | release | Privacy policy that matches actual flows, Data Safety form, Play In-App Review trigger after N successful translations, baseline profile, accessibility pass, store listing under a non-confusing name | 2 |
| 22 | QA | Matrix: API 24/28/31/34/36, password fields in browsers and banking apps, RTL (Arabic/Urdu), low-RAM device, Auto Backup restore | 2 |

**Exit:** release candidate.

---

## Timeline summary

| Sprint | Days | Features |
|---|---:|---|
| 0 | 5 | template, fork, hardening, ads skeleton |
| 1 | 10 | ime-core-keyboard, onboarding-settings, voice-typing-ime |
| 2 | 10 | text-translator, keyboard-translation-bar, process-text-share-translate |
| 3 | 9 | keyboard-themes-customizer, conversation-mode, offline-phrasebook |
| 4 | 9 | camera-translation, dictionary, sticker-packs |
| 5 | 10 | ad-monetization (ads + billing), ai-rewrite-smartbar |
| 6 | 9 | suggestions-autocorrect, release, QA |
| **Total** | **62** | about 12.5 working weeks for one engineer |

**Fastest viable launch:** Sprints 0–2 plus the ads placements from task 17 (about 28 days) give a shippable "private voice + translate keyboard" MVP that already beats the winner on privacy and inbound integrations.

## Dependencies and open items that affect this plan

| Open item (report §10) | Effect on plan |
|---|---|
| 1 (translation-bar debounce) | Closed 2026-10-17: the competitor has no debounce (cancel-previous + stale guard only). None on our design. |
| 2 (OCR caller) | None; different stack. |
| 3 (password fields) | Already designed out in Sprint 1 task 5 policy object. |
| 4 (real RC ad cadence) | Tune our RC defaults after launch from our own data, not theirs. |
| 13 (Firebase key restrictions) | Restrict our own key to package + SHA-1 in Sprint 0. |
| 22 (blur below API 31) | Test in Sprint 3; fall back to a pre-blurred bitmap. |
