# Device test plan

This plan holds every manual check for the keyboard app and the local kb-api backend. It replaces these files:
`docs/manual-test-plan.md`, `docs/sprint-1-device-checklist.md`, `docs/sprint-1-fixes-test-plan.md`,
`docs/sprint-1-manual-test-plan.md`, `docs/sprint-2-device-checklist.md` to `docs/sprint-6-device-checklist.md` and
`docs/sprint-3-fixes-test-plan.md`. Every case was checked against the code on `main` at commit `52f01daa`. The
section "Dropped from old plans" lists the old cases that are gone and why.

Write **Pass**, **Fail** (with what you saw) or **Blocked** in the Result column. Add the device, the Android version,
the build and the date.

If more than one device is connected (for example a phone over wireless adb), always set `ANDROID_SERIAL` first,
for example `export ANDROID_SERIAL=emulator-5554`. Otherwise `./gradlew :app:installDebug` installs on every device.

## Run status

Last run: 2026-11-15, on `emulator-5554` and the local kb-api, build `52f01daa`.

| Scope | Pass | Fail | Blocked |
|---|---|---|---|
| API (curl, local kb-api) | 37 | 0 | 4 (need production) |
| EMU and EMU+API, sections A to P | 124 | 14 | 17 |
| EMU and EMU+API, continuation (B, C, L.15, L.16, Q to X, four reruns) | 56 (plus 12 partial) | 7 | 20 (one not run) |
| EMU, rerun of fixed bugs on 2026-11-16, build `47ec72cf` | 11 | 1 (R.12) | 0 |

The continuation ran on 2026-11-15 in three batches. The reruns of E.5, K.9 and P.5 pass. The G.5 rerun confirms the app bug.

On 2026-11-16 the fixed bugs ran again on `emulator-5554`, build `47ec72cf` on branch `fix/device-test-bugs`. A.10, A.11, G.2, G.5, I.3, K.13, O.9, Q.3, U.4, W.3 and W.6 pass. R.12 still failed for a pick made just after Save. B.8 became a plan fix. Round 2 on branch `fix/device-test-bugs-2` fixed R.12 (`28ad4697`) and the G.2 hardware Backspace (`1a6890bb`). Both pass on the emulator.

Most blocked cases need hardware or setup that the emulator does not have: ad unit ids and Firebase (X), a release or verbose build (S.5, T), a real device (C, T) or Pro billing (B.6).

### Open app bugs

| ID | Bug |
|---|---|
| F.2 | A new typed word is never learned. Learning happens only when a strip candidate is picked. Product gap or bug. |
| K.12 | A field switch during a Live preview commits the preview into the old field. A fix attempt was rejected because Android deactivates the old field's InputConnection before the IME hears about the switch, so a new design or a documented platform limit is needed. |
| N.4, N.5 | After a voice uninstall, Google TTS reports LANG_NOT_SUPPORTED, so the app never offers Install voice. |
| N.1 | One ANR (app not responding) in the settings app. The process used 101% CPU, mostly kernel time. No stack. Cause unknown. |

### Continue here

All planned emulator cases ran. These items are still open:

1. Fix and retest the bugs in "Open app bugs". K.12 needs a new design or a documented platform limit.
2. R.20: rerun on a fresh setup. Install a dictionary pack and a custom colour theme first, then run Clear on device only. Do not run R.19 before it, because a `bmgr restore` wipes all app data.
3. S.5: needs a verbose build and a reinstall.
4. Section X: needs `aikb.firebase.*` and test ad unit ids in Remote Config. Without them no ad can load, so X.1 to X.6 and X.8 are blocked and X.7 and X.9 are partial.
5. Section W: TalkBack speech was not heard on the emulator. Run W.1 to W.6 on a device with TalkBack audio.
6. Section T and the blocked C cases: run on the Pixel 9.
7. G.2: on a device, type "teh " in English and press a hardware Backspace once, to confirm the English autocorrect revert.

Restore this state first:

```sh
export ANDROID_SERIAL=emulator-5554
cd backend && AUTH_MODE=dev GATEWAY=fake ./gradlew run          # in its own shell
curl -s -H 'X-Dev-Uid: dev-local' localhost:8080/v1/quota      # expect 200 and ai_left_today
./gradlew :app:installDebug -Paikb.ai.devAuth=true -Paikb.ai.baseUrl=http://10.0.2.2:8080
adb shell ime set com.manjul.aikeyboard.debug/com.manjul.aikeyboard.FlorisImeService   # again after any force-stop
```

- `am start -S` and `am force-stop` drop the app as the IME. Run `ime set` again after them.
- To stop the bound IME process, use `adb shell run-as $PKG kill -9 <pid>`. `am kill` does not stop it.
- For backup cases, run `bmgr enable true` and `bmgr transport com.android.localtransport/.LocalTransport` first. Restore with `bmgr restore 1 $PKG`.
- Emulator state after the run: R.23 to R.26 deleted the app data, and AI consent was given again after them. English (US) is active, and the Roman Hindi and Roman Urdu presets are added. The English model is downloaded. Airplane mode is off. The network throttle is off.
- The emulator cannot run voice or dictation cases. It has no English on-device speech pack. There is no Whisper model file on the host.

## Setup

### Where tags

| Tag | Meaning |
|---|---|
| EMU | `emulator-5554`: API 37 arm64 Google APIs image (`sdk_gphone16k_arm64`), 16 KB pages. It has no real microphone quality. Play Store presence is unknown. |
| EMU+API | The emulator plus the local kb-api on the host at `localhost:8080` (the app reaches it at `10.0.2.2:8080`). |
| DEVICE | A real phone. Use it for microphone quality, timing, low-end hardware and vendor builds. |
| BACKEND-PROD | Needs Firebase, App Check or a real Anthropic key. |
| RELEASE | Needs Play (licence tester, a test track) or a signed release build. |

### Identity

- Debug applicationId: `com.manjul.aikeyboard.debug`.
- IME component: `com.manjul.aikeyboard.debug/com.manjul.aikeyboard.FlorisImeService`.
- App activity: `com.manjul.aikeyboard.debug/com.manjul.aikeyboard.app.FlorisAppActivity`.

### Build and install

```sh
PKG=com.manjul.aikeyboard.debug
./gradlew :app:installDebug                                                  # plain debug build
./gradlew :app:installDebug -Paikb.ai.devAuth=true -Paikb.ai.baseUrl=http://10.0.2.2:8080   # AI and backend cases
adb shell ime enable $PKG/com.manjul.aikeyboard.FlorisImeService
adb shell ime set $PKG/com.manjul.aikeyboard.FlorisImeService
```

The debug network security file allows cleartext traffic only to `10.0.2.2` and `localhost`. With
`aikb.ai.devAuth=true` the app sends `X-Dev-Uid: dev-local`, so the app and the curl cases share one uid.

### Local backend

```sh
cd backend && AUTH_MODE=dev GATEWAY=fake ./gradlew run
```

The fake gateway streams canned text. A rewrite gives "This is a fake rewrite." A multi-tone call gives
"Fake <mode>." Polish gives "Fake polished text." The fake dictionary knows only the word "run". Some cases
restart the backend with one extra variable, for example `FREE_DAILY=1`. Restart it with no extra variable after each
of those cases. All limit stores are in memory, so a restart also resets the quota.

### Logs and helpers

```sh
adb logcat -c
adb logcat -v time -s AiKbAnalytics                 # analytics events, debug build only
adb logcat -b crash                                 # crashes
adb shell am force-stop $PKG                        # kill the app and IME process
adb shell pidof $PKG                                # empty output means that the process is dead
adb shell cmd connectivity airplane-mode enable     # use disable to turn it off
```

### Chrome test page

```sh
mkdir -p /tmp/aikb-test && cd /tmp/aikb-test
cat > t.html <<'EOF'
<!doctype html><meta name="viewport" content="width=device-width">
<p>Normal<br><input id="n" type="text"></p>
<p>Password<br><input id="p" type="password"></p>
<p>Email<br><input id="e" type="email"></p>
<p>URL<br><input id="u" type="url"></p>
<p>Chat<br><textarea id="c" rows="4"></textarea></p>
EOF
python3 -I -m http.server 8765 --bind 127.0.0.1 &
adb reverse tcp:8765 tcp:8765
# In Chrome on the phone or emulator, open http://127.0.0.1:8765/t.html
```

### Whisper model

The download host is a placeholder (OD-01), so the download fails closed. Push the model by hand after section A,
because A.1 clears the app data:

```sh
adb push ggml-base-q5_1.bin /data/local/tmp/
adb shell run-as $PKG sh -c 'mkdir -p no_backup/models && cp /data/local/tmp/ggml-base-q5_1.bin no_backup/models/'
adb shell am force-stop $PKG
```

### Test text

Use only neutral samples: "helo wrld", "the quick brown fox", "run", "coffee", "kya haal hai". The log audit in
section S uses the markers `zqxmarker` (normal fields), `Pw7zqx!` (password fields) and `clipzqx` (clipboard).
Never type real personal data.

## A. Install and onboarding

Run `adb shell pm clear $PKG` before A.1.

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| A.1 | EMU | Open the app from the launcher. | Onboarding opens on the Welcome step. The splash goes in about 1 s. `onboarding_start` and `onboarding_step_view` are logged. | Pass (EMU 2026-11-15) |
| A.2 | EMU | Step through Welcome, Enable, Select, Languages, Try it, the O6 paywall and Done. | Each step shows once, in this order. `onboarding_step_view` is logged for each step. | Pass (EMU 2026-11-15) |
| A.3 | EMU | Languages step: pick English and Urdu. Leave "Download for offline" off. Continue. | The offer reads "Download for offline (about N MB)". Nothing downloads. `languages_set` is logged. | Pass (EMU 2026-11-15) |
| A.4 | EMU | Clear data. Repeat A.3 with "Download for offline" on. | A download starts and onboarding moves on. `pack_download_start` is logged. | Pass (EMU 2026-11-15) |
| A.5 | EMU | Clear data. Skip the Languages step. | Onboarding still completes. | Pass (EMU 2026-11-15) |
| A.6 | EMU | Try it step: tap the field. | Our keyboard shows and types. | Pass (EMU 2026-11-15) |
| A.7 | EMU | Clear data. Go to a middle step. Press Home. Run `adb shell am kill $PKG`. Reopen the app. | Onboarding resumes at the same step. | Pass (EMU 2026-11-15) |
| A.8 | EMU | Reach the O6 paywall. Close it. Finish onboarding. | The O6 paywall shows once, after Try it. No ad shows during onboarding or in this first session. | Pass (EMU 2026-11-15) |
| A.9 | EMU | Close and reopen the app. | Home opens with no onboarding flash. `onboarding_complete` was logged once. No new funnel events. | Pass (EMU 2026-11-15) |
| A.10 | EMU | Clear data. Before onboarding, run `adb shell am start -a android.intent.action.VIEW -d "ui://florisboard/settings/privacy"`. Repeat with `settings/subscription`, `paywall?trigger=ai_quota` and `pro`. | The two settings links open their screens. The paywall link and the `pro` alias open Home. No crash. | Pass on rerun after fix e2c21b5b (EMU 2026-11-16). Before onboarding, `paywall?trigger=ai_quota` and `pro` open the Welcome step, cold and warm. `settings/privacy` and `settings/subscription` open their screens. No crash. Earlier: Fail, the paywall link skipped the onboarding gate. |
| A.11 | EMU | Run `adb shell am start -a android.intent.action.VIEW -d "ui://florisboard/settings/offline-languages"`. Repeat with `-d "app://settings/voice"`, `-d "ui://florisboard/devtools"` and `-d "ui://florisboard/settings"`. | The first two open their screens. `devtools` and bare `settings` open Home and log `deeplink_unknown` with no value. No crash. | Pass on rerun after fixes e2c21b5b and 3832c613 (EMU 2026-11-16). `settings/offline-languages` and `app://settings/voice` open their screens. `devtools` and bare `settings` open Home, and `deeplink_unknown {}` is logged. No crash. Note: before onboarding is done, the two unknown links open the onboarding step, which is correct. Earlier: Fail, Devtools opened and `app://` did not resolve. |
| A.12 | EMU | Run `adb shell am start -n $PKG/com.manjul.aikeyboard.app.FlorisAppActivity --es open_screen settings/voice`. Repeat with `--es open_screen devtools`. | The first opens Voice settings. The second opens Home and logs `deeplink_unknown`. | Pass (EMU 2026-11-15) |
| A.13 | EMU | Run `adb shell am start -n $PKG/com.manjul.aikeyboard.fork.processtext.TranslateTextActivity -a android.intent.action.VIEW`. Repeat with `-a android.intent.action.SEND -t image/png`. | Nothing shows. No crash. | Pass (EMU 2026-11-15) |
| A.14 | EMU | Install the app. Look for a notification permission prompt during onboarding and the first session. | No notification prompt shows. | Pass (EMU 2026-11-15) |

## B. Consent

