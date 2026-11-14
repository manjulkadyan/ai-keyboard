# Sprint 5 device checklist and exit check

The Sprint 5 build was checked in a sandbox. The Android and backend checks ran there. The instrumented checks ran on
one API 37 emulator, not the API 34 emulator the plan asked for. A physical phone was attached, but no check ran on
it. Everything below needs a device, a content CDN, a deployed backend or an owner action before Sprint 5 is signed
off. Each row names the plan task or requirement it comes from (`T<n>` = plan task).

Suggested devices: a Pixel and a Samsung (API 33+), one API 26-30 phone, and a low-end phone for the colour picker
rows. Sticker and Pro rows need a licence tester account and a build installed from a Play test track.

Fill in the Result column only after running the check. Record device, Android version, build and date there. Every
Result cell starts empty.

## Automated exit check (sandbox, 2026-11-14)

| Check | Command | Result |
|---|---|---|
| Debug build | `$G ./gradlew assembleDebug` | PASS |
| Unit tests | `$G ./gradlew test testDebugUnitTest :build-logic:convention:test` | PASS |
| Check and lint | `$G ./gradlew check :app:lintDebug` | PASS |
| 16 KB alignment | `scripts/check-16kb-alignment.sh --min-libs 4 app/build/outputs/apk/debug/app-debug.apk` | PASS (23 libraries, 4 unaligned 32-bit, informational) |
| Fork markers | `scripts/check-fork-markers.sh` | PASS |
| Data flows | `scripts/check-data-flows.sh` | PASS (37 tracked artifacts) |
| Backend tests | `cd backend && $G ./gradlew test` (branch `sprint-5-dictionary`) | PASS (227 tests, 3 skipped) |

`$G` is the sandbox truststore prefix from the plan's Global Constraints.

## Instrumented runs (emulator-5554, API 37, not API 34)

| Class | Command filter | Result |
|---|---|---|
| `com.manjul.aikeyboard.fork.clipboard.KeystoreKeyProviderTest` | `-Pandroid.testInstrumentationRunnerArguments.class=<class>` | PASS (2 tests) |
| `com.manjul.aikeyboard.fork.stickers.StickerCompatInsertTest` | same | PASS after fix `70d86ddf`. It failed first: the share receiver Activity runs in the test package's process, so the test's static latch never released |
| `com.manjul.aikeyboard.isolation.ImeOnlyColdStartTest` | same | PASS on API 37 after the test fix in this commit. It failed first because the test loaded the classes itself: on ART, `ClassLoader.findLoadedClass` falls back to the dex lookup and defines a class that is not loaded yet, so every check for an existing class returned "loaded" (`NativeAdSlot` and `TranslatorScreen` read "not loaded" only because they are file names, not classes). The test now reads loaded classes from a heap dump of its process. A second fault was hidden behind it: API 37 prints `mCurImeId`, not `mCurMethodId`, in `dumpsys input_method`. No production change: no ad class loads in an IME-only process. I.1 of Sprint 4 is closed on API 37; an API 34 run is still to do |

## D. Dictionary

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| D.1 | Offline lookup | Open Settings, Offline languages, install the dictionary pack. Turn on flight mode. Look up a word on the Dictionary screen. | The definition shows with no network (FR-21 AC1). | T3, FR-21 | |
| D.2 | Query survives flight mode | Type a query. Toggle flight mode. | The query and the result stay (FR-21 AC2). | T3, FR-21 | |
| D.3 | Attribution and licence link | Open an entry. Open Settings, About, Open-source licences. | The attribution and the licence link show on the entry and in the OSS licences screen (FR-21 AC3, FR-09 AC3). | T3, FR-21, FR-09 | |
| D.4 | Define in Chrome | In Chrome, select one word and tap "Define". Repeat with two words. | One word opens its entry. Two words open the Dictionary screen with the text as the query, or the documented two-word behaviour. | T4 | |
| D.5 | Keyboard card is offline | Long-press a suggestion in the keyboard. Capture traffic with a proxy. | The card shows the offline definition. The proxy shows no request. | T4, decision 6 | |
| D.6 | Dictionary native ad | Open the Dictionary screen as a free user, then as a Pro user. | The native ad sits below the fold and never shows for Pro. | T4, S-17 | |
| D.7 | Delete my data resets the dictionary | Install the pack, look up an online word, then run Delete my data. | The pack, the online cache and the cached manifest are gone. | T7 | |

## P. Phrasebook

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| P.1 | Categories and own phrases | Browse the 12 categories. Add, edit and delete an own phrase. | All 12 categories show phrases. Own phrase changes persist (FR-22 AC3). | T5, FR-22 | |
| P.2 | Speak and send | Tap a phrase to speak it. Use "Send via keyboard" into a chat app. | The phrase is spoken. The share sheet opens and the chat app gets the text. | T5, decision 18 | |
| P.3 | Translations in flight mode | Install the language pack. Turn on flight mode. Open a category. | Translations show (FR-22 AC2). | T5, FR-22 | |

