# Sprint 2 device checklist and exit check

The Sprint 2 build was verified in a sandbox with no emulator and no network access to the ML Kit model host.
Everything below needs a physical device (or an emulator image) before Sprint 2 is signed off. Each item names the
task or ruling it came from (`T<n>` = plan task, `RF<n>` = plan Review Focus).

Suggested devices: a Pixel and a Samsung (API 33+, predictive back), one API 31 or 32 device, a 3 GB (low-tier)
phone, and a phone with a hardware keyboard or a Bluetooth keyboard.

Tick a box only after running it on a device. Record device, Android version and build in the notes column.

## 1. Translate bar in the keyboard

| # | Steps | Expected | From |
|---|---|---|---|
| [ ] 1.1 | In WhatsApp, Gmail, Chrome (a page text field) and Messages, type some host text first, then tap the Translate chip. Type a sentence, wait for the preview and press Enter (or ✓). Repeat with Live mode on. | Keys, space and backspace go to the bar and never to the host. Enter inserts the translation once at the cursor. Host text that was there before is unchanged, and nothing is deleted from it, including after closing the bar mid-preview in Live mode. | T8, RF1 |
| [ ] 1.2 | Open the bar in a login form's username field and type. While a translation is in flight, move to the password field (or let the app turn the field into a password field). | The bar closes at once and the Translate chip disappears. No text, translated or source, reaches the password field, including when the request finishes late. | T8, RF2 |
| [ ] 1.3 | Open the bar, type a word, then hide the keyboard (back or the hide key). Show it again within 60 s and reopen the bar. Then hide it again and wait more than 60 s before showing and reopening. | The first time the bar text is still there. After more than 60 s it is empty. Pair and mode are kept both times. The bar is closed after every hide. | T8, T7 decision (d) |
| [ ] 1.4 | Force-stop the app, open a text field and use the keyboard without touching the Translate chip. Check logcat and `adb shell dumpsys meminfo` for ML Kit. | ML Kit is not initialised and no translator is loaded until the first time the bar is opened. | T8, RF6 |
| [ ] 1.5 | API 33+: open the bar and use the predictive back gesture. API 31/32: press back. | The bar closes and the keyboard stays. A second back hides the keyboard. | T8 |
| [ ] 1.6 | Open the bar and tap the mic. Dictate a sentence. Then close the bar while dictating, and open it while dictating in the host. | Partials and the final text go into the bar buffer, not the host. Opening or closing the bar stops the dictation. No host text is deleted. | T8 |
| [ ] 1.7 | Focus a password, phone, email and URL field, and a field in an app marked private. | The Translate chip is not shown. A stale tap (chip still visible during a field switch) opens nothing. | T8, PC-1 |
| [ ] 1.8 | On a 3 GB (or `isLowRamDevice`) phone with fresh settings, open the bar. | The bar starts in Preview mode, and the preview appears about 600 ms after typing stops (450 ms on other devices). | T8, decision 5 |
| [ ] 1.9 | Pick a pair whose languages are not downloaded. Tap "Download …", then hide the keyboard. With no network, tap the "Offline" chip. | The download starts only from the tap, on Wi-Fi only unless that setting is off, and keeps going after the keyboard hides. The offline chip opens Offline languages in the app. | T8, T5 |
| [ ] 1.10 | With a hardware keyboard, open the bar from the chip and type, use backspace and Enter, and try Ctrl+A and forward delete. | Printable keys, space, backspace and Enter go to the bar. Ctrl shortcuts and forward delete do not change host text. | T8 |
| [ ] 1.11 | Smartbar actions editor: try to drag, pin or hide the Translate action. | Translate stays first among the actions, cannot be dragged, and does not count towards the 3 pins. | T8 |
| [ ] 1.12 | Debug build logcat while using the bar: typing, preview, insert, undo, failure chips. Filter on `AiKbAnalytics` and the app package. | No buffer, preview or translated text appears in any log line or event. | Global constraints, PC-10 |

## Translation latency (T4)

Method, commands and the results table: `docs/translation/latency.md`. Record the numbers there.

