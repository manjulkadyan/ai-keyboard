# Product Requirements Document: AI Voice + Translator Keyboard (Android)

**Status:** Draft v1.0 for review
**Date:** 2026-10-17
**Owner:** Product (Manjul)
**Working product name:** `[TBD: working name "Saaz Keyboard"]`. See Open decision OD-01. The store title pattern comes from COMPETITORS §5: "Voice Keyboard: AI Translator".

**Source material** (all under `/Users/manjul/StudioProjects/workspace/apk/ai-keyboard/`)

| Tag | Document |
|---|---|
| **App H** | `voicetranslator_work/REVERSE_ENGINEERING_REPORT.md`, `features.json` (21 features), `FEATURE_BACKLOG.md`, `BUILD_PLAN.md`. App H is "Voice Keyboard and Translator" by Heri Corner (PK): 56,550 installs, ads + IAP. |
| **App L** | `voicetyping_work/REVERSE_ENGINEERING_REPORT.md`, `features.json` (18 features + 11 nonFeatures), `FEATURE_BACKLOG.md`, `BUILD_PLAN.md`, `CLASS_COVERAGE.md`. App L is "Voice Keyboard - Voice Typing" by LogiApp Tech (PK): 59,497 installs, ads only. |
| **CMP** | `COMPARISON.md`: the merged ranked list #1–20, the build-target decision and the estimate. |
| **COMPETITORS** | `COMPETITORS.md`: the market table, gaps §4 (G1–G6 below) and ASO §5. |
| **PS** | `playstore/targets.md`: store listings and Data safety for H and L. |
| **PB** | `ANDROID_FAST_CLONE_PLAYBOOK.md`: architecture conventions, §6 paywall patterns, §7 policy pitfalls. |

**Clean-room rule.** This PRD describes *behaviour*, not implementation. We do not copy any code, resource, string, layout file, theme preset, sticker, phrase list, word list, icon, Lottie file or translation table from App H or App L. Engineers who build features read this PRD and the TRD. They do not read the decompiled sources.

The base is upstream **FlorisBoard** (Apache-2.0), forked from upstream, not from either APK. We keep its `LICENSE` and `NOTICE`, show an open-source licences screen (FR-09), and state in the store listing that the keyboard is "based on FlorisBoard". Both target APKs are white-label FlorisBoard forks (App H §6; App L CLASS_COVERAGE §1). That shared ancestry is the only thing we inherit, and we take it from upstream.

**Evidence discipline.** Every requirement cites its origin: `(App H §x)`, `(App L §x)`, `(H-F:<id>)` or `(L-F:<id>)` for a features.json entry, `(CMP #n)`, `(COMPETITORS §x / Gn)`, `(PB §x)`, or `(gap)` for something neither app has. Where a source finding is inferred rather than read from code, the source marks it `[inf]`; this PRD inherits that uncertainty and does not upgrade it. Secrets found in the APKs are never reproduced. Firebase keys appear only in redacted form in the source reports (e.g. `AIzaSyCd…lqfo`) and are not used.

---

## 1. Vision & Positioning

### 1.1 Problem

Hundreds of millions of Android users type in more than one language every day: Urdu, Hindi, Arabic, Bengali, Indonesian, Spanish or Portuguese mixed with English. Many of them also speak more fluently than they type. The tools they have today fail them in four ways.

- **Gboard and SwiftKey** are excellent keyboards, but their translation is online-only and shallow. They offer no AI rewrite on free Android (SwiftKey has Copilot tone in some markets). Neither supports Roman Urdu input that converts to Nastaliq (COMPETITORS §2, G3).
- **Wispr Flow and Typeless** prove that AI-polished dictation is a product people will pay $15–30 a month for. Their Android presence is weak or nonexistent, and they are priced for US knowledge workers (COMPETITORS §2, G1, G6).
- **Translator-keyboard clones** range from about 50K to 12M installs and live on ads: Goldlab, Ganesha, Digital Apps club, and our two targets H and L. Both targets have the same failures:
  - they send every keystroke to a keyless Google `gtx`/`dict-chrome-ex` endpoint, with no password or incognito check on that path (App H §3.1; App L §3.2; runtime egress from password fields is still unproven, CMP open Q1)
  - they cannot work offline (App H §3.1 "refuses offline"; App L §3.3)
  - they run 13 (H) and 10 (L) interstitial placements on one ad unit, with the cooldown off by default (App H §5; App L §5)
  - they inject a native ad *inside the keyboard*, gated by an 86-package deny-list (App H §5; App L nonFeatures)
  - they declare "no data collected" while sharing voice recordings and device IDs (PS)
- **Ratings in this category hover around 3.7–3.9** for clones with crash/ANR changelogs (Ganesha 3.89, Digital Apps club 3.71). That means there is room for a well-built product (COMPETITORS §2, G5).

### 1.2 Vision

> **Speak or type in your language. Send it polished, in theirs.** It works offline, it stays private, and it lives inside every app.

The product is one keyboard that does three things well.

1. **Voice → text that reads well.** Continuous dictation shows live partial results. One optional tap runs *AI polish*, which removes fillers, adds punctuation and fixes grammar. Polish can also *translate* in the same step (G1).
2. **Type in your language → send in theirs.** An in-keyboard translate bar handles this. It runs on-device by default, offers an optional cloud-quality tier, and works in any app. It never runs in password or incognito fields (G2).
3. **Regional depth.** First-class Urdu, Hindi, Arabic and Bengali layouts. **Roman Urdu / Hinglish transliteration** turns "kya haal hai" into "کیا حال ہے" or "क्या हाल है" (G3). There is also a Translate entry in the system text-selection menu (PROCESS_TEXT).

### 1.3 Positioning vs alternatives

| Alternative | Their strength | Our wedge |
|---|---|---|
| **Gboard** (13.9B installs, 4.48) | Best core typing, glide, free | Offline translate bar; AI polish + translate on voice; Roman Urdu → Nastaliq; South-Asia-first defaults. We do not try to beat Gboard at core typing (non-goal NG-1). |
| **SwiftKey** (1.97B) | Prediction, Copilot tone in some markets | AI rewrite on free Android with a fair quota; offline/private by default. |
| **Grammarly KB** (60.7M, $29.99–139.99) | Writing quality | Voice-first and multilingual; one tenth of the price; regional pricing. |
| **Wispr Flow / Typeless** ($15–30/month) | AI-polished dictation | Native Android IME; translation in the same flow; mid-price Pro of about $2–4/month, regional (G6, CMP mid-price gap). |
| **Translator KB clones** (H, L, Goldlab, Ganesha, All Chats) | Install volume, "translator" ASO | Same job done properly: no ads inside the keyboard, offline, private, faster, and a crash-free bar under 0.3% (G2, G5). We are the only listing in the category with a credible offline + privacy claim. The single existing offline claimant has 100K installs (COMPETITORS §2). |

### 1.4 Positioning statement

For bilingual and multilingual Android users, especially Urdu, Hindi and Arabic speakers, who speak and type across languages every day, **[Product]** is a voice and translator keyboard that turns speech or typing into polished text in any language, inside any app. Unlike Gboard, it translates offline and polishes dictation with AI. Unlike translator-keyboard clones, it never shows ads inside the keyboard and never sends what you type in password or private fields anywhere.

---

## 2. Target Users & Personas

### 2.1 Segments (priority order)

1. **South-Asian bilinguals (PK, IN, BD, Gulf diaspora).** These users write Urdu, Hindi or Bengali, often in Roman script, mixed with English. This is where H and L get their traffic (both are PK publishers, PS) and where the transliteration gap is (G3).
2. **Arabic speakers (MENA and diaspora).** RTL layout, Arabic ↔ English translation, voice.
3. **Global multilingual messengers (ID, BR, MX, ES, FR).** They need everyday chat translation. This is the core translator-keyboard demand (COMPETITORS §1).
4. **Voice-first and low-literacy-in-script users.** They speak fluently but type slowly in the native script.

### 2.2 Personas

| Persona | Profile | Languages | Primary jobs | Pain today | What wins them |
|---|---|---|---|---|---|
| **P1 Ayesha** (Lahore, 24, student / part-time tutor) | Mid-range Android (4 GB RAM), mobile data, WhatsApp-heavy | Urdu (Roman + Nastaliq), English | Writes formal English emails to clients; chats in Roman Urdu | Switches between three apps; Gboard has no Roman → Nastaliq; clones show ads | Roman Urdu transliteration; AI rewrite "Formal" |
| **P2 Rahul** (Delhi, 31, sales rep) | Budget phone, drives between meetings | Hindi, Hinglish, English | Dictates messages while moving; replies to English-speaking customers | Dictation output is messy, with no punctuation and filler words | Continuous voice + AI polish; Hinglish → Devanagari |
| **P3 Omar** (Dubai, 38, logistics supervisor, from Egypt) | Works with Urdu-, Hindi- and English-speaking crews | Arabic, English, some Urdu | Translates short instructions in chat; reads foreign signage and forms | Online-only translators fail on site; worries about privacy | Offline translate bar. Camera OCR and conversation mode moved to a future app (OD-21) |
| **P4 Fatima** (Karachi, 52, homemaker) | Low tech confidence | Urdu speaker, limited typing | Voice messages to family abroad, sometimes in English | Typing in Urdu is slow; setup flows confuse her | Big-mic voice typing; 3-step onboarding in Urdu UI |
| **P5 Lucas** (São Paulo, 27, freelance designer) | Mid/high-end phone | Portuguese, English | Talks with international clients on Slack / WhatsApp | Copy-pasting into Google Translate breaks flow | Translate bar in any app; PROCESS_TEXT translate; AI tone |
| **P6 Nadia** (Dhaka, 29, NGO field officer) | Shared device, patchy connectivity | Bengali, English | Field notes by voice; translating reports | Connectivity; data costs | On-device translation + offline packs (Bengali quality is a risk, R-03) |

### 2.3 Anti-personas (not designed for)

- Power typists who want Gboard-level glide, prediction and per-app learning. We stay "good enough" here (NG-1).
- Enterprise or MDM-managed fleets.
- Users who want an AI chatbot inside the keyboard (NG-4).

---

## 3. Jobs-to-be-Done

| ID | When I… | I want to… | So I can… | Served by |
|---|---|---|---|---|
| J1 | am chatting with someone who speaks another language | type in my language and send in theirs without leaving the chat | keep the conversation flowing | FR-13, FR-11 |
| J2 | am walking, driving or tired of typing | speak and get correct, punctuated text | send messages faster | FR-10 |
| J3 | dictated something messy or in my language | polish it, and optionally translate it, in one tap | sound fluent and professional | FR-16 |
| J4 | wrote a draft that sounds too casual, too long or wrong | rewrite it in a chosen tone | send it confidently | FR-15 |
| J5 | think in Roman Urdu or Hinglish | type phonetically and get native script | write correctly without learning a new layout | FR-24, FR-03 |
| J6 | see foreign text in any app | select it → Translate | understand it without copy-pasting | FR-14 |
| J7 | am offline or on expensive data | still translate and dictate | rely on the keyboard everywhere | FR-11, FR-10, FR-36 |
| J8 | type a password, card number or private message | be sure nothing leaves my phone | trust the keyboard | FR-02, FR-34 |
| J9 | face a sign, menu or document | point the camera and read it in my language | navigate | Not in this app (OD-21) |
| J10 | have a face-to-face conversation across languages | speak in turns and hear the translation | talk to people | Not in this app (OD-21) |
| J11 | want my keyboard to feel like mine | pick a theme, colour or background | enjoy using it | FR-17, FR-18 |
| J12 | want to express myself | send stickers and emoji | chat expressively | FR-04, FR-23 |

---

## 4. Goals & Non-Goals

### 4.1 Goals

| ID | Goal | Measured by |
|---|---|---|
| G-1 | Turn installs into an **active keyboard**: enabled + selected in the first session | Activation rate (§5) |
| G-2 | Make voice + translation the habit loop inside other apps | Voice sessions/DAU, translations/day |
| G-3 | Be the **private/offline** translator keyboard, a claim we can verify | Zero network egress from sensitive fields (automated test); accurate Data safety form; offline translate works with packs installed |
| G-4 | Ship differentiated AI (rewrite, polish + translate) at a sustainable unit cost | AI requests/DAU; cost per MAU < Pro ARPU × 0.25 |
| G-5 | Monetize without hurting the IME experience | Conversion to Pro; ad ARPDAU; store rating ≥ 4.4 |
| G-6 | Quality bar above the category | Crash-free users ≥ 99.7%; ANR < 0.3%; IME warm show < 250 ms p90 (cold ≤ 600 ms p90, TRD §6.4) |

### 4.2 Non-goals

