# Sprint 3 device checklist and exit check

The Sprint 3 build was checked in a sandbox with no emulator, no Docker, no Firestore emulator, no Firebase project
and no Anthropic key. Everything below needs a physical device, a deployed backend or an owner action before Sprint
3 is signed off. Each row names the plan task, decision or Review Focus item it comes from (`T<n>` = plan task,
`D<n>` = plan decision, `RF<n>` = plan Review Focus).

Suggested devices: a Pixel and a Samsung (API 33+), one API 26-30 phone, a 3 GB (low-tier) phone, and a mid-tier
phone (Pixel 6a / Galaxy A54 class) for the latency rows.

Fill in the Result column only after running the check. Record device, Android version, build and date there.

## Automated exit check (sandbox, 2026-10-26)

| Check | Command | Result |
|---|---|---|
| Android unit tests, lint, release build | `$G ./gradlew test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` | Pass after fix `72f32f79` (see below) |
| 16 KB alignment | `scripts/check-16kb-alignment.sh app/build/outputs/apk/release/app-release-unsigned.apk` | Pass |
| Fork markers | `scripts/check-fork-markers.sh` | Pass |
| Backend tests | `cd ../backend && $G ./gradlew test` | Pass |

`$G` is the sandbox truststore prefix from the plan's Global Constraints.

## S. Suggestions and autocorrect (PRD FR-05)

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| S.1 | Strip update ≤ 30 ms p90 on a mid-tier phone | Mid-tier phone, release build, English subtype with the dictionary loaded. Record a Perfetto system trace (app tracing on) while typing 200 characters of mixed words and typos in Messages. Measure from each key's input event to the frame that shows the new candidates. Repeat for ur (largest dictionary, about 99k words). | p90 ≤ 30 ms for en and ur. If not, record the numbers and raise it against T1's search bounds and T4's snapshot lock.  | TRD §8 | |
| S.2 | Candidates on every MVP subtype | Enable en, es, fr, pt, id, ar, ur and hi. In each, type the first 3 letters of a common word and a word with one typo. | The strip shows up to 3 candidates on every subtype, in the subtype's script. Hindi's list is small (about 9,400 words, T2), so note weak results rather than failing them.  | FR-05 AC1 | |
| S.3 | Autocorrect off in URL, email and password fields | Turn autocorrect on for English. Type "teh " in a browser URL bar, an email field, a password field, a number or phone field, and a field with `IME_FLAG_NO_PERSONALIZED_LEARNING` (for example Chrome incognito). Repeat in a plain field, then revert with backspace, and type the same typo again. | Never auto-replaced in the special fields. In the plain field "teh " becomes "the " only for locales in Settings → Suggestions and autocorrect. After a revert the same word is not replaced again. Words with digits, ALL CAPS, `@` or `/` are never replaced. | RF5 | |
| S.4 | Clear learned words | Type and accept a made-up word 3 times so it appears as a candidate. Settings → Suggestions and autocorrect → Clear learned words → Clear. Repeat in incognito mode and in a private app before clearing. | After Clear, the word is no longer suggested, also after force-stopping the app. Words typed in incognito or a private app are never learned. | PC-1 | |
| S.5 | RAM cost of one loaded dictionary | `adb shell dumpsys meminfo com.manjul.aikeyboard` (the IME process) with the keyboard shown on a field, before and after switching from a subtype with no dictionary to en, then to ur. Also try glide typing in en (glide now loads the full word list, T4). | Record the Java heap and total PSS growth per dictionary. Expected under about 15 MB for en; raise it if ur or glide is much higher. Only one dictionary stays loaded after a switch. | T4 | |

