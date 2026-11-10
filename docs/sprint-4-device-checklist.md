# Sprint 4 device checklist and exit check

The Sprint 4 build was checked in a sandbox with no working emulator, no Play Console access, no Firebase project, no
deployed kb-api and no release keystore. A physical phone was attached, but no check ran on it. Everything below needs
a device, a Play test track, a deployed backend or an owner action before Sprint 4 is signed off. Each row names the
plan task, decision or Review Focus item it comes from (`T<n>` = plan task, `D<n>` = plan decision, `RF<n>` = plan
Review Focus).

Suggested devices: a Pixel and a Samsung (API 33+), one API 26-30 phone, an API 31+ phone for Material You, and a
mid-tier phone (Pixel 6a / Galaxy A54 class) for the performance rows. Billing rows need a licence tester account
and a build installed from a Play test track (internal or closed).

Fill in the Result column only after running the check. Record device, Android version, build and date there.

## Automated exit check (sandbox, 2026-11-09)

| Check | Command | Result |
|---|---|---|
| Debug build | `$G ./gradlew assembleDebug` | PASS |
| Unit tests | `$G ./gradlew test testDebugUnitTest :build-logic:convention:test` | PASS |
| Check, lint and release bundle (combined) | `$G ./gradlew --continue check :app:lintDebug :app:bundleRelease` | FAIL ("Google-Services plugin not found"). Not run again as one command after the fix |
| Check and lint | `$G ./gradlew --continue check :app:lintDebug` | PASS after the fix in `830eff3c` |
| Release bundle without release properties | `$G ./gradlew :app:bundleRelease` | FAIL on purpose ("Set the Gradle property aikb.entitlement.publicKey for a release build") |
| Release bundle with placeholders | `$G ./gradlew :app:bundleRelease -Paikb.entitlement.publicKey=PLACEHOLDER_NOT_A_KEY -Paikb.admob.appId=<sample id>` | PASS. Signed with the debug key. The mapping file id is not blank |
| 16 KB alignment | `scripts/check-16kb-alignment.sh --min-libs 4 app/build/outputs/apk/debug/app-debug.apk` | PASS (23 libraries, 4 unaligned 32-bit, informational) |
| Fork markers | `scripts/check-fork-markers.sh` | PASS |
| Data flows | `scripts/check-data-flows.sh` | PASS (37 tracked artifacts) |
| Backend tests | `cd backend && $G ./gradlew test` | PASS (207 tests, 3 skipped) |
| IME-only cold start | `ImeOnlyColdStartTest` on an API 34 emulator | Not run. The emulator does not start in the sandbox. See I.1 |

`$G` is the sandbox truststore prefix from the plan's Global Constraints.