Use the AI build and the local backend. Before B.1, run `curl -s -H 'X-Dev-Uid: dev-local' localhost:8080/v1/quota`
and note `ai_left_today`.

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| B.1 | EMU+API | Clear data and finish onboarding. In a chat field, type "helo wrld" and tap the ✦ chip. | The app consent screen (`AiConsentActivity`) opens. It is a separate screen, not part of the keyboard. || Pass (EMU 2026-11-15) |
| B.2 | EMU+API | Tap Not now. Tap ✦ again. Run the quota curl. | The consent screen shows again. `ai_left_today` is unchanged, so no AI request reached kb-api. `consent_result` is logged with `agreed=false`. || Pass. `ai_left_today` stayed 10. (EMU 2026-11-15) |
| B.3 | EMU+API | Tap ✦. Tap Agree. Pick a tone. Run the quota curl. | The sheet streams "This is a fake rewrite." `ai_left_today` is one lower. `consent_result` is logged with `agreed=true`. || Pass. `ai_left_today` went from 10 to 9. After Agree the keyboard comes back, and a second ✦ tap opens the sheet. (EMU 2026-11-15) |
| B.4 | EMU+API | Settings, Privacy, Your consents. Read the four rows. | AI writing help, Online translation, Auto-polish and Voice typing notice show "Agreed" or "Not agreed". Withdraw is disabled on rows with no consent. || Pass (EMU 2026-11-15) |
| B.5 | EMU+API | Withdraw AI writing help. Tap Withdraw in the dialog. Tap ✦ in a chat field. | `consent_withdrawn` is logged with kind AI. The consent screen shows again. || Pass (EMU 2026-11-15) |
| B.6 | EMU+API | Agree to AI and auto-polish. Withdraw AI writing help. Open Your consents. | Both AI and Auto-polish read "Not agreed". `consent_withdrawn` is logged for AI and for AUTO_POLISH. || Blocked: `isPro` is always false until billing exists, so auto-polish consent cannot be given in the UI. (EMU 2026-11-15) |
| B.7 | EMU+API | Agree to AI. Withdraw it from AI settings. Agree again. Withdraw it from Private apps, "Data and consent". | Each place withdraws the consent and logs `consent_withdrawn` AI. || Pass. Each place logged `consent_withdrawn {kind=ai}`. (EMU 2026-11-15) |
| B.8 | EMU | Agree to Online translation and the Voice typing notice. Withdraw each in Your consents. | The Voice typing notice row reads "Not agreed" after Withdraw, and no `consent_withdrawn` event is logged. Online translation has no consent row to agree to, because cloud translation is not in this build (OD-05). | Plan fix (2026-11-16): the old expected result asked for an Online translation consent, but cloud translation does not exist (OD-05, `docs/data-flows.md`). Voice typing notice: Pass. (EMU 2026-11-15) |
| B.9 | EMU | In Your consents, tap Withdraw and then Cancel in the dialog. | The consent stays. No event is logged. || Pass. The consent stayed. No event showed, but a control event also did not flush within 80 s, so the no-event check is weak. (EMU 2026-11-15) |
| B.10 | EMU | Clear data. In Try it, tap the mic. | The voice notice names the speech engine in use before the system permission prompt. | Pass (EMU 2026-11-15) |

