# SDD ledger — plan: docs/plans/2026-10-18-sprint-1.md

Spec: ../product_docs/TRD.md + PRD.md (reachable). Branch sprint-1. Plan commit 727bf72b. No push.
Note: zd-standards MCP unreachable (ENOTFOUND) — no org standards loaded; tell user.

## Pre-flight scan

### Task pairs sharing files/interfaces
| Pair | Producer → consumer | Finding |
|---|---|---|
| 1→2 | SensitiveFieldPolicy.evaluate/PolicyDecision → ImePolicyController | consistent |
| 1→6 | PolicyDecision.analyticsMinimal → BatchingRelay | :core:analytics depends on :ime:policy; allowed (rule limits only :ime* deps) |
| 2→3,7,11 | policyController().decision/addRestrictionListener | consistent |
| 2→7,8,11,13 | FeatureSettings fields | all consumed fields exist in Task 2 data class |
| 2,10 ManifestHardeningTest | both extend same test | append-only, sequential; fine |
| 2,6,13 FlorisApplication.kt | lazy fields | sequential edits; fine |
| 2,11 FlorisImeService.kt | controller hooks → voice hooks | fine |
| 4→5 | ur-PK preset default `urdu_phonetic` (Task 4 test) vs Task 5 adds urdu_standard to preset | Ruling R1 |
| 4→8 | ensureDefaultSubtypes/MVP_LANGUAGE_TAGS | consistent |
| 6→7,8,11 | EventCatalog factories | consistent |
| 6→13 | Task 13 adds pack_download_* to catalog; Task 6 test asserts names == fixed set | Ruling R2 |
| 7,11 Smartbar.kt / KeyboardManager.kt | lock indicator → voice panel; VOICE_INPUT handler | sequential; Task 7 leaves VOICE_INPUT; fine |
| 8→10/11 | disclosure needs engine kind before Task 10 exists | Ruling R3 |
| 9→10,11,13 | SpeechEngine/EngineKind/VoiceOutput | consistent |
| 10→13 | RecognizerCapabilities.whisper* wired in 13 | consistent |
| 12→13 | WhisperApi interface (Task 12 Step 2) consumed by 13 | consistent; :core:voice gains dep on :lib:whisper |

### Per-task self-consistency
| Task | Finding |
|---|---|
| 1 | Matrix combines flags; precedence not stated for overlaps (e.g. EMAIL + NO_SUGGESTIONS) — Ruling R4 |
| 2 | consistent |
| 3 | `historyAutoCleanOldAfter` unit must match upstream pref's unit — implementer verifies |
| 4 | consistent |
| 5 | "digits choice per subtype" may not exist in upstream schema — Ruling R5 |
| 6 | consistent |
| 7 | consistent |
| 8 | consistent apart from R3 |
| 9 | error codes match SpeechRecognizer constants (6,7,8,9,1,2,4,12,13) |
| 10 | consistent |
| 11 | `KeyboardManager.kt:748` line ref may be stale — implementer locates VOICE_INPUT |
| 12 | WhisperNativeContractTest exercises a fake (rubric: test of a mock) — plan-mandated, Ruling R6; github clone may be blocked in sandbox — check at Task 12 |
| 13 | consistent apart from R2 |
| 14 | consistent |

