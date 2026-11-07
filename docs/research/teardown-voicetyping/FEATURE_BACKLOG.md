# Voice Keyboard - Voice Typing — Feature Backlog

**Source:** `features.json` (18 features: 17 observed in the target plus 1 greenfield; 11 non-features. 3 features and 4 non-features were added by the 2026-10-17 class sweep, see the addendum below) and the Wave 1 `report/*.md`.
**Ranking formula:** `(userImpact × 2 + monetization) / difficulty`, descending. All inputs are 1–5 and were assigned by the integrator. Difficulty assumes the clone starts from **upstream FlorisBoard (Apache-2.0)**, so keyboard plumbing is not counted from scratch.
**Mode:** static-only. Scores rest on `[dec]`/`[inf]` evidence; no live engagement or revenue data was used.

> **This ratio rewards cheapness and ignores dependencies.** Onboarding ranks first only because it is trivially cheap, and it is worthless until the keyboard itself exists (rank 5). Use the **Build order** column for scheduling; `BUILD_PLAN.md` follows it.

## Ranked backlog

| Rank | Score | Feature | Type | D | I | M | Build order | Why this score |
|---:|---:|---|---|:-:|:-:|:-:|:-:|---|
| 1 | 10.00 | Two-step enable/select onboarding + app language | growth | 1 | 4 | 2 | 2 | A day of work that decides activation. A keyboard nobody enables earns nothing. The target has no funnel analytics, so we measure what they cannot. |
| 2 | 6.50 | Voice typing key (IME) | core | 2 | 5 | 3 | 4 | The promise in the app's name. The platform recognizer is free. The target's version is single-shot with no partial results, so a better one is cheap. |
| 3 | 6.00 | Text translator screen | core | 2 | 4 | 4 | 6 | Highest ad-carrying screen in the target (Translate-button interstitial). Also builds the translation engine that ranks 9, 10 and 13 depend on. Tie-break below. |
| 4 | 6.00 | Settings hub + rate/share/feedback + push | growth | 1 | 2 | 2 | 3 | Cheap and needed for store review (privacy policy link, working settings gear). Tie-break below. |
| 5 | 4.33 | Core keyboard (FlorisBoard base, languages, clipboard, emoji) | core | 3 | 5 | 3 | **1** | The product. D3 only because upstream FlorisBoard does the heavy lifting. Rebranding, stripping, subtype and backup rules, and password-field discipline are the work. **Build first regardless.** Tie-break below. |
| 6 | 4.33 | AI rewrite / tone / grammar (greenfield) | opportunity | 3 | 4 | 5 | 14 | The target has no AI at all. This is the clearest category differentiator and the natural Pro upsell. It needs our backend and billing first. Tie-break below. |
| 7 | 4.00 | Dictionary | supporting | 2 | 3 | 2 | 12 | Small, standalone and ad-carrying. An offline DB fixes the target's offline dead end. Tie-break below. |
| 8 | 4.00 | Sticker packs | supporting | 2 | 3 | 2 | 13 | Same score as the dictionary, but it needs our own licensed art and a CDN catalog. Tie-break below. |
| 9 | 3.67 | Translate-while-typing bar (IME) | core | 3 | 4 | 3 | 7 | The fork's signature feature. Rebuilt on-device it becomes a privacy selling point instead of a liability. Tie-break below. |
| 10 | 3.67 | Camera / gallery translator | core | 3 | 4 | 3 | 11 | A category staple. ML Kit OCR v2 replaces deprecated GMS Vision. Tie-break below. |
| 11 | 3.50 | App-side ads + UMP + Remote Config | monetization | 2 | 1 | 5 | 8 | Invisible to users, but it is the target's entire revenue. Built from the playbook `:core:ads` with real frequency caps. Tie-break below. |
| 12 | 3.50 | Guess-word mini game | engagement | 2 | 2 | 3 | 15 | Cheap ad surface and retention hook. Not core to a keyboard. Tie-break below. |
| 13 | 3.33 | Two-sided voice conversation | core | 3 | 4 | 2 | 9 | Strong demo feature, monetizes weakly. Reuses the STT, translation and TTS already built. |
| 14 | 3.00 | Keyboard themes gallery + editor + key sounds | core | 4 | 4 | 4 | 10 | Drives store screenshots and premium theme packs. D4 because the editor and live preview are a lot of UI. Tie-break below. |
| 15 | 3.00 | Word suggestions + autocorrect | core | 3 | 4 | 1 | 5 | The quality gap users feel on every keystroke. It does not monetize directly, so the ratio undervalues it. Tie-break below. |

## Ties, explained