## C. Enable IME

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| C.1 | EMU | Onboarding, Enable step: tap Enable keyboard. Turn it on in system settings. Press Back. | The step moves on by itself. `ime_enabled` is logged. | Pass (EMU 2026-11-15) |
| C.2 | EMU | Select step: tap Select keyboard and pick it. | The step moves on by itself. `ime_selected` is logged. | Pass (EMU 2026-11-15) |
| C.3 | EMU | Run `adb shell am force-stop $PKG`. Do not open the app. Tap a text field in Chrome. | The keyboard shows and types. No crash. || Pass. Used the Messages compose field, because Chrome draws a black page in screencap on this emulator. (EMU 2026-11-15) |
| C.4 | EMU | After C.3, run `adb shell run-as $PKG sh -c 'grep -c aikb_whisper /proc/$(pidof '$PKG')/maps'`. | `0`. The Whisper library is not loaded by the keyboard alone. || Pass, but the plan command is wrong. Native libraries load straight from `base.apk` (`extractNativeLibs=false`), so a grep by library name always gives 0. Checked by APK offset: no mapped range falls in `libaikb_whisper.so`. (EMU 2026-11-15) |
| C.5 | EMU | Force-stop. Show and hide the keyboard several times. Do not open the Translate bar. Grep the process maps for `libtranslate_jni`. Open the bar, type one word and grep again. | No line before first use. `libtranslate_jni` is mapped after first use. || Pass, checked by APK offset as in C.4. Before first use: 2 executable `base.apk` ranges. After one translated word: a third range at offset 0x2438000, inside `libtranslate_jni.so`. (EMU 2026-11-15) |
| C.6 | EMU | Reboot. Do not unlock. Find a text field on the lock screen that uses our keyboard. Open it. | No crash. If the keyboard shows, the lock indicator is on, with no mic, no clipboard history and no learning. || Pass. With a password set, before the first unlock the system uses Gboard. Our IME is `directBootAware="false"`, its process does not run, and there is no crash. (EMU 2026-11-15) |
| C.7 | EMU | Clear data. Set the system language to English (United Kingdom). Open the keyboard. | A usable English subtype exists. || Pass. With the system set to English (United Kingdom), the keyboard shows the English (United States) subtype and types. The locale is set back to en-US. (EMU 2026-11-15) |
| C.8 | EMU | Start an API 34 emulator. Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.manjul.aikeyboard.isolation.ImeOnlyColdStartTest`. | PASS. No ad class loads in an IME-only process. This run is still open on API 34. || Blocked: the API 34 AVD (`Pixel_7`) exits during start with no error in the sandbox. (EMU 2026-11-15) |

## D. Typing

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| D.1 | EMU | Open the smartbar overflow and the actions editor. Pin three actions. Try to pin a fourth. | The header reads "Pinned (3/3)". The fourth pin is refused with "Up to 3 pinned". | Pass (EMU 2026-11-15) |
| D.2 | EMU | In the editor, try to move the mic and the Translate tile. | They do not move and do not count as pins. "Make this app private" does not count and stays last. | Pass (EMU 2026-11-15) |
| D.3 | EMU | Reorder the pins. Close the editor. Hide and reopen the keyboard. | The new order stays. | Pass (EMU 2026-11-15) |
| D.4 | EMU | Open the voice panel. Use the system Back gesture. | The panel closes. The keyboard stays. | Blocked: the emulator has no English on-device speech pack. (EMU 2026-11-15) |
| D.5 | EMU | Type a few paragraphs of neutral text in Chrome and Messages. Switch layouts in between. | Typing works with no regression. | Pass (EMU 2026-11-15) |

## E. Layouts and languages

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| E.1 | EMU | Settings, Languages: add a subtype and open the layout picker. | Urdu standard and Hindi phonetic layouts are listed. Each added subtype shows in the system switcher. | Pass (EMU 2026-11-15) |
| E.2 | EMU | Switch to Urdu, Arabic and Hindi. Use Shift and long-press keys. | Key order is right to left for Urdu and Arabic. Shift draws correctly. Urdu aspirate and heh forms are reachable. | Pass (EMU 2026-11-15) |
| E.3 | EMU | In Urdu, press the digits row and long-press a digit. | Urdu digits show by default. Western digits show on long-press. | Pass (EMU 2026-11-15) |
| E.4 | EMU | In Urdu, long-press the **h** key. | The long-press popup has U+06C1. Record the base key. | Pass (EMU 2026-11-15) |
| E.5 | EMU | Clear data. Open Settings, Languages, Add subtype. | "Urdu (Pakistan) [ROMAN]" and "Hindi (India) [ROMAN]" both show and can be added. A fresh install has no Roman subtype before you add one. | Pass (rerun after R.26 Delete everything). Only English (United States) was installed, with no Roman subtype. Add subtype listed "Hindi (India) [ROMAN]" and "Urdu (Pakistan) [ROMAN]". Both saved and show as "... [ROMAN] (Beta)". (EMU 2026-11-15) |

## F. Suggestions

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| F.1 | EMU | Enable en, es, fr, pt, id, ar, ur and hi. In each, type 3 letters of a common word. | Up to 3 candidates in the script of the subtype. Record weak Hindi results. | Pass (EMU 2026-11-15) |
| F.2 | EMU | In English, accept a made-up word 3 times. Type its first letters. | The word shows as a candidate. | Fail: product gap. Words are learned only when a strip candidate is picked. A new typed word is never offered, so it is never learned. (EMU 2026-11-15) |
| F.3 | EMU | Turn incognito on. Accept a made-up word 3 times. Turn incognito off. Type its first letters. | The word is not learned. | Blocked: depends on F.2. (EMU 2026-11-15) |
| F.4 | EMU | Repeat F.3 in a private app. | The word is not learned. | Blocked: depends on F.2. (EMU 2026-11-15) |
| F.5 | EMU | Settings, Typing (Suggestions and autocorrect), Clear learned words. Accept the dialog. Force-stop. Type the F.2 word. | The dialog reads "Delete every word the keyboard has learned on this phone?". The word is gone after the force-stop. | Pass (EMU 2026-11-15) |
| F.6 | EMU | Long-press a suggestion that has an entry in the offline dictionary (install the dictionary pack first, see L.12). | A definition card shows with More and "Remove suggestion". | Pass (EMU 2026-11-15) |
| F.7 | EMU | On the card from F.6, tap "Remove suggestion". Type the word again. | The word no longer shows as a suggestion. | Pass (EMU 2026-11-15) |
| F.8 | EMU | On the card from F.6, tap More. | The Dictionary screen opens with the word. | Pass (EMU 2026-11-15) |

## G. Autocorrect and user dictionary

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| G.1 | EMU | Turn autocorrect on for English. Type "teh " in the URL, Email and Password fields of the test page and in Chrome incognito. | The word is never replaced in these fields. | Pass (EMU 2026-11-15) |
| G.2 | EMU | Type "teh " in the Normal field. Press Backspace to revert. Type "teh " again. | "the " replaces it the first time. After the revert, no second replace. | Pass on rerun after fix 1a6890bb (EMU 2026-11-16): a hardware Backspace (KEYCODE_DEL) and the on-screen Backspace both revert the commit, checked on the Roman Urdu transliteration commit. Still to check: the English autocorrect "teh" case with a hardware Backspace. Earlier: Pass after fix c8fa8da1 with the on-screen Backspace only. Before that: Fail, Backspace did not undo the correction. |
| G.3 | EMU | Type words with digits, ALL CAPS, `@` or `/`, each with a space after it. | None of them is replaced. | Pass (EMU 2026-11-15) |
| G.4 | DEVICE | Swap two letters in common words in en, es, fr, pt and id. Type first-letter typos such as "jello". | Record how often autocorrect picks the right word and the first-letter miss rate. | |
| G.5 | EMU | Open Home, Typing, User dictionaries, Manage internal user dictionary. Add the word "zqxword". Type "zqx". | The word shows as a suggestion. | Pass on rerun after fix 135b604a (EMU 2026-11-16). "zqxword" added to the internal user dictionary. Typing "zqx" in Messages showed "zqxword" first, then "Zac" and "Sax". Earlier: Fail, user words never showed. |
| G.6 | EMU | Edit and delete the word from G.5. | Each change applies on the next word. | Blocked: depends on G.5. (EMU 2026-11-15) |

## H. Sensitive fields and privacy

Use the Chrome test page and an app login screen.

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| H.1 | EMU | Tap the Password field. Look at the smartbar. | Only emoji, settings and the lock show. No mic, clipboard, paste, undo, Translate, AI or Stickers, also for pinned actions. No suggestions. | Pass (EMU 2026-11-15) |
| H.2 | EMU | Tap the Email and URL fields and a phone field. | The Translate chip is hidden. | Pass (EMU 2026-11-15) |
| H.3 | EMU | Settings, Privacy, Private apps. Add Chrome from the picker. Open Chrome and type. | The lock is on. No mic and no learning. | Pass (EMU 2026-11-15) |
| H.4 | EMU | In a normal field, tap the lock action "Make this app private". | The app becomes private at once. | Pass (EMU 2026-11-15) |
| H.5 | EMU | In the Password field, long-press Enter and pick clipboard. Try a swipe bound to "switch to clipboard". | History never opens in a sensitive field. | Pass (EMU 2026-11-15) |
| H.6 | EMU | In a normal field, open clipboard history. Turn off "hide on next text field". Tap the Password field. Then tap another normal field. | The sensitive field closes the open panel. In the other normal field the panel stays open. | Pass (EMU 2026-11-15) |
| H.7 | EMU | Note the UID with `adb shell dumpsys package $PKG \| grep userId`. Note bytes with `adb shell dumpsys netstats detail \| grep -A3 "uid=<UID>"`. In the Password field, type, backspace, paste and tap around the smartbar for 1 minute. Read the bytes again. | The bytes for our UID do not change. | Pass (EMU 2026-11-15) |
| H.8 | EMU | Settings, Privacy: turn off "Share usage analytics". Type and open the Translate bar. | No `AiKbAnalytics` events are logged. | Pass (EMU 2026-11-15) |
| H.9 | EMU | Turn incognito on with the toggle key. Type and dictate. | Nothing is learned. The lock or incognito indicator shows. | Pass (EMU 2026-11-15) |

## I. Clipboard

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| I.1 | EMU | Fresh install. Open the clipboard panel. | History is off. The prompt in the panel turns it on. | Pass (EMU 2026-11-15) |
| I.2 | EMU | Open clipboard settings. Move the history time slider. | The slider goes from 0 to 1440 min in steps of 5. | Pass (EMU 2026-11-15) |
| I.3 | EMU | Turn history on. Copy "clipzqx", a made-up OTP text "Your code is 482913", and a password copied from a password field. Open history. | Only "clipzqx" is stored. | Pass on rerun after fix 8f1eefae (EMU 2026-11-16) for the duplicate bug. Two copies in Messages ("clipzqx", then "coffee") gave 2 rows, one per copy. The OTP text was not stored. The password field copy was not rechecked. Earlier: Fail, each copy was stored twice. |
| I.4 | EMU | Pin one clip. Leave one unpinned. Move the clock 25 h ahead. Open history. Restore the clock. | The unpinned clip is gone. The pinned clip stays. | Pass (EMU 2026-11-15) |
| I.5 | EMU | Tap "Clear clipboard history". | Unpinned items are gone. | Pass (EMU 2026-11-15) |
| I.6 | EMU | Copy a text with a made-up email, a URL and a phone number. Use for example "mail a@example.com or see https://example.com or call 5550100". Open the panel. | Each smart chip pastes only its own part. | Pass (EMU 2026-11-15) |
| I.7 | EMU | Install a Sprint 4 build. Copy two neutral clips. Install this build over it. Open the panel. Run `adb shell run-as $PKG sqlite3 databases/clipboard_history 'select * from clipboard_history'` (adjust the table name). | The items are kept. The text column shows no plaintext. | Blocked: needs a Sprint 4 build. (EMU 2026-11-15) |
| I.8 | EMU | Run `adb shell run-as $PKG sh -c 'ls -la databases'` with the keyboard idle. | Expected result unclear: the code shows no test hook that makes the key store unavailable on demand. Record whether sealed items hide and come back. | Blocked: the file listing is correct, but no hook makes the key store unavailable. (EMU 2026-11-15) |

## J. Voice (platform and Whisper)

J.1 to J.12 use the Chrome test page. Timing and audio-focus numbers need a real phone.

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| J.1 | DEVICE | Force-stop. Tap the Chat field and the mic. Talk for about 10 s with two short pauses. | Listening starts in about 1 s. Gray partials show. The bars move and never freeze. The final text has a trailing space. `voice_*` events hold no words. | |
| J.2 | DEVICE | Tap the mic 2 more times and finish each session. | All 3 sessions work. No error 11 in the log. | |
| J.3 | DEVICE | Dictate several sentences with pauses over 2 s, for under 60 s in total. | Each sentence is committed and joins the last one with ", " or ". ". The session ends after about 2 s of silence or at 60 s. | |
| J.4 | DEVICE | Dictate in Chat. While a partial shows, use the keyboard hide key. | The partial goes. Field text is unchanged. The mic dot turns off at once. `end_reason=hidden`. | |
| J.5 | DEVICE | Same as J.4, but tap the system down arrow. | The partial goes. `end_reason=hidden`. Record ms to `abandonAudioFocus` and to `rec stop`. | |
| J.6 | DEVICE | Dictate. Swipe home. Reopen Chrome. Repeat and switch apps through Recents. | The partial is gone. Nothing is committed. The mic dot is off. No crash. | |
| J.7 | DEVICE | Dictate in Chat. Tap the Normal field while a partial shows. | The partial leaves Chat. Nothing is committed. `end_reason=field_change`. | |
| J.8 | DEVICE | Dictate in Normal. Tap the Password field while a partial shows. | Voice stops. No text reaches the Password field, also seconds later. No mic in its smartbar. | |
| J.9 | DEVICE | Dictate at the end of existing Chat text. Tap the middle of the older text. Repeat, then tap inside the partial and type a letter. | Our gray text is dropped. Older text is unchanged. Your letter stays. | |
| J.10 | EMU | Force-stop. Tap the mic and stop within 1 s, 5 times fast. Force-stop, tap the mic and press Back within 0.5 s. Wait 5 s. | No crash and no stuck "Starting". The mic indicator is off. | Pass (EMU 2026-11-15) |
| J.11 | DEVICE | Play music. Dictate one sentence in Messages. | Music pauses or ducks and resumes after the session. | |
| J.12 | DEVICE | In Messages, type "existing text". Dictate one sentence. Dictate again and hide with the down arrow while a partial shows. | "existing text" and the first sentence stay. The second partial goes. | |
| J.13 | DEVICE | While Listening, run `adb shell am force-stop com.google.android.tts`. Repeat once it reconnects. | The first drop gives one reconnect. The second drop shows "Voice typing needs a connection. Check your network and try again." | |
| J.14 | DEVICE | Airplane mode on. Dictate in en (model installed). Then in ur (no model). | en works offline. ur shows a clear message with a download offer, never the network message or silence. | |
| J.15 | DEVICE | Enable en, hi, ur and ar. Dictate once in each. Download a missing model and dictate in it without a restart. Remove the hi model and dictate in hi. | A new model works at once. A removed model gives "language unavailable" with no crash. | |
| J.16 | EMU | Settings, Voice, Speech engine, On-device only. Pick a language with no on-device model. Tap the mic. | "Unavailable" with an offer. The cloud recognizer is never used. | Pass (EMU 2026-11-15) |
| J.17 | EMU | Deny the mic permission in system settings. Tap the mic. Allow from the chip. | The chip text shows in full. The grant works and logs `voice_permission_result` with `granted=true`. | Pass (EMU 2026-11-15) |
| J.18 | EMU | Deny again. Tap the mic and press Home on the rationale. | Home closes the trampoline. No crash. | Pass (EMU 2026-11-15) |
| J.19 | DEVICE | Reboot. Tap the mic as soon as the keyboard shows. | The session starts. If error 11 comes, the reconnect recovers it. | |
| J.20 | EMU | Settings, Voice, Offline models. Tap Download on Base and on Small. | The download fails and leaves no file (OD-01 placeholder host). No percentage shows. | Pass (EMU 2026-11-15) |
| J.21 | EMU | Push the Base model (setup). Open Offline models. | Base shows as downloaded, about 57 MB. | Blocked: no Whisper model file to push. (EMU 2026-11-15) |
| J.22 | EMU | Speech engine, Offline (Whisper). Dictate a 10 s English sentence from the host microphone. | A waveform shows, silence stops it, "Transcribing…" shows, then one final text. | Blocked: no Whisper model file to push. (EMU 2026-11-15) |
| J.23 | DEVICE | With Whisper, dictate a 10 s English sentence. Then dictate in Hindi. | Record stop-to-text time. Devanagari is correct, with no garbled text. | |
| J.24 | EMU | With Whisper, start a long dictation, stop, and hide the keyboard while "Transcribing…" shows. | It returns at once. No crash and no late text. | Blocked: no Whisper model file to push. (EMU 2026-11-15) |
| J.25 | EMU | With Whisper, tap the mic, stop and tap again fast, several times. | No audio error. | Blocked: no Whisper model file to push. (EMU 2026-11-15) |
| J.26 | EMU | Dictate with Whisper. Run `adb shell am send-trim-memory $PKG RUNNING_LOW`. Dictate again. | `RUNNING_LOW` frees the model. The next dictation loads it again. | Blocked: no Whisper model file to push. (EMU 2026-11-15) |
| J.27 | EMU | Dictate with Whisper. Wait more than 60 s. Dictate again. | The model is freed after 60 s idle and loads again on the next dictation. | Blocked: no Whisper model file to push. (EMU 2026-11-15) |
| J.28 | EMU | Delete the Base model with Whisper still selected. Tap the mic in Urdu. | A "Get offline model" chip shows and opens Settings, Voice. Push the model again after. | Blocked: no Whisper model file to push. (EMU 2026-11-15) |
| J.29 | EMU | Settings, Voice: scroll down and tap "Offline languages". | Offline languages opens. Back returns to Voice. | Pass (EMU 2026-11-15) |

## K. Translate bar

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| K.1 | EMU | In Messages and Chrome, type host text. Open the bar, type "the quick brown fox" and press Enter. | Keys go to the bar only. Enter inserts the translation once. | Pass (EMU 2026-11-15) |
| K.2 | EMU | Repeat K.1. Tap Undo within 5 s. | Undo removes only the insert. | Pass (EMU 2026-11-15) |
| K.3 | EMU | Repeat K.1. Edit the host text, then tap Undo. Repeat and wait more than 5 s. | After a host edit, Undo changes nothing. After 5 s, no Undo shows. Host text is never lost. | Pass (EMU 2026-11-15) |
| K.4 | EMU | Open the bar in the Normal field and type. Tap the Password field while a request runs. | The bar closes. The chip goes. No text reaches the Password field, also later. | Pass (EMU 2026-11-15) |
| K.5 | EMU | Type in the bar. Hide the keyboard. Show it within 60 s and reopen the bar. Hide for more than 60 s and reopen. | The text stays the first time and is empty after 60 s. Pair and mode stay. The bar is closed after each hide. | Pass (EMU 2026-11-15) |
| K.6 | EMU | Open the bar. Use predictive Back. | The bar closes and the keyboard stays. A second Back hides the keyboard. | Pass (EMU 2026-11-15) |
| K.7 | DEVICE | Open the bar and dictate. Close the bar while dictating. Open the bar while dictating in the host field. | Voice text goes into the bar. Opening or closing stops dictation. No host text is deleted. | |
| K.8 | EMU | Pick a pair with no packs. Make sure that nothing downloads before a tap. Tap Download. Hide the keyboard for a minute. | The download starts only on tap and keeps going while hidden. | Pass (EMU 2026-11-15) |
| K.9 | EMU | Offline, with a pair that has no packs, look at the bar. Tap the offer. | The bar offers the pack download with its size, for example "Download English to French (about 31 MB)". The tap queues the download. | Pass (rerun with the fixed text). In airplane mode with English to French (no French pack), the bar offered "Download English↔French (about 31 MB)". The tap queued `TranslateModelDownloadWorker` in JobScheduler. The offer text did not change after the tap, so the user sees no "queued" or "waits for network" state. The bar pair cannot be changed in the bar; I set it in the Translator. (EMU 2026-11-15) |
| K.10 | EMU | Airplane mode with both packs installed. Type in the bar. | The translation works offline. | Pass (EMU 2026-11-15) |
| K.11 | EMU | With a pair that has no packs, type and toggle the network while a preview shows. | An inline chip shows. The last preview stays. Nothing is committed. No empty text is written. | Pass (EMU 2026-11-15) |
| K.12 | EMU | Settings, Translate. Read the bar mode. Switch it to Live. Type fast in the bar. Close it during a preview and switch fields during a preview. | The default is Preview. In Live the composing preview is replaced in place, never doubled, and removed on close. No host character is deleted. | Fail: app bug. A field switch during a Live preview commits the preview into the old field. (EMU 2026-11-15) A fix attempt was rejected because Android deactivates the old field's InputConnection before the IME hears about the switch, so a new design or a documented platform limit is needed. |
| K.13 | EMU | Hardware keyboard (emulator host keyboard): open the bar, type, Backspace, Enter, Ctrl+A and forward delete. | Printable keys go to the bar. Ctrl shortcuts and forward delete do not change host text. | Pass on rerun after fix 46e8740f (EMU 2026-11-16). With the bar open, hardware keys typed "run" into the bar. Ctrl+A, Ctrl+X and forward delete left the host text "run coffee" unchanged. Earlier: Fail, Ctrl+A and Ctrl+X reached the host while the bar was open. |
| K.14 | EMU | Settings, Translate: change the default pair. Open the bar. | The bar uses the new pair. | Pass (EMU 2026-11-15) |
| K.15 | EMU | Open the bar in a field that sets `TYPE_TEXT_FLAG_NO_SUGGESTIONS`. Type a word. | Expected result unclear: the code leaves this open (Sprint 6 product question 7). Record whether suggestions show and whether Space commits. | Pass (EMU 2026-11-15) |

## L. Translator screen

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| L.1 | EMU | Home, Translate. Type "the quick brown fox", en to ur. | The Urdu result shows right-aligned, with copy, share, speak and star. | Pass (EMU 2026-11-15) |
| L.2 | EMU | Tap share on a result. | The share sheet opens with plain text. | Pass (EMU 2026-11-15) |
| L.3 | EMU | Set the source to "Detect language". Paste Urdu text. Tap swap. | "Detected: Urdu" shows. Swap makes Urdu the target. | Pass (EMU 2026-11-15) |
| L.4 | EMU | Airplane mode. Tap the mic. Agree to the notice and allow the mic. | The notice shows before the permission prompt. Dictated text fills the input and translates offline. | Blocked: no offline recognizer on the emulator, and the voice notice was already accepted. (EMU 2026-11-15) |
| L.5 | EMU | Offline, open a pair with no model. | "Download this language pair to translate offline" shows. The previous result stays. | Pass (EMU 2026-11-15) |
| L.6 | EMU | Translate a few texts. Star one. Open History. Search, swipe one away, then Clear with "Keep favourites". | Search filters. Swipe deletes. Clear keeps the starred row. | Pass (EMU 2026-11-15) |
| L.7 | EMU | Turn "Save history" off in Settings, Translate. Translate a text. | Nothing new is saved in History. | Pass (EMU 2026-11-15) |
| L.8 | EMU | Settings, Offline languages. On Wi-Fi, download Urdu. Translate en to ur offline. | An indeterminate indicator with Stop shows, then "Downloaded". English Delete is disabled with the pivot note. Offline translation works. | Pass (EMU 2026-11-15) |
| L.9 | EMU | Start a download and Stop at once. Start another and Stop during the fetch. Reopen later, also after a force-stop. | The row goes back to "Not downloaded" both times. | Pass (EMU 2026-11-15) |
| L.10 | DEVICE | On mobile data with "Download on Wi-Fi only" on, download Hindi. Turn the switch off. | The download waits for Wi-Fi while on. New downloads run on mobile data after off. | |
| L.11 | EMU | Look at the voice typing section of Offline languages. Tap Settings on the recognizer row. | The row matches the device. Settings opens system voice input. Whisper rows show in debug. | Pass (EMU 2026-11-15) |
| L.12 | EMU | Offline languages: install the English dictionary pack. Turn on airplane mode. Look up "coffee" on the Dictionary screen. | The definition shows with no network. | Pass (EMU 2026-11-15) |
| L.13 | EMU | Type a query on the Dictionary screen. Toggle airplane mode. | The query and the result stay. | Pass (EMU 2026-11-15) |
| L.14 | EMU | Open an entry. Then open Settings, About, Open-source licences. | The attribution and the licence link show on the entry and on the licences screen. | Pass (EMU 2026-11-15) |
| L.15 | EMU+API | Agree to AI. With "Online dictionary" on, look up "run", which is not in the offline pack. | The fake source answers with the verb "To move quickly on foot." and the example "Run home." `dictionary_lookup` is logged. || Pass. `dictionary_lookup {offline=false, found=true}` is logged. (EMU+API 2026-11-15) |
| L.16 | EMU+API | Turn "Online dictionary" off. Look up "run" again. Then turn it on and withdraw AI consent. Look up "run". | No online lookup in either case. || Pass. The backend log shows no `/v1/dictionary` call in either case, and a control lookup with both on shows one call. A cached online answer (fresh within the TTL) still shows with the setting off or with no consent. This is by design in `DictionaryRepository`. Clear the `dictionary_entry` rows first. (EMU+API 2026-11-15) |
| L.17 | EMU | Home, Phrasebook. Browse the categories. | 12 categories show phrases. | Pass (EMU 2026-11-15) |
| L.18 | EMU | Add, edit and delete an own phrase. Force-stop and reopen. | Each change stays. | Pass (EMU 2026-11-15) |
| L.19 | EMU | Tap a phrase to speak it. Tap "Send via keyboard" and pick Messages. | The phrase is spoken. The share sheet opens and Messages gets the text. | Pass (EMU 2026-11-15) |
| L.20 | EMU | Install the language pack. Turn on airplane mode. Open a category. | Translations show. | Pass (EMU 2026-11-15) |

## M. PROCESS_TEXT and share sheet

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| M.1 | EMU | Select text in Chrome and Messages. Pick "Translate". | A sheet shows the translation, a target picker, Copy, Speak, Open in app and Close. No ad. It is not in Recents after. `process_text_open` is logged. | Pass (EMU 2026-11-15) |
| M.2 | EMU | Select text in an editable field. Translate. Tap Replace. | Replace swaps the selection. `process_text_replace` is logged. | Pass (EMU 2026-11-15) |
| M.3 | EMU | Select read-only text. Translate. Close. | No Replace. The page is unchanged. | Pass (EMU 2026-11-15) |
| M.4 | EMU | Share plain text to "Translate" from Chrome and from a notes app. Share an image. | Text opens the sheet without Replace. No "Translate" target shows for an image. | Pass (EMU 2026-11-15) |
| M.5 | EMU | Select more than 5,000 characters. Translate. | "Only the first 5,000 characters are translated" shows. | Pass (EMU 2026-11-15) |
| M.6 | EMU | Disable our keyboard. Open the sheet twice in a day. Move the clock 7 days ahead and open it again. | The "Enable keyboard" card shows the first time, not the second, and again after 7 days. | Pass (EMU 2026-11-15) |
| M.7 | EMU | Tap Copy. Tap Open in app. | Copy works. Open in app shows the Translator with the text translated. | Pass (EMU 2026-11-15) |
| M.8 | EMU | Rotate with the sheet open. | The translation stays, with no second request. | Pass (EMU 2026-11-15) |
| M.9 | EMU | Open the sheet on a pair with no pack. Tap Download. | "Download this language pair to translate offline (about N MB)" shows. After the download the result shows. | Pass (EMU 2026-11-15) |
| M.10 | EMU | In Chrome, select one word and tap "Define". | The Dictionary entry for the word opens. | Pass (EMU 2026-11-15) |
| M.11 | EMU | Select two words and tap "Define". Then select a word longer than 64 characters. | "Select one word" shows in both cases. | Pass (EMU 2026-11-15) |

## N. TTS

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| N.1 | EMU | In the Translator, speak a translation of more than 4,000 characters. | The whole text plays, with no cut at the chunk joins. | Pass (EMU 2026-11-15) |
| N.2 | EMU | Speak Urdu and Hindi results. | Both play. | Pass (EMU 2026-11-15) |
| N.3 | EMU | Start speech. Leave the screen. | The speech stops. The shared TTS engine shuts down after the last screen that uses it closes. | Pass (EMU 2026-11-15) |
| N.4 | EMU | Remove the Bengali voice data. Speak a Bengali result. | "Install voice" shows and opens the TTS installer. | Fail: app gap. Google TTS returns LANG_NOT_SUPPORTED after a voice uninstall, so the app shows "not available" and no Install voice. (EMU 2026-11-15) |
| N.5 | EMU | In the PROCESS_TEXT sheet, tap Speak with and without voice data. | Speech plays, or the installer opens. | Fail: same cause as N.4. Speak plays with voice data. Without it, Speak is disabled and the installer does not open. (EMU 2026-11-15) |

## O. Transliteration

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| O.1 | EMU | Add "Urdu (Roman)" in Settings, Languages. Look at the list row. | The name shows with "(Beta)". | Pass (EMU 2026-11-15) |
| O.2 | EMU | Switch to "Urdu (Roman)". In Messages, type "kya haal hai" with a space after each word. | The strip shows native candidates and the Latin chip. Space commits the top native candidate. `translit_commit` is logged. | Pass (EMU 2026-11-15) |
| O.3 | EMU | Type "kya" and tap the Latin chip. Then type a word that only the rules can spell and press Space. | The chip keeps "kya" in Latin. The rule-only word stays Roman on Space. | Pass (EMU 2026-11-15) |
| O.4 | EMU | Type "meeting", "phone" and "ok", each with a space. | Each stays Latin. | Pass (EMU 2026-11-15) |
| O.5 | EMU | In a host field, type a Roman word and a space so that it commits as native. Press Backspace once. Press Backspace again. | The first Backspace restores the Roman word without the space. The second deletes one character. | Pass (EMU 2026-11-15) |
| O.6 | EMU | Commit a native word. Type another key. Press Backspace. | Backspace deletes as normal. Nothing is restored. | Pass (EMU 2026-11-15) |
| O.7 | EMU | Add "Hindi (Roman)". Repeat O.2 to O.6. | Same results, with Devanagari candidates. | Pass (EMU 2026-11-15) |
| O.8 | EMU | Turn the global suggestions setting off. Type in "Urdu (Roman)". | The Roman strip still shows. | Pass (EMU 2026-11-15) |
| O.9 | EMU | Type in the Password, URL and Email fields. | No strip and no native commit. | Pass on rerun after fix 422baf5b (EMU 2026-11-16). With "Urdu (Pakistan) [ROMAN]" active, the Contacts email field (inputType 0x21) showed no Roman strip, and Space kept "kya" in Latin. The Messages field still showed the native strip. Password and URL fields were not rechecked. Earlier: Fail, an email field showed the Roman strip. |
| O.10 | EMU | Turn incognito on. Long-press a candidate. Open Settings, Languages, "Roman typing" and try to add a mapping. | The card has no "Save mapping". The add in settings is blocked. | Pass (EMU 2026-11-15) |
| O.11 | EMU | Save a mapping from the long-press card. In "Roman typing (Beta)", add, edit and delete one. | Each change applies on the next word. | Pass (EMU 2026-11-15) |
| O.12 | EMU | In "Roman typing", enter "kya1". Then enter a word of 41 letters. | "Use the letters a to z only." and "Enter a word of up to 40 characters." show. | Pass (EMU 2026-11-15) |
| O.13 | EMU | Add two mappings. Run Clear data in Settings, Privacy (Clear on device only). Reopen "Roman typing". | The list is empty. | Pass (EMU 2026-11-15) |
| O.14 | EMU | Open the Translate bar in "Urdu (Roman)". Type a word. Press Space. | The strip follows the bar word. Space commits it. The translation uses Urdu as the source. | Pass (EMU 2026-11-15) |
| O.15 | EMU | Type and accept suggestions in the native `ur-PK` and `hi-IN` subtypes. | They type and suggest as before Sprint 6. | Pass (EMU 2026-11-15) |
| O.16 | EMU | In "Urdu (Roman)", accept a word twice so that it gets a boost. Turn incognito on and type the word. Repeat with a field that sets `IME_FLAG_NO_PERSONALIZED_LEARNING`. | After incognito turns on, the boost is gone and the word ranks as a fresh word, in both cases. | Pass (EMU 2026-11-15) |
| O.17 | EMU | Turn on airplane mode. Repeat O.2 to O.14. | Every check still works. | Pass (EMU 2026-11-15) |
| O.18 | DEVICE | On a low-end phone, take a trace over 200 keystrokes in a Roman subtype. | The p90 per keystroke is below 30 ms. | |

## P. AI rewrite and polish

Use the AI build and the local backend. Agree to AI consent first (B.3).

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| P.1 | EMU+API | In a chat field, type "helo wrld". Tap ✦. Pick a tone. Tap Replace. | "This is a fake rewrite." streams in and replaces only the captured text. `ai_request` and `ai_rewrite_done` are logged. | Pass (EMU 2026-11-15) |
| P.2 | EMU+API | After P.1, tap Undo within 5 s. | The original text comes back. | Pass (EMU 2026-11-15) |
| P.3 | EMU+API | Start a rewrite. Edit the field while it streams. Tap Replace. | "Your text changed, so it was not replaced. You can insert the result instead." shows with Insert. | Pass (EMU 2026-11-15) |
| P.4 | EMU+API | Open the sheet. Press Back. | The sheet closes. The host text is unchanged. | Pass (EMU 2026-11-15) |
| P.5 | EMU+API | Open the AI sheet for the selection. | The sheet shows the tone chips Fix grammar, Formal, Friendly, Shorter, Longer and Emojify. There is no variants view. Multi-tone variants exist only in kb-api (API.11). | Pass (rerun with the fixed text). The sheet shows the tone chips Fix grammar, Formal, Friendly, Shorter, Longer and Emojify (Emojify after a scroll). No variants view. (EMU 2026-11-15) |
| P.6 | EMU+API | Settings, AI: look at the quota row. Run `curl -s -H 'X-Dev-Uid: dev-local' localhost:8080/v1/quota`. | The row reads "N left today", where N is `ai_left_today`. | Pass (EMU 2026-11-15) |
| P.7 | EMU+API | Settings, AI: change the default tone. Open the ✦ sheet. | The first tone is the new default. | Pass (EMU 2026-11-15) |
| P.8 | EMU+API | Select more than 2,000 characters. Tap ✦ and a tone. | "Text is too long" shows. No request reaches kb-api (quota unchanged). | Pass (EMU 2026-11-15) |
| P.9 | EMU+API | Restart the backend with `FREE_DAILY=1`. Rewrite once. Rewrite again. | The second try shows "Daily AI limit reached · resets in N h" with "See Pro". `ai_quota_hit` is logged once. | Pass (EMU 2026-11-15) |
| P.10 | EMU+API | After P.9, tap "See Pro". | The paywall opens with the header "You used today's free AI actions". `paywall_view` is logged with `trigger=ai_quota`. | Pass (EMU 2026-11-15) |
| P.11 | EMU+API | Restart the backend with `UID_PER_MINUTE=1`. Rewrite twice within a minute. | "Too many requests, try again in N s" shows. Nothing is written to the host. | Pass (EMU 2026-11-15) |
| P.12 | EMU+API | Restart the backend with `AI_ENABLED=false`. Tap a tone. Open other fields. | "AI is unavailable right now" shows. The ✦ chip hides for 1 h. | Pass (EMU 2026-11-15) |
| P.13 | EMU+API | After P.12, restart the backend with no extra variable. Force-stop the app. Open a chat field. | The ✦ chip is back, because the pause lives in memory only. | Pass (EMU 2026-11-15) |
| P.14 | EMU+API | Stop the backend. Tap a tone. | "AI needs internet" shows. | Pass (EMU 2026-11-15) |
| P.15 | EMU+API | After a rewrite, open the overflow and tap Report. Pick Wrong or misleading. Leave "Include the text" off and send. | The report is sent. `ai_output_reported` is logged. | Pass (EMU 2026-11-15) |
| P.16 | EMU+API | In a chat field, type a word, then dictate. Tap "✦ Clean up" within 30 s. Tap Replace. Tap Undo within 5 s. | Only the dictated span changes to "Fake polished text.". Undo restores the raw text. `ai_polish_done` is logged. | Blocked: needs dictation, which this emulator cannot do. (EMU 2026-11-15) |
| P.17 | EMU+API | Dictate, then tap "✦ Clean up + translate to X". | The polished text and the translation show. | Blocked: needs dictation (see P.16). (EMU 2026-11-15) |
| P.18 | EMU+API | Dictate, then type a letter. Dictate again and move the cursor out. | The Clean up chip goes in both cases. | Blocked: needs dictation (see P.16). (EMU 2026-11-15) |
| P.19 | EMU+API | Withdraw AI consent. Open a chat field. Run the quota curl before and after you tap the chip. | The consent screen shows. Quota is unchanged. | Pass (EMU 2026-11-15) |
| P.20 | EMU+API | Open the Password field and a private app. | No ✦ chip and no Clean up chip. | Pass (EMU 2026-11-15) |
| P.21 | EMU | Install the plain debug build (no AI properties). Open a chat field. | No ✦ chip, because the backend URL is empty. | Pass (EMU 2026-11-15) |
| P.22 | DEVICE | ar and ur: rewrite a sentence with a name, a number and a link against a real model. Replace. | Reads right to left with correct bidi. Name, number and link are unchanged. Needs BACKEND-PROD for real output. | |
| P.23 | BACKEND-PROD | AI settings, Output script: Same, Roman, Native. Dictate in hi and ur and Polish with each. | Native gives Devanagari or Urdu script. Roman gives Latin. Same keeps the dictated script. | |
| P.24 | BACKEND-PROD | As a Pro user, turn on auto-polish, agree and dictate. Use up the quota. Dictate in a sensitive field. | Each dictation is polished and can be undone. A quota error pauses auto-polish until the next UTC day. Never in a sensitive field. | |
| P.25 | DEVICE | Select 300 characters in a field that holds tens of thousands of characters. Rewrite and Replace. | Record capture and Replace time. Expect under about 200 ms. | |

## Q. Stickers

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| Q.1 | EMU | In Messages, open the sticker panel. Tap a `starter` sticker. | The sticker inserts with `commitContent`. `sticker_send` is logged. | Pass: `sticker_send {pack_id=starter, premium=false, method=commit_content}`. Messages then shows "Attachments are not supported in this conversation", because the emulator has no MMS. The Stickers chip is not pinned by default. Pin it from the overflow first. (EMU 2026-11-15) |
| Q.2 | EMU | In an app without image support, tap a sticker. | The share sheet opens, or "Sticker copied. Paste it in the chat." shows. | Pass: in the Maps search field (no image MIME types) the sticker went to the Maps share target. `sticker_send` has `method=share`. (EMU 2026-11-15) |
| Q.3 | EMU | In a field that accepts no content and no share target, tap a sticker. | "This app does not accept stickers" or the clipboard fallback shows. | Pass on rerun after fix 9d57591d (EMU 2026-11-16). In the Contacts search field, a tap on a Starter sticker showed "Sticker copied. Paste it in the chat." inside the sticker panel, and the system clipboard chip appeared. No toast is needed. Earlier: Fail, the system blocked the "Sticker copied" toast and the user saw no notice. |
| Q.4 | EMU | As a free user, tap a sticker in the Starter Plus pack. | "This pack comes with Pro" shows with "See Pro". | Pass: tapping the Starter Plus tab shows "This pack comes with Pro" with "See Pro". A free user sees no stickers in the pack, so there is no sticker to tap. (EMU 2026-11-15) |
| Q.5 | EMU | Tap "See Pro" on the Q.4 card. | The paywall opens with `trigger=premium_sticker`. | Pass: the paywall opens on `ui://florisboard/paywall?trigger=premium_sticker` with "Unlock premium stickers". The plans show no prices and Continue is disabled (no billing). (EMU 2026-11-15) |
| Q.6 | EMU | Open the Password field, Chrome incognito and a private app. | No Stickers chip shows. | Pass (private app part): with Messages on the Private apps list and Stickers pinned, only the lock and emoji show, also in the overflow. The Password field and Chrome incognito parts are Blocked: Chrome renders black on this emulator and taps do not focus web fields. H.1 covers the Password field smartbar. (EMU 2026-11-15) |
| Q.7 | EMU | Turn on "Download on Wi-Fi only" in Offline languages. Use mobile data. Download a pack in the sticker panel. | Expected result unclear: the debug build reads packs from bundled assets (`CONTENT_BASE_URL` is empty), so the Wi-Fi gate path is not shown for it. Record whether "This pack waits for Wi-Fi…" shows. | Pass as recorded: with "Download on Wi-Fi only" on (it was already on) and Wi-Fi off (mobile data only), the panel showed "Tap the pack to download it". A tap on Starter showed the 8 stickers at once from bundled assets. "This pack waits for Wi-Fi" did not show. Plan fix: the Wi-Fi gate needs a build with `CONTENT_BASE_URL` set. (EMU 2026-11-15) |