## Rulings
- R1: Task 5 adds urdu_standard / hindi_phonetic as alternates; preset default stays upstream urdu_phonetic / hindi InScript so Task 4 test holds — spec (FR-03) requires reachability, not default — if wrong, product swaps one preset string.
- R2: Task 13 updates EventCatalogLimitsTest's expected-name set when adding pack_download_start/done — the test pins the catalog, catalog legitimately grows — cost: none.
- R3: Task 8 VoiceDisclosureSheet takes `() -> EngineKind` (EngineKind from Task 9 not yet present) → Task 8 uses a fork-local `enum class DisclosureEngineKind { ON_DEVICE, SYSTEM_SERVICE, WHISPER }`; Task 11 maps EngineKind to it — avoids forward dep — cost: one tiny mapping.
- R4: Task 1 overlap precedence: any sensitive reason ⇒ all allow* false (dominates); otherwise restrictions combine by AND (each rule only turns flags off) — TRD §4.1.2 is restrictive-wins — cost: matrix expectations change.
- R5: Task 5 digits: if upstream subtype/preset schema lacks a native/western digit choice, use the existing number-row/numeric layout mechanism; if none, ship native digits on layout layer and record deferral in ledger — cost: one setting deferred.
- R6: Task 12 contract test over a fake is accepted (native code can't load on JVM); the real check is assembleDebug + 16 KB script + device checklist — cost: JNI bugs surface only on device.

## Tasks
BASE Task 1: 727bf72b69b53cb336a4ad0f62d64eca0860f4bc
Pre-check Task 12: github.com reachable (200); NDK 26.1.10909125, CMake 4.0.2 present.
Task 1: implementer impl-t1 DONE 2fa541c2; review dispatched (sonnet)
Task 1: complete — 2fa541c2 — review Approved; parked minors (final review): null packageName w/ non-empty sets unpinned; no two-context-reason overlap test; Ctx.values()→entries; setOf(0x10).
BASE Task 2: 2fa541c2
Task 2: impl-t2 DONE_WITH_CONCERNS f6b7ff6f (added gradle/robolectric-offline.gradle.kts for sandbox; translatePair as LanguagePair?; no onStartInputView hook).
Ruling R7: settings load race — controller re-evaluates active field on every store emission, more-restrictive → bump+listeners; corrupt file → defaults — fail-closed within one emission without blocking main thread — cost: a few ms window at cold start.
Task 2: pre-review fix requested from impl-t2 (corruption handler + R7).
Task 2: review of 2fa541c2..8e5d4638 Needs fixes — Important: first-field default settings + EditorInstance never re-reads decision. Minors: onDestroy lazy touch, policyController() throw → fail closed Locked, robolectric pin comment, EditorInstance tests.
Task 2: fix round 1 sent to impl-t2 (fix base 8e5d4638)
Task 2: complete — f6b7ff6f, 8e5d4638, 4849c315 — re-review all addressed. Produces: FlorisImeService.policyController()/policyControllerOrNull(); EditorInstance.applyPolicyDecision; PolicyStateDerivation; gradle/robolectric-offline.gradle.kts (apply in modules with Robolectric tests).
Ruling R8: fix rounds land as extra fix(...) commits rather than amending — keeps review diffs exact — cost: >1 commit per task; offer squash at finish.
BASE Task 3: 4849c315
Task 3: impl-t3 DONE_WITH_CONCERNS 855a9c77 (gate extracted ClipboardCaptureGate; manager wiring untested; slider max 120→1440; files DB destructive fallback also removed)
Task 3: review 4849c315..855a9c77 — Important: stale primaryClip on blocked clip; manager wiring untested; ≤20 test missing. Ruling R9: blocked clip → primaryClip=null; keyword cap 60→160 + 3+3 split codes; wiring-test gap accepted (manager needs FlorisApplication) — parked. Parked minors: v1 DB migrations absent (fork never shipped v1), multi-item clips classify item 0, slider step 5 over 0–1440, release note on auto-clean default.
Task 3: fix round 1 sent to impl-t3 (fix base 855a9c77)
Task 3: complete — 855a9c77, 956a8b0f — re-review all addressed. Parked minor: 160-char keyword window widens false positives for short prose w/ keyword+year (cheap: clip not stored).
BASE Task 4: 956a8b0f
Task 4: impl-t4 DONE_WITH_CONCERNS c2afee20 (no RED; missingSubtypeTags extracted; old default subtype removed)
Task 4: review 956a8b0f..c2afee20 Needs fixes — Important: test re-implements preset lookup; ensureDefaultSubtypes untested; no RED. Ruling R10: no overridesImplicitlyEnabledSubtype (would force English on all locales) — device check: en_GB fresh install. Parked: LICENSES.md "v0.5.2" version claim check in final review.
Task 4: fix round 1 sent (fix base c2afee20)
Task 4: complete — c2afee20, 584c0ecc — re-review all addressed. Parked minors: ensureDefaultSubtypes wrapper (update+persist) unexecuted by tests; persistNewSubtypeList ordering under concurrency; fork.subtypes↔ime.core package cycle.
BASE Task 5: 584c0ecc
Task 5: impl-t5 DONE_WITH_CONCERNS 3e39a94d. Ruling R11: accept ur-PK default numericRow=persian (Urdu digits U+06F0; western on long-press), matching ar/hi-IN — FR-03 asks for the choice, default is product's call — cost: flip one preset field if product prefers western. Ruling R12: accept FlorisLocale.supportsCapitalization=false for "ur" (auto-shift would type shift layer). Linguist flag: urdu_phonetic base h = U+0647, ہ via popup.
Task 5: complete — 3e39a94d — Approved. Parked minors: folder==meta.id assertion; hindi nukta label leading space; Arabic set excludes U+063B–U+0640 (documented). Device: verify subtype editor lists org.aikeyboard layouts; RTL shift visuals.
BASE Task 6: 3e39a94d
Task 6: impl-t6 DONE ad72ee81. Ruling R13: keep brief's wider enum values (engine system_on_device|system_default|whisper; end_reason incl. cap|field_change) and voice_unavailable event — supersets of PRD §12 values, names unchanged — cost: PRD §12 doc update for product/data.
Task 6: review Needs fixes — Important: free-string params (subtype/lang/enum-likes). Ruling R14: null policy → not minimal for analytics (no field context; app-side funnel) — unlike clipboard gate — cost: none known. Fix round 1 sent (fix base ad72ee81): BCP-47 validation, closed enum types, flush-time minimal filter, lastFlushAt, exhaustive limits test.
Task 6: complete — ad72ee81, f9dd1b74 — re-review all addressed. API: EventCatalog takes enums OnboardingSource/OnboardingStep(ONE,TWO,THREE,TRY)/VoiceEngine/EndReason/UnavailableReason + raw numbers (bucketed inside); FlorisApplication.analytics (BatchingRelay.log). Parked minors: lastFlushAt term dead; 2–3 letter words pass tag regex; languagesSet raw count.
BASE Task 7: f9dd1b74
Task 7: impl-t7 DONE_WITH_CONCERNS 5f5f71a1 (no Privacy parent screen; TOGGLE_PRIVATE_APP as quick action; Default arrangement changed for new installs; no STICKERS; UI unverified visually)
Task 7: review 5f5f71a1 Needs fixes — Important: fail-closed paths untested (isAllowedBy, null controller); PolicyChipFilterTest password decision hand-built. Minors: Privacy wording, dup analytics on re-add, privatePackages misnamed, FORK_PATCHES Default wording, import order. Ruling R15: no Privacy parent screen — home entry routes straight to Private apps; reword comments — cost: one nav screen later if product wants grouping. Fix round 1 sent (fix base 5f5f71a1).
Task 7: complete — 5f5f71a1, 38cc8062 — re-review all addressed; fork-marker script re-run by controller OK. API: fork/smartbar ChipId(+forKeyCode), ChipArrangement, decisionOrLocked(), QuickAction.isAllowedBy; fork/privacy PrivateAppsRepository(store, controller?, log) with privatePackages: Flow<Set<String>>, add(pkg); KeyCode.TOGGLE_PRIVATE_APP; route settings/privacy/private-apps. Parked minors: rememberPolicyDecision unkeyed remember + own FailClosed (not via decisionOrLocked); lock long-press not refreshed until next onStartInput; PolicyChipFilterTest import order. Device: lock indicator + Private apps screen visuals.
BASE Task 8: 38cc8062
Task 8: impl-t8 DONE_WITH_CONCERNS 82fdf6c9 (no OnboardingScreenTest — claimed offline-unresolvable, but dl.google.com reachable (200); implementer ran --offline → will require in fix round; no pre-impl RED)
Task 8: review 82fdf6c9 Needs fixes — Important: re-entry re-logs funnel events (huge ime_* buckets); consent write cancelled by sheet dismissal; ime_selected bucket unasserted; + missing OnboardingScreenTest (Google Maven reachable). Ruling R16: ime_* timing measured from firstOpenAt (= onboarding_start) — matches event name seconds_since_start — cost: WELCOME dwell included. Ruling R17: upstream notification-permission setup step dropped with SetupScreen; POST_NOTIFICATIONS deferred (no sprint-1 notification) — cost: re-add if a feature posts notifications. Fix round 1 sent (fix base 82fdf6c9).
Task 8: complete — 82fdf6c9, 75e146d5 — re-review all addressed. API: fork/onboarding OnboardingStep(WELCOME..DONE), ImeStatusSource/InputMethodImeStatus, OnboardingViewModel, OnboardingHolder (androidx VM), VoiceDisclosureSheet + recordVoiceConsent(store, clock) (NonCancellable), DisclosureEngineKind; Compose ui-test deps now in :app. Parked minors: bare window if pref store loads >1 s (splash released at cap); remainingMs=0 → onboarding flash for done user; screen test Agree-gone assertion maybe flaky; import order. Device: start destination, permission sheet, cold-start splash.
BASE Task 9: 75e146d5
Task 9: impl-t9 DONE_WITH_CONCERNS 28e460eb (no true RED — mutation substitute; output collectors must subscribe before start; extras: tapToResume flow, 3 s Finalizing timeout, 10/11→NETWORK)
Task 9: review 28e460eb Needs fixes — Important: cap on Whisper discards audio; output SharedFlow drops events w/o collector/overflow; error while stopping loses text / hangs Transcribing. Ruling R18: output becomes Channel(UNLIMITED).receiveAsFlow() — single consumer (Task 11 VoiceController) — lossless Commit/ClearComposing — cost: no multicast (none needed). Ruling R19: non-continuous session ending on its own final → endReason "silence" (endpointer = silence) — cost: none. Ruling R20: Transcribing bounded by VoiceTimings.transcribeTimeoutMs = 45_000 → Error(OTHER), no commit — cost: very slow devices may time out on 60 s audio. Fix round 1 sent (fix base 28e460eb).
Task 9: re-review 341e8379 all addressed; new Important (inline (?iu) may be rejected by Android ICU) → fix round 2 b6c22a47 (explicit Pattern flags), verified by controller (one-file diff, no inline flags left).
Task 9: complete — 28e460eb, 341e8379, b6c22a47. API: :core:voice SpeechEngine(+Listener), EngineKind(analytics), VoiceState, VoiceErrorKind, VoiceOutput, VoiceTimings(+transcribeTimeoutMs=45_000), SpeechSession(engineFactory, scope, timings, punctuation, clock): state, output (Channel UNLIMITED, single consumer), endReason String?, tapToResume StateFlow, isSegmentedSupported(kind, lang), start/stopByUser/cancel; SpokenPunctuation.apply. Parked minors: SPEECH_RMS_DB=3f noise-floor untested (noisy room → runs to cap); Task 11 must strip trailing space before punctuation commit.
BASE Task 10: b6c22a47
Task 10: impl-t10 DONE_WITH_CONCERNS c611543f (onDeviceSupports untested; segmented end → onFinal(""); PlatformRecognizerCapabilities whisper*=false)
Task 10: review c611543f Needs fixes — Important I1: SYSTEM_ON_DEVICE on API 31/32 creates cloud-capable recognizer (gate S not TIRAMISU). Minors: onDeviceSupports no runCatching/untested; empty segmented run flags segmented unsupported (Task 9 detection) → fix: detection requires non-blank final; double-release test weak; selector gaps; import order. Fix round 1 sent (fix base c611543f).
Task 10: re-review 4d5974b8 all addressed except M1 leak (recognizer assigned after .also) → fix round 2 667e84db, verified by controller.
Task 10: complete — c611543f, 4d5974b8, 667e84db. API: core.voice.platform RecognizerCapabilities / PlatformRecognizerCapabilities(whisper*=false until T13), EngineChoice(Use|Unavailable(reason, offerSystemDownload, offerWhisperDownload)), SpeechEngineSelector.choose(lang, preferWhisper, online), PlatformSpeechEngine(context, onDevice, mainHandler, segmented=false), AudioFocusHolder(audioManager).acquire/release. Segmented end → onFinal(""). Parked: on-device factory 31/32 gate only pure-tested; EXTRA_PREFER_OFFLINE on SYSTEM_DEFAULT is a hint. Device: on-device recognizer on API 31/32 + 33.
BASE Task 11: 667e84db
Ruling R21: FieldGeneration lives in :app; :ime:voice-ui VoiceController takes generation: () -> Int (current FieldGeneration.value) instead of the class — avoids :ime→:app dep — cost: none.
Task 11: impl-t11 DONE_WITH_CONCERNS 8a532cad (partial stays as plain text on password restart; errorCode always null; noHistory risk; editor hunk = voiceOwnsComposing flag, IC logic in ImeTextTarget; +ACCESS_NETWORK_STATE; SpeechSession(punctuationEnabled))
Task 11: review 8a532cad (opus) Needs fixes — Important: sensitive restart leaves our partial in password field; trailingSpace survives cursor moves → punctuation join deletes host char. Minors: helhello re-anchor; errorCode via session.lastErrorCode; trampoline blank after rotation; double policy check; partial RED. Ruling R22: accept editor-hunk deviation (voiceOwnsComposing flag + IC logic in ImeTextTarget) — smaller upstream diff — cost: none. Ruling R23: accept ACCESS_NETWORK_STATE (needed for online check; normal permission) — product note for Play listing. Ruling R24: on cancel we clear our own verified composing region (setComposingText("")) — not host text; Focus 1 outranks literal "finish" — cost: if IMM already finished it, no-op (device check). Fix round 1 sent (fix base 8a532cad).
Task 11 fix round 1: ffe72272 (fix base 8a532cad) — impl reports voice-ui 32 / core:voice 61 / app 123 green, lint/assemble OK; fork markers OK (controller re-ran).
Ruling R25: keyboard hide routes through cancel() and so discards the uncommitted partial (no finishComposingText) — partials are unconfirmed recognizer guesses and committing them on hide could land text in a field whose policy changed; consistent with R24 — cost if wrong: user loses a few spoken words on hide; device-checklist item, cheap to switch to finalize-on-hide later.
Task 11 re-review (rerev-t11, sonnet): all 5 findings addressed; no new Critical/Important.
Task 11: complete — 8a532cad, ffe72272. API: :ime:voice-ui (VoicePanel, Waveform, VoiceChip, ComposingSpanController incl. discard(), VoiceController(generation, decision), smartbarVoicePanel); app/fork/voice (MicPermissionActivity, ImeTextTarget incl. clearComposing(expected), VoiceImeSession); SpeechSession.lastErrorCode; FeatureSettings.firstDictationAt; ACCESS_NETWORK_STATE. Parked: same-length host replacement with cursor at end treated as ours (anchor dropped); commitCursor assumes in-order selection reports; clearComposing guard checks text-before-cursor not span; double policy check; no RED evidence for initial code. Device: real recognizer; predictive back on IME window; triggerModelDownload; noHistory trampoline may lose permission result; IMM finishing composing before restarting=true (R24 clear may no-op); FlorisImeService/KeyboardManager/Smartbar hooks untested; hide-keyboard discards partial (R25).
BASE Task 12: ffe72272
Task 12: impl-t12 (opus) DONE_WITH_CONCERNS 582550c5 — :lib:whisper 13/13, assembleDebug, 16 KB OK all 3 ABIs, R8 keep OK, fork markers OK (controller re-ran). Debug APK +5.03 MB. Concerns: JNI never run (device); arm64 plain armv8-a; transcribe throws IAE/WhisperException; one lock (free waits on transcribe); no x86 .so → load()=0. Review package excludes vendored tree (manual package); reviewer told to diff vendored dirs vs a fresh v1.9.5 clone.
Task 12 device items: base model load → non-zero handle; known clip EN + HI transcribes; unknown lang → WhisperException; free during transcribe waits; arm64 decode speed w/o fp16/dotprod acceptable. Note: pr.yml 16 KB step now requires libaikb_whisper.so per ABI (beyond brief).
Task 12 review (rev-t12, opus): spec ✅ (vendored tree == v1.9.5 d1be6fde, diff -r clean); quality Needs fixes — Important: NewStringUTF on raw whisper bytes (CheckJNI abort); free() blocks main thread during decode (ANR). Minor: thread clamp, pointer-reuse handles, suppress_nst, tautological test, null session check, -Wformat noise.
Ruling R26: transcribe throws IllegalArgumentException (unloaded handle) / WhisperException (native failure or abort) instead of returning "" — silent empty voice output is worse — cost: Task 13 must catch both.
Ruling R27: free() never blocks on a running decode — closes handle + abort_callback flag; last side out frees ctx; aborted transcribe throws WhisperException — cost if wrong: more complex native lifecycle, device-verify.
Task 12 fix round 1: sent to impl-t12 (fix base 582550c5) — all Important + all Minor.
Task 12 fix round 1: 79f12c6e — :lib:whisper 19/19, assembleDebug, 16 KB OK, markers OK (controller re-ran). New: concurrent transcribe on same handle → IllegalStateException (Task 13 must serialise). Device: abort latency after free(); emoji/Devanagari output. Re-review rerev-t12 (opus) dispatched.
Task 12 re-review (rerev-t12, opus): all findings addressed; lifecycle traced clean; no new Critical/Important.
Task 12: complete — 582550c5, 79f12c6e. API: com.manjul.aikeyboard.lib.whisper — interface WhisperApi; object WhisperNative : WhisperApi { load(modelPath, threads): Long (monotonic id, 0 on failure/x86); transcribe(ctx, pcm16kMono, lang, translate=false): String (empty PCM → ""; throws IllegalArgumentException unloaded/closed handle, IllegalStateException concurrent call on same handle, WhisperException native failure/abort); free(ctx) non-blocking (R27) }; threads clamped 1..min(cores,8); suppress_nst. Debug APK +5.03 MB. Parked: check(!busy) before empty-PCM return; redundant double null check; abort only polled between encoder/decoder steps (native memory held ≤1 encoder pass after free). Device: model load non-zero; EN+HI clip; unknown lang → WhisperException; abort latency after free; emoji/Devanagari output; arm64 decode speed w/o fp16/dotprod.
BASE Task 13: 79f12c6e
Ruling R28: Whisper model SHA-256 — pin only an authoritative SHA-256 (whisper.cpp repo on github; HF blocked); otherwise empty TODO(OD-01) constant and manager fails closed (empty hash → FAILED, file discarded) — cost: Whisper downloads unusable until OD-01 hosting fills hashes (host is a placeholder anyway).
Ruling R29: WorkManager dep + INTERNET permission accepted (download worker only, no URL/path logging) — cost: Play listing shows network access (with R23).
Task 13: impl-t13 (opus) dispatched from BASE 79f12c6e; told engine must serialise transcribe, map IAE/ISE/WhisperException → onError, cancel during transcribe → free() abort.
Task 13: impl-t13 DONE_WITH_CONCERNS 3656d105 — full `test` 1999/0 fail, lintDebug, assembleDebug, markers OK (controller re-ran), 16 KB OK. sha256 "" TODO(OD-01) fail closed (R28). Fixed pre-existing: ui-test ComponentActivity allow-listed debug-only in verifyDebugExportedComponents; ComposeHostActivityRule for non-debug unit-test variants. WorkManager SystemJobService/DiagnosticsReceiver allow-listed.
Ruling R30: ship q5_1 quantised models (brief file names ggml-base-q5_1.bin 57 MB / ggml-small-q5_1.bin 182 MB) over the constraint's ~140/470 MB f16 sizes — the brief pins the files, the constraint's sizes are approximate and smaller downloads serve the same goal — cost: small accuracy drop vs f16; TRD size text needs updating.
Ruling R31: SMALL RAM gate uses 5.5 GiB of reported totalMem (phones sold as "6 GB" report ~5.5 GiB) — spec says "≥ 6 GB RAM", meaning the device class — cost: SMALL offered on some 6 GB phones under memory pressure; idle unload mitigates.
Task 13 review (rev-t13, opus): spec ❌ quality good — Important: R31 gate still 6 GiB; Review Focus 4 — panel ignores offerWhisperDownload; ON_DEVICE preference unconsumed (copy promises on-device only, selector still returns SYSTEM_DEFAULT; Offline (Whisper) with no model falls back to server). Minors: strings quotes, stale worker deletes new .part, no delete/resume/restore tests, PcmRecorder join(500) on main, AudioRecord leak on SecurityException, lost mic unreported, WorkManager init in release; 45 s transcribe timeout vs slow Whisper.
Ruling R32: selector takes the full VoiceEnginePreference. AUTO = existing order. ON_DEVICE = SYSTEM_ON_DEVICE → WHISPER (if ready & lang enabled) → Unavailable (with download offers), never SYSTEM_DEFAULT. WHISPER = WHISPER → SYSTEM_ON_DEVICE → Unavailable(offerWhisperDownload), never SYSTEM_DEFAULT — the copy is a privacy promise; both fallbacks keep audio on the phone — cost: users who pick on-device on phones without it get "unavailable" instead of working voice.
Ruling R33: Transcribing timeout is per engine — WHISPER 120 s, others keep 45 s (R20) — 60 s of audio on small/A53 can exceed 45 s and losing the utterance is worse than waiting — cost: up to 2 min spinner on very slow phones; device latency check.
Task 13 fix round 1: sent to impl-t13 (fix base 3656d105) — 3 Important + all Minors + R33.
2026-10-20 USER AUTHORISED PUSH: "commit everything and then push" — push sprint-1 to origin after each commit from now on. origin/sprint-1 was already at 3656d105 before the controller's first push (source unknown; impl-t13 says it never pushed).
Task 13 fix round 1: 41a62f80 — full test/lint/assemble green, 16 KB OK, markers OK (controller re-ran). Pushed to origin/sprint-1. Concerns: analytics reasons unchanged (localOnly flag drives UI copy); whisper download offered only when lang gate on; no RED for app-level tests.
Task 13 re-review (rerev-t13, opus): all findings addressed; no new Critical/Important.
Task 13: complete — 3656d105, 41a62f80 (pushed). API: core.voice.whisper (WhisperSpeechEngine, PcmRecorder, WhisperModelManager, WhisperModelSpec BASE/SMALL q5_1, ModelState, WhisperIdleUnloader, ModelDownloadWorker); SpeechEngineSelector takes VoiceEnginePreference (AUTO/ON_DEVICE/WHISPER, localOnly); VoiceTimings.transcribeTimeoutFor(kind); VoicePanel onDownloadWhisperModel → ui://florisboard/settings/voice; VoiceSettingsScreen; pack_download_start/done; INTERNET; WorkManager; :core:voice api(:core:datastore). Parked: model rename vs delete() not atomic (installed file while ABSENT); quick manual stop/start may overlap AudioRecord release (~100 ms) → ERROR_AUDIO on some devices; no RED for app-level tests; Whisper download offer hidden in release (gate []); settings shows no download %; WorkManager 10-min/3-attempt resume for large models; analytics unavailable reasons unchanged. Device: Whisper decode latency vs 120 s (small on A53); quick stop/start mic; download/resume against real host once OD-01; IME "Get offline model" chip deep link; on-device-only preference never uses server; WorkManager exported components.
BASE Task 14: 41a62f80
Task 14: impl-t14 (sonnet) DONE_WITH_CONCERNS 283fc8b4 (pushed) — clean test 2057/0, lintDebug, assembleRelease (unsigned), 16 KB OK on release, markers OK (controller re-ran). Gap: TRD §8 Sprint 1 exit ("Robolectric no-network test passes"; TRD :257 no network call + no Room write during a scripted password-field session) has NO test — only ManifestHardeningTest + ClipboardCaptureGateTest. LogcatSink untested. Plan box Task 4 Step 2 left unticked (no RED captured).
Ruling R34: the missing TRD exit test is load-bearing — add a Robolectric PasswordFieldEgressTest in Task 14's fix round (scripted session in a password field: keys, backspace, suggestion tap, paste, mic/voice attempt, restarting=true sensitive switch; asserts no socket/connection attempted and no Room write) — it is the sprint's stated exit gate — cost: one more test file + fix round.
Task 14 review (rev-t14, sonnet): spec ❌ / Needs fixes — Important: checklist 6.2 wrongly says POST_NOTIFICATIONS absent (still declared, app/src/main/AndroidManifest.xml:18; WorkManager merges WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE); Play-listing note misses these; exit rows must be updated after R34 test. Minors: checklist omits EXTRA_PREFER_OFFLINE hint (T10), no download % (T13), clipboard slider 0–1440 (T3).
Ruling R35: keep POST_NOTIFICATIONS declared (upstream CrashUtility posts crash notifications; minimal fork) but our onboarding never requests it (R17 stands) — correct the checklist/R17 note; list all release-manifest permissions incl. WorkManager's in the Play listing note — cost: permission visible in listing; Android 13+ crash notifications silently not shown.
Task 14 fix round 1: sent to impl-t14 (fix base 283fc8b4) — R34 egress test + review items.
Task 14 fix round 1: 3bb0d56b (pushed) — 2065 tests/0 fail, lint, markers OK. PasswordFieldEgressTest (real policy/IC/clipboard gate+Room DAO/voice; detector self-test; RED via 2 temp breaks). Limits: native sockets, no suggestion engine in S1, FlorisImeService not under Robolectric. SetupScreen.kt:230 POST_NOTIFICATIONS request unreachable (dead upstream code).
Task 14 re-review (rerev-t14, sonnet): all findings addressed.
Task 14: complete — 283fc8b4, 3bb0d56b (pushed). Parked: SetupScreen.kt:230 dead upstream POST_NOTIFICATIONS request; egress test can't see native sockets / no suggestion engine / no FlorisImeService under Robolectric; LogcatSink untested; Task 4 Step 2 unticked (no RED).
FINAL REVIEW: base = parent of plan commit 727bf72b; head 3bb0d56b; dispatched final-rev (opus).
FINAL REVIEW (final-rev, opus): With fixes — 0 Critical; Important: (1) clipboard history panel reachable in sensitive fields via Enter long-press popup (-213) / SWITCH_TO_CLIPBOARD_CONTEXT swipe, open panel not closed on restrictive restart; (2) ime_session_start never emitted (plan gap). 7 Minor.
Ruling R36: emit ime_session_start now (onStartInputView via SessionSampler) — core retention/sensitive-session metric; plan gap, small — cost: one upstream hunk.
Ruling R37: smartbar in sensitive fields shows an allow-list only (emoji, settings, privacy lock/private-app action); every other quick action hidden — TRD exit "no smartbar action appears" — cost: users lose undo/redo chips in password fields.
Ruling R38: WhisperIdleUnloader frees on RUNNING_LOW/RUNNING_CRITICAL and >= TRIM_MEMORY_BACKGROUND, not UI_HIDDEN — avoids reload whenever settings UI is backgrounded — cost: model may stay resident a bit longer under mild pressure.
Ruling R39: field change via onFinishInput cancels voice before super.onFinishInput() — same discard behaviour as hide (R25) — cost: partial dictation lost on field switch.
Final fix dispatch (ONE): final-fix (opus) — Important 1, 2 + Minors 1,3,4,5,6,7; Minor 2 (main-thread WhisperModelManager construction) parked for Sprint 2.
Final-review declined-to-judge rulings: native whisper.cpp socket egress → device capture (checklist 2.9 limits); R8/release behaviour → final-fix runs assembleRelease, runtime on device; API 33+ recognizer → device checklist; 16 KB on release → already run in Task 14 and re-run by final-fix; strings translation quality → out of scope (English-only constraint).
Final fix: dd4da202 (pushed) — clean test 2124/0, lint, assembleRelease, 16 KB OK, markers OK. Clipboard gate ignores NoField on onFinishInput (keeps upstream hide-on-next-field pref); logic in fork helpers ClipboardPanelGate/ImeSessionStartReporter; device 2.11, 2.12.
Final re-review (rerev-final, opus): all addressed; Sprint 1 ready to merge. Parked: WhisperIdleUnloader local 'free' shadows ctor param (rename shouldFree); FORK_PATCHES.md:55 S1-policy row stale ('onStartInputView needs no call').
Merged: main fast-forwarded to dd4da202 locally (not pushed); tests 2124/0; local sprint-1 deleted, origin/sprint-1 kept.