| # | Steps | Expected | From |
|---|---|---|---|
| [ ] L.1 | Mid-tier phone (Pixel 6a / Galaxy A54 class): `$G ./gradlew :core:translate-mlkit:installDebugAndroidTest`, then `adb shell am instrument -w -r -e tier mid -e download true -e class com.manjul.aikeyboard.core.translate.mlkit.TranslateLatencyTest com.manjul.aikeyboard.core.translate.mlkit.test/androidx.test.runner.AndroidJUnitRunner`. | One `pair p50 p90` line per pair (en→ur/hi/ar/bn/es, ur→en, hi→en). Passes when every p90 is at most 150 ms. If any p90 is above 150 ms, apply the R-03 switch in `latency.md` (600 ms `DebouncePolicy.Default`, live composing off for all) and note it in the sprint exit. | T4, TRD §8, R-03 |
| [ ] L.2 | 3 GB Android 10 phone: the same command with `-e tier low`. | Every p90 at most 400 ms; otherwise record it in the sprint exit and raise it with product. | T4, decision 5 |
| [ ] L.3 | Debug app on both phones: download en, ur, hi, ar, bn, es in the app, then Devtools → enable → "Translate latency probe". Paste the copied numbers into `latency.md`. | Numbers for every pair (no `no_pack`), only a device line and numbers in the clipboard, no sentence text. Probe p90 within about 50 ms of the test's; if much slower, consider the Room mirror (decision 4). | T4 |
| [ ] L.4 | Release build: open Devtools. | No "Translate latency probe" row. | T4 |

## 2. Offline language packs

| # | Steps | Expected | From |
|---|---|---|---|
| [ ] 2.1 | On Wi-Fi, open Settings > Offline languages and tap Download on Urdu. Wait for it to finish. Then translate en→ur with the network off. | The row shows an indeterminate indicator with Stop, then "Downloaded". English is listed as downloaded and its Delete is disabled with the pivot note. The offline translation works. Storage used goes up by about 30 MB per model. | T5 |
| [ ] 2.2 | On mobile data only, with "Download on Wi-Fi only" on, tap Download on Hindi. Then turn the switch off. | With the switch on the row stays "Downloading" and nothing is fetched until Wi-Fi is back. Downloads started after turning the switch off run on mobile data. | T5 |
| [ ] 2.3 | Start a download and tap Stop at once (job still queued), then start another and tap Stop while ML Kit is fetching. Reopen the screen after it would have finished. | The row goes back to "Not downloaded" straight away both times. After the second download finishes, the model is deleted (not kept), and the row stays "Not downloaded" after force-stopping and reopening the app. | T5, RN-06 |
| [ ] 2.4 | Start a download from the keyboard's "Download" chip, hide the keyboard and lock the phone for a minute. Then open Offline languages. | The download carries on with the keyboard hidden and the row shows "Downloaded" (or still "Downloading"). Nothing downloads without a tap: open the bar on a pair with no models and confirm no download starts until the chip is tapped. | T5, T8 |
| [ ] 2.5 | In the keyboard, with no network and a pair that is not downloaded, tap the "Offline" chip. | Offline languages opens in the app. | T5, T8 |
| [ ] 2.6 | Fresh install: go through onboarding to the languages step, pick English and Urdu. Leave the "Download for offline" switch off and continue; repeat (clear data) with the switch on. | The offer shows "about 31 MB" (about 94 MB for Urdu + Hindi). With the switch off nothing downloads. With it on, the Wi-Fi-only download starts and onboarding moves on without waiting. Skip still completes onboarding. | T5, decision 12 |
| [ ] 2.7 | Offline languages, Voice typing section, on a device with and one without an on-device recognizer. Tap Settings on the recognizer row. | The row text matches the device. Settings opens the system voice input screen. Whisper rows appear only in builds with Whisper on. | T5 |
| [ ] 2.8 | TalkBack on Offline languages: move through a row and its button. | Each button reads "Download Urdu", "Stop downloading Urdu" or "Delete Urdu". | T5 |