| ID | Non-goal | Rationale |
|---|---|---|
| NG-1 | Beating Gboard or SwiftKey at core prediction or glide | We use upstream FlorisBoard plus minimal suggestions (CMP #8). Our differentiation is elsewhere. |
| NG-2 | **Any ad inside the keyboard surface** | Policy and trust risk. Both targets do it (App H §5, App L §5) and we drop it explicitly. |
| NG-3 | Accessibility-service two-way chat translation (auto-translating incoming messages) in v1 | Play Accessibility policy risk (CMP open Q8; COMPETITORS G4). Revisit in v2 (FR-37). |
| NG-4 | A general AI chat assistant inside the keyboard | Scope, cost and policy (CMP "out of v1"). |
| NG-5 | Camera translation (photo or live overlay) and conversation mode | They move to a separate future app (OD-21, FR-19, FR-20). |
| NG-6 | Reproducing either app's assets: their 71 subtype presets, 156 layouts, phrase lists, sticker packs or word lists | Clean-room rule (L-F:ime-core-keyboard; L-F:keyboard-themes). |
| NG-7 | Keystroke logging, typing analytics, or cloud learning of the user's words | Privacy commitment §11. |
| NG-8 | iOS in this release plan | Android-first. |
| NG-9 | Selling or sharing user data, and any SDK that does so | §11. |

---

## 5. Success Metrics

Targets are **initial hypotheses** set against category benchmarks and the targets' public data. They will be re-baselined after 4 weeks of MVP data (OD-12).

### 5.1 North-star

**Weekly Active Keyboard Users who used voice or translate at least once in the week (WAKU-VT).**

### 5.2 Funnel & engagement

| Metric | Definition | MVP target | v1 target |
|---|---|---|---|
| **Activation** | % of first opens where the keyboard is **enabled in system settings AND selected as the current IME** within 24 h (`ime_enabled` + `ime_selected` events; verified with `Settings.Secure.DEFAULT_INPUT_METHOD`) | ≥ 55% | ≥ 65% |
| Onboarding completion | % of `onboarding_start` that reach `onboarding_complete` | ≥ 60% | ≥ 70% |
| **D1 retention** | % of activated users who show the keyboard (`ime_session_start`) on day 1 | ≥ 35% | ≥ 40% |
| **D7 retention** | Same, day 7 | ≥ 18% | ≥ 22% |
| D30 retention | Same, day 30 | ≥ 8% | ≥ 11% |
| IME still selected at D7 | % of activated users whose default IME is still ours on D7 (checked on app open) | ≥ 70% | ≥ 75% |
| **Voice sessions** | `voice_session_end` with ≥ 1 committed word, per voice-using DAU per day | ≥ 2.0 | ≥ 2.5 |
| Voice adoption | % of DAU with ≥ 1 voice session | ≥ 25% | ≥ 30% |
| **Translations/day** | `translate_done` (all surfaces: bar, translator, PROCESS_TEXT) per translating DAU | ≥ 4 | ≥ 6 |
| Translate adoption | % of DAU with ≥ 1 translation | ≥ 30% | ≥ 35% |
| AI adoption | % of DAU with ≥ 1 `ai_rewrite_done` or `ai_polish_done` | ≥ 8% | ≥ 12% |
| Transliteration adoption (ur/hi users) | % of DAU with an ur/hi subtype that commit ≥ 1 transliterated word | n/a | ≥ 20% |

### 5.3 Monetization

| Metric | Definition | MVP target | v1 target |
|---|---|---|---|
| Paywall view rate | % of DAU with `paywall_view` per week | 10–20% | 10–20% |
| **Trial start conversion** | `trial_start` / activated users (30-day window) | ≥ 3% | ≥ 4% |
| **Paid conversion** | Paying subscribers / activated users at day 30 | ≥ 1.2% | ≥ 2% |
| Trial → paid | % of trials converting | ≥ 35% | ≥ 40% |
| Ad ARPDAU (free users, app surfaces only) | Revenue from `ad_paid_event` / free DAU | Tracked, no target | Tracked, no target |
| Refund / chargeback rate | | < 2% | < 2% |

### 5.4 Quality guardrails (release-blocking)

| Metric | Threshold |
|---|---|
| Crash-free users (Play Vitals) | ≥ 99.7% |
| User-perceived ANR rate | < 0.30% (Play bad-behaviour threshold is 0.47%) |
| IME `onStartInputView` → first frame, p90 (warm show; cold show budget is ≤ 600 ms p90, TRD §6.4) | < 250 ms |
| Translate bar on-device latency per debounced request, p90 | < 150 ms (CMP estimate) |
| Voice: first partial after speech start, p90 | < 800 ms |
| Sensitive-field egress test | 0 network calls (CI gate, FR-02) |
| Store rating (rolling 28 days) | ≥ 4.4 |

### 5.5 Explicit non-metrics

We do **not** measure or optimize:

- keystrokes, words typed, or text content (§11)
- ads shown per session
- "time in app" for the companion app

Interstitial frequency is a *cost* we cap, not a metric we grow.

---

## 6. Source Feature Disposition (every feature in both apps)

**Legend.**
- **Keep**: same user value, reimplemented clean-room.
- **Improve**: same user value with fixes for the defects found in the source.
- **Drop**: not built.

The **FR** column is the requirement in §7 that implements the feature.

### 6.1 App H: `voicetranslator_work/features.json` (21 features)

| # | H feature id | Backlog score | Disposition | FR | Why (source defect → our fix) |
|---:|---|---:|---|---|---|
| 1 | ime-core-keyboard | 3.25 | **Improve** | FR-01, FR-02 | 7 keyboard languages (App H §6), no sensitive-field policy (App H §3.1). We use upstream FlorisBoard, 8 MVP subtypes and a sensitive policy. |
| 2 | voice-typing-ime | 6.5 | **Improve** | FR-10 | The continuous loop is good (300 ms restart, 2 s silence, 3 s watchdog; App H §3.2). We add on-device preference, a privacy notice and spoken punctuation. |
| 3 | keyboard-translation-bar | 4.67 | **Improve** | FR-13 | No debounce, no password check, refuses offline, `deleteSurroundingText` can delete user text (App H §3.1). We add a 400–500 ms debounce, on-device by default, a composing preview and a sensitive block. |
| 4 | text-translator | 5.5 | **Improve** | FR-12 | Text translations are not saved to history (App H §3.3). We add history, favourites and offline. |
| 5 | conversation-mode | 3.67 | **Drop** (OD-21) | FR-19 | It moves to a separate future app. Only conversation writes `translation_history` (App H §3.3). |
| 6 | camera-translation | 3.67 | **Drop** (OD-21) | FR-20 | It moves to a separate future app. Camera2 OCR chain with an "English only" toast (App H §3.4). |
| 7 | offline-phrasebook | 4.0 | **Improve** | FR-22 | We write our own phrase content. Their lists are not copied. |
| 8 | dictionary | 6.0 | **Improve** | FR-21 | English-only via dictionaryapi.dev (App H §2). We use an offline Wiktionary-derived pack (CC-BY-SA, attributed). |
| 9 | keyboard-themes-customizer | 4.0 | **Improve** | FR-17 | We design our own presets. Custom photo background is kept. |
| 10 | sticker-packs | 4.0 | **Improve** | FR-23 | Third-party `myappsdata.site` catalog (App H §2, §3.5). We use our own CDN and licensed or commissioned art. |
| 11 | suggestions-autocorrect | 2.25 | **Improve** | FR-05 | Minimal upstream suggestions. Autocorrect is opt-in and per-language. |
| 12 | onboarding-settings | 10.0 | **Improve** | FR-08, FR-09 | 12 s splash, ad on splash (App H §5). We use a 3-step onboarding with no ads before activation. |
| 13 | ad-monetization | 3.5 | **Improve** (heavy changes) | FR-27 | 13 interstitials on one unit, cooldown off by default, in-IME native ad, Billing 8.3.0 dead (App H §5). We run app-side ads only, one unit per placement, a global cap, and no ads in the IME. |
| 14 | process-text-share-translate | 8.0 | **Keep / improve** | FR-14 | High-value OS integration. We add on-device, replace-in-place when editable, and a SEND share target. |
| 15 | ai-rewrite-smartbar | 4.33 | **Improve** (built new) | FR-15 | Stub / greenfield in the source. We use our own backend with quota and App Check. |
| 16 | emoji-panel | 4.5 | **Keep** | FR-04 | Upstream FlorisBoard emoji with recents and skin tones. |
| 17 | clipboard-suggestions | 3.5 | **Improve** | FR-06 | 20 items / 60 s, sensitive-clip masking, email/URL/phone extraction (H-F). We keep the masking, store items locally only, and keep history off in sensitive fields. |
| 18 | smartbar-quick-actions | 4.0 | **Improve** | FR-07 | "Autocorrect" action is a placeholder toast (H-F). Every action we ship works. |
| 19 | color-picker | 5.0 | **Keep** | FR-18 | Wheel, eyedropper (sampler) from a background image, 8 recents. |
| 20 | push-notifications | 6.0 | **Improve** | FR-30 | FCM topics, token logged to logcat (App H §8). We add opt-in on Android 13+, no logging, and an in-app preference. |
| 21 | in-app-update | 5.0 | **Improve** | FR-31 | FLEXIBLE by default. IMMEDIATE only when below a Remote Config minimum version. |

### 6.2 App L: `voicetyping_work/features.json` (18 features)

| # | L feature id | Backlog score | Disposition | FR | Why |
|---:|---|---:|---|---|---|
| 1 | onboarding-enable-keyboard | 10 | **Improve** | FR-08 | Good pattern: 31 UI locales, 500 ms enable poll, animation (L-F). We use our own animation and add the selection check and the dangling `SettingsLauncherAlias` fix (L nonFeatures). |
| 2 | ime-voice-typing | 6.5 | **Improve** | FR-10 | Single-shot, 5 s, and the result *replaces the translate buffer* (App L §3.1). We use H's continuous model plus live partials. |
| 3 | text-translator | 6.0 | **Improve** | FR-12 | An interstitial on *every* Translate press, a 10,000-character cap, and the mic refuses offline (App L §3.3). We show no interstitial on primary actions and add offline. |
| 4 | settings-growth | 6.0 | **Improve** | FR-09, FR-33 | Rate always opens the market (L-F:settings-growth, App L §5; H-F:onboarding-settings). We use the In-App Review API at a happy moment. |
| 5 | ime-core-keyboard | 4.33 | **Improve** | FR-01 | 71 presets / 156 layouts, but the picker offers 7 + hi mapped. Clipboard off by default (L-F). We use our own curated subtype list. |
| 6 | ai-rewrite-greenfield | 4.33 | **Improve** (built new) | FR-15 | Greenfield. |
| 7 | dictionary | 4.0 | **Improve** | FR-21 | Offline clears the search (App L §3.6). We ship an offline pack, keep the query, and use `freedictionaryapi` only as an optional proxied online fallback. |
| 8 | stickers | 4.0 | **Improve** | FR-23 | `isPremium` never read; compat path dead (App L §3.7). Premium packs are gated in our build. |
| 9 | ime-live-translate | 3.67 | **Improve** | FR-13 | No debounce or cancel; a stale length deletes text; no password/incognito check (App L §3.2). Fixed in our build. |
| 10 | camera-translator | 3.67 | **Drop** (OD-21) | FR-20 | It moves to a separate future app. Fixed en→es, temp files leaked (App L §3.4). |
| 11 | ads-consent-stack | 3.5 | **Improve** | FR-27, FR-35 | UMP present. App-open every 4 h with no cooldown; 10 interstitials on 1 slot (App L §5). We put UMP first, a global cap, and no app-open on first launch. |
| 12 | guess-word-game | 3.5 | **Keep (later)** | FR-25 | Daily word game with hints from rewarded ads the user starts. Our word list. |
| 13 | voice-conversation | 3.33 | **Drop** (OD-21) | FR-19 | It moves to a separate future app. Room `conversation_messages`, a tab of the translator (App L §3.5). |
| 14 | keyboard-themes | 3.0 | **Improve** | FR-17 | 48 presets, 141 prefs, 12 key sounds (L-F). We design our own set: fewer prefs, sensible defaults. |
| 15 | suggestions-autocorrect | 3.0 | **Improve** | FR-05 | About 81% of the Arabic list is English, no Hindi list, suggestions off by default (L-F). We use proper per-language dictionaries and turn suggestions on. |
| 16 | in-app-update | 6.0 | **Improve** | FR-31 | Forced update shown only at exit (App L §5). Fixed. |
| 17 | open-screen-intent-routing | 6.0 | **Improve** | FR-32 | Exported `open_screen` extra with no validation; the `language` value crashes the l65 route (App L §6). We use typed, allow-listed deep links. |
| 18 | urdu-shift-layout | 2.5 | **Improve** | FR-03 | Urdu shift layer. We build a full Urdu Phonetic + standard layout with our own key maps. |

### 6.3 Non-features and anti-patterns: all dropped

| # | Source item | Disposition | Replacement |
|---:|---|---|---|
| D-01 | In-IME native ad and the 86-package host deny-list (App H §5; L nonFeatures) | **Drop** | No ads in the IME (NG-2). |
| D-02 | Keyless Google `translate.googleapis.com` gtx / `dict-chrome-ex` endpoints (App H §2; App L §2 E1) | **Drop** | ML Kit on-device, or our own proxy to a licensed API (FR-11). |
| D-03 | Logcat leaks: `urlCheck___`, FCM token, host package names (App H §8; L nonFeatures) | **Drop** | Release Timber tree is a no-op. R8 strips `Log.*` (FR-34). |
| D-04 | `Process.killProcess` on exit (App H §6; App L §6) | **Drop** | Normal lifecycle. |
| D-05 | Forced update shown only at exit (App L §5) | **Drop** | FR-31. |
| D-06 | Unvalidated `open_screen` routing (App L §6) | **Drop** | FR-32. |
| D-07 | Dead premium flags: `is_user_premium`, unread `isPremium`; dead Billing 8.3.0 (App H §5; App L §3.7, nonFeatures) | **Drop** | Single `EntitlementRepository` (FR-28). |
| D-08 | 13/10 interstitials on one unit, interstitial on Translate press, cooldown disabled (App H §5; App L §3.3, §5) | **Drop** | One unit per placement, global cap, none on primary actions (FR-27). |
| D-09 | 12 s splash with ad wait, timer restarting on resume (App H §5; App L §5) | **Drop** | Splash ≤ 1 s, no ad on first launch. |
| D-10 | `allowBackup` without rules; FileProvider `external-path` / broad paths (App H §6; App L §6) | **Drop** | `dataExtractionRules`; FileProvider scoped to `cache/share/` (FR-34). |
| D-11 | Destructive Room migration (App L §6) | **Drop** | Tested migrations; history survives updates. |
| D-12 | AdMob test-device ID string in release (L nonFeatures; the class sweep corrected it to the SDK's own emulator constant, not a developer device, so it is low impact) | **Drop** | Test-device config lives in debug builds only. |
| D-13 | Unread Remote Config native keys; 90–94 ad keys (App H §2; App L §2) | **Drop** | Small typed RC schema (TRD). |
| D-14 | Ad-only analytics (App H §5; App L §5, about 50 ad events); event names over 40 characters (App H §5 only) | **Drop** | Product funnel analytics (§12). |
| D-15 | Data safety "no data collected" while sharing voice and device IDs (PS) | **Drop** | Accurate declaration (§11). |
| D-16 | PairIP / tamper kill-switches | **Drop** | Play Integrity on the server only (FR-15). |
| D-17 | Smartbar placeholder "Autocorrect" toast (H-F) | **Drop** | FR-07: only working actions. |

---

## 7. Functional Requirements

**Priority.**
- **MVP**: required for first public release (CMP ranks #1–13).
- **v1**: target about 6 weeks after MVP (CMP #14–20).
- **Later**: v2 or a parked item.

**Disposition:** Keep / Improve / New (gap).

Each FR lists Source, Description, User stories and Acceptance criteria (AC). Each AC is written so QA can test it.

### 7.1 Keyboard core

#### FR-01: Core keyboard on upstream FlorisBoard, with curated subtypes (MVP · Improve)
**Source:** H-F:ime-core-keyboard; L-F:ime-core-keyboard; App H §6 (7 languages); App L §6; CMP #1.
**Description:** A Compose IME built on an upstream FlorisBoard fork. MVP subtypes: **en, es, fr, pt, ar, ur, id, hi** (CMP #1), each declared in `method.xml` with the correct `imeSubtypeLocale` and `languageTag`. The bn, tr and ps subtypes come in v1. Features: number row toggle, long-press symbols, one-handed mode, key haptics and sound (OS default volume), and swipe-to-delete on backspace. Glide typing is a candidate for later (OD-10).
**User stories**
- As P1, I can switch between English and Urdu with the globe key, or by long-pressing space, and the layout changes instantly.
- As P3, Arabic renders right-to-left with correct shaping in the suggestion strip and the translate bar.

**AC**
1. All 8 MVP subtypes appear in system *Languages & input → [Product] → Languages* and in the in-app language picker.
2. Language switching takes < 100 ms p90 with no dropped keystrokes.
3. RTL subtypes mirror the layout where appropriate, and the cursor and selection behave correctly in RTL text.
4. No code, layout JSON or preset is taken from App H or App L. The upstream FlorisBoard licence and NOTICE ship in the APK, and an OSS licences screen exists (FR-09).
5. The IME process does not crash when it gets `onStartInput` with a null `EditorInfo`, or in fields that reject `commitText`. Covered by an instrumentation test.

#### FR-02: Sensitive-field policy (MVP · New)
**Source:** gap. Neither app checks password, incognito or no-learning fields (App H §3.1; App L §3.2; CMP open Q1). COMPETITORS G2.
**Description:** A single `SensitiveFieldPolicy` is evaluated in `onStartInput` from `EditorInfo`. A field is **sensitive** when its input type is one of:
- `TYPE_TEXT_VARIATION_PASSWORD`, `VISIBLE_PASSWORD` or `WEB_PASSWORD`
- `TYPE_NUMBER_VARIATION_PASSWORD`
- `TYPE_CLASS_NUMBER` combined with a card-number hint, as far as we can detect it

A field is also sensitive when `imeOptions` contains `IME_FLAG_NO_PERSONALIZED_LEARNING` (incognito tabs, private modes), or when the user has added its package to the "Private apps" list.

In a sensitive field the keyboard disables:
- the translate bar
- voice input
- AI rewrite and polish
- transliteration network calls (none exist anyway)
- learning and suggestions history
- clipboard history capture
- sticker search

Only local typing remains. A small lock indicator appears in the smartbar.

**User stories**
- As P3, when I type my banking password the translate bar disappears, and I can see the keyboard is in private mode.

**AC**
1. In each sensitive field type above, the translate, voice, AI and clipboard-history controls are hidden or disabled. Tapping where they would be does nothing.
2. **Egress test (CI gate):** an instrumentation test types 200 characters into each sensitive field type with a network interceptor (OkHttp + `TrafficStats` tag check). It asserts **0** requests from the IME process.
3. Nothing from a sensitive field is written to the user dictionary, clipboard history, translation history or analytics.
4. Users can add or remove apps in Settings → Privacy → Private apps. Suggestions come from a curated list of known banking and wallet packages per launch market, matched against the device's launchable apps (Android has no banking category, and `QUERY_ALL_PACKAGES` is not allowed). Suggestions are never auto-added. The smartbar also offers **"Make this app private"** for the current app (RN-05).
5. The policy is re-evaluated on every `onStartInput`, including `restarting=true`.

#### FR-03: Regional layouts: Urdu, Hindi, Arabic, Bengali (MVP for ur/hi/ar; v1 for bn/ps) (Improve)
**Source:** L-F:urdu-shift-layout; L-F:ime-core-keyboard; COMPETITORS G3; CMP #1.
**Description:** Our own key maps:
- **Urdu**: standard plus phonetic, with a shift layer for aspirates and ے/ۓ/ۂ, Urdu digits optional, and Nastaliq-friendly preview font in the bar
- **Hindi**: InScript plus phonetic
- **Arabic**: standard 101
- **Bengali** (v1)
- **Pashto** (v1)

Each layout is reachable from the subtype list and the in-keyboard language switcher.
**AC**
1. Each layout has a shift / alternates layer covering the full script alphabet. A linguist reviewer signs off a coverage checklist.
2. Digits: users can choose Western or native digits per subtype.
3. Layout files are authored by us and reviewed in PR as original work.

#### FR-04: Emoji panel (MVP · Keep)
**Source:** H-F:emoji-panel.
**Description:** Upstream FlorisBoard emoji panel: categories, recents, skin-tone memory, and search (local, keyword index).
**AC**
1. Recents persist across restarts.
2. Search works offline.
3. Emoji search history is not recorded in sensitive fields.

#### FR-05: Suggestions & autocorrect, minimal (MVP · Improve)
**Source:** H-F:suggestions-autocorrect; L-F:suggestions-autocorrect (Arabic list about 81% English, no Hindi list, suggestions off by default); CMP #8.
**Description:** Three-candidate suggestion strip with per-language word frequency lists. Lists come from openly licensed corpora such as the Leipzig Corpora or FrequencyWords, subject to licence review (OD-11). Next-word prediction is v1. Autocorrect is opt-in, per language, and off by default for ur/hi/ar. Learning stays on-device and is disabled in sensitive fields.
**AC**
1. Suggestions are on by default for en, es, fr, pt and id, and show candidates for every MVP subtype.
2. Fewer than 5% of entries in each non-English list are English words (automated check against the en list).
3. "Clear learned words" in Settings deletes the user dictionary.

#### FR-06: Clipboard history (v1 · Improve)
**Source:** H-F:clipboard-suggestions (20 items, 60 s, sensitive masking, email/URL/phone extraction); L-F:ime-core-keyboard (clipboard off by default).
**Description:** Optional clipboard panel: up to 20 items, with pinning. The "recent clip" suggestion chip lasts 60 s. Clips flagged `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+), and clips that look like OTPs or passwords, are masked and never stored. Smart chips are extracted for email, URL and phone. Data is stored only on-device and is encrypted at rest (TRD).
**AC**
1. Off by default. The user turns it on in Settings or with an in-panel prompt.
2. Sensitive clips are never persisted.
3. No capture happens while a sensitive field is focused.
4. "Clear clipboard history" works, and items expire after 24 h unless pinned.

#### FR-07: Smartbar & quick actions (MVP · Improve)
**Source:** H-F:smartbar-quick-actions (up to 3 + fixed; placeholder Autocorrect toast).
**Description:** The smartbar above the keys shows suggestions when the user is typing. When idle it shows action chips: **Mic, Translate, AI ✦, Emoji, Clipboard (v1), Stickers (v1), Settings**. Users can reorder chips and pin up to 3. Mic and Translate are fixed.
**AC**
1. Every chip performs its action. No placeholder toasts.
2. Chip state reflects the sensitive policy (FR-02) and Pro gating (FR-28).
3. Configuration persists.

### 7.2 Onboarding & settings

#### FR-08: Onboarding: enable + select keyboard (MVP · Improve)
**Source:** H-F:onboarding-settings; L-F:onboarding-enable-keyboard (31 UI locales, 500 ms poll, animation); L nonFeatures (dangling `SettingsLauncherAlias`); CMP #2; PB §7.
**Description:** See the §8 flow. Three steps: (1) Enable in system settings, (2) Select as current keyboard, (3) Try it. Step 3 has a practice field with a voice and translate demo, plus language selection. Detection: poll `Settings.Secure.ENABLED_INPUT_METHODS` while the app is resumed, re-check on `onResume`, and observe `DEFAULT_INPUT_METHOD` through a ContentObserver.
**AC**
1. After the user enables the IME in system settings and returns, the app advances to step 2 within 1 s.
2. Step 2 opens the system IME picker (`InputMethodManager.showInputMethodPicker`). Once selected, the app advances automatically.
3. The app emits `onboarding_start`, `onboarding_step_view{step}`, `ime_enabled`, `ime_selected` and `onboarding_complete`.
4. **No ads** and no paywall before `ime_selected` (PB §7). The soft paywall rule is in OD-05.
5. The UI is localized in at least 12 languages at MVP (en, ur, hi, ar, bn, id, es, pt, fr, tr, fa, ms) and 31 by v1. Translations are ours.
6. The launcher alias is declared correctly. The IME `settingsActivity` resolves to our settings screen, verified by a test.
7. Returning users who already have the keyboard enabled and selected skip straight to Home.

#### FR-09: Settings (MVP · Improve)
**Source:** H-F:onboarding-settings; L-F:settings-growth; PB §7 (privacy options row).
**Description:** Settings sections:
- **Languages & layouts**
- **Voice** (engine preference: on-device / auto; spoken punctuation; auto-stop silence 2–5 s)
- **Translate** (default pair, on-device/cloud tier, offline packs → FR-36)
- **AI** (default tone, quota display)
- **Appearance** → FR-17
- **Typing** (suggestions, autocorrect, haptics, sound)
- **Privacy** (private apps, clear data, analytics opt-out, ad privacy options (UMP), Data policy)
- **Subscription** (manage, restore)
- **About** (version, OSS licences, FlorisBoard attribution, Terms, Privacy Policy, Contact)

**AC**
1. The "Privacy options" row is always visible when UMP says privacy options are required (PB §7).
2. Restore Purchases is visible on Settings → Subscription and on the paywall.
3. The OSS licences screen lists FlorisBoard (Apache-2.0) and every bundled third-party library or dataset, including the CC-BY-SA dictionary.

### 7.3 Voice

#### FR-10: Voice typing: continuous, live, on-device preferred (MVP · Improve)
**Source:** H-F:voice-typing-ime (continuous loop: 300 ms restart, 2 s silence, 3 s watchdog, restart on errors 6/7/8; App H §3.2); L-F:ime-voice-typing (single-shot 5 s; result replaces the translate buffer; App L §3.1); CMP #3, open Q6; COMPETITORS G2.
**Description:** Tapping the mic opens a voice panel with a waveform (RMS) and a language chip. The platform `SpeechRecognizer` runs in a continuous loop modelled on H's behaviour, with our own timings (configurable through Remote Config). On API 31+, when an on-device recognizer is available for the locale, we use it (`createOnDeviceSpeechRecognizer`). Otherwise we use `EXTRA_PREFER_OFFLINE` and fall back to the default service. **On-device Whisper fallback (OD-16, decision 2026-10-18):** when no recognizer supports the locale, when no recognition service is installed, or when the user picks "Offline (Whisper)" in voice settings, dictation uses an on-device Whisper model (whisper.cpp) that the user downloads from Offline languages (FR-36). Whisper has no live partials: the user speaks, taps stop, and the text appears after a short "Transcribing…" state. Audio never leaves the phone. With the system recognizer, partials are shown with `setComposingText` and finalized with `commitText`. Spoken punctuation ("comma", "full stop", "new line", and localized equivalents for ur, hi, ar, es, pt, fr, id) is converted client-side. Tapping the mic again or pressing Done stops. After a session ends, the user can tap a **✦ Polish** chip (FR-16).
**User stories**
- As P2, I tap the mic and talk for 40 seconds while pausing to think, and I get one continuous transcript without re-tapping.
- As P4, I see my Urdu words appear as I speak.

**AC**
1. A session continues across pauses up to the configured silence timeout (default 2 s after the last final result, extendable to 5 s). There is no fixed watchdog. After `ERROR_SPEECH_TIMEOUT` or `ERROR_NO_MATCH` the recognizer restarts after 250 ms, at most 3 consecutive times. After `ERROR_RECOGNIZER_BUSY` it is destroyed and recreated after 400 ms (max 2 attempts). A session is capped at 60 s (TRD §4.2.3).
2. Voice output **never replaces** existing field text or the translate buffer. It inserts at the cursor (fixes App L §3.1).
3. Offline: when an on-device recognizer or a downloaded Whisper model exists, dictation works in airplane mode. When neither exists, a clear message offers the system language download or the Whisper download (FR-36). We never fail silently.
4. First-use disclosure: one sheet explains whether audio is processed on-device (system recognizer or Whisper) or by the system speech service (Google). The user acknowledges it before RECORD_AUDIO is requested. The Privacy Policy matches.
5. RECORD_AUDIO is requested mainly at the onboarding try-it step (O5). An IME cannot show a permission dialog, so if the permission is still missing, tapping the mic in the keyboard opens a small permission activity (the keyboard hides while it is open). The copy reads "Allow microphone, then tap the mic again". Dictation does not resume automatically. Denial shows a rationale and a deep link to settings (RN-04).
6. Voice is disabled in sensitive fields (FR-02).
7. Events: `voice_session_start{lang, engine}` (`engine` = `system_on_device` / `system_default` / `whisper`) and `voice_session_end{duration_bucket, words_bucket, end_reason}`. No transcript text is sent.
8. Whisper is offered per language only after it passes a native-speaker eval (same gate as FR-11 AC5). The model is never bundled in the APK. The default is a quantized *base* model (about 140 MB); *small* (about 470 MB) is offered on phones with 6 GB RAM or more, because small models are weak for Urdu (zero-shot WER about 54% on base, 34% on small).
9. Whisper sessions are capped at 60 s like the system path. The model is unloaded from memory after 60 s idle or on low-memory signals, and must not push the IME over the TRD memory budget on a 3 GB phone.

### 7.4 Translation

#### FR-11: On-device translation core (MVP · New)
**Source:** gap. Both apps use keyless gtx and refuse offline (App H §2, §3.1, §4; App L §2, §3.3). COMPETITORS G2 (the only offline claimant has 100K installs). CMP #4, open Q7.
**Description:** A `Translator` interface with two engines:
- **On-device** (default, free): ML Kit Translation + ML Kit Language ID
- **Cloud quality** (optional, Pro): our backend proxy to Google Cloud Translation v3 or DeepL, decided in OD-07. POST bodies only, never query strings; TLS; no logging of text on the server beyond transient processing

The language catalog is the intersection of engine support, labelled clearly. ML Kit covers about 59 languages including ur, hi, ar and bn. The cloud tier covers 100+. App H and L list 103 languages through gtx (App H §6), which we do not use.
**AC**
1. With the pair's packs downloaded, translation works in airplane mode on every translate surface.
2. Auto-detect source using Language ID (confidence threshold ≥ 0.5; otherwise use the user's last source language).
3. The cloud tier is used only when the user enables it (Pro), never for sensitive fields, and never silently as a fallback.
4. Model downloads default to Wi-Fi only, with a user override. Sizes are shown before download (about 30 MB per language).
5. Pashto (ps) has no ML Kit model, so it is cloud-only (Pro) and badged as such (TRD §4.3). Quality gate for ur, hi, ar, ps and bn: an internal eval set of 200 sentences per language, rated by native speakers (R-03). Pairs that score below the bar are labelled "Beta" and recommend the cloud tier.

#### FR-12: Text translator screen + history + TTS (MVP · Improve)
**Source:** H-F:text-translator (no history save; App H §3.3); L-F:text-translator (interstitial on every press, 10,000-character cap, mic refuses offline; App L §3.3); App L §4 (TTS per screen); CMP #5.
**Description:** A full-screen translator: source and target pickers with swap, a multi-line input with a 5,000-character limit (counter shown), mic input (no camera entry, OD-21), results with copy / share / speak (TTS) / favourite, and auto-translate after a 600 ms debounce. Up to 500 entries of history are stored locally (Room), with search, favourites, delete and clear-all. TTS uses the platform `TextToSpeech` with a shared engine (`:core:tts`), and the UI shows when a voice is missing.
**AC**
1. Every successful translation is saved to history unless history is turned off in Settings.
2. **No interstitial on the Translate action** or any primary action (PB §7; D-08).
3. Mic input works offline when an on-device recognizer exists (FR-10).
4. TTS is greyed out with an "Install voice" link when the system lacks a voice for the target language.
5. History survives app updates, because migrations are tested (D-11).

#### FR-13: In-keyboard translate bar (MVP · Improve)
**Source:** H-F:keyboard-translation-bar (App H §3.1: input from keys, backspace, paste, suggestion taps and voice; cancel-previous + stale guard; no debounce; no password check; refuses offline; `deleteSurroundingText` can delete user text; resets onWindowHidden); L-F:ime-live-translate (App L §3.2: no debounce or cancel; stale length deletes text; resets onWindowShown); CMP #6; COMPETITORS G2, G5.
**Description:** Tapping the Translate chip opens a bar above the keys. It has a source → target pair chip with swap, an input line for the source text that the keyboard types into, and a live preview of the translation.
- **Default mode, "Preview & send":** the translation appears in the bar. Tapping ✓ (or Enter) commits it into the host field.
- **Optional mode, "Live replace":** the translation is shown in the host field as *composing text* and finalized on ✓.

Requests are debounced by 400–500 ms, the previous request is cancelled, and stale responses are discarded by sequence number. An in-memory LRU cache holds 200 entries. Voice can feed the bar (FR-10). Pressing undo within 5 s after a commit restores the source text.
**User stories**
- As P5, I type Portuguese in Slack, see the English live, and tap ✓ to send English.
- As P1, I switch the pair to ur → en with one tap.

**AC**
1. **Never deletes user text outside the bar's own composing region.** This is a property test: random edit sequences plus slow and out-of-order responses must never change text that existed before the bar opened. Fixes App H §3.1 and App L §3.2.
2. At most one in-flight request per bar. Responses older than the latest sequence number are discarded.
3. p90 time from the last keystroke to the preview is ≤ debounce + 150 ms on-device on the reference device (a Pixel 6a-class device and a low-end 3 GB device).
4. Hidden in sensitive fields (FR-02). If a field becomes sensitive while the bar is open, the bar closes and clears.
5. Works offline with packs installed. Without packs, the bar shows "Download en↔ur (31 MB)" inline.
6. Bar state (pair, mode) persists across `onWindowHidden` and `onWindowShown`. The text buffer clears when the input field changes.
7. Events: `translate_done{surface:"bar", engine, pair, chars_bucket}` (counted per commit, not per preview) and `translate_preview` (sampled at 1%). No text is sent.

#### FR-14: PROCESS_TEXT + SEND share translate (MVP · Keep/Improve)
**Source:** H-F:process-text-share-translate (backlog 8.0); CMP #7; COMPETITORS G2.
**Description:** A `PROCESS_TEXT` activity labelled "Translate" (and "✦ Rewrite" in v1) appears in the system text-selection menu of any app. It opens a translucent bottom sheet showing the translation, with Copy, Replace (when `EXTRA_PROCESS_TEXT_READONLY` is false; returns `EXTRA_PROCESS_TEXT`), Speak, Open in app, and pair switch. It also accepts `ACTION_SEND text/plain` as a share target.
**AC**
1. "Translate" appears in the selection menu of Chrome, WhatsApp, Gmail and Messages on API 26–35.
2. Replace writes the translation back in editable fields. In read-only fields, Replace is hidden.
3. The sheet works without the keyboard being enabled, as an acquisition surface. It shows a non-blocking "Enable keyboard" card at most once per 7 days.
4. On-device by default. Text longer than 5,000 characters is truncated with a notice.
5. The activity is exported with no data URI handling and validates input type.

### 7.5 AI

#### FR-15: AI rewrite (MVP · New)
**Source:** H-F:ai-rewrite-smartbar; L-F:ai-rewrite-greenfield (both greenfield or stub); CMP #9; COMPETITORS G1; PB architecture `:core:ai`.
**Description:** Tapping the ✦ chip opens a rewrite sheet over the keys. It takes the current field text, or the selection when there is one (read through `getExtractedText` / `getSelectedText`, capped at 2,000 characters). Tones:
- **Fix grammar**
- **Formal**
- **Friendly**
- **Shorter**
- **Longer**
- **Emojify**
- **Translate & polish to [lang]**

The result is shown with *Replace* / *Insert* / *Retry*. **Engine order (OD-08, decision 2026-10-18):** if the phone supports Gemini Nano (ML Kit GenAI / AICore), the language is supported, and the spike in OD-20 passed, the request runs on-device first. Otherwise, or if Nano fails, is busy, or is blocked, it goes to the cheapest passing cloud model. Cloud calls go to our backend `/v1/rewrite`, which proxies to an LLM vendor (OD-08), with Firebase App Check (Play Integrity provider) and a per-user, per-day quota enforced server-side. The client never holds a vendor key.
**AC**
1. Replace performs a single undoable edit: it remembers the original and offers "Undo" for 5 s.
2. Disabled in sensitive fields, and when the field has `IME_FLAG_NO_PERSONALIZED_LEARNING`.
3. Free quota (default 10 rewrites per day, OD-04) is enforced server-side. When the quota runs out the user sees an inline paywall (FR-29). Pro gets a fair-use limit of 150 per day and 3,000 per month (TRD OD-16).
4. p90 latency is < 2.5 s for 300 characters. A timeout at 10 s shows an error with Retry.
5. Prompt and response are not stored server-side beyond 30 days of abuse logs, which contain a hash plus metadata and no text, unless the user opts in (OD-09). The Privacy Policy says so.
6. Output is in the input's language unless a Translate tone is chosen. RTL output renders correctly.
7. Requests carry no device identifiers beyond the App Check token and the Firebase anonymous UID.
8. Before the first AI request that goes to the cloud, the AI consent screen is shown (consent class `consent_ai`, §10.6). Nothing is sent until the user taps "Agree". Requests handled by Gemini Nano on-device need no consent, because text stays on the phone.
9. The AI sheet has a **Report** action (Play AI-generated content policy). The output text is attached to the report only if the user ticks "include the text" (RN-08).
10. On-device (Nano) actions count against the same daily quota as cloud actions, so Free and Pro behave the same on every phone. Revisit after launch data.
11. The sheet does not label which engine answered. `ai_request{engine}` (`nano` / `cloud`) is logged for quality and cost tracking.

#### FR-16: AI voice polish + translate (MVP · New)
**Source:** gap (COMPETITORS G1: Wispr/Typeless prove demand, and no translator keyboard does it); CMP #10.
**Description:** After a voice session (FR-10), a **✦ Polish** chip appears with options: *Clean up* (remove fillers and false starts, add punctuation, fix grammar, keep the language) and *Clean up + translate to [target]*. It uses the same engine order as FR-15 (Gemini Nano on-device first where available, then the cloud), and the cloud path uses its own endpoint `/v1/polish` (TRD §5.3). The just-dictated span (tracked by its composing or commit range) is replaced in place. There is an **"Auto-polish every dictation"** toggle, Pro only, **off by default** (OD-04, RN-03). It stays in MVP.
**AC**
1. Only the dictated span is replaced. Other text is untouched (span tracking test).
2. Undo restores the raw transcript.
3. It counts against the AI quota (shared pool, OD-04). Each auto-polished dictation counts as one action.
4. Code-switched input (Hinglish, Roman Urdu) is preserved or normalized according to the user setting "Output script: Roman / Native".
5. The auto-polish toggle is off by default. Turning it on the first time opens its own consent screen (consent class `consent_auto_polish`, §10.6), which says every dictation transcript is sent to our server and AI provider without a tap. The toggle stays off unless the user taps "Agree". Turning it off, or withdrawing consent in Settings → Privacy, stops it immediately.
6. Auto-polish never runs in sensitive fields (FR-02). When the quota runs out, auto-polish pauses and the raw transcript is kept.
7. The manual ✦ Polish chip uses the AI consent (`consent_ai`) and has the same **Report** action as FR-15 (RN-08).

### 7.6 Regional & transliteration

#### FR-24: Roman Urdu / Hinglish transliteration (v1 · New)
**Source:** gap (COMPETITORS G3; CMP #19).
**Description:** "ur (Roman → اردو)" and "hi (Roman → हिन्दी)" input modes. The user types Latin letters and the suggestion strip shows the top 3 native-script candidates. Space commits the top candidate, and tapping the Latin chip keeps the Roman text. The engine runs on-device: a lexicon plus a character n-gram model built from openly licensed data such as the Dakshina dataset (CC-BY-SA), subject to licence review (OD-11). Users can add custom mappings.
**AC**
1. Top-1 accuracy ≥ 85% and top-3 ≥ 95% on a held-out set of 2,000 common words per language.
2. Candidate latency < 30 ms p90 per keystroke on a low-end device.
3. Fully offline, with zero network calls.
4. Works in the translate bar's source line, so the user can type Roman Urdu and translate to English.

### 7.7 Companion-app features

#### FR-17: Themes (MVP: basic set; v1: custom) (Improve)
**Source:** H-F:keyboard-themes-customizer; L-F:keyboard-themes (48 presets, 141 prefs, 12 sounds); CMP #13.
**Description:**
- **MVP:** 12 original presets (light, dark, AMOLED, and Material You dynamic colour on API 31+), key border on/off, and key sound packs (4 original sounds).
- **v1:** custom theme with a photo background (crop, blur, dim), key colour and opacity, font, and 20+ premium presets.

**AC**
1. Theme changes apply live in the preview and in the IME without a restart.
2. Premium presets show a 👑 badge. Tapping one opens a full preview, and Apply goes through the paywall (FR-29).
3. All presets and sounds are original or properly licensed, with the licence recorded in `assets/LICENSES`.
4. Photo backgrounds are stored in app-private storage and excluded from backup.

#### FR-18: Colour picker (v1 · Keep)
**Source:** H-F:color-picker (wheel, sampler, 8 recents).
**Description:** HSV wheel plus hex input, an eyedropper that samples from the custom background image, and 8 recent colours.
**AC:**
1. Colours chosen with the wheel, hex or eyedropper apply to the selected element.
2. Recents persist.

#### FR-19: Conversation mode (moved out of this app, OD-21)
**Status:** Out of scope for this app since 2026-11-14. It belongs in a separate future app (OD-21). The text below stays as input for that app.
**Source:** H-F:conversation-mode (App H §3.3); L-F:voice-conversation (App L §3.5: Room `conversation_messages`); CMP #14.
**Description:** A split-screen conversation for two people, one language each. Each side has a mic: speech → text → translation → optional auto-speak (TTS). The transcript is shown as chat bubbles, and sessions are saved locally. There is a "Face-to-face" flip mode that rotates the top half 180°.
**AC**
1. One turn (speak → translated text shown) takes under 2 s p90 on-device after the end of speech.
2. Sessions are saved, listed, deletable and shareable as text.
3. Works offline with on-device recognizer + packs.

#### FR-20: Camera translation (moved out of this app, OD-21)
**Status:** Out of scope for this app since 2026-11-14. It belongs in a separate future app (OD-21). The text below stays as input for that app. TRD OD-10 (Arabic-script OCR) no longer applies to this app.
**Source:** H-F:camera-translation (Camera2, en-only toast; App H §3.4); L-F:camera-translator (CameraX, fixed en→es, temp files leaked; App L §3.4); CMP #15, out-of-v1 live overlay.
**Description:**
- **v1:** CameraX capture or gallery import, then ML Kit Text Recognition v2 (Latin, Devanagari). ML Kit v2 has no Arabic-script model; ar/ur/fa sources are marked "not supported for camera" until TRD OD-10 (default: defer to v1.1, then cloud OCR for Pro) (R-03). The recognized text is translated with FR-11 and shown as a block list with copy and share. An "overlay" view draws the translations over the bounding boxes on the still image.
- **v2:** live viewfinder overlay.

**AC**
1. The user chooses source and target. Source is auto-detected by default. Nothing is hard-coded.
2. Temp images are deleted on screen exit and on cold start (cache cleanup test).
3. CAMERA permission is requested only when the screen is entered. Gallery import uses the Photo Picker, so no storage permission is needed.

#### FR-21: Dictionary (v1 · Improve)
**Source:** H-F:dictionary (dictionaryapi.dev, en only; App H §2); L-F:dictionary (freedictionaryapi `translations=true`; offline clears search; App L §2 E2, §3.6); CMP #16.
**Description:** An offline English dictionary pack derived from Wiktionary (CC-BY-SA 4.0, attributed in About and on the screen footer), with definitions, part of speech, examples, synonyms and TTS. v1.x adds packs for ur, hi and ar if licensed data allows. An optional online lookup goes through our proxy.
**AC**
1. Lookup works offline after the pack installs (about 40 MB, downloaded on demand).
2. The query is retained when connectivity changes.
3. The attribution string and licence link are visible.

#### FR-22: Phrasebook (v1 · Improve)
**Source:** H-F:offline-phrasebook; CMP #17.
**Description:** Original phrase content: about 300 phrases across 12 categories (greetings, travel, health, work, shopping…), written in English. Translations are produced by FR-11 at runtime, or pre-generated by us and reviewed for the top 10 languages. Users can save their own phrases and favourites. Tapping a phrase speaks it, and there is a "Send via keyboard" action.
**AC**
1. No phrase text is copied from App H.
2. Works offline (pre-generated or with packs).
3. Users can create, edit and delete their own phrases.

#### FR-23: Stickers (v1 · Improve)
**Source:** H-F:sticker-packs (third-party catalog; App H §3.5); L-F:stickers (`isPremium` never read; App L §3.7); CMP #18.
**Description:** A sticker panel inside the keyboard. A JSON catalog is served from our CDN (signed; cached 24 h). Packs are free or Pro. Stickers are sent with the Commit Content API (`InputConnectionCompat.commitContent`) when the host supports the MIME type. Otherwise the panel shares through a FileProvider limited to `cache/share/`.
**AC**
1. Premium packs require an entitlement, checked locally through `EntitlementRepository`.
2. Art is commissioned or licensed, and each pack records its licence.
3. No sticker request is made from sensitive fields.
4. The catalog fetch carries no user identifiers.

#### FR-25: Daily word game (v1-late / Later · Keep)
**Source:** L-F:guess-word-game (rewarded hints); CMP #20.
**Description:** A daily 5-letter word puzzle in the companion app, in en (plus es, pt or id later). It has streaks and a share card. Hints come from rewarded ads that the user starts, or are unlimited for Pro.
**AC**
1. Rewarded ads only on an explicit "Watch ad for hint" tap (PB §7).
2. The word list is original or openly licensed.
3. The game works offline.

#### FR-26: Text-to-speech service (MVP · Improve)
**Source:** App L §4 (TTS instance per screen); App H §3.3.
**Description:** A shared `TtsManager` that initializes lazily and is released on app background. Users can choose a voice per language and set the speech rate.
**AC**
1. At most one `TextToSpeech` instance per process.
2. No leaks on rotation (LeakCanary clean in debug).

### 7.8 Monetization, growth & platform

#### FR-27: App-side ads + consent (MVP · Improve)
**Source:** H-F:ad-monetization; L-F:ads-consent-stack; App H §5; App L §5; PB §7; CMP #11.
**Description:** Ads are shown only on companion-app surfaces, and only to free users. Ad SDK: AdMob with UMP, mediation optional later (OD-06). Placements:

| Placement | Format | Rule |
|---|---|---|
| Home (bottom) | Adaptive banner | Hidden on first session |
| Translator / History / Dictionary result | Native (one per screen) | Below the fold; never adjacent to buttons |
| Leaving a feature screen back to Home | Interstitial | Only after ≥ 2 completed feature uses; global interstitial cap 1 per 3 min and 6 per day; never on first session; never on exit or back-to-launcher |
| App open (resume) | App-open | Never on first launch; never over splash; at most 1 per 4 h; not within 30 s of an interstitial |
| Word game hints | Rewarded | User-started only |

**AC**
1. **The IME process makes zero ad requests.** CI check: the `:ime` module has no dependency on `:core:ads`, enforced by a Gradle dependency rule. Runtime assert: no `MobileAds` class is loaded in the IME service. This holds only if the ad SDK's auto-init providers are removed or the IME runs in its own process (TRD §11.1). The claim "no ads in your keyboard" stays, but it must not appear in the store listing until the CI test in TRD §11.8 passes (RN-10).
2. UMP consent completes before `MobileAds.initialize()`. The privacy options row is available (FR-09).
3. There is exactly one ad unit ID per placement, configured through typed Remote Config with defaults.
4. A global frequency controller enforces the caps above for every placement.
5. `onPaidEvent` is logged as `ad_paid_event{placement, format, value_micros, currency, precision}`.
6. Pro users load no ad SDK request. Ads initialization is skipped once the entitlement is known.
7. No interstitial on primary actions: Translate, Mic, Copy, Apply theme, Purchase.

#### FR-28: Pro subscription & entitlement (MVP · Improve)
**Source:** App H §5 (Billing 8.3.0 present but dead; IAP $5.49–6.49 on PS); App L nonFeatures (dead `is_user_premium`); CMP #12, open Q4, Q9; PB §6–7.
**Description:** Play Billing Library 8.3.0 or later (the playbook catalog pins 8.3.0 while its monetization module cites 9.1.0; the TRD pins 8.3.0) with a single subscription product `pro` that has two base plans, monthly and annual, plus a trial offer on the annual plan. There may also be an optional lifetime one-time product (OD-03). `EntitlementRepository` is the single source of truth. It calls `queryPurchasesAsync` on cold start and on resume. Our backend verifies purchases (Play Developer API + RTDN) and exposes the entitlement to the AI quota service. Purchases are acknowledged within 3 days, and on purchase whenever possible.
**AC**
1. A purchase unlocks Pro features within 2 s of the purchase callback, both in the app and in the IME process (shared DataStore + IPC broadcast).
2. Restore works on reinstall and on a new device with the same Google account.
3. Expiry, grace period, on-hold and pause states are handled. During grace and on-hold the user sees a "Fix payment" banner.
4. The server rejects unverified purchase tokens for AI quota upgrades.
5. Refunds and revocations remove the entitlement within one RTDN cycle.

#### FR-29: Paywall (MVP · New)
**Source:** PB §6 (soft paywall, annual anchoring, 3–7 day trial, contextual triggers, remove-ads gateway, restore, localized prices); CMP mid-price gap; COMPETITORS G6.
**Description:** A single paywall screen with contextual headers. The header changes with the trigger: "Unlock unlimited AI polish", "Remove ads", "Premium themes". It has a benefit list, the annual plan pre-selected with a "save X%" badge, the monthly plan, trial terms, localized prices from `ProductDetails`, Restore, Terms and Privacy links, and a close button that is visible from 0 s. See §10 for the triggers.
**AC**
1. The close button is visible immediately, and the paywall can always be dismissed.
2. Trial and renewal terms are shown in plain text next to the CTA ("7 days free, then PKR X/year, cancel anytime in Google Play").
3. Prices are always fetched from Play and never hard-coded.
4. Events: `paywall_view{trigger}`, `paywall_close{trigger}`, `purchase_start{plan}`, `trial_start`, `purchase_success{plan}`, `purchase_fail{code}`.

#### FR-30: Push notifications (v1 · Improve)
**Source:** H-F:push-notifications (FCM topics; token logged; App H §5, §8).
**Description:** FCM with topic `all` and per-locale topics, for product updates and word-of-the-day (v1). On Android 13+, POST_NOTIFICATIONS is requested after the user's second successful session, never during onboarding. An in-app notification preference controls categories.
**AC**
1. The token is never logged.
2. Maximum of 3 marketing pushes per week, enforced server-side.
3. Each category has an opt-out.

#### FR-31: In-app update (MVP · Improve)
**Source:** H-F:in-app-update; L-F:in-app-update (forced exit-only update; App L §5).
**Description:** Play In-App Updates. FLEXIBLE is the default, with a snackbar to restart. IMMEDIATE is used only when `versionCode < rc.min_supported_version`. Updates are checked on Home resume at most once per 24 h.
**AC**
1. No update UI on exit.
2. IMMEDIATE is triggered only by the Remote Config minimum.
3. Users can dismiss FLEXIBLE.

#### FR-32: Deep links & intent routing (MVP · Improve)
**Source:** L-F:open-screen-intent-routing (exported `open_screen`, `language` crash; App L §6).
**Description:** A typed allow-list of destinations: `translator`, `themes`, `paywall?trigger=`, `settings/{section}`. There is no `conversation` or `camera` route (OD-21). They are reachable via the `app://` scheme internally and verified App Links for marketing. Unknown or invalid values route to Home.
**AC**
1. A fuzz test on `MainActivity` with random extras and URIs produces no crash.
2. Only allow-listed routes resolve.
3. The paywall cannot be deep-linked during onboarding.

#### FR-33: Rate & review (v1 · Improve)
**Source:** L-F:settings-growth; App H §5 ("rate always opens market").
**Description:** The Play In-App Review API is triggered at a happy moment: after the 5th successful translate commit or 3rd AI polish, at most once per 60 days. The "Rate us" settings row opens the store listing.
**AC**
1. Never shown in the first 2 days or right after an error.
2. No pre-filter ("Do you like us?" gating) that routes unhappy users away from the store.

#### FR-34: Privacy controls, data deletion & hardening (MVP · New)
**Source:** App H §6, §8; App L §6, §8; PS (Data safety; no deletion option); COMPETITORS G2; gap.
**Description:** See §11.
- Settings → Privacy offers "Delete my data": local wipe, plus a server delete of the anonymous UID, AI abuse logs, AI reports and purchase linkage. For Google-linked users it also deletes the Firebase Auth account.
- A **web deletion page** (Google sign-in, calls the same delete endpoint) is required by Play because Google sign-in creates accounts. Its URL is listed in Play Console (RN-08).
- Settings → Privacy lists the four consent classes (AI, cloud translate, auto-polish, voice notice) and lets the user withdraw each one (RN-09).
- `dataExtractionRules` exclude history, the clipboard store, the user dictionary and photos from cloud backup and device transfer. Transfer of settings only is optional.
- Network Security Config disables cleartext.
- FileProvider is scoped to `cache/share/`.
- In release builds, the Timber tree is a no-op and R8 strips `android.util.Log`.
- No `killProcess`.
**AC**
1. A release-build log audit (logcat grep during a scripted session) finds zero app log lines containing text, tokens, URLs or package names.
2. Deletion completes server-side within 30 days, and locally immediately. It works both in the app and from the web deletion page.
3. The Data safety form matches the SDK inventory, reviewed against the TRD SDK list before each release.

#### FR-35: Analytics & consent (MVP · New)
**Source:** App H §5 and App L §5 (ad-only analytics, no funnel; names over 40 characters in App H); PB §7.
**Description:** Firebase Analytics, or an equivalent (OD-13), carries the event plan in §12. There is an analytics opt-out toggle. Event names are snake_case and at most 40 characters. Parameters are bucketed. **No text content** and no host package names are sent. The only exception is an aggregate category (`host_category` from a local mapping of the top 200 apps), and that is an open decision (OD-14).
**AC**
1. A schema test fails the build if an event name is longer than 40 characters, or if a parameter key is not in the registry.
2. The IME process logs events only through a batched IPC to the app process, at most 1 flush per 60 s.
3. The analytics opt-out stops all non-essential events immediately.

#### FR-36: Offline language packs manager (MVP · New)
**Source:** gap (COMPETITORS G2); CMP #4.
**Description:** Settings → Offline languages lists translation models (ML Kit), recognizer availability (system on-device recognizer status, with a link to system settings), Whisper voice models (base / small, per FR-10 AC8), the dictionary pack and transliteration data. Each row shows size, status, download / delete, and a Wi-Fi-only toggle.
**AC**
1. A download shows an indeterminate progress indicator and an approximate size (ML Kit gives no byte progress and cannot cancel). Before download, the size shown is the total for the models still missing; a non-English pair also needs the English model. The action is named **"Stop"**, not "Cancel": the app hides the download and deletes the model when it finishes (RN-06).
2. A storage summary is shown.
3. The keyboard's offline banner links here.

### 7.9 Parked / later

#### FR-37: Two-way chat translation via Accessibility (Later · v2 candidate)
**Source:** COMPETITORS G4 ("Translator KB All Chats", 5.1M installs); CMP out-of-v1; CMP open Q8.
**Description:** Translate *incoming* messages in chat apps using an AccessibilityService overlay. It is parked until the Play Accessibility policy review is done (R-05). If it is built, it will be a separate opt-in module with a prominent disclosure, and limited to user-chosen apps.
**AC (gate):** A written policy assessment and a declared Accessibility use case are approved before any work starts.

#### FR-38: AI chat assistant in the keyboard (Later · Drop for v1)
**Source:** CMP out-of-v1.
**Description:** Not planned. Revisit after AI unit economics are proven.

---

## 8. Onboarding Flow

```
Install → Launch
  └─ Splash (≤ 1 s, no ad)  ─────────────────────────────────────────────┐
      ├─ IME enabled AND selected? ── yes ──► Home                      │
      └─ no                                                              │
         ▼                                                               │
  [O1] Welcome + UI language (auto from system; 12+ locales)             │ events:
       "Speak or type in your language. Send it in theirs."              │ onboarding_start
       CTA: Get started                                                  │
         ▼                                                               │
  [O2] Step 1/3 Enable                                                   │ onboarding_step_view{1}
       Illustration + 1-line privacy promise                             │
       ("We never see passwords. No ads in your keyboard.")              │
       CTA → system Input-method settings                                │
       ← return (poll + onResume) → auto-advance                         │ ime_enabled
       System warning dialog explained beforehand in-copy                │
         ▼                                                               │
  [O3] Step 2/3 Select                                                   │ onboarding_step_view{2}
       CTA → showInputMethodPicker → auto-advance on DEFAULT_INPUT_METHOD│ ime_selected  ← ACTIVATION
         ▼                                                               │
  [O4] Step 3/3 Languages                                                │ onboarding_step_view{3}
       Pick typing languages (pre-select from system locales, e.g. en+ur)│ languages_set
       Pick default translate pair; offer pack download (Wi-Fi only)     │ pack_download_start
         ▼                                                               │
  [O5] Try it (practice field; keyboard shown)                           │
       Coach marks: ① mic → say something ② Translate chip ③ ✦ AI        │ first_dictation / first_translate
       Skip available at all times                                       │ onboarding_complete
         ▼                                                               │
  [O6] Soft paywall (OD-05 default: SHOW once, dismissible, trial offer) │ paywall_view{trigger:onboarding}
         ▼                                                               │
  Home ──────────────────────────────────────────────────────────────────┘
```

**Rules**
- No ads anywhere from O1 to O6 or in the first session (PB §7).
- RECORD_AUDIO is requested at O5 on the first mic tap, after the voice disclosure sheet (FR-10 AC4). This is the main place to ask, because the keyboard itself cannot show a permission dialog (FR-10 AC5, RN-04). If the user denies it, they continue with typing.
- POST_NOTIFICATIONS is not requested during onboarding (FR-30).
- If the user leaves at O2 or O3, the next launch resumes at the same step. If the IME is still not selected 24 h after install, one reminder notification is sent, but only if notification permission was already granted (Android < 13 default).
- **The keyboard-side nudge:** if the IME is enabled but not the default when the app opens, Home shows a persistent "Select [Product] as your keyboard" card.

---

## 9. Screen Inventory

| ID | Screen / surface | Process | Tier | FRs | Ads (free) | Notes |
|---|---|---|---|---|---|---|
| S-01 | Splash | app | MVP | FR-08 | None | ≤ 1 s |
| S-02 | Onboarding O1–O5 | app | MVP | FR-08 | None | |
| S-03 | Paywall | app | MVP | FR-29 | None | Contextual header |
| S-04 | Home (hub: Translator, Dictionary, Phrasebook, Themes, Game tiles; activation card). No Conversation or Camera tile (OD-21) | app | MVP (tiles appear as features ship) | — | Banner (not first session) | |
| S-05 | Text Translator | app | MVP | FR-12, FR-26 | Native below result | No interstitial on Translate |
| S-06 | Translation History / Favourites | app | MVP | FR-12 | Native | |
| S-07 | Language picker (sheet) | app + IME | MVP | FR-11 | None | Offline badge per language |
| S-08 | Settings root + sections (Languages, Voice, Translate, AI, Typing, Privacy, Subscription, About) | app | MVP | FR-09 | None | |
| S-09 | Offline languages manager | app | MVP | FR-36 | None | |
| S-10 | Private apps list | app | MVP | FR-02 | None | |
| S-11 | Themes gallery + preview | app | MVP (basic) / v1 (custom) | FR-17 | Native in gallery | |
| S-12 | Custom theme editor + colour picker | app | v1 | FR-17, FR-18 | None | |
| S-13 | Conversation | Not in this app (OD-21) | n/a | FR-19 | n/a | |
| S-14 | Conversation history | Not in this app (OD-21) | n/a | FR-19 | n/a | |
| S-15 | Camera translate (capture → results/overlay) | Not in this app (OD-21) | n/a | FR-20 | n/a | |
| S-16 | Dictionary search + entry | app | v1 | FR-21 | Native | |
| S-17 | Phrasebook categories + list | app | v1 | FR-22 | Native | |
| S-18 | Word game | app | v1-late | FR-25 | Rewarded (user-started) | |
| S-19 | OSS licences / attributions | app | MVP | FR-01, FR-09 | None | |
| S-20 | Delete my data confirm | app | MVP | FR-34 | None | |
| S-21 | PROCESS_TEXT / SEND translate sheet | app (translucent) | MVP | FR-14 | **None** | Acquisition surface |
| K-01 | Keyboard main (letters, numbers, symbols) | IME | MVP | FR-01, FR-03, FR-05 | **None (never)** | |
| K-02 | Smartbar (suggestions / action chips / private lock) | IME | MVP | FR-07, FR-02 | None | |
| K-03 | Voice panel (waveform, lang chip, stop, ✦ Polish) | IME | MVP | FR-10, FR-16 | None | |
| K-04 | Translate bar (pair chip, source line, preview, ✓, undo) | IME | MVP | FR-13 | None | |
| K-05 | AI rewrite sheet (tones, result, replace/insert/retry, quota) | IME | MVP | FR-15 | None | Inline paywall entry |
| K-06 | Emoji panel | IME | MVP | FR-04 | None | |
| K-07 | Clipboard panel | IME | v1 | FR-06 | None | |
| K-08 | Sticker panel | IME | v1 | FR-23 | None | |
| K-09 | Transliteration candidate strip | IME | v1 | FR-24 | None | |
| K-10 | In-IME quick settings (language, theme toggle, one-handed) | IME | MVP | FR-01 | None | |
| K-11 | Offline / download banner | IME | MVP | FR-36 | None | |

---

## 10. Monetization

### 10.1 Model

Freemium. The free tier is generous and carries ads on **companion-app surfaces only**. **Pro** removes ads and unlocks unlimited AI plus premium content. No feature that protects privacy is ever paywalled: the sensitive policy, offline translation and deletion are free.

### 10.2 Free vs Pro

| Capability | Free | Pro |
|---|---|---|
| Keyboard, layouts, emoji, suggestions, transliteration | ✓ | ✓ |
| Voice typing (on-device / system) | ✓ unlimited | ✓ |
| On-device translation (bar, translator, PROCESS_TEXT) | ✓ unlimited | ✓ |
| Cloud-quality translation tier | ✗ (OD-07 may allow 20 per day as a taster) | ✓ fair use |
| AI rewrite + AI voice polish (shared pool) | **10 per day** (OD-04) | 150 per day, 3,000 per month fair use (TRD OD-16) |
| Auto-polish every dictation (off by default, own consent, counts against quota) | ✗ | ✓ |
| Themes | 12 presets + basic custom (v1) | + 20+ premium presets, premium key sounds |
| Stickers | Free packs | + premium packs |
| Word game hints | Rewarded ad | Unlimited |
| App-side ads | Yes (capped) | **None** |
| Offline packs, privacy features, history | ✓ | ✓ |

### 10.3 Pricing (defaults; final in OD-02 and OD-03)

The category has a pricing hole between ad-clone IAP ($3.49–6.49; H's IAP is $5.49–6.49, PS) and AI keyboards ($15–30 per month; COMPETITORS §2, G6). We position at the **mid-price**:

| Plan | US default | Regional (PK / IN / BD / EG / ID) | Notes |
|---|---|---|---|
| Monthly | $2.99 | Play price template, about PKR 450 / INR 149 / BDT 199 | No trial |
| **Annual** (pre-selected) | $19.99 (save about 44%) | about PKR 2,500 / INR 899 | **7-day free trial** (PB §6: 3–7 days) |
| Lifetime (optional, OD-03) | $39.99 | about PKR 5,500 / INR 1,999 | One-time product; launch at v1 if annual conversion is weak |

Prices are always localized through `ProductDetails` (FR-29 AC3).

### 10.4 Paywall triggers

| Trigger ID | Moment | Surface | Frequency |
|---|---|---|---|
| T1 `onboarding` | After O5 (OD-05) | S-03 full | Once |
| T2 `ai_quota` | AI quota exhausted in the IME or app | K-05 inline card → S-03 | Each time the quota is hit (inline); full paywall at most once per day |
| T3 `premium_theme` | Apply on a 👑 theme | S-03 | On tap |
| T4 `remove_ads` | "Remove ads" link on banner/native ad slots and in Settings | S-03 | On tap |
| T5 `cloud_quality` | Toggle cloud translation tier | S-03 | On tap |
| T6 `premium_sticker` | Tap a premium pack | S-03 | On tap |
| T7 `auto_polish` | Enable auto-polish | S-03 | On tap |
| T8 `win_back` | Day 3 and day 10 Home open with no purchase (soft card, not full screen) | Home card | Max 2 lifetime |

There are no paywall interruptions in the middle of a task inside the IME, apart from T2's inline card, which the user can dismiss and which leaves typing intact.

### 10.5 Ads summary

See FR-27 for placements and caps. One unit per placement. A global frequency cap covers every placement. `onPaidEvent` revenue is logged. **There are no ads in the keyboard**, so there are no host deny-lists and no IME ad SDK. No interstitial is tied to Translate, Mic, Copy, Apply or Purchase, and none appears on exit or back.

### 10.6 Policy notes (Play, AdMob, Billing)

- **Ads (PB §7):** no interstitials on app exit or back-press. No app-open ad on first launch or over splash. Ads never sit adjacent to tappable controls. UMP runs before `MobileAds.initialize()`, and a persistent privacy-options entry point exists. Rewarded ads run only when the user starts them. No ads in the IME, which removes the "ads in system-level surfaces / disruptive ads" risk.
- **Subscriptions:** clear disclosure of trial length, price and renewal next to the CTA. Cancellation instructions are shown. Purchases are acknowledged within 3 days, or Play auto-refunds them. No dark patterns: the close button is visible, and no fake countdowns.
- **IME / user data:** the Data safety form follows the full draft in TRD §11.6 (RN-07). Under Play's definitions, data sent to a provider that processes it for us is *collected, not shared*; data the AdMob SDK sends for its own advertising is *shared*. Key lines:
  - **Audio: not declared** on the system-recognizer path, because the RecognitionService app captures the audio, not us. Declare Audio only if cloud speech recognition ships (TRD OD-04). On-device Whisper does not change this: our process records the audio, but it is processed on the phone and never transmitted, so it is not collected under Play's definitions.
  - Text selected for AI or cloud translation, and transcripts sent for polish: collected, not shared, ephemeral, optional.
  - Device IDs and approximate location: collected and **shared** (AdMob), free tier only.
  - App interactions, crash logs and diagnostics: collected; shared with AdMob for ad interactions and diagnostics.
  - User IDs (Firebase UID), email (Google sign-in only), purchase history: collected, not shared.
  - Typed keystrokes: not collected.

  Text input is never declared as "collected" for analytics, because we don't collect it.
- **Consent classes (RN-09):** four classes, each with its own disclosure screen and an affirmative "Agree" (dismissing or going back is not consent), shown in the app, not inside the keyboard window. Each can be withdrawn in Settings → Privacy, which turns the feature off.
  1. `consent_ai`: before the first AI rewrite or polish sent to the cloud (FR-15, FR-16, and the v1 PROCESS_TEXT "✦ Rewrite" in FR-14). Gemini Nano on-device requests do not need it
  2. `consent_cloud_translate`: before the first cloud-translation request
  3. `consent_auto_polish`: when auto-polish is turned on (FR-16 AC5)
  4. `consent_voice_notice`: a notice, not consent, before the first dictation (FR-10 AC4)
- **AI-generated content policy:** the rewrite output is user-initiated. A "Report" action exists in the AI sheet for rewrite and polish, as Play's AI-generated content policy requires reporting for generative apps. Text is attached only if the user opts in (RN-08).
- **Account deletion:** in the app and through a web deletion URL (FR-34, RN-08).
- **Families:** not targeted at children. The content rating will be set accordingly.
- **Trademark/ASO:** no use of "Google", "Gboard" or competitor names in the title or icon. Keywords follow COMPETITORS §5.

---

## 11. Privacy Commitments

These are product requirements, not marketing copy. Each one is tested.

| # | Commitment | Enforced by |
|---|---|---|
| PC-1 | **Text typed in password, incognito/no-learning fields, or user-listed private apps never leaves the device**: no translation, voice, AI, sticker search, clipboard capture or analytics. | FR-02 policy + CI egress test (FR-02 AC2) |
| PC-2 | **No keystroke logging.** We never record, transmit or analyse what the user types, either locally for analytics or remotely. Suggestions learning stays on-device and can be cleared. | FR-05, FR-35 schema test (no text params), code review checklist |
| PC-3 | **On-device by default.** Translation, transliteration, dictionary, voice (on-device recognizer or Whisper, where available) and AI (Gemini Nano, where supported) run on-device. Text goes off-device only when the user explicitly uses AI, the cloud translation tier, or the system speech service, and each of these is disclosed once. | FR-10 AC4, FR-11, FR-15, FR-16 |
| PC-4 | **No ads in the keyboard. No ad or analytics SDK in the IME's code path** beyond batched first-party events relayed through the app. | FR-27 AC1 Gradle rule |
| PC-5 | Server-side AI and translate proxies do not persist request text. Only abuse metadata is kept, for at most 30 days. | Backend config; TRD; privacy policy |
| PC-6 | No host-app package names or URLs are logged or sent. | FR-34 log audit; FR-35 (OD-14) |
| PC-7 | Users can delete all data in-app and through a web form. Local data is excluded from cloud backup. | FR-34 |
| PC-8 | The Data safety form is accurate and reviewed every release against the SDK inventory. | FR-34 AC3, release checklist |
| PC-9 | No data sale. No SDKs whose terms permit data resale. | TRD SDK allow-list |
| PC-10 | Release builds emit no sensitive logs. | FR-34 AC1 |

---

## 12. Analytics Event Plan

**Conventions.** snake_case. Names at most 40 characters. At most 25 parameters, from a registry. Text content, host package names and keystroke counts are never included. Durations and lengths are bucketed. The IME batches events and relays them to the app process. Every event respects the analytics opt-out.

**User properties:** `ui_locale`, `typing_langs` (sorted, at most 5), `pro_status` (free/trial/pro/grace), `ime_selected` (bool), `ad_consent` (granted/denied/na), `install_cohort_week`.

| Event | When | Key params | Feeds metric |
|---|---|---|---|
| `onboarding_start` | O1 shown | `source` (launcher/process_text/deeplink) | Onboarding completion |
| `onboarding_step_view` | Each step shown | `step` (1–3, try) | Funnel |
| `ime_enabled` | Enable detected | `seconds_since_start_bucket` | **Activation** |
| `ime_selected` | Selected as default | `seconds_since_start_bucket` | **Activation** |
| `languages_set` | O4 done | `count`, `has_rtl` | |
| `onboarding_complete` | O5 done or skip | `skipped` (bool) | Completion |
| `ime_session_start` | `onStartInputView` (sampled 1 per 10 min per user) | `subtype`, `is_sensitive` (bool) | **D1/D7**, IME-still-selected |
| `first_dictation` | First voice session with words | `lang` | Activation quality |
| `voice_session_start` | Mic tapped | `lang`, `engine` (on_device/system) | **Voice sessions** |
| `voice_session_end` | Session ends | `duration_bucket`, `words_bucket`, `end_reason` (user/silence/error), `error_code` | Voice sessions, quality |
| `voice_permission_result` | RECORD_AUDIO result | `granted` | |
| `translate_done` | Translation committed or shown as final | `surface` (bar/translator/process_text), `engine` (on_device/cloud), `src`, `tgt`, `chars_bucket`, `offline` (bool) | **Translations/day** |
| `first_translate` | First `translate_done` | `surface` | |
| `translate_error` | Failure | `surface`, `reason` (no_pack/timeout/net/quota) | Quality |
| `pack_download_start` / `pack_download_done` | Model download | `lang`, `kind` (translate/dict/translit), `size_bucket`, `result` | Offline adoption |
| `ai_rewrite_done` | Rewrite applied (replace/insert) | `tone`, `chars_bucket`, `latency_bucket`, `action` (replace/insert) | AI adoption |
| `ai_polish_done` | Polish applied | `mode` (clean/clean_translate), `auto` (bool) | AI adoption |
| `ai_quota_hit` | Quota exhausted | `feature` (rewrite/polish) | Paywall T2 |
| `ai_error` | Failure | `reason` | |
| `ai_output_reported` | User reports output | `tone` | Policy |
| `translit_commit` | Transliterated word committed (sampled 1%) | `lang` | Transliteration adoption |
| `process_text_open` | PROCESS_TEXT/SEND sheet | `action` (process_text/send), `editable` (bool) | Acquisition |
| `process_text_replace` | Replace tapped | | |
| `conversation_turn` | One translated turn | `src`, `tgt`, `engine` | Not in this app (OD-21) |
| `camera_translate_done` | OCR + translate | `blocks_bucket`, `src`, `tgt` | Not in this app (OD-21) |
| `dictionary_lookup` | Lookup | `offline` (bool), `found` (bool) | v1 |
| `theme_apply` | Theme applied | `theme_id`, `premium` (bool) | |
| `sticker_send` | Sticker committed | `pack_id`, `premium` (bool), `method` (commit_content/share) | v1 |
| `paywall_view` | Paywall shown | `trigger` (T1–T8) | Conversion |
| `paywall_close` | Dismissed | `trigger`, `seconds_bucket` | |
| `purchase_start` | Plan CTA | `plan` (monthly/annual/lifetime) | |
| `trial_start` | Trial purchase success | `plan` | **Trial conversion** |
| `purchase_success` | Paid purchase success | `plan`, `price_tier` | **Conversion** |
| `purchase_fail` | Billing error or cancel | `code` | |
| `subscription_state` | State change (from server or app) | `state` (active/grace/hold/paused/expired/revoked) | Churn |
| `restore_purchases` | Restore tapped | `found` (bool) | |
| `ad_impression` | (SDK auto) | `placement`, `format` | Ads |
| `ad_paid_event` | `onPaidEvent` | `placement`, `format`, `value_micros`, `currency`, `precision` | Ad ARPDAU |
| `privacy_private_app_add` | User adds a private app | (no package name) | |
| `delete_data` | Data deletion confirmed | | |
| `review_prompt_shown` | In-App Review launched | `trigger` | |
| `update_prompt` | In-app update shown | `type` (flexible/immediate), `result` | |
| `notification_open` | Push opened | `category` | v1 |

Revenue tracking: Play Console is the source of truth for subscriptions. Analytics events are for funnel attribution only.

---

## 13. Release Plan

Timeline from CMP: MVP is about 48 dev-days (about 10 weeks), full v1 about 72 dev-days. With a 25–30% buffer, that gives **about 3 months to MVP and about 4.5 months to v1**. v2 is scoped after v1 data. The TRD bottom-up estimate is 60 dev-days to MVP and 88.5 to v1 (81.5 after OD-21), including the scope that TRD §8.1 adds for FR-03, 06, 07, 09, 18, 30, 33, 34 and 35. With buffer that is 75–78 and 111–115 working days (about 3.6 and 5.3 months). Plan dates use the TRD figure.

**Revised plan (decision 2026-10-18, RN-01): no scope cuts.** The TRD §11.9 review raises the MVP to 76 raw dev-days, or 95–99 working days with buffer. Adding the 14-day closed test and Play review, the public MVP launch is **about M+5**, and v1 (about 104.5 raw days, 131–136 with buffer) is **about M+6.5**. All MVP and v1 scope below stays, except for one later change. **OD-21 (2026-11-14)** removes FR-19 and FR-20 and saves about 7 dev-days. v1 becomes 104.5 − 7 = about 97.5 raw days, or 122–127 with buffer (TRD §11.9). **Added 2026-10-18:** the on-device Whisper fallback (FR-10) and a Gemini Nano spike plus on-device-first AI (FR-15/16, OD-20) add an estimated 7–9 raw dev-days, which moves the public MVP to **about M+5.5** (estimate to confirm in the TRD).

### 13.1 MVP: "Voice + Translate keyboard that is private" (≈ M+5)

Features (CMP #1–13): FR-01, FR-02, FR-03 (ur, hi, ar), FR-04, FR-05, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14, FR-15, FR-16, FR-17 (basic), FR-26, FR-27, FR-28, FR-29, FR-31, FR-32, FR-34, FR-35, FR-36.

**Stages**
Stage weeks are rescaled to the M+5 plan.
1. **Internal alpha (≈ week 13):** core, voice, translate bar, and the sensitive policy with the CI egress gate.
2. **Closed testing (≈ week 18):** 100 or more testers in PK, IN and AE (Play requirement: 12+ testers for 14 days for new personal accounts, if applicable). Native-speaker quality eval for ur, hi, ar (FR-11 AC5).
3. **Open testing / staged production (≈ week 21–22):** 5% → 20% → 50% → 100%, gated on §5.4 guardrails.

**Exit criteria**
- Activation ≥ 50% in closed testing
- Crash-free ≥ 99.5% in testing
- Zero sensitive-egress failures
- Data safety form submitted and approved
- Privacy policy live

### 13.2 v1: "Regional depth + companion app" (≈ M+6.5)

FR-03 (bn, ps), FR-05 (next-word prediction), FR-06, FR-17 (custom), FR-18, FR-21, FR-22, FR-23, **FR-24 (Roman Urdu / Hinglish)**, FR-25 (if capacity allows; otherwise v1.1), FR-30, FR-33. FR-19 and FR-20 are out of this app (OD-21). UI locales go to 31. Lifetime SKU decision (OD-03). PROCESS_TEXT "✦ Rewrite" action.

### 13.3 v2: "Beyond the keyboard" (≈ M+9, scoped after v1 data)

- FR-37 two-way chat translation (only if the policy gate passes)
- Glide typing evaluation (OD-10)
- Additional dictionaries (ur, hi, ar)
- Fine-tuned Whisper models for ur, hi, bn, ps to raise on-device accuracy (R-02)
- Additional languages (tr, fa, ms, sw)
- Ad mediation
- Wear/tablet layout polish

---

## 14. Risks

| ID | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| R-01 | **Activation drop-off.** Users abandon at the system "this keyboard may collect all text" warning. | High | High | Pre-warn in copy (O2). State the privacy promise. Auto-advance. Activation card on Home. A/B test copy (OD-12). |
| R-02 | **Voice quality / offline availability varies by OEM.** On-device recognizers are missing for ur, bn or ps on many devices (CMP open Q6). | High | High | Fall back to the system service with disclosure. Offline language download guidance. On-device Whisper fallback at MVP (FR-10), gated per language by eval; fine-tuned models in v2. |
| R-03 | **ML Kit translation quality for ur, hi, ar, ps and bn is below user expectations** (CMP open Q7). | Medium | High | Native-speaker eval gate. "Beta" label. Cloud tier for Pro. Fail-open messaging. Track `translate_error` and thumbs-down (v1). |
| R-04 | **Play policy:** IME data-collection review, AI-generated content, subscription disclosure, Data safety mismatch. | Medium | High | No ads in the IME. Prominent disclosures. Accurate Data safety. Report-output flag. Pre-launch policy review checklist. |
| R-05 | **Accessibility policy** blocks two-way chat translation (G4). | High | Medium | Parked to v2 behind a gate (FR-37). Not part of the core value. |
| R-06 | **AI cost overrun / abuse.** Scripted quota abuse, long inputs. | Medium | Medium | App Check + Play Integrity, server quota, a 2,000-character cap, cheap model tier for polish (OD-08), Gemini Nano on-device first where supported (no cloud cost), per-UID rate limits, cost alarms. |
| R-07 | **Clean-room / IP challenge** (assets or strings resemble App H or L). | Low | High | Engineers never read decompiled sources. Original assets with a licence log. PR checklist. FlorisBoard attribution. |
| R-08 | **Data licensing:** CC-BY-SA dictionary or transliteration data imposes share-alike obligations on derived data packs. | Medium | Medium | Ship derived packs as separate downloadable data with attribution. Legal review (OD-11). Keep CC-BY-SA data separate from app code. |
| R-09 | **Low-end device performance:** IME jank or memory pressure from ML Kit plus Compose. | Medium | High | Lazy model loading, models in the app process via a bound service (TRD), Baseline Profiles, perf CI on a 3 GB device. |
| R-10 | **Monetization underperforms** without in-IME ads (competitors rely on them). | Medium | Medium | Mid-price regional Pro, contextual triggers, rewarded game hints, mediation in v2. Ads are not the primary lever. |
| R-11 | **Competitor response:** Gboard adds offline translate or AI polish. | Medium | Medium | Regional depth (Roman Urdu), privacy positioning, price. Move fast on v1. |
| R-12 | **FlorisBoard upstream changes / maintenance** (pre-1.0 APIs). | Medium | Medium | Pin to a tagged release. Keep fork changes in our own modules (`:ime/bridge`). Merge upstream quarterly. |
| R-13 | **Trademark collision** with the working name. | Low | Medium | Trademark search before branding (OD-01). |
| R-14 | **Gemini Nano unusable from the keyboard.** ML Kit GenAI allows inference only from the top foreground app; an IME may get `BACKGROUND_USE_BLOCKED`. Device coverage in PK/IN/BD is small, and ur/hi support is unconfirmed. | High | Low | Spike before committing (OD-20). Cloud fallback is always present, so failure only removes a cost saving. |

---

## 15. Open Decisions

| ID | Decision | Options | **Recommended default** | Owner | Needed by |
|---|---|---|---|---|---|
| OD-01 | Product name & brand | Saaz / Zuban / Bolo / other | Shortlist 3. Pick the one that clears a trademark search in PK, IN and US | Product + Legal | Week 4 |
| OD-02 | Pro price points | $1.99–4.99/month; $14.99–29.99/year | **$2.99/month, $19.99/year**, regional templates (PK, IN, BD, EG, ID) | Product + Growth | Closed testing |
| OD-03 | Lifetime SKU | None / $29.99 / $39.99 | **Not at MVP.** Add $39.99 in v1 if annual trial→paid < 30% | Product | v1 planning |
| OD-04 | Free AI quota & pooling | 5 / 10 / 20 per day; separate or shared pools | **10 per day, shared pool** (rewrite + polish). Auto-polish is Pro only, off by default, needs its own consent, and counts against the quota (RN-03) | Product + Backend | Week 6 |
| OD-05 | Soft paywall after onboarding | Show / hide / A/B | **Show once, dismissible, after O5**. A/B against hidden at 50/50 from launch | Growth | Week 8 |
| OD-06 | Ad network & mediation | AdMob only / AdMob + AppLovin MAX | **AdMob + UMP only at MVP.** Evaluate mediation in v2 | Growth/Eng | Week 6 |
| OD-07 | Cloud translation vendor & free taster | Google Cloud Translation v3 / DeepL / both; free taster 0 or 20 per day | **Google v3** (best ur, hi, ar coverage). **Pro only, no free taster** at MVP | Eng + Product | Week 5 |
| OD-08 | LLM vendor/model for AI | Gemini Flash-class / GPT-4o-mini-class / Claude Haiku-class | **The cheapest model passing our eval** on 300 ur, hi, ar, en rewrite samples. Abstracted behind `/v1/rewrite`. TRD OD-07 implements this per action (Haiku 5.5 first, Sonnet 5.5 if a language or tone fails the eval); close the two together. **Decision 2026-10-18 (RN-02): cheapest models only.** Opus 5.5 is allowed only as the eval reference, never in production. If the eval forces Sonnet 5.5 for an action, the Pro monthly cap for it drops to about 400 actions. Gemini Nano on-device runs first where supported, if OD-20 passes. Finance still signs off the cost ceiling per user | Eng (AI) + Finance | Week 4 |
| OD-09 | AI request retention | 0 days / 30-day metadata / opt-in samples | **Metadata only for 30 days, no text.** Opt-in "help improve" off by default | Product + Legal | Week 6 |
| OD-10 | Glide typing | MVP / v1 / v2 / never | **v2 evaluation.** Not differentiating | Eng | v1 planning |
| OD-11 | Corpus/data licences (frequency lists, Wiktionary, Dakshina) | Use / substitute / license commercially | **Use with attribution as separate data packs**, after legal review | Legal | Week 3 |
| OD-12 | Metric targets re-baseline | Keep / adjust | **Re-baseline after 4 weeks of production data** | Product + Data | M+4 weeks |
| OD-13 | Analytics stack | Firebase / PostHog / Amplitude | **Firebase Analytics + Crashlytics** (free, Play-native). Export to BigQuery | Eng | Week 2 |
| OD-14 | Host-app category analytics | None / local category mapping | **None at MVP** (strict privacy). Reconsider in v1 with a privacy review | Product + Privacy | v1 planning |
| OD-15 | minSdk | 24 / 26 | **26** (PB convention). Covers about 96% of the target markets; 24 would be a deliberate deviation (CMP) | Eng | Week 1 |
| OD-16 | Recognizer strategy (CMP Q6) | System only / system + own offline model | **System recognizer (prefer on-device) first, on-device Whisper fallback at MVP** (decision 2026-10-18). Cloud speech recognition is not used. Fine-tuned models evaluated in v2 | Eng | Week 3 |
| OD-17 | Translate-bar default mode | Preview & send / Live replace | **Preview & send** (safest; never touches host text until ✓) | Product/UX | Week 5 |
| OD-18 | Remote Config cadence (CMP Q5) | 1 h / 12 h / 24 h fetch | **12 h minimum fetch, 1 h in debug.** Small typed schema | Eng | Week 4 |
| OD-19 | Two-way chat translation (Accessibility) | Build v2 / never | **Policy assessment in v1, decide for v2** | Product + Legal | v1 end |
| OD-20 | Gemini Nano on-device AI | Ship on-device first / cloud only | **Spike first (about 1 day):** confirm an IME can call ML Kit GenAI (no `BACKGROUND_USE_BLOCKED`), measure ur/hi/ar/en quality against the OD-08 eval, and check device coverage in launch markets. Pass → Nano first, cloud fallback. Fail → cloud only, retry in v1 | Eng (AI) | Sprint 1 |
| OD-21 | Conversation mode and camera translation (FR-19, FR-20) | Keep in v1 / move to a separate app | **Decided 2026-11-14: move both to a separate future app** (`docs/decisions/od-21-drop-conversation-camera.md`). This keeps the app focused on the keyboard and saves about 7 dev-days in Sprint 5 | Product | done |

---

## 16. Out of Scope (this PRD)

- Everything listed in §6.3 (dropped anti-patterns)
- NG-1 to NG-9
- Backend implementation details, which are in the TRD
- Web or desktop clients
- Enterprise features
- Any reuse of App H or App L code, endpoints, assets, keys or strings
- Conversation mode (FR-19) and camera translation (FR-20). They move to a separate future app (OD-21)

---

## 17. Traceability Matrix

Every FR is listed with its source references. **Gap codes:**
- **G1** AI-polished voice + translation
- **G2** offline / privacy
- **G3** regional depth + Roman Urdu / Hinglish
- **G4** two-way chat
- **G5** quality bar
- **G6** pricing middle ground

These are from COMPETITORS §4.

| FR | Title | Tier | Disposition | App H source | App L source | CMP | COMPETITORS / gap | Metric(s) |
|---|---|---|---|---|---|---|---|---|
| FR-01 | Core keyboard + subtypes | MVP | Improve | H-F:ime-core-keyboard; §6 | L-F:ime-core-keyboard; §6 | #1 | G5 | Activation, D1/D7 |
| FR-02 | Sensitive-field policy | MVP | New | §3.1 (no check) | §3.2 (no check) | #1; Q1 | G2 | Egress = 0 |
| FR-03 | Regional layouts | MVP/v1 | Improve | §6 (7 langs) | L-F:urdu-shift-layout | #1 | G3 | D7 (ur/hi/ar) |
| FR-04 | Emoji panel | MVP | Keep | H-F:emoji-panel | (upstream) | #1 | — | — |
| FR-05 | Suggestions/autocorrect | MVP | Improve | H-F:suggestions-autocorrect | L-F:suggestions-autocorrect | #8 | G5 | D7 |
| FR-06 | Clipboard history | v1 | Improve | H-F:clipboard-suggestions | L-F:ime-core-keyboard (clipboard off) | — | G2 | — |
| FR-07 | Smartbar quick actions | MVP | Improve | H-F:smartbar-quick-actions | — | #1 | G5 | Voice/translate adoption |
| FR-08 | Onboarding enable + select | MVP | Improve | H-F:onboarding-settings; §5 (splash) | L-F:onboarding-enable-keyboard; nonFeatures (alias) | #2 | G5 | **Activation** |
| FR-09 | Settings | MVP | Improve | H-F:onboarding-settings | L-F:settings-growth | #2 | G2 | — |
| FR-10 | Voice typing | MVP | Improve | H-F:voice-typing-ime; §3.2 | L-F:ime-voice-typing; §3.1 | #3; Q6 | G1, G2 | **Voice sessions** |
| FR-11 | On-device translation core | MVP | New | §2, §3.1, §4 (gtx, refuses offline) | §2 E1, §3.3 | #4; Q7 | G2 | Translations/day |
| FR-12 | Text translator + history | MVP | Improve | H-F:text-translator; §3.3 | L-F:text-translator; §3.3 | #5 | G5 | Translations/day |
| FR-13 | Translate bar | MVP | Improve | H-F:keyboard-translation-bar; §3.1 | L-F:ime-live-translate; §3.2 | #6 | G2, G5 | **Translations/day** |
| FR-14 | PROCESS_TEXT / SEND | MVP | Keep/Improve | H-F:process-text-share-translate | — | #7 | G2 | Translations/day, installs |
| FR-15 | AI rewrite | MVP | New | H-F:ai-rewrite-smartbar | L-F:ai-rewrite-greenfield | #9 | G1 | AI adoption, conversion |
| FR-16 | AI voice polish + translate | MVP | New | — (gap) | — (gap) | #10 | G1 | AI adoption, voice sessions |
| FR-17 | Themes | MVP/v1 | Improve | H-F:keyboard-themes-customizer | L-F:keyboard-themes | #13 | — | Conversion (T3) |
| FR-18 | Colour picker | v1 | Keep | H-F:color-picker | — | — | — | — |
| FR-19 | Conversation mode | Out (OD-21) | Improve | H-F:conversation-mode; §3.3 | L-F:voice-conversation; §3.5 | #14 | G2 | Translations/day |
| FR-20 | Camera translation | Out (OD-21) | Improve | H-F:camera-translation; §3.4 | L-F:camera-translator; §3.4 | #15 | G3 | Translations/day |
| FR-21 | Dictionary | v1 | Improve | H-F:dictionary; §2 | L-F:dictionary; §2 E2, §3.6 | #16 | G2 | — |
| FR-22 | Phrasebook | v1 | Improve | H-F:offline-phrasebook | — | #17 | G2 | — |
| FR-23 | Stickers | v1 | Improve | H-F:sticker-packs; §2, §3.5 | L-F:stickers; §3.7 | #18 | — | Conversion (T6) |
| FR-24 | Roman Urdu / Hinglish transliteration | v1 | New | — (gap) | — (gap; urdu-shift only) | #19 | **G3** | Transliteration adoption, D7 |
| FR-25 | Daily word game | v1-late | Keep | — | L-F:guess-word-game | #20 | — | D7, ad ARPDAU |
| FR-26 | TTS service | MVP | Improve | §3.3 | §4 | #5 | G5 | — |
| FR-27 | App-side ads + consent | MVP | Improve | H-F:ad-monetization; §5 | L-F:ads-consent-stack; §5 | #11 | G5 | Ad ARPDAU, rating |
| FR-28 | Pro subscription | MVP | Improve | §5 (Billing dead); PS IAP | nonFeatures (dead premium) | #12; Q4, Q9 | G6 | **Conversion** |
| FR-29 | Paywall | MVP | New | — | — | #12; mid-price gap | G6 | Conversion |
| FR-30 | Push notifications | v1 | Improve | H-F:push-notifications; §8 | — | — | — | D7 |
| FR-31 | In-app update | MVP | Improve | H-F:in-app-update | L-F:in-app-update; §5 | — | G5 | Crash-free (version spread) |
| FR-32 | Deep links / routing | MVP | Improve | §6 (`open_screen`) | L-F:open-screen-intent-routing; §6 | — | G5 | Crash-free |
| FR-33 | Rate & review | v1 | Improve | §5 (rate opens market) | L-F:settings-growth | — | G5 | Rating |
| FR-34 | Privacy, deletion, hardening | MVP | New | §6, §8 | §6, §8 | Q1 | G2 | Data safety accuracy |
| FR-35 | Analytics & consent | MVP | New | §5 (ad-only analytics) | §5 (≈50 ad events) | — | — | All metrics |
| FR-36 | Offline packs manager | MVP | New | §3.1 (refuses offline) | §3.3 | #4 | G2 | Offline adoption |
| FR-37 | Two-way chat (Accessibility) | Later | Parked | — | — | out of v1; Q8 | G4 | — |
| FR-38 | AI chat in keyboard | Later | Drop (v1) | — | — | out of v1 | — | — |

### 17.1 Reverse check: every source feature maps to an FR or a drop

- **App H (21/21):** ime-core-keyboard→FR-01/02 · voice-typing-ime→FR-10 · keyboard-translation-bar→FR-13 · text-translator→FR-12 · conversation-mode→FR-19 · camera-translation→FR-20 · offline-phrasebook→FR-22 · dictionary→FR-21 · keyboard-themes-customizer→FR-17 · sticker-packs→FR-23 · suggestions-autocorrect→FR-05 · onboarding-settings→FR-08/09 · ad-monetization→FR-27 (+D-01, D-08, D-09) · process-text-share-translate→FR-14 · ai-rewrite-smartbar→FR-15 · emoji-panel→FR-04 · clipboard-suggestions→FR-06 · smartbar-quick-actions→FR-07 (+D-17) · color-picker→FR-18 · push-notifications→FR-30 · in-app-update→FR-31.
- **App L (18/18):** onboarding-enable-keyboard→FR-08 · ime-voice-typing→FR-10 · text-translator→FR-12 · settings-growth→FR-09/33 · ime-core-keyboard→FR-01 · ai-rewrite-greenfield→FR-15 · dictionary→FR-21 · stickers→FR-23 · ime-live-translate→FR-13 · camera-translator→FR-20 · ads-consent-stack→FR-27/35 · guess-word-game→FR-25 · voice-conversation→FR-19 · keyboard-themes→FR-17 · suggestions-autocorrect→FR-05 · in-app-update→FR-31 · open-screen-intent-routing→FR-32 · urdu-shift-layout→FR-03.
- **App L nonFeatures (11/11):** in-IME ad + deny-list→D-01 · dead is_user_premium→D-07 · missing SettingsLauncherAlias→FR-08 AC6 · logcat leaks→D-03 · unread RC natives→D-13 · AdMob test-device ID→D-12 · Nav3 correction→(TRD architecture note, no product impact) · killProcess→D-04 · forced update→D-05 · open_screen crash→D-06 · stale-length deletion→FR-13 AC1.
- **Competitor gaps:** AI rewrite→FR-15 · AI voice polish + translate→FR-16 · offline/private→FR-02, FR-11, FR-36, §11 · Roman Urdu/Hinglish→FR-24 · PROCESS_TEXT translate→FR-14 · two-way chat→FR-37 (parked) · quality bar→§5.4 guardrails · pricing middle ground→§10.3.

---

## Review log (2026-10-17)

Evidence-fidelity review against both teardown reports, `CLASS_COVERAGE.md`, both `features.json`, `COMPARISON.md`, `COMPETITORS.md` and the TRD.

**Checks that passed.** All 21 App H features, all 18 App L features and all 11 App L nonFeatures map to an FR or a D-row (§17.1). All backlog scores ((I×2+M)/D) recompute correctly. The verified figures hold: the 86-package deny-list, 13 (H) and 10 (L) interstitial placements, 20-item / 60 s clipboard, 31 L locales, 48 presets, 141 prefs, the Arabic list at about 81% English, 103 gtx languages, and the competitor numbers (Gboard 13.9B / 4.48, SwiftKey 1.97B, Grammarly 60.7M and $29.99–139.99, Ganesha 3.89, Digital Apps club 3.71). The PRD contains no copied strings or assets and no unredacted keys (only the 8+4 form). There are no double-brace template placeholders or TODO markers; the one `TBD` is the working name, which OD-01 covers. Every OD-01..OD-19 has a default and an owner.

**Fixes made in this pass.**
- Source table: App H's title is now "Voice Keyboard and Translator", and App L's publisher is now "LogiApp Tech".
- §1.1: the install range is now about 50K to 12M (the targets have 56–59K). The password-field claim now says "no check on that path, runtime egress unproven (CMP Q1)" instead of stating that password keystrokes are sent. The interstitial wording is now "13 / 10 placements on one unit, cooldown off by default".
- NG-6: "71 theme presets" is now "71 subtype presets". D-12 notes that the test-device ID is the SDK's emulator constant. D-14 and FR-35 now say the over-40-character event names are an H-only finding. Also fixed the "an SEND" typo and the L#4 citation.
- FR-10 AC1 now matches TRD §4.2.3: no fixed watchdog, a 250 ms restart capped at 3, a BUSY recreate at 400 ms, and a 60 s session cap.
- FR-11 AC5: Pashto is cloud-only (Pro) because ML Kit has no Pashto model.
- FR-20 and §6.1 H#6: ML Kit OCR v2 has no Arabic-script model, so those sources are deferred per TRD OD-10. (Superseded by OD-21, which moves FR-20 out of this app.)
- FR-16 now uses `/v1/polish`.
- FR-28: Billing is now "8.3.0 or later". The playbook pins two versions (8.3.0 in the catalog, 9.1.0 in the module), and the TRD pins 8.3.0.
- The Pro AI quota is now 150 per day and 3,000 per month in FR-15 and §10.2, matching TRD OD-16.
- OD-08 now cross-references TRD OD-07, which applies the same per-action cheapest-passing-model rule, and has Finance as co-owner.
- §5.4 and G-6: the 250 ms IME show target is labelled as the warm figure; the cold budget is ≤ 600 ms p90.
- §13 now cites the TRD estimate: 60 / 88.5 dev-days, including the scope added in TRD §8.1. Note that the PRD §13.1/13.2 milestone labels (about M+3 / M+4.5) assume the smaller CMP estimate.

**Still open (not product-blocking).** CMP Q1 (runtime egress from password fields) is unproven for both apps; the PRD treats it as a risk, not a fact. TRD OD IDs are numbered on their own, so PRD OD-nn and TRD OD-nn are different decisions.

## Review log (2026-10-18): product decisions on REVIEW_NOTES RN-01..RN-10

- **RN-01:** no scope cuts. §13 now plans MVP at about M+5, v1 at about M+6.5 and v2 at about M+9. Stage weeks are rescaled.
- **RN-02:** cheapest models only. OD-08 records that Opus 5.5 is the eval reference only, and that a Sonnet 5.5 fallback means a Pro cap of about 400 per month for that action. The cost ceiling per user still needs Finance sign-off. Note on speech: Wispr Flow has no public developer API (access by approval only, not taking new partners), and OpenAI's hosted transcription is paid (about $0.003–0.006 per minute). Only the open-source Whisper model is free, run on-device (TRD OD-04). Whisper does speech-to-text only; polish still needs an LLM.
- **RN-03:** auto-polish stays in MVP as a Pro toggle, off by default, with its own consent, counted against the quota and never in sensitive fields (FR-16 AC3, AC5–AC6; §10.2; OD-04).
- **RN-04:** FR-10 AC5 and §8 make O5 the main place to ask for the microphone; the keyboard opens a permission activity and does not auto-resume.
- **RN-05:** FR-02 AC4 uses a curated banking and wallet list per launch market, plus a "Make this app private" smartbar action.
- **RN-06:** FR-36 AC1 uses an indeterminate indicator, the total size of missing models, and "Stop" instead of "Cancel".
- **RN-07:** §10.6 follows the TRD §11.6 Data safety draft. Audio is declared only if cloud speech recognition ships.
- **RN-08:** FR-34 adds the web deletion page; FR-15 AC9 and FR-16 AC7 add "Report" with opt-in text.
- **RN-09:** §10.6 defines four consent classes; FR-34 lets users withdraw them.
- **RN-10:** FR-27 AC1 keeps the "no ads in your keyboard" claim off the store listing until the TRD §11.8 CI test passes.

**Follow-up decisions (2026-10-18).**
- **Voice:** on-device Whisper is the MVP fallback, with the system recognizer kept as primary because Whisper on Android cannot stream live text. Updated FR-10 (description, AC3, AC4, AC7–AC9), FR-36, §10.6, PC-3, R-02, OD-16 and §13.3. No cloud speech recognition, so Audio stays undeclared.
- **AI:** Gemini Nano runs first where supported, and the cheapest cloud model is the fallback, after a spike (new OD-20, new R-14). Updated FR-15 (engine order, AC8, AC10, AC11), FR-16, §10.6 consent, OD-08 and R-06.
- **Timeline:** the two additions are estimated at 7–9 raw dev-days, giving about M+5.5. The TRD needs a matching estimate.

## Review log (2026-11-14): OD-21

- **OD-21:** conversation mode (FR-19) and camera translation (FR-20) move to a separate future app.
  They overload this app and dilute its keyboard focus.
- FR-19 and FR-20 keep their text and are marked as moved out. J9 and J10 have no FR in this app.
- Updated persona P3, NG-5, the Translations/day sources, §6.1 H#5 and H#6, §6.2 L#10 and L#13, FR-12, FR-32, S-04,
  S-13 to S-15, §10.2, the event plan, §13, R-03, §16 and the traceability rows.
- TRD OD-10 (Arabic-script OCR) no longer applies to this app.
