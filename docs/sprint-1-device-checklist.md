# Sprint 1 device checklist and exit check

The Sprint 1 build was verified in a sandbox with no emulator and no network access to the Whisper
model host. Everything below needs a physical device (or an emulator image) before Sprint 1 is signed
off. Each item names the task or ruling it came from (`T<n>` = plan task, `R<n>` = ledger ruling).

Suggested devices: a Pixel (stock recognizer), a Samsung and a Xiaomi (OEM recognizer behaviour), a 3 GB
phone and a Cortex-A53 class phone for Whisper, and one API 31 or 32 device plus one API 33+ device.

Tick a box only after running it on a device. Record device, Android version and build in the notes column.

## 1. IME core

| # | Steps | Expected | From |
|---|---|---|---|
| [ ] 1.1 | Force-stop the app. Enable the keyboard in system settings and open any text field. Do not open the app first (IME-only cold start). | Keyboard appears and types. No crash. Policy is evaluated on the first field. | Brief; T2, Review Focus 5 |
| [ ] 1.2 | Reboot, leave the device locked. From the lock screen (for example the emergency dialer or a camera-launched text field), show the keyboard and type. | Keyboard shows without crashing (the service no longer starts before first unlock). Lock indicator on, voice chip hidden, clipboard history and learning off. | Brief; T1, T2, Review Focus 5 |
| [ ] 1.3 | Fresh install with the system language en_GB. Open the keyboard. | A usable English subtype exists and is not forced onto other locales (no `overridesImplicitlyEnabledSubtype`). | R10, T4 |
| [ ] 1.4 | Settings, Languages: add a subtype and open the layout picker. | The `org.aikeyboard` layouts (Urdu standard, Hindi phonetic) are listed and selectable. All 8 MVP subtypes appear in the system subtype switcher. | T4, T5 |
| [ ] 1.5 | Switch to Urdu (ur-PK), Arabic and Hindi layouts. Press shift and long-press keys. | Shift key renders correctly in the RTL character row; RTL key order is right to left (K1 rightmost); the Urdu aspirate and the heh forms are reachable; digits follow the numeric-row choice (R11: ur-PK defaults to Urdu digits U+06F0, western on long-press). | T5, R11, R12 |
| [ ] 1.6 | Type with the Urdu phonetic layout: the h key. | Known issue: the base key types Arabic heh U+0647 and Urdu heh U+06C1 is on long-press. Confirm the linguist accepts this or flag it. | T5 (linguist flag) |
| [ ] 1.7 | Smartbar: pin up to 3 chips, try a 4th, try to pin or reorder the mic chip. | Fourth pin rejected, mic is never pinned or reordered, order survives a keyboard restart. Translate and AI chips are not shown at all. **Code fixed, needs device re-check (S1-1.7):** the actions editor now enforces the rules (`PinnedActions`, "Pinned (n/3)"); the private-app action is exempt from the 3 pins, pending the PRD FR-07 decision. **Pixel 9 re-check 2026-10-25 (partial):** editor shows Sticky = Voice input only, "Pinned (3/3)" (Emoji, Clipboard, Settings + exempt Make private), 17 hidden actions, no Translate/AI. Drag steps (4th pin refused, hint, mic immovable, order after restart) not automatable with adb — manual. | T7 |
| [x] 1.8 | Check the visuals of the smartbar lock indicator in a private field and its long-press toggle. After changing the private flag, move to another field. | Lock indicator is legible in light and dark themes. Expected: the lock state and the long-press option update without changing field (`ImePolicyController` re-evaluates the field on every settings change). **Pixel 9 2026-10-25 (dark theme):** lock shows at the far left, mic removed; a duplicate "…" overflow appeared in password fields — fixed (S1-1.8, QuickActionsRow). Long-press has no menu: it adds the app to `privatePackages` directly (confirmed in the datastore). Live update PASS: tapping "Make private" in a normal field shows the lock, removes the mic and hides the action without changing field. Light theme PASS (lock legible). Once an app is private the keyboard hides the toggle; undo is in Settings > Private apps (by design). **Open:** the "long-press toggle" wording above implies a toggle or menu; product to confirm that long-press = add to private immediately is intended. | T7 |
| [ ] 1.9 | Predictive back gesture while the voice panel is open (IME window). | Panel closes, keyboard stays. | T11 |