## A. AI rewrite (PRD FR-15, FR-29 card)

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| A.1 | Deploy the backend | Follow `../backend/deploy/README.md` sections 1-8: project, service account, Secret Manager key, Firestore (with the `ai_reports` TTL on `expires_at`), Cloud Run (`min-instances=1`, `asia-south1`), load balancer with Cloud Armor (per-IP 60/min), App Check and Play Integrity. Confirm Cloud Run's `X-Forwarded-For` puts the client IP last (T6). | `/healthz` answers 200 from the public URL. `/v1/quota` without App Check returns 401. `AUTH_MODE=dev` is refused on Cloud Run. The access log lines carry no request text. | D1 | |
| A.2 | Release build with App Check enforced, end to end | Build release with `aikb.ai.baseUrl` and the four `aikb.firebase.*` properties set. Turn App Check enforcement on. Install from an internal test track (Play Integrity needs a Play-installed build). Agree to AI consent and run a rewrite. | Anonymous sign-in and the App Check token work, the rewrite streams and Replace works. A sideloaded or tampered build gets 401 `app_check`. No Firebase traffic before consent (check with a proxy or `adb logcat`). | D1, D6, T10 | |
| A.3 | First text ≤ 1.5 s p50 | Mid-tier phone on 4G and Wi-Fi in a launch market (or with a matching RTT). 20 rewrites of 100-300 characters, mixed tones. Time from tapping a tone to the first preview text (screen recording or a debug trace). | p50 ≤ 1.5 s. | TRD §8 | |
| A.4 | p90 < 2.5 s for 300 characters | The same setup, 20 rewrites of exactly 300 characters, time from the tone tap to Replace becoming enabled. | p90 < 2.5 s. If not, record the model and region and raise it against the step-up and region choices. | TRD §8 | |
| A.5 | 402 shows the quota card | On the deployed backend, use 10 actions with a free uid (or set `FREE_DAILY=1` on a test revision). Tap ✦ again. | The sheet shows "Daily AI limit reached · resets in N h" with "See Pro", which opens the "Pro is coming soon" screen. `ai_quota_hit` is logged once. | D14, FR-29 | |
| A.6 | 429 path | Send more than 10 requests in a minute from one uid (fast tone taps on different texts), or set `UID_PER_MINUTE=1` on a test revision. | The sheet shows "Too many requests, try again in N s" with N from `Retry-After`. Nothing is written to the host. | D16 | |
| A.7 | 503 hides AI for 1 h | Set `AI_ENABLED=false` on a test revision, then tap a tone. Reopen the keyboard in other fields. Kill the IME process and try again. | The ✦ chip disappears for 1 hour (in memory only). It comes back after the hour or after the process restarts. | D16 | |
| A.8 | Consent before the first request | Fresh install. Tap ✦ in a field, before agreeing. Then tap Not now; then repeat and tap Agree. Proxy or logcat the network. Withdraw consent in Settings → Private apps → Data and consent, and tap ✦ again. | The consent screen appears as a separate app screen, not inside the keyboard. Nothing reaches kb-api or Firebase before Agree. After withdraw, the consent screen appears again. | PC-1, PRD §10.6 | |
| A.9 | Replace and Undo in WhatsApp, Gmail and Chrome | In each app: select text, rewrite, Replace, then Undo within 5 s. Then rewrite, and edit the field (or switch fields) while the preview streams, and tap Replace. Also Undo after editing the field. | Replace changes only the captured range. Undo restores the original text. When the field changed, Replace is refused with the "Your text changed" message and Insert is offered. Undo is refused once the field changed. Host text is never lost. | RF1, T9 | |
| A.10 | RTL output in ar and ur | With an Arabic and an Urdu subtype, rewrite a sentence with a name, a number and a link in it. Replace. | The preview and the replaced text read right to left with correct bidi for the number and link. Names, numbers and links are unchanged. | FR-15 | |
| A.11 | Report with and without text | Report one output with "include text" ticked and one without. Check `ai_reports` in Firestore. | Both reports exist with reason and prompt id. Only the ticked one has text. Both have `expires_at` 90 days out. | T7, T12 | |

## P. ✦ Polish after dictation (PRD FR-16)

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| P.1 | Polish replaces only the span in 3 apps | In WhatsApp, Gmail and Chrome, type a sentence first, then dictate after it. Tap "Clean up" within 30 s and Replace. Repeat with "Clean up + translate" with a translate pair set. Then dictate and type a letter before tapping. | Only the dictated span changes; the typed text before it is untouched. The chip goes away once you type or move the cursor out of the span. Editors that change dictated text (auto-capitalise, smart punctuation) show no chip, which is expected (T14). | RF1, D11 | |
| P.2 | Undo restores the raw text | After a Polish Replace, tap Undo within 5 s. | The raw transcript is back, exactly as dictated. | D11 | |
| P.3 | Roman and Native output for hi and ur | AI settings → Output script: Same as dictated, Roman, Native. Dictate in Hindi and Urdu with each setting and Polish. | Native gives Devanagari (hi) or Urdu script (ur). Roman gives Latin script. Same as dictated keeps the dictated script. | D12, FR-16 AC4 | |

## E. Evals and cost (TRD §7.1, OD-08)

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| E.1 | Eval baseline per prompt on Haiku, and the per-language step-up decision | With `ANTHROPIC_API_KEY` set, run the baseline command in `../backend/evals/README.md` for `rewrite@1`, `polish_translate@1` and `multi_tone@1` on `claude-haiku-5-5`, then on `claude-sonnet-5-5` for any language or tone that fails. Have native speakers review the hi, ur and ar cases (and Roman hi/ur) first (T8). | A report per prompt and model is saved. For each language and action, record whether Haiku passes or the route steps up to Sonnet. Update `REWRITE_MODEL` / `STEP_UP_MODEL` and OD-08. | D15 | |
| E.2 | Cache-read share > 0 | After E.1 or A.3, check the access log field `cache_read_tokens` or the Anthropic console usage. | Cache-read input tokens are above 0 on repeated calls of the same prompt, which shows `cache_control` on the system block works. | TRD §4.4.2 | |

