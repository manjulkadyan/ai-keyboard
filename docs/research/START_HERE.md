# ai-keyboard: start here

This folder holds a static-only teardown (2026-10-17) of two voice/translator keyboards, plus market research. The goal is to build a legally distinct "private AI voice + translate keyboard".

**Read in this order**
1. `COMPARISON.md`: the two apps side by side, the build-target decision (App H's product shape on upstream FlorisBoard, with App L's engineering choices), gaps against competitors, and the **ranked merged feature set for the MVP** (the input to the PRD/TRD).
2. `COMPETITORS.md`: market table for 12 apps, feature matrix, market gaps, ASO keywords, and which APKs to tear down next.
3. The two per-app teardowns below, for evidence and implementation detail.

## Market research
| File | What it is |
|---|---|
| `COMPETITORS.md` | Competitor analysis (Gboard, SwiftKey, CleverType, Wispr, Typeless, translator clones, …) |
| `playstore/targets.md` | Parsed Play listing and data-safety facts for both targets |
| `playstore/targets_parsed.json`, `playstore/*.html` | Raw listing data for the two targets |
| `playstore/competitors/` | 24 competitor listings, `parsed.json`, and `search/` (7 Play search result pages) |
| `playstore/datasafety/` | Data-safety pages plus `summary.txt` |
| `scripts/parse_play.py`, `scripts/ds_summary.py` | Listing and data-safety parsers |

## App H: "Voice Keyboard and Translator" (`com.heri.language.keyboard.voice.typing`, v1.4)
Workdir: `voicetranslator_work/` (run log `voicetranslator_run.log`)
| File | What it is |
|---|---|
| `REVERSE_ENGINEERING_REPORT.md` | Integrated report: endpoints, lifecycles, privacy, secrets (redacted), 24 open items |
| `FEATURE_BACKLOG.md`, `features.json` | 15 scored features (`(I×2+M)/D`) and the build order |
| `BUILD_PLAN.md` | 7 sprints, about 62 days, MVP after about 28 days |
| `report/{manifest,backend,monetization,native,antitamper}.md` | Wave 1 team reports |
| `endpoints*.txt`, `sdk-roster.txt`, `anti-tamper.txt`, `secrets-keys-redacted.txt`, `jadx.log`, `jadx-failed-classes.txt` | Runbook outputs. `secrets-candidates.txt` holds unredacted values: **local only, do not share** |

## App L: "Voice Keyboard - Voice Typing" (`com.logiapps.language.keyboard.voice.typing.translator`, v1.3)
Workdir: `voicetyping_work/` (run log `voicetyping_run.log`)
| File | What it is |
|---|---|
| `REVERSE_ENGINEERING_REPORT.md` | Integrated report: endpoints, lifecycles, privacy, secrets (redacted), 22 open items |
| `FEATURE_BACKLOG.md`, `features.json` | 15 scored features and the build order |
| `BUILD_PLAN.md` | 7 sprints, about 64 days |
| `report/*.md`, runbook `*.txt` | Same layout as App H |

## Inputs and intermediate files
- `*.xapk`: the two source artifacts.
- `*_work/{unpack,base,jadx_out,jadxhome}`: unpacked and decompiled trees. These are large, so grep them with `grep -rI` and don't open them whole.
- `*_work/traffic/`: empty in both workdirs, because this pass was static-only.

## Next steps
- Write the PRD/TRD from `COMPARISON.md`, section "Merged feature set".
- Optional dynamic pass to close the shared open questions (password-field egress, Perf `q=` payload, live RC cadence). See `COMPARISON.md`, "Open questions".

## Product docs and full class sweep (2026-10-17)
- `product_docs/PRD.md`: 38 requirements (FR-01..38), keep/improve/drop for every feature in both apps, traceability matrix, 19 open decisions.
- `product_docs/TRD.md`: architecture, IME/voice/translation/AI design, backend, sprint plan (§8.1 maps every FR). §11 (engineering and Play-policy review) overrides the body where they disagree.
- `product_docs/REVIEW_NOTES.md`: 10 product decisions needed (RN-01..10).
- `*_work/CLASS_COVERAGE.md`: every first-party and FlorisBoard class read (100%), feature -> class map, new findings and corrections.

## Build status (Sprint 3, 2026-10-26)
- `app-android/` (git, branch `sprint-3`): the keyboard, a FlorisBoard fork. Sprint 3 added on-device suggestions
  and opt-in autocorrect for en, es, fr, pt, id, ar, ur and hi, a per-language user dictionary, the in-keyboard
  ✦ AI rewrite sheet and ✦ Polish after dictation. Plan: `app-android/docs/plans/2026-10-26-sprint-3.md`.
  Device and owner checks still open: `app-android/docs/sprint-3-device-checklist.md`. Gemini Nano decision:
  `app-android/docs/decisions/od-20-gemini-nano.md` (cloud only for MVP). Data flows: `app-android/docs/data-flows.md`.
- `backend/` (separate git repo, branch `main`): **kb-api**, Kotlin + Ktor 3. Auth (App Check + Firebase ID token,
  or dev auth), quota, rate limits, spend breaker, `/v1/rewrite`, `/v1/polish`, `/v1/ai/report`, the OpenAPI
  contract and the eval harness (`backend/evals`). Not deployed yet: `backend/deploy/README.md`.
- AI is hidden in builds without a kb-api URL. The app never holds a vendor key; only the backend reads
  `ANTHROPIC_API_KEY` from its environment.

### Run the backend and the emulator end to end (dev auth)
In the sandbox, prefix every Gradle call with the truststore `JAVA_TOOL_OPTIONS` (see the plan's Global Constraints).

1. Start kb-api locally. With canned output (no key, no cost):
   ```sh
   cd backend
   AUTH_MODE=dev GATEWAY=fake ./gradlew run
   ```
   With the real model instead: `AUTH_MODE=dev ANTHROPIC_API_KEY=... ./gradlew run` (spends money; never commit the key).
   Check it: `curl -i -H 'X-Dev-Uid: dev-local' localhost:8080/v1/quota`.
2. Build and install a debug app that talks to it. `10.0.2.2` is the host machine as seen from the Android emulator;
   the debug network security config allows cleartext only to `10.0.2.2` and `localhost`:
   ```sh
   cd app-android
   ./gradlew :app:installDebug -Paikb.ai.devAuth=true -Paikb.ai.baseUrl=http://10.0.2.2:8080
   ```
   For a physical phone, use `adb reverse tcp:8080 tcp:8080` and `-Paikb.ai.baseUrl=http://localhost:8080`.
   Dev auth sends `X-Dev-Uid: dev-local` and is forced off in beta and release builds.
3. On the emulator: enable the keyboard, open any text field, type a sentence and tap ✦. Agree on the consent
   screen (nothing is sent before that), then pick a tone, preview, and Replace or Insert. For Polish, dictate
   with the mic, then tap "Clean up" on the chip within 30 s.
4. Release builds need the Firebase options (`aikb.firebase.projectId`, `appId`, `apiKey`, `projectNumber`) and a
   deployed backend with `AUTH_MODE=firebase`: device checklist rows A.1 and A.2.
