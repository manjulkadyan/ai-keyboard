# Sprint 1 fixes test plan (voice disconnect, privacy logs)

Covers the two fixes made after the Sprint 1 device run. Each case names the item it re-checks in
`docs/sprint-1-device-checklist.md` (column "Checklist") and, where it overlaps, the step in
`docs/sprint-1-manual-test-plan.md` ("Manual"). Tick both documents when a case passes.

- **Package:** debug `com.manjul.aikeyboard.debug`, release `com.manjul.aikeyboard`
- **Type:** **A** = automated (JVM / Robolectric, runs in `./gradlew test`), **M** = manual on a device
- Mark each case **Pass**, **Fail** (write what you saw) or **Blocked**. Record device, Android version and build.

## The fixes

**F1, voice disconnect (no ticket, `core/voice`).** On Pixel 9 / API 37 the language-support probe
(`PlatformRecognizerCapabilities.onDeviceSupports`) destroyed its on-device `SpeechRecognizer` just before the
session's recognizer bound. The system recognition service shut down, the session got
`ERROR_SERVER_DISCONNECTED` (11) and the user saw "Voice typing needs a connection". Now:

- the probe recognizer is destroyed `PROBE_RELEASE_DELAY_MS` (3 s) after it answers (result or error) or times out, once (`PlatformRecognizerCapabilitiesTest`);
- positive answers are cached per language for the life of the capabilities object (one per
  `VoiceImeSession`, so per IME service instance; `MicPermissionActivity` has its own). Negative and `null`
  answers are not cached;
- `SpeechSession` reconnects once (after `busyDelayMs`, 400 ms) on error 11. A second 11 in the same session
  fails as `VoiceErrorKind.NETWORK`. The allowance resets on every `start`.

Already verified once on Pixel 9 / API 37: 3 sessions OK, logcat `Client <uid> has opened 2 sessions`, no error 11.

**F2, privacy logs (`S1-privacy-logs`).** Debug builds ran Flog at every level, and info/debug lines carry key
labels (password fields too), spell-check text (`text=`) and `EditorInfo` summaries. Flog now runs at error +
warning only unless the build is made with `-Paikb.verboseFlog=true` (`BuildConfig.VERBOSE_FLOG`). Release
stays silent (`isFloggingEnabled = BuildConfig.DEBUG`). Two error lines were scrubbed:
`KeyboardManager` "Received unknown key" (now only the key type) and `HanShapeBasedLanguageProvider`
SQLiteException (no composing text).

## 0. Setup

```sh
export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/Users/manjul/StudioProjects/workspace/apk/ai-keyboard/.sandbox-tools/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"
./gradlew :app:assembleDebug                       # default build: error + warning only
./gradlew :app:assembleDebug -Paikb.verboseFlog=true   # verbose build, only for case P5 (keep the APK apart)
./gradlew :app:assembleRelease                     # for case P4

PKG=com.manjul.aikeyboard.debug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
# Voice log: our analytics, the platform recognizer and the system speech service
adb logcat -v time AiKbAnalytics:V SpeechRecognizer:V RecognitionService:V '*:S' | tee voice.log
# Wider net for F1 (service lifecycle lines like "Client ... has opened 2 sessions" have OEM-specific tags)
adb logcat -v time | grep -iE "speech|recogni|ondevice|has opened|error.?11|disconnect" | tee f1.log
```

Useful one-liners:

```sh
adb shell am force-stop $PKG                            # cold IME process (next field starts it)
adb shell pidof $PKG                                    # empty = process dead
adb shell cmd connectivity airplane-mode enable         # airplane on (disable to turn off)
adb shell ime set $PKG/com.manjul.aikeyboard.FlorisImeService   # make our keyboard current
grep -c '"error_code":11\|error_code=11' voice.log      # count failed sessions with code 11
```

`voice_session_end` carries `error_code` only when the session ends in an error. A successful reconnect is
**not** visible in analytics (see gap G3); look for two recognizer binds in `f1.log` instead.

## 1. Automated tests

### Existing

| ✓ | ID | Test | Covers | Type |
|---|---|---|---|---|
| [ ] A1 | `core/voice/.../SpeechSessionTest.kt` `serverDisconnectReconnectsOnceThenFailsAsNetwork` | First 11 destroys the engine, stays `Starting`, a new engine is built after 400 ms; second 11 ends in `Error(NETWORK)` with `lastErrorCode == 11` and no third engine. | F1 retry and exhaustion | A |
| [ ] A2 | `SpeechSessionTest.kt` `serverDisconnectReconnectAllowanceResetsPerSession` | After `cancel` + `start`, the first 11 retries again. | F1 per-session reset | A |
| [ ] A3 | `core/voice/.../platform/LanguageInstalledTest.kt`, `SpeechEngineSelectorTest.kt`, `PlatformSpeechEngineTest.kt` | Language matching and engine choice unchanged. | F1 regression | A |
| [ ] A4 | `scripts/check-fork-markers.sh` | Every F2 hunk in upstream files has `FORK(S1-privacy-logs)` and a `FORK_PATCHES.md` row. | F2 fork rules | A |