## S. Stickers

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| S.1 | Starter pack inserts | In Messages or WhatsApp, open the sticker panel and tap a `starter` sticker. | The sticker inserts with `commitContent`. | T6 | |
| S.2 | Share or clipboard fallback | In an app without image support, tap a sticker. | The share or clipboard fallback runs. | T6 | |
| S.3 | Pro pack gate | Without Pro, tap a Pro pack sticker. Tap "See Pro". Then buy with a licence-tester account and tap again. | The card shows. "See Pro" opens `paywall?trigger=premium_sticker`. After the purchase the sticker inserts. | T6 | |
| S.4 | No chip in sensitive fields | Open a password field, incognito and the lock screen. | No STICKERS chip shows (FR-23 AC3). | T6, FR-23 | |
| S.5 | Catalog fetch carries no identity | Capture the catalog fetch with a proxy. | No auth header, no cookie and no query (FR-23 AC4). | T6, FR-23 | |
| S.6 | Waits for Wi-Fi | Turn on Wi-Fi-only. Use a metered network. Open the sticker panel and tap to download a pack. | The panel shows "Waits for Wi-Fi" and downloads nothing. A proxy shows no pack request. | T6, T7 | |

## C. Clipboard

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| C.1 | Off by default | Fresh install, open the clipboard panel. | History is off. The in-panel prompt turns it on (FR-06 AC1). | T7, FR-06 | |
| C.2 | No OTP or password | Copy an OTP and a password. | Neither appears in the panel (FR-06 AC2). | T7, FR-06 | |
| C.3 | Pin and expiry | Pin an item. Wait 24 h. Tap "Clear clipboard history". | Unpinned items expire and pinned ones stay. Clear works (FR-06 AC4). | T7, FR-06 | |
| C.4 | Smart chips | Copy text with an email, a URL and a phone number. | Each chip pastes only that part. | T7 | |
| C.5 | Upgrade keeps items, no plaintext | Install the S4 build, copy items, upgrade to this build. On a debug build run `sqlite3` on the clipboard DB. | The items are kept. The text column shows no plaintext. | T7 | |
| C.6 | Key store unavailable | If the key store is unavailable for a short time, open the clipboard panel. Then let the key store recover. | The panel hides sealed items and wipes nothing. After the key store recovers, the items come back. | T7, review round 2 | |

## K. Custom colours

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| K.1 | Colour inputs | Pick colours with the wheel, the hex field and a recent colour. | Each applies to the chosen element (FR-18 AC1). | T8, FR-18 | |
| K.2 | Recents and live theme | Restart the app. Open the picker. | Recents survive the restart (FR-18 AC2). The custom theme applies live in the keyboard. | T8, FR-18 | |
| K.3 | Drag smoothness | On a low-end device, drag on the hue ring and on the saturation and value panel. | The drag stays smooth. The stylesheet rebuilds on each drag event, so watch for dropped frames. | T8 | |
| K.4 | Pick while Save runs | Tap "Save" and pick a colour while it runs. | The saved theme and the preview match the last colour picked. | T8 | |
| K.5 | Delete my data and colours | Save a custom theme. Run Delete my data. Check the Custom colours screen and the installed themes. | The custom colours reset. Record whether the custom theme extension stays installed (a known S5 limit). | T8 | |

## X. Cross-cutting

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| X.1 | TalkBack | Turn on TalkBack. Walk the sticker panel, the colour picker and the definition card. | Each control has a spoken name and role. | T4, T6, T8 | |
| X.2 | Release logcat audit | Run a release build through a scripted session of dictionary, phrasebook, sticker, clipboard and colour use. Read logcat. | No query, phrase, clip text or package name appears. | All | |

## Open issues

- `ImeOnlyColdStartTest` passes on API 37 after its test fix (see section C). It has not run on API 34 yet. Run it once
  on an API 34 emulator to confirm the `mCurMethodId` branch and the heap dump read.

## Open questions for product

1. **Plan size (decision 2):** the plan is 9.25 days against 7.5 days in the TRD. Is the extra 1.75 days accepted, or do
   we move "Custom colours" or the clipboard encryption to Sprint 6?
2. **Content owners and dates:** who builds the real Wiktionary pack, writes the about 300 phrases, orders the top-10
   translations and commissions the sticker art (OD-18)? Engineering needs them before the v1 release, not in S5.
3. **CDN:** which host serves `<content base>/v1/`, and who holds the content signing key (decision 12)? Until then,
   beta builds have no downloadable content.
4. **OD-11 terms:** who checks the Free Dictionary API terms for proxy use and caching? Until then, `/v1/dictionary`
   answers 503 in production.
5. **Dictionary rate limit:** is 60 lookups a minute per user right? Do free and Pro users get the same limit?
6. **Online dictionary consent (decision 6):** is an "Online dictionary" setting, default on, enough? Or do we need a
   one-time notice before the first online lookup?
7. **Phrasebook ad (decision 19):** does S-17 get a `native_phrasebook` placement? It is not in PRD FR-27.
8. **New routes (decision 20):** can `dictionary` and `phrasebook` join the PRD FR-32 allow-list?
9. **Backend branch:** the `backend/` submodule is on `sprint-4-billing`. Is S4 merged to the backend `main`, so that S5
   can branch from it?
10. **Event values (decisions 11 and 22):** can `sticker_send.method` add `clipboard`, and can `pack_download_*` add
    `kind=stickers`?
11. **"Send via keyboard" (decision 18):** is the share sheet the intended action, or do you want a phrasebook panel
    inside the keyboard?
12. **Photo backgrounds:** which sprint builds them? The eyedropper waits for them (decision 16).