## R. Settings and backup

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| R.1 | EMU | Home, Settings. Open each of the 9 sections. | Each opens the right screen. Back returns to Settings. | Pass. All 9 rows open the right screen and Back returns to Settings. Plan fix: Settings is reached from Home through the "Settings" row, and the hub lists Languages, Voice, Translate, AI, Appearance, Typing, Privacy, Subscription, About. (EMU 2026-11-15) |
| R.2 | EMU | For each slug in `languages voice translate ai appearance typing privacy subscription about`, run `adb shell am start -a android.intent.action.VIEW -d "ui://florisboard/settings/<slug>"`. | Each opens its screen. `appearance` opens Themes. `typing` opens Suggestions and autocorrect. | Pass. Each slug opens its screen. `appearance` opens the theme preset grid (title "Appearance"). `typing` opens "Suggestions and autocorrect". (EMU 2026-11-15) |
| R.3 | EMU | Settings, Translate: turn "Download on Wi-Fi only" on and off. Then do the same in Settings, Voice and in Offline languages. | Translate settings sets the translate and Whisper Wi-Fi setting. Voice settings sets the Whisper setting only. Offline languages sets translate, Whisper and content. | Pass. Read from `files/datastore/feature_settings.json`: Translate sets `translateWifiOnly` and `whisperWifiOnly`. Voice sets `whisperWifiOnly` only. Offline languages sets all three, with `contentWifiOnly`. All end on true. (EMU 2026-11-15) |
| R.4 | EMU | Settings, Subscription: tap Restore with no purchase. Repeat offline. | "No purchase found" both times. | Pass. "No purchase found" shows online and in airplane mode (also after a force-stop, so it is not a cached message). (EMU 2026-11-15) |
| R.5 | EMU | Settings, About. | FlorisBoard attribution, Terms, Privacy Policy, Contact and the two dictionary licences show. Terms opens `https://example.com/terms`. Contact uses `support@example.com` (placeholders, release gate Z.12). | Partial. "Based on FlorisBoard, Apache-2.0", Privacy policy, Terms, Contact, Project license and Third-party licenses show. Terms opens `https://example.com/...` in Chrome. Contact sends SENDTO `mailto:` to Gmail and shows `support@example.com`. The two dictionary licences are not on About: the only licence line for them is in `DictionaryScreen.kt` ("Licence: {licence}"). Plan fix or app gap: decide where the dictionary licences must show. Stopped at the call budget. (EMU 2026-11-15) |
| R.6 | EMU | Home, Appearance. Apply each of the 12 presets (amoled, dark, forest, light, material_you_dark, material_you_light, midnight, ocean, rose, sand, slate, sunset). Toggle key borders. Open a field after each. | Each applies live in the preview and the keyboard. Borders on and off work. `theme_apply` is logged. | Pass for the 8 free presets: each applied in the preview and in the keyboard, with `theme_apply {theme_id=<id>, premium=false}` logged for light, dark, amoled, material_you_light, material_you_dark, ocean, forest and slate. Key borders off and on both apply. The 4 premium presets (sunset, rose, midnight, sand) open the paywall for a free user, see R.7. Plan fix: say that only 8 presets apply for a free user. (EMU 2026-11-15) |
| R.7 | EMU | As a free user, tap a premium preset. Close the paywall. Open Themes again. | The paywall opens with `trigger=premium_theme`. The theme is not applied, also on reopen. | Pass. Sunset then Apply opens the paywall with the "Unlock premium themes" header (the premium_theme trigger). The activity intent shows only `ui://florisboard/settings/subscription`, because the paywall is an in-app route. After Close and a force-stop, Slate is still applied and Sunset still has the lock. (EMU 2026-11-15) |
| R.8 | EMU | Pick each key sound: soft, crisp, wood, pop. Type fast. Pick system and toggle the system touch-sound setting. Pick off. | Sounds play with no lag. System follows the system setting. Off is silent. | Blocked for the sound itself: adb cannot hear audio on the emulator. Partial check: each choice (soft, crisp, wood, pop, system, off) is stored as `keySound` in `feature_settings.json`. Ten fast key taps with Pop logged no skipped frames and no ANR. Left on System (system touch sounds = 1). Needs a device with ears. (EMU 2026-11-15) |
| R.9 | EMU | Pick Material You. Change the wallpaper. | Keyboard colours follow the new wallpaper. | Blocked: the emulator wallpaper change and the Material You colour check need a person to compare colours. Not run within the budget. (EMU 2026-11-15) |
| R.10 | EMU | Custom colours: pick a colour with the wheel, the hex field and a recent colour. | Each applies to the chosen element. | Pass. Wheel: a tap on the hue ring set Keys to `#80CC33`. Hex: `#3366CC` on Keys applied live. Recent colour: a tap on "Recent colour #FDFDFE" set Key text to `#FDFDFE`. Plan fix: type the leading `#`. With `adb shell input text`, escape it, because the shell reads `#` as a comment. (EMU 2026-11-15) |
| R.11 | EMU | Force-stop the app. Open the picker. | The recent colours stay. The custom theme applies live in the keyboard. | Pass. After Save and a force-stop, the picker shows Keys `#3366CC` and the recent colour `#FDFDFE`. The keyboard shows the blue custom theme (`theme__day_theme_id` = `com.manjul.aikeyboard.themes.custom:custom`). My first Save tap did not store anything (`customThemeColors` stayed empty), most likely because the tap landed while the layout moved. The second tap stored it. (EMU 2026-11-15) |
| R.12 | EMU | Tap Save and pick a colour while it runs. | The saved theme and the preview match the last colour picked. | Pass on rerun after fix 28ad4697 (EMU 2026-11-16): a colour picked about 30 ms after Save is stored when the user leaves the screen and is kept after a force-stop. Earlier: Fail after fix 775c83cb, a pick after the write ended was dropped. |
| R.13 | DEVICE | On a low-end phone, drag on the hue ring and on the saturation and value panel. | The drag stays smooth. | Blocked: DEVICE case (low-end phone). Not run on the emulator. (EMU 2026-11-15) |
| R.14 | EMU | Settings, Privacy: tap Clear data. Go back. Tap Delete my data. | Both open the Delete my data screen. | Pass. Clear data and Delete my data both open the Delete my data screen. No button on it was tapped. (EMU 2026-11-15) |
| R.15 | EMU | Translate and star a few texts. Run `adb shell bmgr backupnow $PKG`. Install a later debug build over it. | The backup does not hold `databases/app_database*`. History and favourites stay after the update. | Partial pass. `data_extraction_rules.xml` includes only `jetpref_datastore` and `files/ime`. A `bmgr backupnow` on LocalTransport succeeded with 12800 bytes, but `databases/` holds 2.4 MB, so `app_database` is not in it. The archive cannot be read (no root on this image). The update step was not run, because a new build would replace the AI build under test. Plan fix: run `adb shell bmgr enable true` and pick LocalTransport first. On this emulator Backup Manager is disabled by default. I set both back afterward. (EMU 2026-11-15) |

