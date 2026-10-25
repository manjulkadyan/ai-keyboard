# Sprint 1 manual test plan (single device)

A step-by-step run of everything Sprint 1 built, ordered so one person with one phone can go top to bottom
in about two hours. Each step points to its row in `docs/sprint-1-device-checklist.md` (the full,
multi-device checklist); tick both when a step passes.

- **Build under test:** debug `0.5.2-debug+dd4da202`, package `com.manjul.aikeyboard.debug`
- **Reference device:** Pixel 9, Android 17 (API 37), arm64, 4 KB pages
- Mark each step **Pass**, **Fail** (write what you saw) or **Blocked**.

## Still to do (as of 2026-10-25)

Manual checks left (test-plan step → checklist row):

- [ ] **Actions-editor drag checks** (step 2.6 → checklist 1.7): a 4th pin is refused with the "Up to 3 pinned"
  hint; the mic tile can't move; pinned order survives a keyboard restart. adb can't drive the drags.
- [ ] **Permission trampoline on a Samsung** (step 5.10 → checklist 4.7). Pixel 9 already passed.
- [ ] **Voice checks that need your own voice:**
  - [ ] Live partials and final text (step 5.1 → checklist 4.1)
  - [ ] Segmented sessions with pauses (step 5.2 → checklist 4.3)
  - [ ] Hide the keyboard mid-session (step 5.3 → checklist 4.2)
  - [ ] Switch field mid-partial (step 5.4 → checklist 2.12)
  - [ ] Switch into a password field mid-partial (step 5.5 → checklist 2.5)
  - [ ] Move the cursor mid-partial (step 5.6 → checklist 2.6)
  - [ ] Audio focus with music (step 5.7 → checklist 4.8)
- [ ] **Voice re-check after the Sprint 2 host-safety fix** (commit `ba22e4a4`, rebuild first): voice now
  replaces its own composing text, or joins punctuation onto its last commit, only while the cursor is still
  right after what it wrote; otherwise it just finishes composing. In Messages and Chrome: dictate two sentences
  with a pause (the ", " / ". " join still lands right after the first); dictate, then tap into the middle of the
  partial and type a letter before it settles (your letter stays, nothing of yours is deleted); dictate at the end
  of existing text (the existing text is untouched).

Already verified on the Pixel 9 on 2026-10-25 (don't repeat):

- [x] **Checklist 1.8 PASS** (no test-plan step): "Make private" updates live (lock shows, mic and the action
  hide, no field refresh); lock and smartbar icons are legible in the light theme. Long-press has no menu:
  long-pressing the lock adds the app to the private list at once; once private the toggle is hidden and undo
  is Settings > Private apps. The checklist wording "long-press toggle" suggests otherwise; needs product
  confirmation (see below).

### Decisions needed

- [x] **16 KB alignment of ML Kit's 32-bit libs.** The prebuilt `libtranslate_jni.so` and
  `liblanguage_id_l2c_jni.so` for armeabi-v7a and x86 fail the 16 KB page-alignment check. Pick a fix. Blocks
  Sprint 2 Task 3 step 5 and Task 4.
  Resolved: follow [Google's guide](https://developer.android.com/guide/practices/page-sizes) (16 KB is required
  on 64-bit only); the check now fails only for arm64-v8a/x86_64 and lists the 32-bit libs as info (Sprint 2 decision 14).
- [ ] **"Make this app private" and the 3-pin limit.** Does it stay exempt (PRD FR-07)?
- [ ] **Long-press lock.** Is "long-press = add to private immediately" the intended behaviour?
- [ ] **Working branch.** Keep `sprint-1-close-sprint-2-start` or move its commits to main (local only, none
  pushed).
- [ ] **Voice trailing-space edge case.** If the host types text identical to our last voice commit right after
  it, before the selection update arrives, the ", " join can delete the host's space instead of ours (found by the
  `:ime:editing` property test at 30,000 seeds, seed 1326; not caught at the default 1,000). Closing it means
  changing how voice adds trailing spaces. Accept for now, or change it?
- [ ] **Translate bar (Sprint 2 Task 7) product questions**, listed under Task 7 in
  `docs/plans/2026-10-25-sprint-2.md`: Enter before the preview needs a second Enter (or auto-commit when it
  arrives?); `translate_done offline=true` means "translated on device" (or "no connection"?); a sensitive close
  clears the whole translation cache; hiding the keyboard keeps the bar text; no Preview/Live switch in the bar
  yet.

## 0. Setup (once)

1. Unlock the phone and keep it awake: Developer options → **Stay awake** on, plugged in.
2. Install (already done once): `./gradlew :app:installDebug`, or
   `adb install -r -t app/build/outputs/apk/debug/app-debug.apk`.
