# Target apps: parsed Play listing facts

Fetched 2026-10-17 (en/US). Sources: `playstore/<id>.html` (listing), `playstore/datasafety/<id>.html` (data safety page).
Raw parsed JSON is in `playstore/targets_parsed.json`. Parser is `scripts/parse_play.py` (reads the `AF_initDataCallback` app payload).

| Field | Voice Keyboard and Translator | Voice Keyboard - Voice Typing |
|---|---|---|
| Package | com.heri.language.keyboard.voice.typing | com.logiapps.language.keyboard.voice.typing.translator |
| Developer | Heri Corner (Rameez Amanat, Mian Channun, Pakistan) | LogiApp Tech (Samar Sajjad, Rawalpindi, Pakistan) |
| Support email / site | arameezsc@gmail.com / blogspot site | comboduo682@gmail.com / blogspot site |
| Category | Productivity | Productivity |
| Installs | 50K+ (56,550 exact) | 50K+ (59,497 exact) |
| Rating / ratings count | Not shown (too few ratings) | Not shown (too few ratings) |
| Released / last updated | Sep 19, 2026 / Sep 19, 2026 (no update since launch) | Sep 19, 2026 / Sep 19, 2026 (no update since launch) |
| Content rating | Everyone | Everyone |
| Ads | Contains ads | Contains ads |
| IAP | Yes, $5.49 - $6.49 per item | None listed |
| Price | Free | Free |
| Screenshots | 8 | 7 |
| What's new | None (first release) | None (first release) |

## Short descriptions
- Heri: "Voice Keyboard. Type with voice, translate text, custom layouts, and stickers."
- LogiApp: "Voice typing made simple with voice-to-text, translation, stickers, and themes."

## Description summary (paraphrased)
**Heri Corner, Voice Keyboard and Translator** (about 1.5K chars)
- Keyboard customisation: key shape, key height, colours, backgrounds. Ready-made solid, gradient and image themes.
- Voice typing in "different languages". It names English, Spanish, French, Portuguese, Arabic, Urdu and Indonesian.
- Translation tools: text translation, camera (image) translation, and a voice conversation translator.
- English dictionary and a phrase book.
- Keyboard stickers.
- No AI, grammar, offline, clipboard or font features are claimed.

**LogiApp Tech, Voice Keyboard - Voice Typing** (about 0.8K chars)
- Voice typing for messages.
- Translation of typed text, speech and image text, plus a conversation mode.
- The same customisation set: key shapes, colours, backgrounds, and solid/gradient/image themes.
- Keyboard stickers and an English dictionary.
- Extra: a "Word Challenge" mini-game where you build words from given letters.
- No AI, grammar, offline or font features are claimed.

## Data safety (developer-declared)
| | Heri | LogiApp |
|---|---|---|
| Shared with third parties | Device or other IDs (analytics, developer comms, advertising); Voice or sound recordings (app functionality) | Voice or sound recordings (app functionality); Crash logs (analytics); App interactions (analytics, advertising); Device or other IDs (analytics, advertising) |
| Collected | "No data collected" | "No data collected" |
| Security | Encrypted in transit; no deletion request option | Encrypted in transit; no deletion request option |

The "shares voice recordings" entry most likely means audio is sent to a cloud speech or translate API, such as Google speech services [inf].

## Cross-target observations
- [obs] Both apps were released on the same day (Sep 19, 2026). They share a near-identical feature list and the same description structure, both developers are in Pakistan, and both portfolios have the same sibling apps (Arabic, Amharic and Pashto keyboards). Heri also has Notepad, App Lock and PDF reader apps. LogiApp also has Live Earth Map, IPTV and Wallpaper apps.
- [inf] The two apps are probably one white-label codebase published under two developer accounts. Expect the same SDK stack in both APKs (AdMob, Firebase, a translate API, an image-OCR API).
- [obs] Each reached about 57-59K installs about 4 weeks after launch, with no rating shown yet. [inf] That points to paid UA or cross-promotion from the sibling keyboard apps, not organic growth.
- [obs] "Similar apps" links on the listings: Answer.AI (GPT keyboard), Chinese/Urdu/Tamil keyboards, Kitkat keyboard, ivoca conversation translator, Bengali translator, Collins dictionary.
- [obs] Neither app claims AI rewrite, grammar, reply suggestions, offline mode, clipboard or fonts.