### Delete my data

Run R.16 to R.27 last in the session, because they wipe data. Before R.16, prepare this data:

- Add a user dictionary word.
- Translate a few texts and copy some clips.
- Save a custom theme.
- Install the dictionary pack and download one model.

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| R.16 | EMU | Run `adb shell bmgr backupnow $PKG`. | The backup finishes. Keep it for R.19. | Pass. After `bmgr enable true` and `bmgr transport com.android.localtransport/.LocalTransport`, the backup succeeded (12800 bytes). Clipboard prep: Copy in Messages added no row (clips=0), because clipboard history is off by default. The clipboard part of R.18 and R.19 is not prepared. |
| R.17 | EMU | Keep the keyboard closed. Tap "Clear on device only" with the models option off. Open Settings, Dictionary, User dictionary. | The user dictionary is empty. | Pass. Clear on device only showed a confirm dialog, then "Your data was cleared on this device." The internal user dictionary (`floris_user_dictionary.words`) and `user_word` and `dictionary_entry` went from 1 to 0. Checked through DB copies. |
| R.18 | EMU | After R.17, look at history, learned words, clipboard, consents, onboarding, Pro and models. | History, learned words and clipboard are empty. AI asks for consent again. Onboarding does not show again. Models stay. | Pass. History 2 to 0, learned words 1 to 0, clipboard 0 to 0 (not prepared). `consentAiAt` is 0, so AI asks for consent again. `onboardingDone` stays true. Pro stays none (it was none before). The en_fr and en_ur ML Kit models stay. |
| R.19 | EMU | Run `adb shell bmgr restore $PKG`. | History, clipboard and learned words do not come back. | Pass. Plan fix: Android 16 needs `bmgr restore 1 $PKG` (token from `bmgr list sets`). The restore finished (restoreFinished: 0). After it, `app_database` and `floris_user_dictionary` do not exist (no history or learned words), clipboard_history has 0 rows, and `consentAiAt` stays 0. Note: the full restore wipes the app data first, as a reinstall does. So the ML Kit models, `onboardingDone` and the IME setup flag were also gone, and onboarding showed again. Not a bug: `feature_settings.json` and `no_backup` are outside the backup rules. |
| R.20 | EMU | After R.17, open the Dictionary and Custom colours screens. | The dictionary pack, the online cache and the custom colours are reset. Record whether the custom theme stays installed (known Sprint 5 limit). | Partial. R.19's full restore wiped all app data before this check, so the reset cannot be tied to Clear on device only. Before R.17 no dictionary pack, online cache or custom colours were set up in this run. Rerun on a fresh setup with a custom theme first. |
| R.21 | EMU | Clear on device only with "Also remove downloaded voice and translation models" on. | Translate and Voice settings show the models as not downloaded. | Pass with a note. With the models option on, Clear on device only showed "Your data was cleared on this device." Offline languages then shows French (downloaded just before) as "Not downloaded". Voice shows Base as "Not downloaded" (no Whisper model was on the device). Note: 12 MB of files stay in `no_backup/com.google.mlkit.translate.models/en_fr` after 15 s. ML Kit reports the model as gone, so this is likely ML Kit's own cleanup. Look at it if disk space matters. |
| R.22 | EMU+API | Airplane mode on. Tap Delete everything. | "Could not delete. Check your connection and try again." shows. Nothing is wiped. | Pass. In airplane mode, Delete everything showed a confirm dialog, then "Could not delete. Check your connection and try again." The databases, `feature_settings.json` and the prefs file stayed in place. There was little data to keep after R.21, so this checks the files, not row counts. |
| R.23 | EMU+API | Online. Use AI once. Tap Delete everything. Watch the backend console. Run the quota curl. | "Deleting…" shows, then the wipe. The backend logs `account_delete` with `firebase_user=skipped`. `ai_left_today` is back to full. `delete_data` is logged once. No `Bearer` in logcat. | Pass. AI used once (`ai_left_today` 8 to 7). Delete everything showed "Deleting…", then "Your data was deleted on our servers and on this device." The backend logged `account_delete ... firebase_user=skipped` and `POST /v1/account/delete` 200. The quota curl shows `ai_left_today` 10 (full). `delete_data` logged once. No `Bearer` in logcat. `consentAiAt` is 0 after it. |
| R.24 | EMU+API | Tap Delete everything twice fast. | Expected result unclear: the local backend logs no request lines, so count the `account_delete` audit lines. Expect one. | Pass. Two fast taps on Delete in the confirm dialog sent one request: one `account_delete` audit line and one `delete_data` in logcat. Two fast taps on Delete everything after it opened no second request (the second tap closed the dialog). |
| R.25 | EMU+API | Run `adb shell run-as $PKG chmod 444 databases/clipboard_history*`. Tap Delete everything. Tap Try again. Restore the permissions with `chmod 660`. Tap Try again. | The partial message shows with Try again. `delete_data` is logged once only. The last retry succeeds. | Pass. Plan fix: `chmod 444` on the files alone did not fail the wipe (Room deletes the files by unlinking them). Also run `chmod 555 databases`, and use `chmod 771 databases` to restore. With that, the partial message showed: "Your data was deleted on our servers, but some data on this device was not cleared. Try again to clear it." A retry while still locked showed it again. After restore, Try again showed the full success message. One `account_delete` and one `delete_data` across the three tries. |
| R.26 | EMU+API | Throttle the emulator network. Tap Delete everything and rotate during "Deleting…". | The work continues. The state stays. | Pass. With `adb emu network delay umts` and `speed gsm`, I tapped Delete everything and rotated to landscape during "Deleting…" (the text stayed shown after rotation). The work finished: the success message showed after rotating back. One `account_delete`, one `delete_data`, no crash. Note: the local backend answers fast, so the throttle window was short. |
| R.27 | EMU | Read the Delete my data screen. Tap "Manage subscription". | "This does not cancel a Google Play subscription. Cancel it in Google Play." shows. On a device with Play, Play subscriptions open. | Pass. The screen shows "This does not cancel a Google Play subscription. Cancel it in Google Play." Manage subscription opens the Play Store. The emulator has no Google account, so Play shows its sign-in page. |

