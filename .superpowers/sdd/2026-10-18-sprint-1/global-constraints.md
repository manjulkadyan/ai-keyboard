## Global Constraints

- Gradle in the sandbox: prefix every call with `JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/Users/manjul/StudioProjects/workspace/apk/ai-keyboard/.sandbox-tools/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit"`. Written `$G` below (`$G ./gradlew …`).
- Work on branch `sprint-1`, one commit per task, tests green before each commit. Do not push.
- minSdk 26, target/compile 36. Every `.so` 16 KB page-aligned (`scripts/check-16kb-alignment.sh`).
- Every edited upstream file: `// FORK(S1-<topic>): reason` on or within 3 lines of each changed hunk, plus a row in `docs/FORK_PATCHES.md`. `scripts/check-fork-markers.sh` must pass after every task.
- Module rule: `:ime*` must not depend on `:core:ads`, `:core:billing`, `:feature:*`.
- No user content (typed text, transcript, clipboard text, host package names) in any log, analytics event or exception message. Release builds log nothing about text (PC-10).
- Analytics event names: snake_case, ≤ 40 chars, ≤ 25 params, param keys ≤ 40, values ≤ 100 chars. PRD §12 names are canonical.
- `fallbackToDestructiveMigration` is banned. `Process.killProcess`/`System.exit` are banned (lint).
- All user-visible copy in `strings.xml` (our strings in `values/strings_fork.xml` for `:app`, module `strings.xml` otherwise). English only in this sprint; the 12-locale translations are outsourced (PRD FR-08 AC5) — keep strings translatable, no concatenation.
- Never derive anything (code, layouts, word lists, strings) from either teardown APK.
- Voice timings (PRD FR-10 AC1): end-of-session silence 2 s default (setting 2–5 s), restart after `ERROR_SPEECH_TIMEOUT`/`ERROR_NO_MATCH` after 250 ms max 3 consecutive, `ERROR_RECOGNIZER_BUSY` destroy+recreate after 400 ms max 2, session cap 60 s.
- Whisper: model never bundled; base (~140 MB) default, small (~470 MB) offered only with ≥ 6 GB RAM; unloaded after 60 s idle or `onTrimMemory(≥ TRIM_MEMORY_RUNNING_LOW)`; batch only; audio never leaves the phone.

## Decisions taken in this plan (flag to product if wrong)

1. **Dictionaries move to Sprint 3.** Upstream `LatinLanguageProvider.suggest()` is a stub, so per-locale frequency lists have no consumer until FR-08 suggestions (S3). This sprint only records `assets/dict/LICENSES.md` with the existing upstream `data.json` entry.
2. **Clipboard encryption at rest (TRD §11.6) moves to the S5 clipboard-panel task.** History stays off by default (PRD FR-06 AC1); this sprint fixes sensitivity, TTL and policy gating.
3. **Translate and AI chips stay hidden** until S2/S3 register their handlers (PRD FR-07 AC1: no placeholder chips). The chip model and pin/reorder logic ship now.
4. **Curated banking-package list is empty** until the launch-market list arrives (RN-05). The picker and suggestion plumbing ship.
5. **Whisper language gate:** RC-style default `whisper_enabled_langs = []` in release (no language has passed the native-speaker eval), every language in debug/beta. The engine works; release exposure waits for evals.
6. **Whisper model URL:** a build config field `WHISPER_MODEL_BASE_URL` (placeholder `https://example.com/models/`, TODO(OD-01) host), files verified by SHA-256 pinned in code. Hugging Face is blocked in the sandbox, so downloads are tested with a local fake server.
7. **IME service becomes not `directBootAware`** (TRD §11.2 #2.2 MVP rule).
8. **Onboarding O4 pack download and O6 soft paywall** are S2/S4; O4 here sets languages and the translate pair only.

## Review Focus

1. **Field turns sensitive mid-session** (`onStartInput(passwordInfo, restarting=true)` while voice partials are composing): voice stops, our composing span is finished without committing the partial, no further recognizer callbacks reach the editor. Test in Task 11.
2. **Host app edits or moves the cursor while partials are composing:** we drop our span and never delete host text by stored length. Test in Task 11.
3. **Mic permission revoked or "only this time" lapsed between sessions:** the chip shows and no recognizer is created; permission is never cached. Test in Task 11.
4. **No recognition service and no Whisper model, or airplane mode without on-device support:** the panel shows an explicit message with the right download offer, never fails silently. Test in Task 10 (selection) and Task 11 (UI state).
5. **Lock screen / locked device keyboard:** policy treats it as sensitive; voice, clipboard history and learning off; no crash because the service no longer starts before first unlock. Test in Task 1 (matrix) and Task 2 (manifest test).

## Rulings
- R1: Task 5 adds urdu_standard / hindi_phonetic as alternates; preset default stays upstream urdu_phonetic / hindi InScript so Task 4 test holds — spec (FR-03) requires reachability, not default — if wrong, product swaps one preset string.
- R2: Task 13 updates EventCatalogLimitsTest's expected-name set when adding pack_download_start/done — the test pins the catalog, catalog legitimately grows — cost: none.
- R3: Task 8 VoiceDisclosureSheet takes `() -> EngineKind` (EngineKind from Task 9 not yet present) → Task 8 uses a fork-local `enum class DisclosureEngineKind { ON_DEVICE, SYSTEM_SERVICE, WHISPER }`; Task 11 maps EngineKind to it — avoids forward dep — cost: one tiny mapping.
- R4: Task 1 overlap precedence: any sensitive reason ⇒ all allow* false (dominates); otherwise restrictions combine by AND (each rule only turns flags off) — TRD §4.1.2 is restrictive-wins — cost: matrix expectations change.
- R5: Task 5 digits: if upstream subtype/preset schema lacks a native/western digit choice, use the existing number-row/numeric layout mechanism; if none, ship native digits on layout layer and record deferral in ledger — cost: one setting deferred.
- R6: Task 12 contract test over a fake is accepted (native code can't load on JVM); the real check is assembleDebug + 16 KB script + device checklist — cost: JNI bugs surface only on device.

## Tasks