## I. Instrumented exit check

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| I.1 | IME-only cold start loads no ad SDK | Start an API 34 emulator (Google APIs image). Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.manjul.aikeyboard.isolation.ImeOnlyColdStartTest`. | PASS. No GMA class is loaded in the app process after 30 s of typing with only the keyboard. | T6, T13 step 3, D22 | |

## B. Billing (licence tester, Play test track)

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| B.1 | Buy monthly and annual | Licence tester account, build from the internal track. Open the paywall, buy `monthly`. Cancel it in Play, then buy `annual`. Open a text field after each purchase. | Pro appears in the app and in the IME within 2 s of the purchase callback. | T2, T4 | |
| B.2 | Trial start on annual | With a tester who never had a trial, pick the annual plan with the 7-day free trial. Watch DebugView or the `AiKbAnalytics` logcat tag. | `trial_start` is logged once, with the plan. | T2, T5 | |
| B.3 | Restore | Uninstall and reinstall, open the app. Then sign in to a second device with the same Google account and open the app. Also tap Settings → Subscription → Restore purchases. | Pro is restored on both devices without a new purchase. | T2, T11 | |
| B.4 | Grace and on-hold | Use the Play test cards "Slow test card, declines after a few minutes" for a renewal in grace, then let it go into account hold. | Settings home shows "Fix payment" in both states. A tap opens the Play subscriptions page. | T3, T11 | |
| B.5 | Refund removes Pro | Refund the tester's order in Play Console (Order management → Refund, with entitlement removal). | Pro goes within one RTDN cycle (one push to `/internal/rtdn`), in the app and at the next IME start. | T3, RF4 | |
| B.6 | Offline purchase | Turn the network off right after the Play purchase sheet closes. Reconnect after 1 min. | The paywall shows "Activating Pro…". Pro never flashes on and off. Pro shows after the reconnect. | T2, RF1 | |

## P. Paywall

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| P.1 | Each trigger header | Open the paywall from each trigger: T1 (O6 in onboarding), T2 (IME quota card "See Pro"), T3 (a premium theme), T4 (Remove ads), T7 (Settings → Subscription), T8 (win-back card on Home). | Each header matches its trigger. `paywall_view` and `paywall_close` carry the right trigger. | T5 | |
| P.2 | Close button on a slow network | Throttle the network (developer options or a proxy at 2G speed). Open the paywall. | The close button is visible at once, before the prices load. | T5 | |

## A. Ads and consent (free user, debug test ads)

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| A.1 | UMP form, then privacy options | Debug build with an EEA debug geography (UMP test device id). Fresh install, finish onboarding. Open Settings → Privacy. | The UMP form shows after onboarding. The ad privacy options row is in Settings → Privacy and opens the form. | T6, T11 | |
| A.2 | No ad in the first session | Fresh install. Go through O1 to O6, then use the app for the rest of the first session. | No ad of any format shows in O1 to O6 or in the first session. | T7 | |
| A.3 | No app-open ad from restricted entries | Second session or later. Open the app through PROCESS_TEXT ("Translate" on a selection), SEND (share text), a return from the Play Billing sheet and a return from the update flow. | No app-open ad in any of them. | T7, RF3 | |
| A.4 | Interstitial rules and caps | Do 2 completed feature uses (a translate, a theme apply), then go Home. Repeat at once, then over a day. | An interstitial shows only after 2 uses and then Home. At most one in 3 min and 6 in a day. | T7, D14 | |
| A.5 | Pro removes ads at once | With the Home banner and a native ad on screen, buy Pro. | Both ads go at once. No new ad loads in this session. | T7, RF2 | |
| A.6 | IME PSS after a native ad | Open the Translator with a native ad. Leave the app. Wait 60 s. Open a text field in another app. Run `adb shell dumpsys meminfo com.manjul.aikeyboard`. | IME PSS ≤ 120 MB (TRD §11.1 #1.2). A miss goes to product as an OD-12 option B request. | T13, D13 | |
| A.7 | No ads host from the keyboard | Proxy on (mitmproxy or Charles). Force-stop the app. Use only the keyboard for 30 s in another app. | The capture shows no ads host (`googleads`, `doubleclick`, `googlesyndication`, `admob`). | T6, TRD §11.1 | |

## U. In-app update (internal app sharing)

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| U.1 | FLEXIBLE update | Install an older build from internal app sharing. Upload a newer version code. Open Home. Accept the FLEXIBLE update. | The download runs. A "Restart" snackbar shows. Restart installs the new build. At most one prompt in 24 h. | T8 | |
| U.2 | IMMEDIATE with the RC gate | Set RC `min_supported_version` above the installed version. Open Home. Decline the update. Resume Home. | The IMMEDIATE update shows. Decline never exits the app. It asks again on the next resume. | T8 | |

## T. Themes and key sounds

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| T.1 | 12 presets and borders | Home → Appearance. Apply each of the 12 presets (Pro on for the premium ones). Toggle key borders. Open a text field after each. | Each preset applies live in the preview and in the IME. Borders on and off both work. | T9, T10 | |
| T.2 | Key sounds | Pick each key sound (soft, crisp, wood, pop) and type fast. Pick `system` and toggle the system touch-sound setting. | No lag or clipping while typing fast. `system` follows the system touch-sound setting. | T10 | |
| T.3 | Material You | API 31+: pick the Material You preset. Change the wallpaper. Open a text field. | Keyboard colours follow the new wallpaper. | T9 | |

## D. Data deletion and logs

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| D.1 | Delete my data, server side | Needs kb-api deployed with `POST /v1/account/delete` and the Firebase user deleter (the route is in `backend/` at `dc40619`, not deployed yet). Use AI once, so that kb-api holds quota rows for the uid. Settings → Privacy → Delete my data → Delete everything, online. Then read Firestore for the uid. | kb-api holds no rows for the uid (usage, entitlement links, integrity, AI reports). The Firebase Auth user is gone. Local data is wiped. `delete_data` is logged once. | T12, D19 | |
| D.2 | Release logcat audit | Release build. Run a scripted session: type in normal and password fields, translate, dictate, rewrite with AI, buy or restore Pro, delete my data. Capture `adb logcat -v time` for the app process. Grep for the typed markers, `Bearer`, `GPA.`, `eyJ`, `http`, and host package names. | No typed text, tokens, URLs or package names (PRD FR-34 AC1). | T12, RF5 | |

## G. Performance and Play gates

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| G.1 | TRD §6.4 mid-tier budgets | Pixel 6a class phone, USB, screen on. Run `./gradlew :benchmark:connectedBenchmarkAndroidTest` (`ColdStartupBenchmark`, `ImeShowBenchmark`). Read `benchmark/build/outputs/connected_android_test_additional_output/`. For p90 values, open the Perfetto traces and read the `aikb_ime_show` and `aikb_key_commit` slices. Measure the other §6.4 rows (suggestion strip, jank, memory) with Perfetto and `dumpsys meminfo`. | Every §6.4 mid-tier budget is met: app cold start ≤ 700 ms p50, keyboard cold show ≤ 350 ms p50 and ≤ 600 ms p90, warm show ≤ 80 ms p90, key commit ≤ 16 ms p90, IME PSS idle ≤ 90 MB. | T13, TRD §6.4 | |
| G.2 | Pre-launch report | Upload the closed testing bundle (`docs/release/closed-testing.md`). Read Play Console → Testing → Pre-launch report. | No crash or ANR. | T13 | |
| R.1 | Closed testing release live | Follow `docs/release/closed-testing.md`. Fill the Data safety form from `docs/release/data-safety-draft.md`. | The closed testing release is live for the tester list. The Data safety form matches the draft. | T13, T12 | |

## X. Other device-only items raised by the tasks

These are not exit rows in the plan, but earlier tasks deferred them to a device. Run them with the rows above.

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| X.1 | Ad entry from the settings key, rotation and the share sheet | Second session or later, free user. From the keyboard, open Settings with the settings key. Rotate on Home. Then open the app from the share sheet (SEND) and rotate. Then open a `ui://florisboard/paywall` deep link. | No app-open ad after the settings key. The entry is kept across rotation, so no app-open ad shows after the share entry. No app-open ad on a deep link or the paywall. | T7 | |
| X.2 | Play `isUpdateTypeAllowed` during an in-progress IMMEDIATE update | Internal app sharing, RC `min_supported_version` above the installed version. Start the IMMEDIATE update. Kill the app while it downloads. Reopen Home. Note what Play answers for `isUpdateTypeAllowed(IMMEDIATE)` (debug log or a breakpoint). | The IMMEDIATE flow resumes. No second `update_prompt` is logged. Record the real `isUpdateTypeAllowed` value: the fake says false and the code does not rely on it. | T8 | |
| X.3 | Settings deep links before onboarding | `adb shell pm clear com.manjul.aikeyboard`. Before onboarding, run `adb shell am start -a android.intent.action.VIEW -d "ui://florisboard/settings/privacy"`, then `settings/subscription` and `paywall`. | Record what opens (known gap from T11: settings deep links open before onboarding). The paywall link stays blocked. No crash. | T11 | |
| X.4 | Both privacy rows open Delete my data | Settings → Privacy → Clear data, then back, then Delete my data. | Both open the Delete my data screen. | T12 | |
| X.5 | Clear on device only, without models | Have translation history, learned words, clipboard history and a downloaded Whisper or translate model. Clear data on this device with "Also remove models" off. | History, learned words and clipboard history are empty. Consents are reset, so AI asks again. Onboarding does not show again. Pro stays. Models stay. | T12, D20 | |
| X.6 | Clear on device only, with models | Repeat X.5 with "Also remove downloaded voice and translation models" on. | The models are gone (Translate and Voice settings show "not downloaded"). | T12 | |
| X.7 | Delete everything offline | Airplane mode on. Delete everything. | "Could not delete. Check your connection and try again." Nothing is wiped. | T12, D19 | |
| X.8 | Double tap | Online. Tap Delete everything twice fast. | One request in the proxy or the kb-api log. | T12 | |
| X.9 | New Firebase ids after delete | After Delete everything, run one AI request. Check DebugView and the kb-api log. | `delete_data` was logged once. A new Firebase installation id and a new anonymous uid are used. | T12 | |
| X.10 | Pro after delete | Pro user with an active Play subscription. Delete everything. | The screen says the subscription is not cancelled. "Manage subscription" opens Play. Pro comes back after the entitlement refresh. | T12 | |
| X.11 | Ad caps reset | Free user. Delete everything, then cold start. | The first-session rule applies again (no ad in the next session). | T12 | |
| X.12 | Backup does not restore wiped data | `adb shell bmgr backupnow com.manjul.aikeyboard`. Clear on device. Restore the backup (`adb shell bmgr restore`). | History, clipboard and learned words do not come back. | T12 | |
| X.13 | Rotation while deleting | Online with a slow network. Delete everything, rotate during "Deleting…". | The work continues and the state is kept. | T12 | |
| X.14 | Upstream user dictionary wipe | Add a word in Settings → Dictionary → User dictionary. Keep the IME closed. Clear on device. Reopen the user dictionary. Repeat with "Enable Floris user dictionary" off, then turn it on. Repeat once more with the keyboard open in a text field while you clear, then type the word. | The user dictionary is empty every time, and the keyboard stops suggesting the word without a restart. | T12 fix 1, final review M-3 | |
| X.15 | Partial wipe and retry | Debug build. Make the clipboard database read-only (`adb shell run-as com.manjul.aikeyboard.debug chmod 444 databases/clipboard_history*`). Delete everything online. Tap Try again, then restore the permissions and tap Try again. | The partial message shows with Try again. Try again sends no second kb-api request and logs no second `delete_data`. After the fix the retry succeeds. | T12 fix 1 | |
| X.16 | Release signing | On the owner's machine, set the four `aikb.signing.*` properties and run `./gradlew :app:bundleRelease`. Run `jarsigner -verify -verbose:summary app/build/outputs/bundle/release/app-release.aab`. | The bundle is signed with the upload key, not the debug key. | T13 | |