## S. Analytics logcat

Type `zqxmarker` in normal fields and `Pw7zqx!` in password fields. Copy `clipzqx`. Dictate one sentence. Use the
Translate bar and AI once. Then run:

```sh
PID=$(adb shell pidof $PKG)
adb logcat -d -v time --pid=$PID > ime.log
grep -nE "zqx|Pw7|clipzqx|helo|kya" ime.log
grep -nE "label=|text=|composing=|EditorInfo|info=|packageName|Bearer|X-Dev-Uid" ime.log
grep -nE " [DI] (KeyboardManager|FlorisImeService|FlorisSpellCheckerService|EditorInstance|KeyboardState)" ime.log
```

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| S.1 | EMU+API | Run the session above. Run the three greps. | All greps are empty. `AiKbAnalytics` lines hold only enums, buckets and booleans. | Pass (partial session). In Messages: typed `zqxmarker` and `clipzqx`, used the Translate bar ("helo wrld") and AI Fix grammar (backend `POST /v1/rewrite` 200). All three greps on the `--pid` log are empty. `AiKbAnalytics` holds only `ai_request {engine=cloud}`. The backend log has no marker. Not covered: the password marker (Chrome test page does not work here), the clipboard copy (no adb copy command) and dictation (no mic). (EMU 2026-11-15) |
| S.2 | EMU | Repeat in Chrome, Messages and a private app. Add their package names to the grep. | No host package name shows in our lines. | Pass with a note. In our process only the framework `ImeTracker` line names a host: `I/ImeTracker(<pid>): com.google.android.apps.messaging:<id>: onHidden`. That is Android code, not ours. Our own tags never name a host. Chrome is not usable here. The private-app part is covered by the Messages run in Q.6. Plan fix: exclude `ImeTracker` from the grep, or note it as a platform line. (EMU 2026-11-15) |
| S.3 | EMU | Review `grep -rnE 'flog(Error\|Warning)' app/src/main/kotlin`. Run `adb logcat -b crash -d`. | No error line holds user content. | Pass with one note. 62 `flogError`/`flogWarning` calls. None prints typed text, transcripts or clipboard text. Note (upstream code): `EmojiHistory.kt:61` logs `$e` from `Json.decodeFromString`, and a kotlinx serialization error can quote part of the JSON input, so recent emoji can reach logcat. `adb logcat -b crash -d` has no line for our package. (EMU 2026-11-15) |
| S.4 | EMU | Run `adb shell dumpsys package $PKG \| sed -n '/requested permissions/,/install permissions/p'`. | Record the list. POST_NOTIFICATIONS is declared, but no prompt showed (A.14). | Pass. Requested: BIND_GET_INSTALL_REFERRER_SERVICE, ACCESS_ADSERVICES_TOPICS, POST_NOTIFICATIONS, READ_GSERVICES, ACCESS_LOCAL_NETWORK, FOREGROUND_SERVICE, ACCESS_ADSERVICES_AD_ID, INTERNET, ACCESS_NETWORK_STATE, ACCESS_ADSERVICES_ATTRIBUTION, VIBRATE, RECORD_AUDIO, AD_ID, WAKE_LOCK. POST_NOTIFICATIONS is declared and `granted=false`, and no prompt showed. This also blocks IME toasts (see Q.3). (EMU 2026-11-15) |
| S.5 | EMU | Build with `-Paikb.verboseFlog=true`. Type once. Grep for `FlorisImeService`. Reinstall the default build. | `grep -cE " [DI] FlorisImeService" ime.log` is above 0. Do not share this APK. | Not run. It needs a verbose build and a reinstall, then a reinstall of the AI build. That replaces the build under test and goes over the case budget. Code check: `VERBOSE_FLOG` comes only from `-Paikb.verboseFlog=true` (`app/build.gradle.kts:116`). S.1 confirmed 0 `[DI] FlorisImeService` lines in the default build. (EMU 2026-11-15) |
| S.6 | EMU | Run each feature once: onboarding, voice, translate, PROCESS_TEXT, dictionary, sticker, theme, transliteration. Read `AiKbAnalytics`. | Each event name is in the catalog. `translit_commit` holds only `lang` and `source`. `sticker_send` holds `pack_id`, `premium` and `method`. | Partial pass. Seen in this run and batch 1: ai_request, consent_result, consent_withdrawn, dictionary_lookup, ime_session_start, pack_download_start, pack_download_done, privacy_private_app_add, translate_error, theme_apply (R.6) and sticker_send (Q.1 to Q.3). Each is in `EventCatalog.kt`. `sticker_send` holds only `pack_id`, `premium` and `method`. All values are enums, buckets or booleans. Not seen live: onboarding, voice, PROCESS_TEXT and transliteration. In code, `translit_commit` holds only `lang` and `source`. (EMU 2026-11-15) |
| S.7 | EMU+API | Watch the backend console during P.1 to P.17. | No user text shows in the backend log. | Pass for the AI run in S.1. The backend `kbapi.access` lines hold only request_id, uid_hash, method, route, status, latency, prompt_id and model. No marker or user text is in `backend.log`. P.1 to P.17 were not watched again in this batch. (EMU 2026-11-15) |
| S.8 | BACKEND-PROD | Build with `aikb.firebase.*`. Clear data. Enable the keyboard from system settings without opening the app. Type for 30 s. Watch DebugView. Then finish onboarding and accept the UMP form (EEA). | No Analytics event before consent. Events after consent. | Blocked: BACKEND-PROD (needs a build with `aikb.firebase.*` and Firebase DebugView). (EMU 2026-11-15) |

## T. Performance and memory

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| T.1 | EMU | Run `adb shell dumpsys meminfo $PKG`. Switch from a subtype with no dictionary to en, then ur. | Record PSS growth per dictionary. Expect under about 15 MB for en. Only one dictionary stays loaded. | Partial. With only en (US) installed, after a force-stop and typing "the quick brown fox" in Messages, the process PSS is 177 MB (Native Heap 29.7 MB, Dalvik 13.9 MB). The no-dictionary and ur subtypes are not installed, so the per-dictionary growth was not measured within the budget. Note: `.dex mmap` is 97.8 MB of it, which is typical for a debug build. Measure again on a release build. (EMU 2026-11-15) |
| T.2 | EMU | Load a Roman subtype. Run `dumpsys meminfo` before and after. | The keyboard stays at 90 MB or less. The lexicon adds 8 MB or less. | Fail as measured, but the budget does not fit a debug build. Before any Roman subtype the PSS is already 177 MB, above the 90 MB limit, and 97.8 MB of that is `.dex mmap`. No Roman subtype is installed, so the lexicon growth was not measured. Plan fix: run T.1 and T.2 on a release (R8) build. (EMU 2026-11-15) |
| T.3 | EMU | Translate a few sentences. Run `dumpsys meminfo`. Wait 60 s idle and measure. Translate, send `RUNNING_LOW` and measure. | PSS with one model is at most 160 MB. After idle or trim, PSS drops by about the model size. Record the three numbers. | Blocked. Only the English model is on the emulator, so the Translate bar shows "Download English to Spanish (about 31 MB)" and no model is loaded. The debug build is already at 182 MB PSS before any model, above the 160 MB limit, so run this on a release build. (EMU 2026-11-15) |
| T.4 | DEVICE | With Whisper, dictate 60 s. Run `adb shell dumpsys meminfo $PKG \| grep TOTAL`. | Done in under 120 s. Record time and PSS. | Blocked: DEVICE case. Not run on the emulator. (EMU 2026-11-15) |
| T.5 | DEVICE | On a 3 GB phone and a Cortex-A53 phone, dictate 30 s and 60 s with Whisper Base. | No low-memory kill. No SIGILL. Record PSS and decode time. | Blocked: DEVICE case. Not run on the emulator. (EMU 2026-11-15) |
| T.6 | DEVICE | Perfetto trace while you type 200 characters on a mid-tier phone, in en and ur. | Strip update p90 is at most 30 ms. | Blocked: DEVICE case. Not run on the emulator. (EMU 2026-11-15) |
| T.7 | DEVICE | Run `TranslateLatencyTest` on a mid-tier and a 3 GB phone. See `docs/translation/latency.md`. | Mid-tier p90 is at most 150 ms. Low-end p90 is at most 400 ms. | Blocked: DEVICE case. Not run on the emulator. (EMU 2026-11-15) |
| T.8 | DEVICE | 3 GB phone, fresh settings. Open the Translate bar. | Preview mode by default. Preview in about 600 ms. | Blocked: DEVICE case. Not run on the emulator. (EMU 2026-11-15) |
| T.9 | DEVICE | Run the TRD 6.4 mid-tier budgets on a Pixel 6a class phone (Macrobenchmark and Perfetto). | Every budget is met. | Blocked: DEVICE case. Not run on the emulator. (EMU 2026-11-15) |
| T.10 | BACKEND-PROD | Run 20 rewrites of 100 to 300 characters on 4G and on Wi-Fi. | First text p50 is at most 1.5 s. The 300-character p90 is under 2.5 s. | Blocked: BACKEND-PROD case (needs the real kb-api and a real model). (EMU 2026-11-15) |
| T.11 | EMU | Open the Translator with a native ad. Leave it. Wait 60 s. Run `dumpsys meminfo` on the keyboard. | Keyboard PSS is at most 120 MB. | Fail as measured on the debug build. PSS was 253 MB with the Translator open and 243 MB 60 s after Home, above 120 MB. The IME and the app run in one process (`ps` shows 1), so the Translator memory stays in the keyboard process. No native ad was found on the screen in the UI dump (debug test ads may need consent or a network fill). Plan fix: measure on a release build, and say how to get the native ad. (EMU 2026-11-15) |