Run: `./gradlew :core:voice:testDebugUnitTest :ime:voice-ui:testDebugUnitTest :app:testDebugUnitTest && scripts/check-fork-markers.sh`

### Gaps to add

| ID | Proposed test | Why | Feasibility |
|---|---|---|---|
| G1 | `core/voice/.../platform/RecognizerCapabilitiesTest.kt` (Robolectric). Use `ShadowSpeechRecognizer` (4.17 has `getLatestSpeechRecognizer`, `triggerSupportResult`, `triggerSupportError`, `isDestroyed`). Cases: (a) after a positive result the probe is **not** destroyed, `ShadowLooper.idleFor(2999 ms)` still not, `idleFor(1 ms)` destroyed; (b) same for `triggerSupportError` (returns `null`); (c) a second `onDeviceSupports("en")` after a positive answer returns `true` without creating a recognizer (`getLatestSpeechRecognizer` unchanged); (d) after a negative answer the next call probes again, and if that one says installed the answer flips to `true` (model downloaded later); (e) no answer within 1.5 s returns `null` and destroys the recognizer. | No test covers the delayed destroy or the cache today. | Feasible. Needs `@Config(sdk = [34])` (API 33+ path) and must match the android-all pin in `gradle/robolectric-offline.gradle.kts`. `installedOnDeviceLanguages` is set through `RecognitionSupport.Builder`. |
| G2 | `SpeechSessionTest`: 11 on a **restart** (segmented API 30-32 restart policy, or after a busy recreate) still gets exactly one reconnect; 11 after the first partial keeps the committed text. | Interaction with `restarts` / `busyRetries` counters is untested. | Feasible with the existing rig. |
| G3 | Decide whether a successful reconnect should be counted (e.g. a `reconnects` param on `voice_session_end`, no content). Without it we cannot see in the field how often F1's workaround fires. | Observability. | Product decision; EventCatalog allow-list change. |
| G4 | `FlorisApplication` Flog levels: JVM test or lint check that `flogLevels` is `ERROR or WARNING` when `VERBOSE_FLOG` is false. Cheapest: a unit test reading `BuildConfig.VERBOSE_FLOG == false` in the default `:app` test build, so a stray `-Paikb.verboseFlog=true` in `gradle.properties` fails CI. | Guards against the opt-in leaking into shared builds. | Feasible. |

## 2. F1 device cases (voice disconnect)

Device matrix (run V1 to V3 on every row; the rest on the reference device unless noted):

| Device | Why | Path |
|---|---|---|
| Pixel 9, API 37 (reference) | Where the bug was seen | API 33+ probe, on-device recognizer |
| Pixel, API 33 to 36 | Probe + Google recognizer on older releases | API 33+ probe |
| Samsung (One UI, API 33+) | Samsung or Google service as default recognizer | API 33+ probe |
| Xiaomi (HyperOS/MIUI, API 33+) | OEM recognizer, aggressive process killing | API 33+ probe |
| API 31 or 32 device | `onDeviceSupports` returns `null` without probing, so only the reconnect applies | No probe |
| Emulator API 34 with Google Play image | Cheap repeat runs; on-device model may be missing (exercise V7) | API 33+ probe |