3. In a terminal, keep two logs open:
   ```sh
   adb logcat -c
   adb logcat -s AiKbAnalytics          # analytics events (debug build only)
   adb logcat -b crash                  # crashes
   ```
4. Note your current keyboard so you can switch back afterwards:
   `adb shell settings get secure default_input_method`
5. **Whisper model (for section 6).** The download host is not set yet (OD-01), so put the model on the
   phone by hand. On your Mac (outside the sandbox) download
   `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin` (about 57 MB), then:
   ```sh
   adb push ggml-base-q5_1.bin /data/local/tmp/
   adb shell run-as com.manjul.aikeyboard.debug sh -c \
     'mkdir -p no_backup/models && cp /data/local/tmp/ggml-base-q5_1.bin no_backup/models/'
   adb shell am force-stop com.manjul.aikeyboard.debug
   ```
   Do this **after** section 1 (step 1.1 clears app data).

## 1. Onboarding (fresh install)

| ✓ | Step | Expected | Checklist |
|---|---|---|---|
| [ ] 1.1 | `adb shell pm clear com.manjul.aikeyboard.debug`, then open the app from the launcher. | Onboarding opens directly. Splash gone within about 1 s. Logcat shows `onboarding_start`, `onboarding_step_view`. | 3.1, 3.4 |
| [ ] 1.2 | Tap **Enable keyboard**, turn on "AI Keyboard (debug)" in system settings, press back. | App moves to the next step on its own. `ime_enabled` logged. | 3.2, 3.4 |
| [ ] 1.3 | Tap **Select keyboard** and pick it in the system picker. | Advances on its own. `ime_selected` logged. | 3.2, 3.4 |
| [ ] 1.4 | Languages step: pick a few (for example English, Hindi, Urdu), continue. | `languages_set` logged. | 3.2, 3.4 |
| [ ] 1.5 | Try-it step: tap the field. | Our keyboard shows and types. | 3.2 |
| [ ] 1.6 | In try-it, tap the mic. A "Before you speak" sheet appears: tap **Agree**, then **Don't allow** in the system dialog. Tap the mic again → **Only this time**. | The sheet's text names the speech engine in use (on-device / phone speech service / Whisper). After denial you stay on try-it with "Microphone access is off…". Allowing works. | 3.3 |
| [ ] 1.7 | Mid-onboarding (repeat 1.1 to a middle step), run `adb shell am kill com.manjul.aikeyboard.debug` while the app is in the background, then reopen. | Resumes at the same step. | 3.2 |
| [ ] 1.8 | Finish onboarding. Close and reopen the app. | Opens to the final screen, no onboarding flash, no new funnel events in logcat. `onboarding_complete` was logged once. | 3.4, 3.5 |

## 2. Keyboard core

| ✓ | Step | Expected | Checklist |
|---|---|---|---|
| [ ] 2.1 | Force-stop the app (`adb shell am force-stop com.manjul.aikeyboard.debug`). Without opening the app, tap a text field in Messages or Chrome. | Keyboard appears and types; no crash. | 1.1 |
| [ ] 2.2 | Check that Whisper and WorkManager did not load just by showing the keyboard: `adb shell run-as com.manjul.aikeyboard.debug sh -c 'grep -c aikb_whisper /proc/$(pidof com.manjul.aikeyboard.debug)/maps'` | `0`. | 5.13 |
| [ ] 2.3 | Settings → Languages → add a subtype → layout picker. | Urdu standard and Hindi phonetic layouts are listed. Every enabled language appears in the system language switcher (globe / long-press space). | 1.4 |
| [ ] 2.4 | Switch to Urdu, Arabic and Hindi. Use shift and long-press keys. | Right-to-left key order is correct. Shift draws correctly. Urdu aspirate and heh forms are reachable. Urdu shows Urdu digits; western digits on long-press. | 1.5 |
| [ ] 2.5 | Urdu: press the **h** key, then long-press it. | Base key = ه (U+0647), long-press has ہ (U+06C1). Known; note whether this is acceptable. | 1.6 |
| [ ] 2.6 | Smartbar: open the overflow (⋯) panel and tap its edit tile to open the actions editor. Drag a 4th action (for example Undo) from Hidden into Pinned; try to drag the mic tile; drag a pinned chip onto the mic slot or into Hidden and back; reorder the pinned chips. Close the editor, hide and reopen the keyboard. | The subheader reads "Pinned (3/3)". The 4th pin is refused ("Up to 3 pinned" shows and the tile goes back to Hidden). The mic tile can't be dragged and nothing replaces it. The "Make this app private" action does not count as a pin and stays last. Order survives. No Translate or AI chips. A phone that had more than 3 pinned before this build shows the first 3 and the rest in Hidden. (Code fixed, S1-1.7.) | 1.7 |
| [ ] 2.7 | Open the voice panel, then do the system back gesture. | Panel closes; keyboard stays. | 1.9 |
| [ ] 2.8 | Lock the phone and reboot. Before unlocking, open the emergency dialer's text field (if it uses the keyboard). | No crash. If our keyboard shows: lock indicator on, no mic, no clipboard history. | 1.2 |