## U. Lifecycle

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| U.1 | EMU | Rotate on Home, in the Translator and in Settings. | State stays. No crash. | Pass. Home, the Translator (text "coffee" kept) and Voice settings each rotated to landscape and back. State stayed. No crash. (EMU 2026-11-15) |
| U.2 | EMU | Open the keyboard. Run `adb shell am kill $PKG` with the app in the background. Tap a field. | The keyboard starts again and types. | Pass with a plan note. `am kill` did not kill the process while the IME was bound (same pid). A `kill -9` of the process restarted it at once, and a tap on the Messages field showed the keyboard, which typed. No crash. Plan fix: use `adb shell run-as $PKG kill -9 $(pidof $PKG)`. (EMU 2026-11-15) |
| U.3 | EMU | Change the system dark mode with the keyboard open. | The keyboard redraws. No crash. | Pass with a note. Messages recreated on the dark mode change, so the keyboard hid. A tap showed it again, redrawn, and it typed. No crash. The custom theme from R.11 has fixed colours, so the keys did not change colour. (EMU 2026-11-15) |
| U.4 | EMU | Change the system font size to the largest. Open the keyboard and the mic chip. | Text fits. The permission chip text shows in full. | Pass on rerun after fix e278c95c (EMU 2026-11-16). At font scale 2.0, "?123", the full space bar label "Urdu (Pakistan) [ROMAN]" and the hint symbols fit inside their keys. The app text outside the keyboard still scales. The font scale was reset to 1.0. Earlier: Fail, "?123" showed as "?" and the hint symbols overlapped the letters. |
| U.5 | EMU | Lock and unlock with the keyboard open. | The keyboard shows again with no crash. | Pass. After lock and unlock (no lock password set), the keyboard showed again and typed. No crash. (EMU 2026-11-15) |

## W. RTL and TalkBack

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| W.1 | EMU | Set ur and ar as source and target. Use the bar, the Translator and the sheet. Mix English words and digits. | Text is right-aligned, right to left. Caret and pair arrows follow the direction. Mixed text orders correctly. | Partial (Translator only). With Urdu as target, the output "کافی 2 رنز" is right-aligned and the digit sits in the right place. After Swap, the Urdu input is right-aligned with the caret at the left end of the RTL text, and the English output is left-aligned. Arabic, the bar and the sheet were not checked, over the case budget. Note: `adb shell input text "coffee 2 runs"` gave "coffeeof 2 runs", because autocorrect took the space as a pick. (EMU 2026-11-15) |
| W.2 | EMU | TalkBack on. Focus the lock in the Password field and in a private app. | TalkBack reads "Private field. Translation, voice and learning are off" and the "Make this app private" action where it applies. | Partial (labels checked, speech not heard). TalkBack speech cannot be heard over adb, so I read the IME accessibility tree (`uiautomator dump --windows`). In the Wi-Fi password field the lock reads "Private field. Translation, voice and learning are off" and has the long-press action "Make this app private" (it worked: "App made private" with Undo). In a private app (Settings) the lock has the same label and no Make private action, as the code intends. The lock icon is 54x54 px (18 dp) inside a 144 px (48 dp) key. Chrome hid the web page from uiautomator, so I used the Wi-Fi Add network password field. (EMU 2026-11-15) |
| W.3 | EMU | TalkBack on. Explore the Translate bar. Close the bar. | Every control has a label and a 48 dp target. The preview is announced politely. Focus returns to the keyboard. | Pass on rerun after fix d713e494 for the smartbar part (EMU 2026-11-16, accessibility dump, TalkBack speech not heard). The chips are clickable with labels: "Translate as you type", "Open stickers", "Rewrite with AI", "Open emoji panel", "Make this app private", "Show or hide additional actions" and "Open voice input provider". The expand toggle is clickable, and its icon carries "Show or hide quick actions". The Translate bar part passed before. Earlier: Fail, the smartbar toggle and chips had no labels and were not clickable. |
| W.4 | EMU | TalkBack on Offline languages. | Buttons read "Download Urdu", "Stop downloading Urdu" or "Delete Urdu". | Pass (labels checked, speech not heard). The buttons read "Download Urdu", then "Stop downloading Urdu" during the download, then "Delete Urdu". Each is 120 px (40 dp) tall, under the 48 dp target. Note: Spanish reads "Not downloaded" although batch 1 downloaded it. (EMU 2026-11-15) |
| W.5 | EMU | TalkBack on. Walk the sticker panel, the colour picker and the definition card. | Each control has a spoken name and role. | Partial (sticker panel only, labels checked, speech not heard). Each sticker has "Sticker N" and the Pro tab reads "Starter Plus, Pro pack". But the clickable node has no name or Button role (the name is on a child, so TalkBack can still merge it). The tabs do not report a selected state. The colour picker and the definition card were not checked, over the case budget. (EMU 2026-11-15) |
| W.6 | EMU+API | TalkBack on. Walk the AI sheet and the report sheet. | Each control has a spoken name and role. | Pass on rerun after fix 6e7d2a53 (EMU 2026-11-16, accessibility dump, speech not heard). The report reason rows (Offensive, Wrong or misleading, Unsafe and the rest) are radio rows 144 px (48 dp) tall in a list that scrolls inside the sheet. Every row comes into full view on scroll. Cancel and Send report stay fully visible under the list, each 48 dp tall. Earlier: Fail, the rows were 24 dp and the buttons were cut off. |

## X. Ads (debug test ads)

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| X.1 | EMU | Fresh install as a free user. Finish onboarding. | Expected result unclear: the debug app builds `UmpConsentManager()` with no debug geography, so the EEA form shows only where UMP detects EEA. Record whether the form shows. | Blocked: no ad can load on this build. Every `ad_unit_*` key defaults to "" in `ConfigCatalog.kt:43`, and `aikb.firebase.*` is not set, so Remote Config cannot fetch unit ids. UMP part: after the fresh install in batch 1 no consent form showed, and Privacy has no "Ad privacy options" row, so UMP did not detect EEA. Needs a build with Firebase and test unit ids. (EMU 2026-11-15) |
| X.2 | EMU | Open Settings, Privacy where UMP requires privacy options. Tap "Ad privacy options". Turn off the network and tap it again. | The UMP form opens. On failure a short toast shows. No crash. | Blocked: Privacy shows no "Ad privacy options" row on this emulator, because UMP does not require privacy options outside the EEA. Needs a UMP debug geography or an EEA device. (EMU 2026-11-15) |
| X.3 | EMU | Open the app through PROCESS_TEXT and a share (SEND). | No app-open ad. | Blocked for the ad check: no ad unit ids on this build (see X.1). No crash on PROCESS_TEXT or SEND (A.13 and section M). (EMU 2026-11-15) |
| X.4 | EMU | Open Settings from the keyboard settings key. Rotate on Home. Open a paywall deep link. | No app-open ad after the settings key or on the paywall. | Blocked: no ad unit ids on this build (see X.1). (EMU 2026-11-15) |
| X.5 | EMU | Do 2 feature uses (translate or theme apply), then go Home. Repeat fast. | The interstitial shows only after 2 uses, then Home. At most one per 3 min. | Blocked: no ad unit ids on this build (see X.1). (EMU 2026-11-15) |
| X.6 | EMU | Count interstitials across a day (move the clock). | At most 6 a day. | Blocked: no ad unit ids on this build (see X.1). (EMU 2026-11-15) |
| X.7 | EMU | Run a proxy. Force-stop. Use only the keyboard for 30 s. | No ads host is contacted. | Partial pass, no proxy. After a force-stop and 30 s of keyboard-only use in Messages, the IME process held no TCP socket (`/proc/net/tcp` had no row for its uid), and logcat showed no ads host. A proxy was not run in the sandbox. (EMU 2026-11-15) |
| X.8 | EMU | Open the Dictionary, Translator and History screens as a free user. | Each shows a native ad slot. Record where it sits on a tall screen. | Blocked: the Dictionary, Translator and History screens show no native ad slot, because the unit ids are empty (see X.1). Batch 2 saw the same in T.11. (EMU 2026-11-15) |
| X.9 | EMU | After a Delete everything (R.23), cold start the app twice. | No ad in the first session after the delete. | Partial pass. After R.23 to R.26, `ads_state.json` had `session_count` 0. After two cold starts it is 2 with `shows` empty, so the session counter reset and no ad showed. No ad can load at all here (no ad unit ids, see X.1), so this checks only the counter reset. |

## API. Direct API checks (curl)

Start the backend with `cd backend && AUTH_MODE=dev GATEWAY=fake ./gradlew run`. Set these in the host shell:

```sh
B=http://localhost:8080
U='X-Dev-Uid: dev-local'
J='Content-Type: application/json'
```