## Q and L. Quota requests and legal

| ID | Check | How | Pass criterion | From | Result |
|---|---|---|---|---|---|
| Q.1 | Play Integrity quota increase requested | Play Console → App integrity → request a higher Play Integrity API quota (`../backend/deploy/README.md` section 7). | Request filed. Record the ticket and the granted daily limit. | D1, TRD §11.4 | |
| L.1 | Legal sign-off on the CC-BY-SA dictionaries | Legal reviews `app/src/main/assets/dict/LICENSES.md`, the About → Third-party licences entries for FrequencyWords (CC-BY-SA-4.0) and urduhack urdu-words (MIT), and the share-alike effect on the shipped `.sdict` files. | Written OK, or a list of changes. | D2, OD-11 | |

## Other device-only items raised by the tasks

These are not exit rows in the plan, but the sandbox could not check them. Run them with the rows above.

| ID | Check | Pass criterion | From | Result |
|---|---|---|---|---|---|
| X.1 | Autocorrect quality on the real dictionaries: swap two letters in common words and type them with autocorrect on (en, es, fr, pt, id). | Autocorrect mostly picks the intended word. T4 measured that it fires on only 14-21 % of these typos and picks the right word 45-70 % of the time, and for rarer words it replaces them with more common ones. Tune the engine score or threshold in `:core:suggest` before release. | T1, T4 | |
| X.2 | First-letter typos (for example "jello" for "hello"): T1's first-letter anchor skips some of them. | Note the miss rate. Product decides whether it is acceptable. | T1 | |
| X.3 | The ✦ chip, the sheet overlay, the Polish chip and the key handling in `FlorisImeService` (only compile-checked; the service cannot run under Robolectric). | Everything in A and P works. On API 26-32, Back hides the keyboard and closes the sheet. | T13, T14 | |
| X.4 | Firebase token sources (`FirebaseTokenSource`, `FirebaseBootstrap`) with a real project. | Covered by A.2. No Firebase init at process start: no Firebase log lines until the first AI request after consent. | T10 | |
| X.5 | Large host fields: rewrite in a field with tens of thousands of characters (a long Google Doc or a notes app). | Capture and Replace stay responsive (under about 200 ms for the field read). | T9 | |
| X.6 | Auto-polish with a fake Pro (debug build, Pro forced on). Turn it on in AI settings, agree to the auto-polish consent, dictate. | Each dictation is polished and can be undone. A quota error pauses auto-polish until the next UTC day. It never runs in a sensitive field. | T14, D13 | |
| X.7 | Analytics opt-out and consent events in a debug build (filter logcat on `AiKbAnalytics`). | `ai_*` and `consent_*` events carry enums, buckets and booleans only, never text. With "Share usage analytics" off, no events are sent. | T11, PC-10 | |
| X.8 | Release APK size and method count after Firebase Auth and App Check. | Record the size growth against Sprint 2's release APK. | T10 | |

## Sandbox results

Filled in by Task 15 on 2026-10-26.

- **Android unit tests:** all green on the first run: 3,093 test runs across every module and variant (debug, beta,
  release), 0 failures, 0 skipped.
- **Release build:** `:app:assembleRelease` built `app-release-unsigned.apk`.
- **Lint:** the first run failed with 5 new errors. Four were `MissingClass` on the manifest's `tools:node="remove"`
  entries for `FirebaseInitProvider` and the firebase-auth components. One was `NetworkSecurityConfig` ("at most 1
  `<debug-overrides>`"), because the debug-only config added a second one next to main's. Fixed in `72f32f79`:
  `tools:ignore="MissingClass"` on the removals, and user CAs moved into the debug config's `base-config` (the debug
  build is always debuggable, so behaviour is unchanged). After the fix, `:app:lintDebug` passes with 20 warnings
  (dependency updates and unused strings) and nothing new above the baseline. The manifest tests pass.
- **16 KB alignment:** OK. 23 native libraries checked; 4 32-bit x86 libraries are unaligned, which is
  informational only (the rule is for 64-bit).
- **Fork markers:** OK (upstream v0.5.2; 102 modified, 6 marker-exempt, 196 rename-only, 207 deleted).
- **Backend (`../backend`, `main`):** `./gradlew test` passes: 128 tests, 0 failures, 2 skipped (`FirestoreUsageStoreIT`,
  which needs `FIRESTORE_EMULATOR_HOST`).
- **About screen licences:** the FrequencyWords (CC-BY-SA-4.0) and urduhack (MIT) entries were missing from the
  third-party licence list that decision 2 asks for. They were added in `1272b534` (AboutLibraries config); see L.1.

Not run in the sandbox (no device, Docker, Firebase project, Firestore emulator or Anthropic key): every row above,
the Docker image and Cloud Run deploy files, `FirebaseTokenSource`/`FirebaseBootstrap`, the Firestore stores, the real
`AnthropicGateway` stream, and the eval baseline.