- **Ranks 3 and 4 (6.00).** Text translator is D2/I4/M4 (numerator 12, divided by 2). Settings is D1/I2/M2 (numerator 6, divided by 1). The translator ranks first because its numerator is twice as large: the same score comes from doing more, not from being cheaper. It also builds the shared translation engine.
- **Ranks 5 and 6 (4.33).** Core keyboard is D3/I5/M3 and AI rewrite is D3/I4/M5. The numerators are both 13. The core keyboard wins because impact (the ×2 term) is higher, and because every IME feature, AI rewrite included, depends on it.
- **Ranks 7 and 8 (4.00).** Dictionary and stickers are identical on every input (D2/I3/M2). The dictionary ranks first because it can ship with open data and no ongoing content operations. Stickers need commissioned or licensed art and a hosted catalog before they have any value.
- **Ranks 9 and 10 (3.67).** Live translate and camera translator are identical (D3/I4/M3). Live translate ranks first because it is the target's signature in-keyboard feature, and because doing it on-device is a privacy differentiator we can market. The camera translator is category parity.
- **Ranks 11 and 12 (3.50).** Ads stack is D2/I1/M5 and guess-word is D2/I2/M3. The numerators are both 7. The ads stack ranks first because it is the revenue foundation that the guess-word game itself needs (its value is mostly the ads it carries).
- **Ranks 14 and 15 (3.00).** Themes is D4/I4/M4 (numerator 12, divided by 4). Suggestions is D3/I4/M1 (numerator 9, divided by 3). Themes ranks first because it has the larger numerator and is a premium-entitlement surface. **Build order puts suggestions much earlier (5)**: a keyboard with bad suggestions is uninstalled long before anyone opens the theme editor.

## What the formula gets wrong here

1. **Suggestions/autocorrect (rank 15) is under-ranked.** M1 punishes it, but retention for a keyboard depends on typing quality. The plan builds it in Sprint 2.
2. **Core keyboard (rank 5) is under-ranked** for the usual reason: it is the dependency for ten other rows.
3. **AI rewrite (rank 6) is over-ranked for v1** because the formula does not count the backend, App Check, quota and billing it needs. It is scheduled after billing lands.
4. **The in-IME ad is not on this list on purpose.** It would score M5, but it is a Play-policy liability (ads inside a system keyboard, selectively hidden from Google apps). See `features.json` → `nonFeatures`.

## Non-features (do not build)

| Item | Why not |
|---|---|
| In-IME native ad with Google/launcher deny-list | Reviewer-evasion pattern; Play Ads/IME policy risk at account level |
| Per-keystroke upload to `translate_a/single` | ToS violation and keystroke egress; replace with ML Kit on-device |
| Logging user text to logcat | Release-build privacy leak |
| `allowBackup=true` with no rules | Clipboard and learned words to cloud backup. Correction (class sweep): clipboard exposure applies only if the user turns on clipboard history (off by default), and only to the text DB/metadata; media lives in `noBackupFilesDir` `[dec]`. |
| Dead `is_user_premium` flag, unread RC natives | Template debris. (The "AdMob test-device ID" `B3EEABB8…9ECB` is the SDK's emulator constant, not app debris; corrected by the class sweep.) |
| `Process.killProcess` in `MainActivity.onDestroy` | Kills the keyboard, which shares the process |
| Forced update with an exit-only decline dialog | Hostile UX; use FLEXIBLE updates, with IMMEDIATE only below a Remote Config minimum version |
| `open_screen` routing to unregistered routes (`language` → `l65` crash) | Use typed, allow-listed deep links |
| Live-translate stale-length deletion, no password/incognito check | Data loss and privacy risk |

## Addendum: exhaustive class sweep (2026-10-17)

`CLASS_COVERAGE.md` read all 3,459 inventory first-party classes and added three features. Scores use the same formula. **Merged rank** is the feature's position among all 18 rows. Existing rows that tie keep their order; new rows go after existing rows with the same score.

| Merged rank | Score | Feature | Type | D | I | M | Build order | Why this score |
|---:|---:|---|---|:-:|:-:|:-:|:-:|---|
| 5 | 6.00 | Play in-app update (`in-app-update`) | growth | 1 | 2 | 2 | with 3 (Settings) | About an hour with Play Core. The target forces it with an exit-only dialog; we use FLEXIBLE, with IMMEDIATE only below a Remote Config minimum version. |
| 6 | 6.00 | Keyboard → app deep routing (`open-screen-intent-routing`) | supporting | 1 | 2 | 2 | with 1 (core keyboard) | Every smartbar/keyboard shortcut into the app depends on it. Typed, validated routes fix the target's `language` crash. |
| 18 | 2.50 | Urdu shift layout (`urdu-shift-layout`) | supporting | 2 | 2 | 1 | with 1 (core keyboard) | Layout JSON work for the target's ur/ar markets. Low ratio, but cheap parity if those markets matter. |

Existing ranks 5-15 move down two places (core keyboard becomes 7 … suggestions becomes 17). Build order is unchanged: the new items ride along with the build steps shown.

**Score-relevant notes from the sweep (scores unchanged):**
- **Suggestions/autocorrect:** the target has an opt-in "Auto correction" that commits the top prefix match on space (off by default), and suggestions are off by default too. The quality gap is still prefix-only matching, so I4/M1 holds.
- **Navigation:** the target uses Navigation3 (`NavDisplay`, 15 registered routes), not Navigation-Compose. The clone can use either; the routing feature above covers the deep-link entry.
- **Stickers:** `isPremium` is never read, so every pack is free. Premium packs are pure upside for us.
- **Live translate:** the target's version has a text-deleting bug and no password check. That strengthens the on-device rebuild case for rank 11 (was 9).