## 3. Privacy: password fields and private apps

Use a login page in Chrome (for example any site's sign-in form) and an app's login screen.

| ✓ | Step | Expected | Checklist |
|---|---|---|---|
| [ ] 3.1 | Tap the password field. Look at the smartbar. | Only emoji, settings and the lock icon (with "make this app private"). No mic, clipboard, paste, undo, translate or AI, even if you pinned them. No suggestions. | 2.1 |
| [ ] 3.2 | In the password field: long-press Enter and pick clipboard (if offered); try a swipe bound to "switch to clipboard". | Clipboard history never opens. | 2.11 |
| [ ] 3.3 | In a normal field, open clipboard history. In clipboard settings turn **off** "hide on next text field". Then tap the password field. | The panel closes as soon as the password field gets focus. In a second normal field it stays open (normal upstream behaviour). | 2.11 |
| [ ] 3.4 | Open clipboard history in a normal field of some app, then tap the lock icon and make that app private. | Panel closes. | 2.11 |
| [ ] 3.5 | Settings → Privacy → Private apps. Add an app from the picker. Open that app and type. | Lock icon on, no mic, no learning. The suggestions list in the picker is empty (expected for now). | 2.4 |
| [ ] 3.6 | **Network check.** Note the app's UID: `adb shell dumpsys package com.manjul.aikeyboard.debug \| grep userId`. Run `adb shell dumpsys netstats detail \| grep -A3 "uid=<UID>"` and note the bytes. Focus a password field and type, backspace, paste and tap where the mic would be for 1 minute. Re-run the command. | The bytes for our UID do not change. | 2.9 |

## 4. Clipboard

| ✓ | Step | Expected | Checklist |
|---|---|---|---|
| [ ] 4.1 | Fresh state: open clipboard settings. | History is **off** by default. Auto-clean slider goes 0 to 1440 min in steps of 5; label reads sensibly. | 2.2, 2.10 |
| [ ] 4.2 | Turn history on. Copy normal text; copy a password from Google Password Manager; copy an SMS OTP ("Your code is 482913"). Open clipboard history. | Only the normal text is stored. The password and OTP are not stored. | 2.2 |
| [ ] 4.3 | Pin one clip, leave one unpinned. Set the phone clock 25 h ahead (turn off automatic time), reopen clipboard history. Restore the clock. | Unpinned clip is gone; pinned one stays. | 2.3 |

## 5. Voice: Google on-device recognizer

| ✓ | Step | Expected | Checklist |
|---|---|---|---|
| [ ] 5.1 | In Messages (or any chat field) tap the mic and speak a sentence. | Gray live text (partials) appears while you speak, waveform moves, final text is inserted with a trailing space. `voice_*` events in logcat contain no spoken words. | 4.1, 2.8 |
| [ ] 5.2 | Speak several sentences with pauses longer than 2 s (stay under 60 s). | Each sentence is inserted as you pause; the session continues until about 2 s of silence or 60 s. | 4.3 |
| [ ] 5.3 | Start dictating, then swipe home (or press back to hide the keyboard) mid-sentence. | Mic indicator (green dot) turns off almost immediately. The gray partial is removed, nothing is committed. | 4.2 |
| [ ] 5.4 | Start dictating in field A, then tap field B in the same app mid-sentence. | Partial removed from A, nothing committed in A. | 2.12 |
| [ ] 5.5 | Start dictating in a normal field on a login page, then tap the password field. | Voice stops; no text ends up in the password field. | 2.5, 2.7 |
| [ ] 5.6 | Dictate, and while gray text is showing, tap elsewhere in the text to move the cursor. | Our gray text is dropped. None of your existing text is deleted. | 2.6 |
| [ ] 5.7 | Play music, dictate, stop. | Music pauses during dictation and resumes after. | 4.8 |
| [ ] 5.8 | Airplane mode on. Pick a language without an offline Google model (for example Urdu) and tap the mic. | A clear message such as "You are offline and this language has no offline speech model." with a **Download offline speech** or **Get offline model** chip; never a silent failure. | 4.5 |
| [ ] 5.9 | Settings → Voice → Speech engine → **On-device only**. Airplane mode off. Pick a language with no on-device model and tap the mic. | "No on-device speech engine is available for this language." (or a download offer). It never uses the cloud recognizer even though you're online. | 4.10 |
| [ ] 5.10 | System settings → Apps → AI Keyboard → Permissions → Microphone → **Don't allow**. Tap the mic. Then allow it from the chip. Repeat once and deny; then press Home while the voice notice or the rationale is showing. Also check portrait, landscape and the largest font size. | "Allow microphone, then tap the mic again" chip is shown in full (it may wrap to two lines; no "…"). The permission screen opens over the current app; granting works and Logcat (`AiKbAnalytics`) shows `voice_permission_result` with `granted=true`. Denying shows the "Microphone is off" dialog and `granted=false`. After Home the permission screen is gone and not in Recents. (Code fixed: S1-4.6 chip layout, S1-4.7 `noHistory` removed.) | 4.6, 4.7 |
| [ ] 5.11 | Type a few paragraphs normally in a few apps, with voice in between. | No regressions in normal typing. | 4.9 |

## 6. Voice: Whisper (needs the model from setup step 5)

| ✓ | Step | Expected | Checklist |
|---|---|---|---|
| [ ] 6.1 | Settings → Voice → Offline models. | Base shows "Downloaded, 57 MB". Small is listed (Pixel 9 has more than 5.5 GiB). Downloading Small ends in "Download failed" and leaves no file (no host or hash yet, OD-01). | 5.10, 5.15 |
| [ ] 6.2 | Speech engine → **Offline (Whisper)**. Dictate a 10 s English sentence. | "Listening…" with a moving waveform, silence stops it, "Transcribing…" shows, then one final text is inserted (no live partials with Whisper). Note the time from stop to text. | 5.8, 5.7 |
| [ ] 6.3 | Switch to Hindi and dictate. Then say something that should produce an emoji or mixed script. | Devanagari text is correct (no garbled characters). | 5.3 |
| [ ] 6.4 | Dictate 60 s non-stop. | Finishes under 120 s; the spinner never runs past that. Note the time. Check memory: `adb shell dumpsys meminfo com.manjul.aikeyboard.debug \| grep TOTAL`. | 5.7, 5.1 |
| [ ] 6.5 | Start a long dictation, stop, and while the spinner shows, hide the keyboard. | Returns immediately, no crash, no late text appears. | 5.5 |
| [ ] 6.6 | Tap the mic, stop, tap again quickly, several times. | No "audio error". (Known risk: about 100 ms overlap.) | 5.8 |
| [ ] 6.7 | After a Whisper dictation, wait 60 s, then `adb shell am send-trim-memory com.manjul.aikeyboard.debug RUNNING_LOW`. Dictate again. | Model is freed and reloads on the next dictation (first result is slower). | 5.9 |
| [ ] 6.8 | Open voice settings and leave it (keyboard hidden only), then dictate. | Model was not freed (no reload delay). | 5.9 |
| [ ] 6.9 | Delete the base model in voice settings (Whisper still selected). Check the settings screen, then tap the mic in a language the on-device recognizer lacks (for example Urdu). | Settings say "Download a model below. Until then, on-device speech recognition is used…". In the keyboard a **Get offline model** chip appears; tapping it opens Settings → Voice. Re-push the model (setup step 5) afterwards. | 5.11 |

## 7. Logs and permissions

| ✓ | Step | Expected | Checklist |
|---|---|---|---|
| [ ] 7.1 | Search the whole session's logs: `adb logcat -d \| grep -iE "<a word you typed>\|<a word you dictated>\|<a clip you copied>"`. | No matches from our process. No host app package names in `AiKbAnalytics` lines. | 2.8 |
| [ ] 7.2 | `adb shell dumpsys package com.manjul.aikeyboard.debug \| sed -n '/requested permissions/,/install permissions/p'` | RECORD_AUDIO, ACCESS_NETWORK_STATE, INTERNET, VIBRATE, POST_NOTIFICATIONS, WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, the app-private receiver permission. **Also listed on Android 17: ACCESS_LOCAL_NETWORK**, which is not in our manifest (the OS adds it for apps holding INTERNET). Checked on the Pixel 9 on 2026-10-20. | 6.2 |
| [ ] 7.3 | The app never asked for notifications at any point. | No notification permission prompt was ever shown. | 6.2 |

## 8. Cleanup

- Switch back to your normal keyboard (Settings → Keyboard), or
  `adb shell ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME`.
- Turn automatic time back on if you changed the clock.

## Not testable on this phone

These remain open in `docs/sprint-1-device-checklist.md`:

- **Other phones:** Samsung and Xiaomi recognizer behaviour (4.1, 4.7); API 31 and 32 (4.3, 4.4); a 3 GB
  phone and a Cortex-A53 class phone for Whisper (5.1, 5.6).
- **Release build (6.1, 6.3, 6.4, 5.12):** Whisper hidden, exported components, startup, size. These need a
  signed release build.
- **16 KB pages:** on the Pixel 9 you can try Developer options → "Boot with 16 KB page size", then repeat 6.2
  (checklist 6.1).
- **Model download and resume (5.10, 5.14):** blocked until OD-01 provides the host and the SHA-256 hashes.
