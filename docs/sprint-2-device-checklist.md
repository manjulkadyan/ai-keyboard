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
| [ ] 1.9 | Pick a pair whose languages are not downloaded. Tap "Download …", then hide the keyboard. With no network, tap the "Offline" chip. | The download starts only from the tap, on Wi-Fi only unless that setting is off, and keeps going after the keyboard hides. The offline chip opens Offline languages in the app. Known gap: until Task 5 lands, that route does not exist: check the app opens without crashing. | T8, T5 |
| [ ] 1.10 | With a hardware keyboard, open the bar from the chip and type, use backspace and Enter, and try Ctrl+A and forward delete. | Printable keys, space, backspace and Enter go to the bar. Ctrl shortcuts and forward delete do not change host text. | T8 |
| [ ] 1.11 | Smartbar actions editor: try to drag, pin or hide the Translate action. | Translate stays first among the actions, cannot be dragged, and does not count towards the 3 pins. | T8 |
| [ ] 1.12 | Debug build logcat while using the bar: typing, preview, insert, undo, failure chips. Filter on `AiKbAnalytics` and the app package. | No buffer, preview or translated text appears in any log line or event. | Global constraints, PC-10 |