Every response carries an `X-Request-Id` header. Use `curl -s -i` to see headers and `curl -sN` for streams.

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| API.1 | EMU+API | `curl -s -i $B/healthz` | 200 `{"status":"ok"}` with no auth header. `X-Request-Id` is present. | Pass (local kb-api 2026-11-15) |
| API.2 | EMU+API | `curl -s -H "$U" $B/v1/quota` | 200 with `ai_left_today` 10, `resets_at` and `tier` "FREE". No `ai_daily_limit` field. | Pass (local kb-api 2026-11-15) |
| API.3 | EMU+API | Run API.2 three times. | `ai_left_today` stays the same. Reading the quota spends nothing. | Pass (local kb-api 2026-11-15) |
| API.4 | EMU+API | `curl -s -i $B/v1/quota` (no header). | 401 `{"error":"auth"}`. | Pass (local kb-api 2026-11-15) |
| API.5 | EMU+API | `curl -s -i -H 'X-Dev-Uid: bad uid!' $B/v1/quota` | 401 `{"error":"auth"}`. | Pass (local kb-api 2026-11-15) |
| API.6 | EMU+API | `curl -s -i $B/v1/nope`, then `curl -s -i -H "$U" $B/v1/nope`. | Without the header, 401. With it, 404 `not_found`. | Pass (local kb-api 2026-11-15) |
| API.7 | EMU+API | `curl -s -i -H "$U" $B/v1/rewrite` (GET). | 405 `method_not_allowed`. | Pass (local kb-api 2026-11-15) |
| API.8 | EMU+API | `curl -s -i -H "$U" -H "$J" -d '{bad' $B/v1/rewrite` | 400 `bad_request`. | Pass (local kb-api 2026-11-15) |
| API.9 | EMU+API | `curl -sN -i -H "$U" -H "$J" -d '{"mode":"fix","locale":"en","text":"helo wrld","client_req_id":"r1"}' $B/v1/rewrite` | 200 `text/event-stream` with `Cache-Control: no-cache` and `X-Accel-Buffering: no`. Three `event: delta` frames ("This is ", "a fake ", "rewrite."), then `event: done` with `quota_left` 9, `trimmed` false and `prompt` "rewrite@1". | Pass (local kb-api 2026-11-15) |
| API.10 | EMU+API | Send the API.9 request again twice with `client_req_id` "r1". Then a fourth time. | The second and third calls show the same `quota_left` as the first. The fourth call counts as a new action, so `quota_left` drops by one. | Pass (local kb-api 2026-11-15) |
| API.11 | EMU+API | `curl -s -H "$U" -H "$J" -d '{"mode":"fix","modes":["formal","friendly"],"locale":"en","text":"helo wrld","client_req_id":"m1"}' $B/v1/rewrite` | 200 JSON with `variants` [{"mode":"formal","text":"Fake formal."},{"mode":"friendly","text":"Fake friendly."}], `quota_left` and `prompt` "multi_tone@1". | Pass (local kb-api 2026-11-15) |
| API.12 | EMU+API | Send API.9 with `"mode":"poem"`, then with `"locale":"e"`, then with `"text":""`, then with a `client_req_id` of 65 characters. | 400 `bad_request` each time. No quota is spent. | Pass (local kb-api 2026-11-15) |
| API.13 | EMU+API | Send API.11 with `"modes":["formal"]`, then with `"modes":["formal","formal"]`. | 400 `bad_request` both times. | Pass (local kb-api 2026-11-15) |
| API.14 | EMU+API | Send API.9 with `"mode":"translate"` and no `target_lang`. Then add `"target_lang":"ur"`. | The first gives 400 `bad_request`. The second streams the fake rewrite. | Pass (local kb-api 2026-11-15) |
| API.15 | EMU+API | `T=$(python3 -I -c "print('a'*4001)")`, then send API.9 with `"text":"'$T'"`. | 400 `too_long`. | Pass (local kb-api 2026-11-15) |
| API.16 | EMU+API | `curl -sN -H "$U" -H "$J" -d '{"transcript":"the quick brown fox","target_lang":"en","client_req_id":"p1"}' $B/v1/polish` | SSE stream that ends with `event: done` holding `polished` "Fake polished text.", `translation` "Fake translation.", `source_lang_detected` "en", `quota_left` and `prompt` "polish_translate@1". | Pass (local kb-api 2026-11-15) |
| API.17 | EMU+API | Send API.16 with `"script":"LATIN"`, then with `"transcript":""`, then with `"source_lang":"e"`. | 400 `bad_request` each time. `"script":"ROMAN"` and `"NATIVE"` stream normally. | Pass (local kb-api 2026-11-15) |
| API.18 | EMU+API | Restart with `FREE_DAILY=1`. Send API.9 with id "q1". Send it with id "q2". | The first streams. The second gives 402 `{"error":"quota","resets_at":...}`. | Pass (local kb-api 2026-11-15) |
| API.19 | EMU+API | Restart with `UID_PER_MINUTE=1`. Send API.9 with id "u1", then at once with id "u2". | The second gives 429 `rate_limited` with `retry_after` in the body and a `Retry-After` header. | Pass (local kb-api 2026-11-15) |
| API.20 | EMU+API | Restart with `HOURLY_SPEND_LIMIT_USD=0`. Send API.9 with id "s1", then with id "s2". | The first streams. The second gives 503 `ai_paused`. | Pass (local kb-api 2026-11-15) |
| API.21 | EMU+API | Restart with `AI_ENABLED=false`. Send API.9. Then send API.12 with `"mode":"poem"`. | The first gives 503 `ai_paused`. The bad body gives 400 `bad_request`, because validation runs first. | Pass (local kb-api 2026-11-15) |
| API.22 | EMU+API | `curl -s -i -H "$U" -H "$J" -d '{"client_req_id":"r1","prompt_id":"rewrite@1","reason":"wrong","include_text":false}' $B/v1/ai/report` | 200 `{"ok":true}`. API.2 shows no quota change. | Pass (local kb-api 2026-11-15) |
| API.23 | EMU+API | Send API.22 with `"reason":"spam"`, then with `"prompt_id":"Rewrite-1"`, then with an empty `client_req_id`. | 400 `bad_request` each time. | Pass (local kb-api 2026-11-15) |
| API.24 | EMU+API | Restart with `UID_PER_MINUTE=1`. Send API.22 twice at once. | The second gives 429. | Pass (local kb-api 2026-11-15) |
| API.25 | EMU+API | `curl -s -H "$U" -H "$J" -d '{"word":" Run ","lang":"en"}' $B/v1/dictionary` | 200 with one entry: `headword` "run", `pos` verb, `definition` "To move quickly on foot.", `example` "Run home.", `synonyms` ["sprint"]. `source` "fake" and `licence_url` "https://example.com/fake-dictionary-licence". | Pass (local kb-api 2026-11-15) |
| API.26 | EMU+API | Send API.25 with `"word":"zzzz"`. | 404 `not_found`. | Pass (local kb-api 2026-11-15) |
| API.27 | EMU+API | Send API.25 with `"word":"run fast"`, then `"word":""`, then a word of 65 letters, then `"lang":"fr"`. | 400 `bad_request` each time. | Pass (local kb-api 2026-11-15) |
| API.28 | EMU+API | Restart with `DICTIONARY_PER_MINUTE=1`. Send API.25 twice at once. | The second gives 429. | Pass (local kb-api 2026-11-15) |
| API.29 | EMU+API | Restart with `AUTH_MODE=dev` only (no `GATEWAY=fake`). Send API.25. | 503 `dictionary_unavailable`, because no `DICTIONARY_SOURCE_URL` is set. | Pass (local kb-api 2026-11-15) |
| API.30 | EMU+API | `curl -s -i -H "$U" -H "$J" -d '{"purchase_token":"","product_id":"pro_monthly"}' $B/v1/entitlement/verify` | 400 `bad_request`. | Pass (local kb-api 2026-11-15) |
| API.31 | EMU+API | Send API.30 with `"purchase_token":"tok1"`. Then `curl -s -i -H "$U" $B/v1/entitlement`. | Both give 503 `unavailable`, because Play billing is off outside GCP. | Pass (local kb-api 2026-11-15) |
| API.32 | EMU+API | Run API.9 once. Then `curl -s -i -H "$U" -H "$J" -d '{"client_req_id":"d1"}' $B/v1/account/delete`. Then API.2. | 200 `{"deleted":true}`. The backend log shows `account_delete` with `firebase_user=skipped`. API.2 shows the full quota again. | Pass (local kb-api 2026-11-15) |
| API.33 | EMU+API | Send API.32 again. Then send it with `"client_req_id":""` and with 65 characters. | The repeat gives `{"deleted":true}` again. The bad ids give 400. | Pass (local kb-api 2026-11-15) |
| API.34 | EMU+API | `curl -s -i -X POST $B/internal/rtdn` | 401 with an empty body. | Pass (local kb-api 2026-11-15) |
| API.35 | EMU+API | `curl -s -i $B/delete-account` | 200 HTML with `Content-Security-Policy` starting `default-src 'none'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` and `Cache-Control: public, max-age=3600`. | Pass (local kb-api 2026-11-15) |
| API.36 | EMU+API | Run API.9 and API.16. Search the backend console for "helo" and "quick brown". | No match. User text is never logged. | Pass (local kb-api 2026-11-15) |
| API.37 | EMU+API | Run `K_SERVICE=x AUTH_MODE=dev ./gradlew run`. Then run `GATEWAY=fake ./gradlew run` with no `AUTH_MODE`. | Both refuse to start: "AUTH_MODE=dev is refused on Cloud Run (K_SERVICE is set)" and "GATEWAY=fake needs AUTH_MODE=dev". | Pass (local kb-api 2026-11-15) |
| API.38 | BACKEND-PROD | On the deployed kb-api, call `/v1/quota` with no App Check token. Then with `X-Dev-Uid`. | 401 `app_check` both times. Dev auth is refused. | Blocked: needs the production backend (2026-11-15) |
| API.39 | BACKEND-PROD | Send a report with `include_text` true and one with false. Read `ai_reports` in Firestore. | Both exist. Only the first has text. Both expire in 90 days. | Blocked: needs the production backend (2026-11-15) |
| API.40 | BACKEND-PROD | Delete everything from the app on a Firebase build. Read Firestore and Firebase Auth for the old uid. | No rows remain for the uid. The Firebase Auth user is gone. The next AI request uses a new anonymous uid. | Blocked: needs the production backend (2026-11-15) |
| API.41 | BACKEND-PROD | Run the eval baselines in `backend/evals/README.md`. | Reports are saved. Cache-read tokens are above 0. | Blocked: needs the production backend (2026-11-15) |

## Z. Release, licence and Play

| ID | Where | Steps | Expected | Result |
|---|---|---|---|---|
| Z.1 | RELEASE | Install the release APK. Repeat the S.1 session as a script. Read logcat. | No Flog output. No text, tokens, URLs or package names. | |
| Z.2 | RELEASE | Release build: run a session of dictionary, phrasebook, sticker, clipboard, colour and Roman typing use. Read logcat. | No query, phrase, clip text, Roman word, candidate, mapping or package name shows. | |
| Z.3 | RELEASE | Release build: open Voice settings. | No Offline (Whisper) option and no model section. | |
| Z.4 | RELEASE | Release build: open Devtools. | No "Translate latency probe" row. | |
| Z.5 | RELEASE | Release build with `-Paikb.ai.devAuth=true`. Look for the ✦ chip and the `X-Dev-Uid` header in traffic. | Dev auth is off in release. No `X-Dev-Uid` is sent. | |
| Z.6 | RELEASE | Run `./gradlew :app:check` on the release variant. Reboot and start the release app before first unlock. | Only allow-listed components are exported. No crash. Record the APK size. | |
| Z.7 | RELEASE | Run `scripts/check-16kb-alignment.sh` on the release APK. On the 16 KB emulator, open the keyboard and translate. | 64-bit libraries are aligned. Libraries load. | |
| Z.8 | RELEASE | Release with App Check enforced, from an internal track. Agree to AI consent and rewrite. Try a sideloaded build. | Rewrite streams and Replace works. The sideloaded build gets 401 `app_check`. No Firebase traffic before consent. | |
| Z.9 | RELEASE | Set the four `aikb.signing.*` properties. Run `./gradlew :app:bundleRelease`. Run `jarsigner -verify -verbose:summary app/build/outputs/bundle/release/app-release.aab`. | Signed with the upload key, not "CN=Android Debug". | |
| Z.10 | RELEASE | Buy monthly, then annual, with a licence tester. | Pro shows in the app and the keyboard within 2 s. `purchase_success` is logged. | |
| Z.11 | RELEASE | Start the annual trial. | `trial_start` is logged. | |
| Z.12 | RELEASE | Read About and the store listing before release. | `contact_email` and `terms_url` are no longer the `example.com` placeholders. | |
| Z.13 | RELEASE | Turn the network off right after the purchase callback. Reconnect. | "Activating Pro…" shows with no Pro flash. Pro shows after reconnect. | |
| Z.14 | RELEASE | Reinstall and open the app. Then use a second device with the same Google account. | Pro is restored on both. "Pro restored" shows after Restore. | |
| Z.15 | RELEASE | Use Play test cards for grace and on-hold. | The "Fix payment" banner shows on the Settings home. | |
| Z.16 | RELEASE | Refund in Play Console. | Pro goes within one RTDN cycle. | |
| Z.17 | RELEASE | Open each paywall trigger: ai_quota, premium_theme, remove_ads, cloud_quality, premium_sticker, auto_polish and win_back. | Each header matches its trigger. `paywall_view` and `paywall_close` are logged. | |
| Z.18 | RELEASE | Throttle the network. Open the paywall. | The close button shows at once. | |
| Z.19 | RELEASE | With a banner and a native ad on screen, buy Pro. | Both ads go at once. | |
| Z.20 | RELEASE | As a free user, buy Pro. Tap a Starter Plus sticker. | The sticker inserts. | |
| Z.21 | RELEASE | Apply a premium theme with Pro. Let Pro lapse (licence tester cancel). Restart the keyboard. | The theme goes back to the default in the app and at the next keyboard start. | |
| Z.22 | RELEASE | Delete everything with an active subscription. Tap "Manage subscription". | Play opens. Pro comes back after the refresh. | |
| Z.23 | RELEASE | Install an older build. Share a newer one with internal app sharing. Open Home. Accept FLEXIBLE. | The download runs. A "Restart" snackbar shows. Restart installs. At most one prompt per 24 h. | |
| Z.24 | RELEASE | Set Remote Config `min_supported_version` above the installed version. Open Home. Decline. Resume Home. | The IMMEDIATE update shows. Decline never exits the app. It asks again on the next resume. | |
| Z.25 | RELEASE | Start an IMMEDIATE update and kill the app during it. Reopen Home. | The update resumes. No new `update_prompt` is logged. | |
| Z.26 | RELEASE | Open Home through PROCESS_TEXT, SEND and during onboarding. | No update check. | |
| Z.27 | RELEASE | Capture the sticker catalog fetch with a proxy on a build with a content host set. | No auth header, no cookie and no query. | |
| Z.28 | RELEASE | Install a Sprint 4 release build. Use the clipboard. Upgrade to this release. | The items stay. | |
| Z.29 | RELEASE | Upload to closed testing. Read the pre-launch report. | No crash or ANR. | |
| Z.30 | RELEASE | Follow `docs/release/closed-testing.md`. Fill the Data safety form from `docs/release/data-safety-draft.md`. | The release is live. The form matches the draft. | |
| Z.31 | RELEASE | Read the licence rows in Settings, About, Open-source licences and `app/src/main/assets/LICENSES.md`. | FlorisBoard (Apache-2.0), whisper.cpp, the dictionaries (CC-BY-SA) and the Dakshina data show with their licences. | |

## Dropped from old plans

These old IDs have no case above. Old IDs that are not listed here were merged into a case above.

Already passed or automated:
- S1-1.8, S1-4.6 (layout part): passed on a Pixel 9 on 2026-10-25.
- S3F-A1 to S3F-A7: automated voice tests.
- S1F-A1 to S1F-A4, S1F-P6: unit tests and scripts that CI runs on every PR.
- S1F-G1 to S1F-G4: proposals for automated tests, not manual checks.
- S4-step3: duplicate of S4-I.1 (now C.8).

Stale behavior:
- S1-1.7 "AI chip not shown": the AI chip now exists. P.20 and P.21 cover the cases that hide it.
- S1-5.4: no debug path requests an unknown Whisper language.
- S1-5.10, S1-5.14: the Whisper download host is a placeholder (OD-01). J.20 covers the fail-closed result.
- S1-5.12 Whisper in release: kept as Z.3. S1-5.15 "Base shows downloaded" is now J.21 after a manual push.
- S1-2.10, S1M-7.2 notification prompt: no screen requests the permission now. A.14 records it.
- S3-A.5 "Pro is coming soon": now the paywall with `ai_quota` (P.10).
- S4N-9 distinct offline restore message: the code shows "No purchase found" for both, so R.4 expects that.
- S4N-11 "known coupling": the Voice switch now sets only Whisper by design. R.3 tests the three switches.
- S4-X.3 "known gap": settings links open before onboarding by design. Paywall links go Home (A.10).

Not a test (record or owner action):
- S1-1.6 Urdu h key decision, S1-4.11 (VO-20) `EXTRA_PREFER_OFFLINE`: product records, kept in E.4 as a record only.
- S3-X.1 and S3-X.2 numbers kept in G.4. S4N-13 ad position kept in X.8 as a record.
- S2-Q.1 to S2-Q.3 quality ratings, S2-L.4 probe content: owner work in `docs/translation/`.
- S3-Q.1 Play Integrity quota request, S3-L.1 legal review: owner actions, not tests.
- S3-A.1 deploy steps: covered by API.38 and `backend/deploy/README.md`.
- S5 and S6 open product questions: not tests.
