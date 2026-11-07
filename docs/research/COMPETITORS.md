# Competitor research: voice typing and translator keyboards (Android)

**Date:** 2026-10-17
**Targets:** `com.heri.language.keyboard.voice.typing` (Heri Corner) and `com.logiapps.language.keyboard.voice.typing.translator` (LogiApp Tech). Full facts are in `playstore/targets.md`.

**Method**
- Fetched the Play listings with curl (`hl=en&gl=US`) and parsed the embedded `AF_initDataCallback` app payload with `scripts/parse_play.py`. That gives exact install counts, rating, ratings count, release and update dates, the ads flag, the IAP range and the screenshot count.
- Fetched the data safety pages (`playstore/datasafety/`, summary in `summary.txt`).
- Built the candidate list from the targets' "Similar apps" and "More by developer" links, plus 7 Play searches: voice keyboard, voice typing keyboard, translator keyboard, ai keyboard, ai keyboard chatgpt, speech to text keyboard, multi language keyboard (`playstore/competitors/search/`).
- Fetched 24 candidates (`playstore/competitors/*.html`, parsed into `parsed.json`) and picked 12 for the deep comparison.
- Tags: **[obs]** means seen on the listing or data safety page. **[inf]** means inference or general market knowledge that the listing does not state.
- Feature matrix values: ✓ claimed on the listing; ✓* known from product knowledge but not on the listing [inf]; ✗ not claimed; ? unclear.

---

## 1. Market table

Ratings = number of star ratings. Reviews = written reviews field. Dates are first release and last update.

| # | App (package) | Developer | Installs (exact) | Rating | Ratings / Reviews | Released / Updated | Monetization [obs] |
|---|---|---|---|---|---|---|---|
| T1 | Voice Keyboard and Translator (com.heri.language.keyboard.voice.typing) | Heri Corner (PK) | 50K+ (56.6K) | n/a | n/a | 2026-09-19 / 2026-09-19 | Ads + IAP $5.49-6.49 |
| T2 | Voice Keyboard - Voice Typing (com.logiapps.language.keyboard.voice.typing.translator) | LogiApp Tech (PK) | 50K+ (59.5K) | n/a | n/a | 2026-09-19 / 2026-09-19 | Ads only |
| 1 | Gboard (com.google.android.inputmethod.latin) | Google | 10B+ (13.9B) | 4.48 | 18.9M / 442K | 2013 / 2026-09-30 | Free, no ads, no IAP |
| 2 | Microsoft SwiftKey AI Keyboard (com.touchtype.swiftkey) | Microsoft | 1B+ (1.97B) | 4.29 | 4.6M / 226K | 2010 / 2026-10-13 | Free, "Contains ads" flag, no IAP |
| 3 | Grammarly-AI Writing Assistant (com.grammarly.android.keyboard) | Grammarly | 50M+ (60.7M) | 4.28 | 264K / 26K | 2017 / 2026-10-14 | Freemium sub $29.99-139.99, no ads |
| 4 | CleverType AI Keyboard (com.clevertype.ai.keyboard) | CleverType | 1M+ (4.0M) | 4.34 | 42.7K / 390 | 2023-04 / 2026-10-17 | Freemium sub $0.49-134.99, no ads |
| 5 | AI Keyboard: Writer, Grammar (starnest.aitype.aikeyboard.chatbot.chatgpt) | Starnest JSC (VN) | 1M+ (3.2M) | 4.34 | 31.3K / 600 | 2023-11 / 2026-10-02 | Ads + IAP $3.49-59.99 |
| 6 | Wispr Flow: AI Voice-to-Text (com.wispr.flowapp) | Wispr AI | 1M+ (1.68M) | 4.70 | 11.8K / 1.2K | 2026-02 / 2026-10-16 | Sub $15.00-143.99, no ads |
| 7 | Typeless: AI Voice Keyboard (com.typeless.mobile) | Typeless | 500K+ (777K) | 4.16 | 3.0K / 241 | 2026-01 / 2026-10-10 | Sub $29.99-144.99, no ads |
| 8 | Translator Keyboard All Chats (com.chat.translator.keyboard.translate.language) | Wallet Passes Alliance | 5M+ (5.1M) | 4.71 | 36.9K / 131 | 2024-05 / 2026-10-09 | Ads + IAP $0.99-99.99 |
| 9 | Keyboard Translator for Chat (com.translate.chattranslator.keyboard.android2022) | Goldlab Pro | 10M+ (12.2M) | 4.21 | 22.2K / 281 | 2022-05 / 2026-09-24 | Ads + IAP $3.75-29.99 |
| 10 | English Voice Typing Keyboard (com.voice.chat.typing.keyboard) | Ganesha Gallery (IN) | 5M+ (8.7M) | 3.89 | 15.6K / 53 | 2023-07 / 2026-07-02 | Ads + IAP $0.99-54.99 |
| 11 | Voice Typing Keyboard (com.easyvoicetyping.keyboard.inputmethod) | Digital Apps club | 1M+ (1.41M) | 3.71 | 2.2K / 59 | 2024-11 / 2026-09-26 | Ads + IAP $7.00-45.00 |
| 12 | Voice Typing Keyboard (com.tech.voice.keyboard.typing.inputmethod) | Nexovix Studio | 50K+ (66K) | 4.5 | 89 / 2 | 2026-05 / 2026-07-28 | Ads + IAP $8.99 |