## Sandbox results

Task 13 ran these on 2026-11-09. The command output tails are in
`.superpowers/sdd/2026-11-07-sprint-4/task-13-report.md`.

- `:app:bundleRelease` first failed with "Google-Services plugin not found". The Crashlytics plugin adds a mapping
  upload task to every minified build, and that task needs the Google-Services plugin. The fix turns the upload off
  (commit `830eff3c`). A later fix makes each build write a new random mapping file id, not the blank id. Upload
  `mapping.txt` with its resource file and the Firebase CLI (`docs/release/closed-testing.md`, step 6).
- Without `aikb.entitlement.publicKey`, `bundleRelease` fails on purpose ("Set the Gradle property
  aikb.entitlement.publicKey for a release build"). With placeholder values for it and for `aikb.admob.appId`, it
  passes. Then the bundle is signed with "CN=Android Debug", because no `aikb.signing.*` value is set. X.16 checks
  the real key.
- `:benchmark` did not compile against benchmark 1.3 (`collectBaselineProfile` is gone). It is fixed and
  `:benchmark:assembleBenchmark` passes. No benchmark ran, because the sandbox has no device for it (G.1).
- I.1 did not run. `emulator -avd Pixel_7 -no-window` (API 34) stopped with exit code 132 after it found the system
  image. A physical phone was attached, but the exit check needs an API 34 emulator, so the test did not run on it.
