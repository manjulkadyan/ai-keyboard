# Voice Keyboard and Translator — Feature Backlog

**Source:** `features.json` (21 features: 19 present in the winner, including 6 added by the exhaustive class sweep of 2026-10-17 (`CLASS_COVERAGE.md`), and 2 greenfield opportunities) + `report/*.md` Wave 1 + `REVERSE_ENGINEERING_REPORT.md` §12.
**Ranking formula:** `(userImpact × 2 + monetization) / difficulty`, descending. All inputs are 1–5 and assigned by the integrator.
**Mode:** STATIC-ONLY. Every score rests on `[dec]`/`[inf]` evidence; no live engagement, retention or revenue data informed these numbers.

> **The formula is cheapness-biased and dependency-blind.** It ranks the core keyboard 20th of 21, yet nothing inside the keyboard (voice typing, translation bar, themes, stickers, suggestions, AI rewrite) can ship without it. Its difficulty-4 score divides a 5-impact feature down. The **Build order** column is the dependency-corrected sequence that `BUILD_PLAN.md` follows. Use the score to argue value for effort; use the build order to schedule.

## Ranked backlog

| Rank | Score | Feature (slug) | Type | D | I | M | Build order | Why this score |
|---:|---:|---|---|:-:|:-:|:-:|:-:|---|
| 1 | 10.00 | Keyboard-enable onboarding, settings, growth hooks (`onboarding-settings`) | growth | 1 | 4 | 2 | 2 | Every install that does not enable the keyboard is lost; the 2-step settings-highlight + picker flow with 500 ms polling is a few days of work. Monetizes indirectly by carrying the first banner/native. |
| 2 | 8.00 | PROCESS_TEXT + share-to-translate (`process-text-share-translate`) | opportunity | 1 | 3 | 2 | 6 | The winner has zero inbound integrations. One activity reuses the translation core and puts the app in every app's text-selection menu. Score is inflated by the translation core it depends on. |
| 3 | 6.50 | Voice typing in the keyboard (`voice-typing-ime`) | core | 2 | 5 | 3 | 3 | The app's name promises it. Platform `SpeechRecognizer` + a permission trampoline is small; partials and on-device mode are cheap upgrades the winner skipped. |
| 4 | 6.00 | Dictionary (`dictionary`) | supporting | 1 | 2 | 2 | 10 | One Retrofit call and one screen. Low impact, but a natural interstitial/native slot and nearly free. |
| 5 | 6.00 | FCM topic push notifications (`push-notifications`) | growth | 1 | 2 | 2 | with 2 | NEW (class sweep). One topic subscription plus the API 33+ permission prompt; the winner shows only console notifications and logs the token. Cheap re-engagement. |
| 6 | 5.50 | Text translator with voice, TTS, history (`text-translator`) | core | 2 | 4 | 3 | 4 | Category table stakes (and, unlike the winner, save text and camera results to history) and the shared translation core every other translate surface reuses. |
| 7 | 5.00 | Play Core in-app update (`in-app-update`) | growth | 1 | 2 | 1 | with 2 | NEW (class sweep). A few lines of Play Core; keeps users on builds with current ad and consent code. |
| 8 | 5.00 | Color picker (`color-picker`) | supporting | 1 | 2 | 1 | with 7 | NEW (class sweep). Bottom sheet with wheel, bitmap sampler and recents; only useful once the theme customizer exists. |
| 9 | 4.67 | Translate-as-you-type bar (`keyboard-translation-bar`) | core | 3 | 5 | 4 | 5 | The signature feature and the clone's biggest privacy win (on-device). Difficulty 3 because of debounce, `setComposingText` semantics and a password-field guard on every entry path (the winner has none). |
| 10 | 4.50 | Emoji panel, recents, `:` suggestions (`emoji-panel`) | supporting | 2 | 4 | 1 | with 1 | NEW (class sweep). Comes with the Floris fork; the work is keeping recents hidden on the lock screen and polishing the palette. |
| 11 | 4.33 | AI rewrite in the smartbar (`ai-rewrite-smartbar`) | opportunity | 3 | 4 | 5 | 13 | The winner has no AI at all. Highest monetization in the set, but needs our own proxy, quotas and the subscription, hence scheduled late. |
| 12 | 4.00 | Themes, customizer, key sounds (`keyboard-themes-customizer`) | core | 3 | 4 | 4 | 7 | Tie-break below. Themes are the main reason keyboard users open the companion app and the most natural premium content. |
| 13 | 4.00 | Smartbar quick actions (`smartbar-quick-actions`) | supporting | 2 | 3 | 2 | with 1 | NEW (class sweep). Custom row of up to 3 actions plus fixed buttons; also the slot for AI rewrite. Do not ship placeholder actions like the winner's Autocorrect toast. |
| 14 | 4.00 | Offline phrasebook (`offline-phrasebook`) | supporting | 2 | 3 | 2 | 9 | Tie-break below. Pure local SQLite; the work is writing and pre-translating our own phrase set. |
| 15 | 4.00 | Sticker packs (`sticker-packs`) | supporting | 2 | 3 | 2 | 11 | Tie-break below. Needs our own CDN manifest and content before it is worth anything. |
| 16 | 3.67 | Conversation mode (`conversation-mode`) | core | 3 | 4 | 3 | 8 | Tie-break below. Reuses STT + translation + TTS already built. |
| 17 | 3.67 | Camera translation (`camera-translation`) | core | 3 | 4 | 3 | 12 | Tie-break below. Needs a new stack (CameraX + ML Kit Text Recognition v2) (the winner's OCR chain was traced in the class sweep, open item 2 closed, but we do not reuse it). |
| 18 | 3.50 | Clipboard history + smart clip suggestions (`clipboard-suggestions`) | supporting | 2 | 3 | 1 | with 1 | NEW (class sweep). Upstream Floris; the work is backup exclusion and skipping password-field clips. |
| 19 | 3.50 | Ad stack (`ad-monetization`) | monetization | 2 | 1 | 5 | 1 (core:ads skeleton), 14 (placements) | Invisible to users, decisive for revenue. The `:core:ads` + UMP skeleton goes in Sprint 0; per-screen placements are wired as features land. |
| 20 | 3.25 | **Core keyboard (IME)** (`ime-core-keyboard`) | core | 4 | 5 | 3 | **1** | The product. Ranked 20th purely because difficulty 4 divides it down. **Build first regardless.** A ratio-obedient reading of this row would be a serious mistake. |
| 21 | 2.25 | Suggestions + autocorrect (`suggestions-autocorrect`) | core | 4 | 4 | 1 | 15 | High user value (the winner's suggestions are mixed-language and unranked), but real autocorrect and prediction is research-grade work that monetizes directly not at all. The winner only has an opt-in top-candidate auto-commit and a placeholder Autocorrect action. Ship upstream Floris behaviour with per-locale dictionaries first; improve later. |

## Tie-breaks

Ties are broken by, in order: (1) higher user impact, (2) higher monetization, (3) fewer unbuilt dependencies at that point in the build, (4) lower open-item risk.

- **Ranks 4–5 (score 6.00).** Dictionary and push notifications are identical on D/I/M. Dictionary wins on rule 3 (no dependencies; push needs the onboarding flow and a Firebase project wired first).
- **Ranks 7–8 (score 5.00).** In-app update and color picker are identical on D/I/M. In-app update wins on rule 3: it only needs the app shell, while the color picker is useless before the theme customizer exists.
- **Ranks 12–15 (score 4.00).** Themes (I4) beat smartbar actions, phrasebook and stickers (all I3/M2) on rule 1. Smartbar actions win next on rule 3 (they ship with the IME fork, the first thing built). Phrasebook beats stickers on rule 3 because it only needs the translation core and a local asset, while stickers need a CDN, a signed manifest and original content.
- **Ranks 16–17 (score 3.67).** Conversation and camera are identical on D/I/M. Conversation wins on rule 3 (it reuses STT, translation and TTS that exist by Sprint 2) and rule 4 (its main unknown, auto-speak, is our own UX choice; camera needs a whole new CameraX/ML Kit stack).
- **Ranks 18–19 (score 3.50).** Clipboard suggestions (I3) beat the ad stack (I1) on rule 1. The ad stack's `:core:ads` skeleton still goes in Sprint 0 per the Build order column.

## Module mapping (per `ANDROID_FAST_CLONE_PLAYBOOK.md` structure)

| Feature | Module(s) |
|---|---|
| ime-core-keyboard | `:ime` (forked FlorisBoard as an Android library, package renamed, NOTICE kept), `:core:datastore` |
| voice-typing-ime | `:core:speech` (service-scoped recognizer holder), `:ime` |
| keyboard-translation-bar | `:core:translation` (ML Kit + optional proxy client behind one interface), `:ime` |
| text-translator | `:feature:translator`, `:core:translation`, `:core:speech`, `:core:database` (history) |
| conversation-mode | `:feature:conversation` |
| camera-translation | `:feature:camera` (CameraX + ML Kit Text Recognition v2) |
| offline-phrasebook | `:feature:phrasebook` (Room prepopulated from our own asset) |
| dictionary | `:feature:dictionary`, `:core:network` |
| keyboard-themes-customizer | `:feature:themes`, `:core:database`, `:ime` theme bridge |
| sticker-packs | `:feature:stickers`, `:core:network`, `:ime` sticker panel |
| suggestions-autocorrect | `:ime` NLP provider + per-locale dictionary assets |
| onboarding-settings | `:feature:onboarding`, `:feature:settings`, `:core:analytics` |
| ad-monetization | `:core:ads`, `:core:billing`, `:core:remoteconfig` |
| process-text-share-translate | `:feature:translator` (extra entry activities) |
| ai-rewrite-smartbar | `:core:ai` (proxy client), `:ime` smartbar action, backend `/v1/rewrite` |
| emoji-panel | `:ime` (upstream Floris emoji palette) |
| clipboard-suggestions | `:ime` clipboard + `:core:database` (excluded from backup) |
| smartbar-quick-actions | `:ime` smartbar |
| color-picker | `:feature:themes` (shared `:core:ui` component) |
| push-notifications | `:core:notifications` (FCM), `:feature:settings` permission prompt |
| in-app-update | `:app` (Play Core app-update), `:core:remoteconfig` (min version) |

## `[partial]` features (manual triage)

| Feature | Gap | Open item |
|---|---|---|
| keyboard-translation-bar | Closed 2026-10-17: body of `androidx/lifecycle/o0` case 10 recovered; no debounce, cancel-previous + stale guard. Clone choice does not depend on it (we debounce at 400–500 ms regardless). | 1 |
| camera-translation | Closed 2026-10-17 (exhaustive class sweep): `mb/l:332` → `cb/g1` → `a2/b` c6 → recovered `b0/f` (overlay crop + Mobile Vision) → `eb/p` → gtx. Clone uses CameraX + ML Kit regardless. | 2 |

## Explicitly not in the backlog (do not copy)

- In-keyboard native ad and the 86-package host suppression list (`FlorisImeService.java:683`).
- Logging of user text, host package names or FCM tokens.
- The keyless gtx endpoint as a production engine.
- The winner's phrasebook DB, background/key-skin art, key-sound MP3s and string tables.
- `Process.killProcess` on app exit (it kills the IME too), and initialising AdMob inside the IME process.
- Logging of RC values, sticker lists, per-key vibrator events or `suggestionCheck___` debug output in release builds.
- Unguarded password-field paths into any translation buffer (the winner routes keys, backspace, suggestion taps and paste without a check).
- Placeholder quick actions (the winner's Autocorrect toast) and storing theme backgrounds as palette indices.