**Also seen but not deep-dived [obs]:**
- Voice Typing: AI Keyboard (`com.voice.typing.keyboard`, Daimond Devs, 7.7K installs): a tiny clone that already advertises grammar fix, tone change and translate.
- Voice Typing: Speech to Text (`com.all.languages.voicetyping.keyboard`, 100K+, 4.32): the only one claiming **offline translation**.
- Dictate Keyboard (`net.devemperor.dictate`, 6K, 4.58): Whisper keyboard where you bring your own API key; the source is open on GitHub [inf].
- Voiskey AI Voice Typing (`com.creativeapp.voiceinput`, 34K, sub $29.99+): a Wispr-style clone.
- Bobble AI (82M+), ai.type (521K), Easy Translator Keyboard (71K), Fexo Translator Keyboard (16K), Multi Language Keyboard (14K), Softkeys Voice Typing (28K), AI Voice Typing Keyboard (technosub, 70 installs).

**Key size signals**
- [obs] Pure "voice + translator" clones go from 50K up to about 9M installs. Their ratings are the lowest in the set (3.7-3.9) for the older large ones (Ganesha, Digital Apps club).
- [obs] Translator-keyboard specialists (#8, #9) reach 5-12M installs with 4.2-4.7 ratings.
- [obs] The new AI-dictation apps (#6, #7) launched in 2026 and reached 0.8-1.7M installs at $15-30/mo-tier pricing. This is the fastest-growing sub-segment [inf].

---

## 2. Feature matrix

Column key: **Voice** = voice typing (languages). **TransType** = translate as you type. **AIwrite** = AI rewrite, grammar or tone. **Reply** = AI reply suggestions. **AIchat** = chat with AI / assistant. **OCR** = camera or image translation. **Themes** = themes or custom backgrounds. **Emoji/Stk/GIF** = emoji, stickers, GIFs. **Clip** = clipboard. **Dict** = dictionary. **Convo** = conversation translator. **Offline** = offline support.

| App | Voice (langs) | TransType | AIwrite | Reply | AIchat | OCR | Themes | Emoji/Stk/GIF | Fonts | Clip | Dict | Convo | Offline | Model |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| T1 Heri | ✓ (7 named) | ✓ | ✗ | ✗ | ✗ | ✓ | ✓ photo bg | Stickers | ✗ | ? | ✓ + phrasebook | ✓ | ✗ | Ads+IAP |
| T2 LogiApp | ✓ (unspecified) | ✓ | ✗ | ✗ | ✗ | ✓ | ✓ photo bg | Stickers | ✗ | ? | ✓ + word game | ✓ | ✗ | Ads |
| Gboard | ✓ (hundreds) | ✓ Google Translate | ✓* proofread (some devices) | ✓* smart reply | ✗ | ✗ | ✓ | ✓✓✓ | ✗ | ✓* | ✗ | ✗ | ✓* offline voice | Free |
| SwiftKey | ✓* (700+ kbd langs) | ✓* MS Translator | ✓ rewrite/tone/compose | ? | ✓ Copilot | ✗ | ✓ photo bg, 100+ | ✓✓✓ + AI images | ✗ | ✓* | ✗ | ✗ | ? | Free (ads flag) |
| Grammarly | ✗ | ✗ | ✓ best-in-class grammar | ? | ? | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | Sub |
| CleverType | ✓ AI-cleaned | ✓ 40+ | ✓ | ✓ (accessibility) | ✓ ChatGPT, 30+ assistants | ✗ | ✓ AI themes | ✓ GIF + stickers (new) | ? | ? | ✗ | ✗ | ✗ | Sub, no ads |
| Starnest AI KB | ? | ✓ | ✓ GPT-4o | ✓ | ✓ chatbot | ✓ image/PDF | ✓ 100 | 2,000 emojis | ✓ 60 | ? | ✗ | ✗ | ✗ | Ads+IAP |
| Wispr Flow | ✓ 100+ AI-cleaned | ✗ | ✓ cleanup/format | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ personal | ✗ | ✗ | Sub |
| Typeless | ✓ 100+ AI-cleaned | ? | ✓ speak-to-edit, per-app tone | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ personal (auto-learn) | ✗ | ✗ | Sub |
| Translator KB All Chats | ✓ | ✓ | ✗ | ✗ | ✗ | ? | ✓ | ? | ✗ | ? | ✗ | ✓ + TTS | ✗ | Ads+IAP |
| KB Translator for Chat | ✓ speech translator | ✓ | ✗ | ✗ | ✗ | ? | ? | ? | ✗ | ? | word translation | ✓ | ✗ | Ads+IAP |
| English Voice Typing | ✓ English-first | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | Emoji | ✓ | ? | ✗ | ✗ | ✗ | Ads+IAP |
| Voice Typing KB (Digital Apps) | ✓ | ✓ | ✓ "improve text" | ✓ quick responses | ✓ AI assistant | ? | ✓ photo bg | Emoji | ? | ? | ✓ | ✓ live | ✗ | Ads+IAP |
| Voice Typing KB (Nexovix) | ✓ | ✓ | ✗ | ✗ | ✗ | ✓ + PDF translator | ✓ | ? | ✗ | ? | ✓ | ✓ talking | ✗ | Ads+IAP |

**Data safety highlights [obs]**
- Both targets, Nexovix, Digital Apps club and Translator KB All Chats declare "no data collected" but share Device IDs, and some also share audio, with third parties for ads and analytics. This is the standard AdMob-clone pattern [inf].
- Wispr Flow declares the widest collection and sharing: voice, browsing history, contacts and purchase history.
- Starnest declares neither collection nor sharing.
- Gboard, SwiftKey, CleverType and Typeless declare "no data shared". Grammarly declares sharing (diagnostics only).

---

## 3. Per-app notes

**T1 Heri Corner, Voice Keyboard and Translator**
- [obs] A utility bundle: voice typing, text, camera and conversation translation, dictionary, phrase book, stickers and a deep theme editor.
- [obs] Launched 2026-09-19 and already at 56.6K installs with no rating. [inf] Probably paid UA or cross-promo from the developer's Arabic, Amharic and Pashto keyboard apps.
- [obs] Has IAP ($5.49-6.49, likely ad removal or a lifetime unlock) and ads. Shares voice recordings with third parties.
- [inf] Targets South Asia and MENA. The voice languages named on the listing include Urdu and Arabic.

**T2 LogiApp Tech, Voice Keyboard - Voice Typing**
- [obs] Functionally the same as T1 (voice, text/voice/image/conversation translation, themes, stickers, dictionary) and released the same day.
- [obs] Differs only by the "Word Challenge" game and having no IAP (ads only).
- [inf] A sibling white-label build. One teardown probably covers both.

**Gboard**
- [obs] The default incumbent at 13.9B installs. Translate-as-you-type, multilingual autocorrect, handwriting, GIFs and stickers, and Pixel-only AI sticker creation.
- [inf] Offline voice typing and AI proofread are tied to the device (Pixel or Gemini). On non-Pixel devices AI rewriting is limited, so mid and low-end users have a gap.
- [inf] No conversation translator, OCR or dictionary inside the keyboard. Those live in the separate Translate and Lens apps.

**Microsoft SwiftKey**
- [obs] Copilot chat inside the keyboard, AI rewrite/tone/compose, AI images and memes, 100+ themes, photo backgrounds, and 5 simultaneous languages out of 700+.
- [obs] The "Contains ads" flag is set but there is no IAP. [inf] The AI features are free and gated behind a Microsoft account.

**Grammarly**
- [obs] Positioned as a writing assistant that works over any keyboard (floating widget), not as a replacement keyboard. GenAI rewrites for length, tone and confidence.
- [obs] The most expensive tier ($29.99-139.99). English-centric, no voice and no translation. It sets user expectations for grammar quality.

**CleverType AI Keyboard**
- [obs] The most complete AI keyboard: grammar, paraphrase/tone, ChatGPT, AI reply (reads the last message through the Accessibility service), 40+ language translation, 30+ custom assistants, and AI voice with filler removal.
- [obs] Shipping fast (updated today). The latest release added GIFs, stickers, floating and split keyboards. Subscription only, no ads.
- [inf] The closest model of an "AI super-keyboard". Its weakness is regional and low-resource languages, and conversation or OCR translation.

**Starnest AI Keyboard: Writer, Grammar**
- [obs] GPT-4o / 4o-mini backed: grammar, tone changer, AI reply, email writer, chatbot, image/PDF translation, 60 fonts, 100 themes, 2,000 emojis.
- [obs] Ads plus IAP while declaring no data collected. [inf] A Vietnamese studio playbook: an AI wrapper monetised through ads and a weekly sub.

**Wispr Flow**
- [obs] Premium AI dictation: filler and self-correction removal, auto formatting, a personal dictionary, 100+ languages, and accessibility positioning (RSI, ADHD, stuttering).
- [obs] Rated 4.70 and at 1.68M installs within 8 months at $15/mo-class pricing. It proves users will pay for voice *quality*.
- [obs] No translation, themes or emoji. It is an overlay or voice tool, not a full keyboard.

**Typeless**
- [obs] Same thesis as Wispr: AI-polished dictation, "speak to edit" (voice commands to rewrite), per-app tone, and auto-learned vocabulary (latest update).
- [obs] $29.99-144.99. Declares no data shared.

**Translator Keyboard All Chats**
- [obs] Translator-first keyboard: translate as you type in any chat, voice auto-type, TTS, conversation translator, themes. 40 screenshots, an aggressive ASO listing.
- [obs] 5.1M installs and 4.71 rating, the best rating among the ad-funded clones. IAP goes up to $99.99. [inf] Lifetime or weekly upsell.

**Keyboard Translator for Chat (Goldlab)**
- [obs] Keyboard translator plus a word translator, speech translator, Hangman game and a "Learn Language" module. 12.2M installs.
- [obs] Shows that gamification and learning add-ons (like T2's Word Challenge) are an established pattern.

**English Voice Typing Keyboard (Ganesha Gallery)**
- [obs] English-first voice typing with themes, fonts and emoji. 8.7M installs but 3.89 rating, last updated July 2026.
- [inf] The biggest pure voice-typing clone. Low rating plus stale updates make it vulnerable to a better-quality entrant.

**Voice Typing Keyboard (Digital Apps club)**
- [obs] The target formula plus an "AI assistant" (improve text, quick responses, predictions), live voice conversation translation, a dictionary, and a WhatsApp status saver.
- [obs] 1.41M installs but a 3.71 rating. The latest update is "Crashes and ANRs resolved". [inf] The quality problem is exploitable.
- [inf] The most direct evolution path for the targets (voice translator clone plus an AI layer).

**Voice Typing Keyboard (Nexovix Studio)**
- [obs] Released 2026-05. Adds a PDF translator, voice recorder, glide typing and number row to the target formula. 66K installs, 4.5 from 89 ratings.
- [inf] The same "utility pack" template as the targets, possibly the same outsourcing ecosystem.

---

## 4. Market gaps and differentiation opportunities

1. **AI-polished voice combined with translation in one step ("speak in Urdu or Hindi, send clean English or Arabic")** [inf]
   - Wispr and Typeless own the voice-quality segment but have no translation output and cost $15-30/mo.
   - The translator clones (#8-12, T1, T2) do raw ASR plus Google-style translation with no cleanup.
   - Nobody combines filler removal, tone and translated output at clone-tier pricing.
2. **Offline and privacy-first voice and translation** [obs/inf]
   - Only one small app (`com.all.languages.voicetyping.keyboard`) claims offline translation.
   - The targets and most clones declare that they share voice recordings and device IDs.
   - On-device ASR (Whisper-tiny or Vosk) plus ML Kit on-device translation, sold as "works offline, voice never leaves the phone", is unclaimed and fits low-connectivity markets.
3. **Regional and low-resource language depth** [inf]
   - The AI keyboards stop at about 40 languages for translation (CleverType) and are English-centric (Grammarly, Starnest).
   - The clone developers (including the targets' own portfolios of Urdu, Pashto, Amharic and Arabic keyboards) prove there is demand but offer no AI.
   - An AI keyboard with good Urdu, Hindi, Arabic, Pashto and Bengali voice input, Roman-script transliteration (Roman Urdu or Hinglish to native script), and grammar/tone in those languages has little competition.
4. **Two-way chat translation** [inf]
   - Translate incoming messages (read through Accessibility, as CleverType already does for AI reply) and suggest replies in the other person's language.
   - The current translator keyboards only translate outgoing text.
5. **Quality bar in the clone tier** [obs]
   - The largest voice clones sit at 3.7-3.9 stars and their changelogs are about crashes and ANRs.
   - A stable, light keyboard with fewer interstitial ads can win ratings, and with them ranking.
6. **Pricing middle ground** [inf]
   - There is a gap between ad-funded clones (IAP up to $6.49 for the targets) and $15-30/mo AI subscriptions.
   - A $2-4/mo or regional-priced "AI voice and translate" tier, with a free ad tier, is underserved.

---

## 5. ASO keyword cluster

Taken from competitor titles and short descriptions [obs]. Priority reflects frequency across listings [inf].

- **Core (high):**
  - voice keyboard, voice typing keyboard, voice typing, speech to text, voice to text, talk to text, speak to text
- **Translation (high):**
  - translator keyboard, translate keyboard, keyboard translator, chat translator, translate as you type, all languages translator, conversation translator, voice translator
- **AI (rising):**
  - AI keyboard, AI voice typing, AI writing assistant, grammar keyboard, grammar fix, tone changer, AI reply, ChatGPT keyboard, rewrite, paraphrase
- **Utility add-ons (mid):**
  - camera translator, photo translate, PDF translator, English dictionary, phrase book, voice recorder
- **Personalisation (mid):**
  - keyboard themes, custom keyboard background, stickers, emoji keyboard, stylish fonts
- **Language long tail (high intent, low competition):**
  - Urdu voice typing, Hindi voice typing, Arabic voice keyboard, English voice typing, multi language keyboard, Roman Urdu keyboard
- **Suggested title pattern:** "Voice Keyboard: AI Translator" plus a short description that combines voice typing, translate as you type, AI grammar and offline. Every competitor title is built around "Voice Typing Keyboard" or "Translator Keyboard", so the AI and offline modifiers are the differentiators.

---

## 6. APKs worth tearing down next

| Priority | App | Why |
|---|---|---|
| 1 | T1 or T2 (one is enough) | They are probably the same codebase. Get the speech and translate APIs, OCR provider, ad stack and theme engine. |
| 2 | `com.easyvoicetyping.keyboard.inputmethod` (Digital Apps club) | The clone formula plus an AI assistant at 1.4M installs. Shows how a clone adds an LLM backend (which API, proxy, quotas) and its paywall. |
| 3 | `com.chat.translator.keyboard.translate.language` | The best-rated (4.71) translator keyboard at 5.1M. Translation pipeline, conversation mode, monetisation and onboarding funnel. |
| 4 | `com.clevertype.ai.keyboard` | The reference AI super-keyboard. Accessibility-based AI reply, voice cleanup pipeline, subscription paywall design. |
| 5 | `starnest.aitype.aikeyboard.chatbot.chatgpt` | An ads-plus-sub GPT keyboard. GPT proxy architecture, fonts and themes engine, how ads and the AI quota interact. |
| 6 | `com.tech.voice.keyboard.typing.inputmethod` (Nexovix) | A recent copy of the same template. Check for a shared SDK or codebase with the targets, and look at its PDF translator implementation. |
| ref | `net.devemperor.dictate` | Open source [inf]. Read the code on GitHub rather than the APK, as a reference for a Whisper-based voice IME. |
| ref | Wispr Flow / Typeless | UX reference for AI dictation (filler removal, speak-to-edit). Tear down only if we build the AI-voice-quality path. |