## T9. Translation history database and shared TTS

No UI of its own: run these once the Translator screen (T10) or the selection sheet (T11) uses them.

| # | Steps | Expected | From |
|---|---|---|---|
| [ ] T9.1 | Speak a long translation (over 4,000 chars) in English, then an Urdu and a Hindi one, on a device with Google TTS. Leave the screen while speaking. | Speech covers the whole text with no gap or cut word at chunk joins; speaking stops and the engine shuts down when the last screen using it closes (one TextToSpeech per process). | T9, FR-26 |
| [ ] T9.2 | On a device with no voice data for a language (e.g. bn), tap speak. | The app offers to install voice data and the button opens the system TTS data installer. | T9, FR-26 |
| [ ] T9.3 | Translate a few texts, then run `adb shell bmgr backupnow com.manjul.aikeyboard` (or a device transfer) and inspect the backup. Install a later build over the old one. | `databases/app_database*` is not in the backup. History survives the app update (no destructive migration). | T9, TRD §6.3 |

## T10. Translator screen

| # | Steps | Expected | From |
|---|---|---|---|
| [ ] T10.1 | Open the app; tap "Translate" at the top of Home. Type a sentence in English with en→ur. | The Urdu result appears about 600 ms after typing stops, right-aligned (RTL), with copy, share, speak and star. No ad or interstitial at any point. | T10, FR-12 AC2 |
| [ ] T10.2 | Tap speak with a Google TTS voice installed, then on a language with no voice data (e.g. bn). | Speech covers the whole text; for the missing voice the speak button is greyed out with "Install voice", which opens the system TTS installer. | T10, FR-12 AC4 |
| [ ] T10.3 | Airplane mode on, on a device with an on-device recognizer that has the source language. Tap the mic (first time: agree to the voice notice, then allow the microphone). | The notice comes before the permission prompt. Dictated words fill the input and are translated offline. On a device with no on-device recognizer, offline shows "Voice input is not available". | T10, FR-12 AC3, decision 7 |
| [ ] T10.4 | Tap share on a result. | The system share sheet opens with the translation as plain text. | T10 |
| [ ] T10.5 | Choose "Detect language" as the source and type Urdu text. | "Detected: Urdu" shows under the pickers; swap makes Urdu the target. | T10 |
| [ ] T10.6 | Translate a few texts, star one, open History (top bar). Search, swipe a row away, then Clear history with "Keep favourites" on. Turn "Save history" off in the overflow menu and translate again. | Search filters; swiping deletes; clear keeps only the starred row. With history off nothing new is saved. | T10, FR-12 AC1 |
| [ ] T10.7 | Install a later build over this one. | History and favourites are still there. | T10, FR-12 AC5 |
| [ ] T10.8 | Open a pair with no downloaded model, offline. | "Download this language pair to translate offline" with a Download button showing the size; the previous result stays. | T10 |

## Q. Translation quality eval (ur/hi/ar/bn)

| # | Steps | Expected | From |
|---|---|---|---|
| [ ] Q.1 | Run `TranslateEvalHarness` on any API 26+ phone with network, as in `docs/translation/quality-ur-hi-ar-bn.md` ("How to run"), then `adb pull` the `eval/` folder. | Four `eval-<lang>-<date>.csv` files, 250 rows each, with ML Kit output and latency; the `error` column is empty for nearly every row. The `am instrument` output shows only counts and paths. | T12, decision 10 |
| [ ] Q.2 | Give each language's CSV to two native raters with `docs/translation/eval-rating-template.csv`; aggregate and fill the results and routing tables. | Every language and direction has a pass or "Beta" verdict under the FR-11 AC5 rule; the status line in the quality doc no longer says "Pending". | T12, PRD FR-11 AC5 |
| [ ] Q.3 | While the harness (or Offline languages) downloads a model, capture traffic (for example a proxy or `tcpdump` on an emulator). | Only Google model-download hosts and ML Kit telemetry are contacted; no request carries corpus text. Record the hosts in `docs/data-flows.md`. | T12, decision 11 |