| ✓ | ID | Steps | Expected | How to observe | Checklist | Type |
|---|---|---|---|---|---|---|
| [ ] V1 | **Cold IME process, online.** `adb shell am force-stop $PKG`. Open Messages, tap the field, tap the mic, speak a sentence. | Listening within ~1 s, partials, final text committed. No "needs a connection". | `f1.log`: one probe bind, the session bind, `has opened 2 sessions` (or OEM equivalent), no `error 11` / `ERROR_SERVER_DISCONNECTED`. `voice.log`: `voice_session_end` with `end_reason` not `error`. | 4.1 | M |
| [ ] V2 | **Warm IME process.** Right after V1, tap the mic again 2 more times, finishing each session. | All 3 sessions work. Second and third start faster (cached positive answer, no probe). | `f1.log`: no new probe recognizer created for the 2nd and 3rd session. `grep -c error_code=11 voice.log` = 0. | 4.1 | M |
| [ ] V3 | **Airplane mode, model installed.** `adb shell cmd connectivity airplane-mode enable`, force-stop, repeat V1 with a language that has an on-device model (en). | Dictation works offline. No network message. | As V1. | 4.4, 4.5 | M |
| [ ] V4 | **Airplane mode, no model.** Pick a language with no on-device model (try ur or ar). Tap the mic. | Explicit "language unavailable" message with the download offer (API 33+ `triggerModelDownload`), not "needs a connection" and not silence. | `voice.log`: `voice_unavailable` or `voice_session_end` with `error_code` 12/13, never 11. | 4.5, 4.10 | M |
| [ ] V5 | **Rapid double-tap inside the 3 s probe window.** Force-stop. Tap the mic, stop it within 1 s, tap again immediately (repeat 5 times fast). | Every start reaches Listening or a clean message. No crash, no stuck "Starting", mic indicator turns off when stopped. | `f1.log`: no error 11. `adb logcat -b crash` empty. `adb shell dumpsys media.audio_flinger \| grep -i record` shows no lingering record client after the last stop. | 4.2, 4.9 | M |
| [ ] V6 | **Hide during probe.** Force-stop, tap the mic and press back within 0.5 s. Wait 5 s. | No crash. Session `Idle`. The probe recognizer is still destroyed (no leaked binding). | `adb shell dumpsys activity services \| grep -iA3 recognition` after 5 s: our UID no longer bound. Crash buffer empty. | 4.2 | M |
| [ ] V7 | **Language switch cache.** Enable en, hi, ur, ar. Dictate once in each (online). Then switch back through them. | First use per language probes once; repeat uses of a language reported installed do not probe again. Languages reported **not** installed probe each time (expected). | `f1.log`: count probe binds per language; expect 1 for installed languages, 1 per attempt for others. | 4.1, 4.5 | M |
| [ ] V8 | **Model downloaded after a negative answer.** With a language whose model is missing (from V4/V7), accept the download offer (or download it in Settings → System → Speech / Google app). Without restarting the keyboard, tap the mic in that language. | On-device dictation now works in that language (the negative answer was not cached). | `f1.log`: a fresh probe followed by a successful session. | 4.5 | M |
| [ ] V9 | **Model removed after a positive answer** (cache cannot see it). Dictate in hi so it is cached, then remove the hi offline model in system speech settings and dictate again in the same IME process. | A clear "language unavailable" message (error 12/13), not "needs a connection", no crash. Accept this as known: the cache only clears with the IME process (`am force-stop`). | `voice.log`: `error_code` 12 or 13. | 4.5 | M |
| [ ] V10 | **Retry exhaustion still shows the network message.** Force the service to drop twice: while Listening run `adb shell am force-stop com.google.android.tts` (on-device service on Pixel; on other OEMs find it with `adb shell settings get secure voice_recognition_service`) and repeat as soon as the session reconnects. | First drop: brief Starting, then Listening again (one reconnect). Second drop: "Voice typing needs a connection" shown, session ends. | `voice.log`: one `voice_session_end` with `end_reason=error`, `error_code=11`. `f1.log`: exactly 2 session binds. | 4.1 | M (A1 covers logic) |
| [ ] V11 | **Probe timeout path.** On the emulator or a slow device, cold start the device (reboot) and tap the mic as soon as the keyboard shows, before the recognition service is warm. | Session still starts. If the probe times out (1.5 s), the recognizer is destroyed at once (timeout path is **not** delayed); check whether error 11 comes back here and the reconnect recovers it. | `f1.log`: look for 11 followed by a successful second bind. Report if the user ever sees the network message. | 4.1 | M |
| [ ] V12 | **API 31/32.** On the API 31 or 32 device run V1, V2 and V10. | No probe (`onDeviceSupports` is `null`), sessions work, retry behaves as in V10. API 31/32 never create a cloud-capable recognizer. | `f1.log`: no `checkRecognitionSupport` / probe bind. | 4.3, 4.4 | M |
| [ ] V13 | **Permission trampoline path.** Revoke mic permission, tap the mic, grant through the trampoline (it builds its own capabilities object and probe). Start dictation immediately. | Session works, no error 11 right after the grant. | `f1.log`. | 4.6, 4.7 | M |
| [ ] V14 | **Audio focus.** Play music, dictate, stop. | Music pauses and resumes, also when the session reconnected once (repeat with V10's first drop). | Listen. `adb shell dumpsys audio \| grep -A5 "Audio Focus stack"`. | 4.8 | M |

### Regression of checklist 4.1 to 4.10

Re-run device checklist items **4.1 to 4.10** on the reference device and on one non-Pixel device (manual plan
section "Voice" covers the single-device steps). Cases V1 to V14 above map onto them; any item not hit by a
V-case (4.3 segmented sessions with pauses, 4.6 permission chip, 4.9 normal typing after voice, 4.10 "Offline
only" with no on-device support and no Whisper model) must be run as written in the checklist. 4.10 must still
show "unavailable" with an offer and never fall back to the server recognizer.

## 3. F2 device cases (privacy logs)

Use a login page in Chrome (password field) and a normal chat field. In every case type a marker that is easy
to grep, e.g. `zqxmarker` in the normal field and `Pw7zqx!` in the password field, plus one dictated sentence
and one copied clip `clipzqx`.

```sh
adb logcat -c
PID=$(adb shell pidof $PKG)
adb logcat -v time --pid=$PID > ime.log            # everything our process prints
# after the run:
grep -nE "zqx|Pw7|clipzqx" ime.log                 # typed / copied markers: must be 0 lines
grep -nE "label=|text=|composing=|EditorInfo|info=|packageName|com\.android\.chrome|com\.google\.android\.apps\.messaging" ime.log
grep -nE " [DI] (KeyboardManager|FlorisImeService|FlorisSpellCheckerService|EditorInstance|KeyboardState)" ime.log
```

| ✓ | ID | Steps | Expected | How to observe | Checklist | Type |
|---|---|---|---|---|---|---|
| [ ] P1 | **Default debug build, typing.** Install the default debug APK. Type the markers in the normal field and the password field, use backspace, long-press keys, switch layouts (en, hi, ur, ar). | No marker, key label, `text=`, `composing=` or `EditorInfo`/`info=` summary in any line. No D/I lines from Flog tags. | The three greps above return nothing (third grep: no Flog D/I lines; W/E lines are allowed if they hold no content). | 2.8; Manual 3.x | M |
| [ ] P2 | **Default debug build, spell check and voice.** Turn on the system spell checker using our service, type misspelled words; dictate one sentence; copy and paste `clipzqx`. | No transcript, spell-check word or clip text in logs. `AiKbAnalytics` lines carry only buckets/codes. | Same greps, plus `grep -n AiKbAnalytics ime.log` reviewed by eye. | 2.8 | M |
| [ ] P3 | **Host package names.** Repeat P1 in 3 apps (Chrome, Messages, one private app). | No host package name in our process's lines. | Second grep (add the 3 package names). | 2.8, 2.4 | M |
| [ ] P4 | **Release build.** Install the release APK (`com.manjul.aikeyboard`), repeat P1 and P2. | No Flog output at all; analytics go to the no-op sink. | `adb logcat -v time --pid=$(adb shell pidof com.manjul.aikeyboard) > rel.log`; all greps empty, and `grep -cE " [DIWE] " rel.log` shows only system/framework lines. | 2.8 | M |
| [ ] P5 | **Verbose opt-in build.** Build with `-Paikb.verboseFlog=true`, install, type once. | Info/debug Flog lines return (proves the switch works). Never distribute this APK; reinstall the default build afterwards. | `grep -cE " [DI] FlorisImeService" ime.log` > 0. `aapt dump` is not needed; `BuildConfig.VERBOSE_FLOG` is visible in `app/build/generated/source/buildConfig/debug/.../BuildConfig.java`. | none | M |
| [ ] P6 | **Default build flag is off.** | `VERBOSE_FLOG = false` in the generated `BuildConfig` of a plain `assembleDebug` and of CI builds; no `aikb.verboseFlog` in `gradle.properties`. | `grep -rn VERBOSE_FLOG app/build/generated/source/buildConfig/` and `grep -rn verboseFlog gradle.properties ~/.gradle/gradle.properties`. | none | A (see G4) |
| [ ] P7 | **Scrubbed error lines.** Code review: `KeyboardManager` "Received unknown key of type", `HanShapeBasedLanguageProvider` SQLiteException without `composing=`. Search for other error/warning lines that still interpolate user content. | No `flogError`/`flogWarning` interpolates typed text, composing text, clipboard text or `EditorInfo`. | `grep -rnE 'flog(Error\|Warning)' app/src/main/kotlin` and review each hit that interpolates a value. | 2.8 | M (review) |
| [ ] P8 | **Crash path.** Trigger a handled error (e.g. pick a Han layout with a broken DB if reproducible, otherwise skip) and check the crash buffer. | Exception messages carry no user content. | `adb logcat -b crash -d`. | 2.8 | M |

## 4. Exit criteria

- A1 to A4 green; G1 written (or ticketed) before Sprint 2 voice work starts.
- V1, V2, V5, V10 pass on Pixel 9 / API 37 and on at least one Samsung or Xiaomi; V12 on an API 31/32 device.
- No user-visible "needs a connection" in any case except V10's second drop (and real network failures on
  service recognizers).
- P1 to P4 return zero hits; P5 confirmed and its APK removed from the device.
- Checklist 4.1 to 4.10 and 2.8 re-ticked with device, Android version and build.