## 2. Privacy and policy

| # | Steps | Expected | From |
|---|---|---|---|
| [ ] 2.1 | Open a password field (browser login, app login). Look at the smartbar. | The smartbar shows only emoji, settings and the privacy lock (with its "make this app private" action): no mic, translate, AI, clipboard or stickers, and no other quick action (undo, redo, paste, ... are hidden even if the user added them, R37). No suggestions, no learning. | TRD exit; T7, T1, R37 |
| [ ] 2.2 | Copy a normal text, then a password from a password manager (flagged sensitive), then an OTP text. Open clipboard history (enabled). | Normal text is stored. The sensitive and OTP clips are not stored. The blocked clip is not left on the primary clip for us to read (R9). History is off by default. | T3, R9 |
| [ ] 2.3 | Leave a stored clip for more than 24 h (or move the device clock forward), then open the clipboard panel. | Unpinned clips older than the TTL are removed; pinned ones stay. | T3 |
| [ ] 2.4 | Private apps screen (Settings, Privacy, home entry): add an app from the picker, open that app. | Picker lists launcher apps, the added app is treated as private (lock indicator, no voice, no learning). The suggestion list is empty (curated banking list is empty until RN-05). Screen visuals are acceptable. | T7, R15 |
| [ ] 2.5 | Switch into a password field while voice partials are composing (dictate, then tap a password field in the host app, or change the field with the keyboard open). | Voice stops. Our partial is removed from the password field and nothing is committed. No later recognizer text reaches the field. | T11, Review Focus 1, R24 |
| [ ] 2.6 | Dictate partials, then move the host cursor or edit host text mid-partial. | Our span is dropped. No host text is deleted. | T11, Review Focus 2 |
| [ ] 2.7 | Run the R24 scenario in several host apps; the input method manager may already finish composing before `restarting=true`. | The clear is a harmless no-op and no partial text remains in the field. | R24, T11 |
| [ ] 2.8 | Logcat (debug build), exercise typing, voice, clipboard and onboarding. Filter on `AiKbAnalytics` and the app package. | No typed text, transcript, clipboard text or host package name appears in any log line or event. | Global constraints, PC-10 |
| [ ] 2.9 | **Password-field egress, on a device.** The JVM version is `PasswordFieldEgressTest` (R34, runs in `./gradlew test`): it scripts a password-field session with a `ProxySelector` guard, an empty WorkManager and a Room statement log. Still do it on hardware, because the test cannot run `FlorisImeService`: with a password field focused, type, backspace, paste and tap the mic, and check the app opens no connection (per-UID counters in `NetworkStatsManager`, or a proxy). Repeat as an instrumented test when Sprint 3 adds INTERNET-using features (translation cloud, AI); that is the Sprint 3 gate. Today the only INTERNET user is the Whisper model download worker (R29), which is never started from a password field. | Zero bytes sent by the app while a sensitive field is focused. | Brief; R29, R34 |
| [ ] 2.10 | Clipboard settings: the auto-clean slider. | Range is 0 to 1440 minutes (widened from upstream's 120) in steps of 5; the 24 h default is reachable. Check it is usable and the label reads sensibly. Release note: auto-clean is on by default. | T3 |
| [ ] 2.11 | In a password field and in a private app: long-press Enter and pick the clipboard entry; use a swipe bound to "switch to clipboard". Then open clipboard history in a normal field, turn off "hide on next text field", and move to a password field (and make the app private from the lock indicator while the panel is open). | Clipboard history never opens in the sensitive field; an open panel closes when the field becomes sensitive. In normal fields the panel still opens and stays open across fields as the upstream pref says. | Final review Important 1 |
| [ ] 2.12 | Dictate partials in one field, then tap another (normal) field in the same app. | The partial is removed from the first field, nothing is committed there (same as hiding the keyboard, R25). | R39 |

## 3. Onboarding

| # | Steps | Expected | From |
|---|---|---|---|
| [ ] 3.1 | Fresh install, launch the app. | Start destination is onboarding. Splash is gone within 1 s on a cold start (cap), with no bare window if the prefs store is slow. | T8 |
| [ ] 3.2 | Walk O1 to O5: enable keyboard, select keyboard (polling picks up the change), languages, try-it field. | Each step advances on its own when the system state changes; the try-it field shows our keyboard. Process death mid-flow resumes at the right step. | T8 |
| [ ] 3.3 | Tap the mic in the try-it step and deny, then allow, the microphone permission. Also try "Only this time". | The permission sheet shows. Denial keeps the user in try-it. Disclosure copy names the active recogniser kind. | T8, T11 |
| [ ] 3.4 | **Onboarding funnel in Logcat.** On a debug build run `adb logcat -s AiKbAnalytics` and complete onboarding. | Funnel events appear in order (`onboarding_start`, `onboarding_step_view`, `ime_enabled`, `ime_selected`, `languages_set`, `onboarding_complete`), `ime_*` timings counted from `onboarding_start` (R16). Re-entering onboarding logs no funnel events. Release builds use the no-op sink and log nothing. Firebase DebugView is not available until Firebase is set up. | Brief; T6, T8, R16 |
| [ ] 3.5 | Finish onboarding, reopen the app. | A returning user with the keyboard ready starts at the final step, no onboarding flash (known minor: `remainingMs=0`). | T8 |

## 4. Voice (platform recognizer)

| # | Steps | Expected | From |
|---|---|---|---|
| [ ] 4.1 | On a Pixel, Samsung and Xiaomi, tap the mic in a chat field and speak. | Live partials appear as composing text, the waveform follows the voice, the final text is committed with a trailing space. | TRD exit; T9, T10, T11 |
| [ ] 4.2 | **Keyboard hidden mid-session.** Start voice, then hide the keyboard (back or switch app) while listening. Measure with Logcat timestamps and the privacy mic indicator. | Session goes `Idle` and the mic indicator turns off within 300 ms. No crash. On hide the uncommitted partial is discarded, not committed (R25). Check that this is acceptable to product. | Brief; R24, R25 |
| [ ] 4.3 | **Segmented sessions.** On API 33+ speak several sentences with natural pauses beyond 2 s, within the 60 s cap. | Segments are committed as they arrive and the session continues, ending after the silence window (2 s default) or the cap. On API 30 to 32, behaviour falls back to the restart policy (restart after speech-timeout or no-match after 250 ms, at most 3 in a row; busy recreate after 400 ms, at most 2). | Brief; T9, T10 |
| [ ] 4.4 | On API 31 and 32, and then on API 33+, run an on-device-only session. | The on-device recognizer is used. API 31 and 32 do not create a cloud-capable recognizer. | T10 (I1) |
| [ ] 4.5 | Airplane mode, language without an offline model installed. | An explicit message with the right download offer is shown. Never fails silently. On API 33+ the system model download (`triggerModelDownload`) works. | T10, T11, Review Focus 4 |
| [x] 4.6 | Revoke the mic permission in system settings (and let an "Only this time" grant lapse), then tap the mic. | The permission chip shows ("Allow microphone, then tap the mic again") in full, wrapping to two lines if needed, with no ellipsis; no recognizer is created. Granting through the trampoline works. **Code fixed, needs device re-check (S1-4.6):** the chip was capped at half the free width; check portrait, landscape, large font and an Urdu or Arabic UI locale. **PASS on Pixel 9 2026-10-25:** portrait at font 1.15/1.3/2.0, landscape 1.3; at 1.3+ portrait it needed the auto-size fix (chip text shrinks to 12 dp min). No recognizer created without permission. RTL not meaningful: voice strings have no ar/ur translations. | T11, Review Focus 3 |
| [ ] 4.7 | Permission trampoline on several OEMs. | The translucent trampoline looks right over other apps. Denying shows the rationale and granting logs `voice_permission_result{granted=true}`. Leaving the notice or rationale (Home) finishes the trampoline. **Code fixed, needs device re-check (S1-4.7):** `noHistory` removed (it drops the permission result); the activity finishes itself in `onStop` unless the permission dialog is up. Check on a Pixel and a Samsung. **Pixel 9 PASS 2026-10-25:** deny → rationale, `voice_permission_result` logged for false and true, Home and grant both finish the trampoline (no activity record left). Samsung still to do. | T11 |
| [ ] 4.8 | Audio focus: play music, start dictation, stop it. | Music pauses and resumes. | T10 |
| [ ] 4.9 | Check the IME hooks that have no unit test: mic key, voice panel in the smartbar, `FlorisImeService`/`KeyboardManager` hooks. | Behave as above with no regressions in normal typing. | T11 |
| [ ] 4.10 | Set "Offline only" (on-device) in voice settings with no on-device support and no Whisper model. | Voice is unavailable with an offer. It never silently uses the server recogniser. | R32 |
| [ ] 4.11 | On a service-recognizer (system default) session, check whether `EXTRA_PREFER_OFFLINE` has any effect on your devices. | It is only a hint on `SYSTEM_DEFAULT`; some services ignore it and use the network. Do not rely on it for privacy (use the on-device-only preference, R32). | T10 |

## 5. Voice (Whisper)

All Whisper download items depend on OD-01 (model host and hashes). Until then downloads fail closed (R28).

| # | Steps | Expected | From |
|---|---|---|---|
| [ ] 5.1 | **Whisper base on a 3 GB phone.** Debug build, load the base model and dictate for 30 and 60 s. Record peak PSS (`adb shell dumpsys meminfo <pkg>`) and decode time. | Stays within the memory budget with no low-memory kill of the IME. Record latency. | Brief; T12, T13 |
| [ ] 5.2 | `WhisperNative.load` with the base model. | Returns a non-zero handle. On an x86 device load returns 0 and the UI reports unavailable. | T12 |
| [ ] 5.3 | Transcribe a known en clip and a known hi clip. Include a clip producing Devanagari and one producing an emoji. | Expected text; UTF-8 is intact (no mojibake). | T12 |
| [ ] 5.4 | Request an unknown language code. | `WhisperException`, surfaced as an error, not a crash. | T12 (R26) |
| [ ] 5.5 | Free the engine while a transcribe is running (cancel dictation mid-decode). | Returns at once (R27), decode aborts at the next encoder or decoder step, no crash, no late text. Measure the abort latency. | T12, R27, T13 |
| [ ] 5.6 | Decode speed on a Cortex-A53 or A73 class phone. | Plain armv8-a build does not SIGILL and decode speed is acceptable. | T12 |
| [ ] 5.7 | Transcription latency for small on an 8 GB device and base on a 4 GB device, with 60 s of audio. | Completes inside the Whisper transcribe timeout of 120 s (R33); the spinner never exceeds it. | R33, T13 |
| [ ] 5.8 | Dictate with Offline (Whisper) in en-US and hi-IN on a debug build. Stop and start the mic quickly. | RMS animation works, silence auto-stops, one final text is inserted. Quick stop and start does not give `ERROR_AUDIO` (known: AudioRecord release can take about 100 ms). | T13 |
| [ ] 5.9 | Idle 60 s, then `adb shell am send-trim-memory <pkg> RUNNING_LOW`. Repeat after loading the model with `UI_HIDDEN` (open and leave the voice settings screen). | `RUNNING_LOW` frees the model and the next dictation reloads it. `UI_HIDDEN` does not free it (R38). | T13, R38 |
| [ ] 5.10 | Download and resume (after OD-01 hashes are pinned): download base on Wi-Fi, check `no_backup/models/ggml-base-q5_1.bin` and that no `.part` is left. Test Wi-Fi-only versus mobile data, kill the app or drop the network mid-download (Range resume, hash still verifies), and delete during a download. | File verifies by SHA-256; unmetered-only waits for Wi-Fi; resume works; delete cancels the work and removes the `.part`. WorkManager retry window (10 min, 3 attempts) is enough for the small model. | T13, R28, R30 |
| [ ] 5.11 | The "Get offline model" chip in the IME. | Deep-links to the voice settings screen. | T11, T13 |
| [ ] 5.12 | Release build. | The Offline (Whisper) option and the model section are hidden and the engine never picks Whisper (gate is empty). | Decision 5 |
| [ ] 5.13 | IME cold start. Check `adb shell dumpsys jobscheduler` and `/proc/<pid>/maps`. | WorkManager is not initialised and `libaikb_whisper.so` is not loaded just by starting the keyboard. | T13 |
| [ ] 5.14 | Rename versus delete of the model while a download finishes (rare race). | No installed file is left with state ABSENT. | T13 (parked) |
| [ ] 5.15 | Start a Whisper model download from settings and watch the screen. | Known gap: settings show no download percentage, only a state. Decide if that is acceptable. | T13 |

## 6. Packaging and permissions

| # | Steps | Expected | From |
|---|---|---|---|
| [ ] 6.1 | Install the release APK or AAB built by CI. Run `scripts/check-16kb-alignment.sh` on the APK, and on a 16 KB page-size device or emulator image start the app and dictate with Whisper. | `libfl_native.so` and `libaikb_whisper.so` are 16 KB aligned for every ABI and load on a 16 KB page-size device. AAB alignment is checked in the Main stage. | R6, T12 |
| [ ] 6.2 | Inspect the installed release app's declared permissions (`adb shell dumpsys package <pkg>`). | Exactly: `RECORD_AUDIO`, `ACCESS_NETWORK_STATE` (R23), `INTERNET` (R29), `VIBRATE` and `POST_NOTIFICATIONS` (both upstream), and WorkManager's merged `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` (R29), plus the app-private `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. `POST_NOTIFICATIONS` stays declared because upstream `CrashUtility` posts crash notifications; onboarding never requests it (R17, R35), so on API 33+ crash notifications stay off until the user grants it in system settings. | R17, R23, R29, R35 |
| [ ] 6.3 | Check exported components of the release build (including WorkManager `SystemJobService` and `DiagnosticsReceiver`). | Only the allow-listed components are exported. | T13 |
| [ ] 6.4 | Release build startup. | The IME is not `directBootAware`; no crash before first unlock. Release APK size is acceptable (debug APK grew by about 5 MB with the Whisper library). | T2, T12 |

## Pending product and ops decisions

These are not code tasks. They need an owner outside engineering.

- **OD-01: Whisper model host and SHA-256 hashes.** `WHISPER_MODEL_BASE_URL` is the placeholder
  `https://example.com/models/` and the pinned hashes are empty. Until both are filled, downloads fail closed
  (an empty hash makes the manager fail the download and discard the file, R28). The privacy policy URL is
  also a placeholder (OD-01).
- **Play listing notes for the permissions.** The merged release manifest declares: `RECORD_AUDIO` (voice,
  requested only from the mic chip), `ACCESS_NETWORK_STATE` (R23, the online check in engine selection),
  `INTERNET` (R29, used only by the Whisper model download worker; no URL or path logging), `VIBRATE`
  (upstream, key haptics), `POST_NOTIFICATIONS` (upstream, for crash notifications; we never request it, R17 and
  R35), and from WorkManager `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE` (R29). All are normal
  permissions except `RECORD_AUDIO` and `POST_NOTIFICATIONS`. The Data safety and permissions text must cover
  them and keep the "audio never leaves the phone" wording accurate for the Whisper and on-device-only paths.
  Upstream `SetupScreen.kt` still contains a `POST_NOTIFICATIONS` request (line 230) but no route reaches it: the
  setup route shows the new onboarding (`Routes.kt`), so it is dead code. Decide whether to drop the permission
  or surface a notification step when a feature needs it.
- **`whisper_enabled_langs = []` in release.** No language has passed the native-speaker evals, so release
  builds do not expose Whisper (decision 5). Debug and beta enable all languages. Set the list per language
  only after the evals pass.
- **TRD Whisper size text.** The TRD still says a downloadable "small" model and the constraints say base
  about 140 MB and small about 470 MB. The build ships q5_1 files: `ggml-base-q5_1.bin` (57 MB) and
  `ggml-small-q5_1.bin` (182 MB) (R30). Update the text, and note the small accuracy drop against f16.
  The small model is offered at 5.5 GiB of reported RAM, which stands for the "6 GB" device class (R31).
- **PRD section 12 analytics enums.** The shipped catalog is a superset of the PRD values (R13): `engine`
  is `system_on_device`, `system_default` or `whisper` (PRD: `on_device`, `system`); `end_reason` also has
  `cap` and `field_change` (PRD: `user`, `silence`, `error`); `voice_unavailable` and `pack_download_start`
  and `pack_download_done` are not in the PRD table. Event names are unchanged. Update the PRD so data and
  product use the same enum values.
- **Other open product flags** from the sprint decisions: curated banking-package list is empty until
  RN-05; ur-PK defaults to Urdu digits (R11); ur capitalization is off (R12); onboarding O4 sets languages
  and the translate pair only; the O6 paywall and pack downloads are Sprint 2 and 4; hide-keyboard discards
  the partial (R25); on-device-only users on unsupported phones get "unavailable" instead of working voice
  (R32).

## TRD section 8 Sprint 1 exit criteria mapped to tests

TRD section 8 Sprint 1 says: "in a password field no smartbar action appears and the Robolectric
no-network test passes; voice shows live partials; onboarding funnel events visible in DebugView."
The plan narrows it to four checks. Each test file below was confirmed to exist at the Sprint 1 head.

| Criterion | Covered by | Notes |
|---|---|---|
| Password field: no smartbar action | `app/src/test/kotlin/com/manjul/aikeyboard/fork/smartbar/PolicyChipFilterTest.kt` (`password field keeps only emoji and settings`, `locked decision hides everything but emoji and settings`) and `PolicyQuickActionsTest.kt` (`sensitive fields show only emoji, settings and the private-app action`, R37); decision source `ime/policy/.../SensitiveFieldPolicyMatrixTest.kt` (about 400 cases); `app/.../fork/policy/ImePolicyControllerTest.kt` | Emoji, settings and the privacy lock / private-app action stay by design (they take no field content); every other quick action is hidden (R37). The on-screen result is device item 2.1. |
| Password field: no Room write | `app/src/test/kotlin/com/manjul/aikeyboard/fork/policy/PasswordFieldEgressTest.kt` (`scriptedPasswordSessionMakesNoNetworkCallAndNoRoomWrite`): a Room `QueryCallback` records every INSERT/UPDATE/DELETE on the clipboard database during the scripted session; plus `app/.../ime/clipboard/ClipboardCaptureGateTest.kt` (gate unit cases) | R34. The paste step mirrors `ClipboardManager.onPrimaryClipChanged` (real gate in front of the real DAO); `ClipboardManager` itself needs `FlorisApplication` and is not instantiated. Device item 2.2. |
| Robolectric no-network test (TRD wording) | `PasswordFieldEgressTest` (R34): scripted password-field session (onStartInput, restart from a normal field, key presses, backspace, paste, mic action) with a `ProxySelector` guard that records any connection attempt and an empty WorkManager (no Whisper download queued). `positiveControlsProveTheDetectorsFire` shows the network, Room and WorkManager detectors fire. | Covers java.net and OkHttp URL connections (the Whisper worker's path) but not raw native sockets. Sprint 1 has no suggestion engine, so there is no suggestion tap to script. `FlorisImeService` itself cannot run under Robolectric, so the script drives the same policy controller, voice session and gate it wires. Device item 2.9. |
| Voice shows live partials | `core/voice/.../SpeechSessionTest.kt` (`partialsEmitComposingAndFinalCommitsWithTrailingSpace`); `ime/voice-ui/.../ComposingSpanControllerTest.kt` and `VoiceControllerTest.kt`; `app/.../fork/voice/VoiceImeSessionTest.kt` (`ownPartialUpdatesKeepReplacingTheRegion`, the password and host-edit cases) | Fakes only. A real `SpeechRecognizer` showing partials is device item 4.1. |
| Onboarding funnel events | `app/.../fork/onboarding/OnboardingViewModelTest.kt` (start, step, languages, skip, re-entry logs nothing, mic denial); `core/analytics/.../EventCatalogLimitsTest.kt` (names and limits) | The Logcat sink (`LogcatSink`, tag `AiKbAnalytics`, wired only when `BuildConfig.DEBUG`) replaces DebugView because Firebase is not set up. `LogcatSink` itself has no unit test. Device item 3.4. |
