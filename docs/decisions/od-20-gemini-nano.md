# OD-20: Gemini Nano on-device AI

Status: **decided for MVP: cloud only, until the spike runs on a device.** Revisit after the spike (v1 at the
latest). Owner: Eng (AI). Date: 2026-10-26 (Sprint 3, plan decision 9).

## Question

PRD FR-15 and FR-16 (engine order, OD-08) and TRD §11 ask for this order: if the phone supports Gemini Nano (ML Kit
GenAI / AICore), the language is supported and the OD-20 spike passed, the request runs on the device first. If
not, or if Nano fails, is busy or is blocked, the request goes to the cloud. PRD R-14 flags the main risk: ML Kit
GenAI allows inference only from the top foreground app, so an IME may get `BACKGROUND_USE_BLOCKED`. Device
coverage in PK/IN/BD is also small, and ur/hi support is unconfirmed.

## Decision

- **MVP ships cloud only.** Every ✦ rewrite and ✦ Polish request goes through `CloudAiEngine` to our kb-api. The
  `ai_request{engine}` event is always `cloud`.
- **Nano is not built in Sprint 3.** The spike needs real devices with AICore, and the sandbox has none. Building the
  engine without the spike would ship code we cannot test, for a path that R-14 rates as likely to fail.
- **The seam exists.** `:core:ai` defines `AiEngine` (`name`, `stream(AiRequest): Flow<AiEvent>`) with one
  implementation, `CloudAiEngine`. `AiAppGraph` builds the engine lazily. A Nano engine can slot in as a second
  implementation, with an ordering wrapper that tries Nano and falls back to cloud, without changing `:ime:ai-ui`.
  `AiEngineParam.ON_DEVICE` (`on_device`) already exists in the analytics catalog.
- **Consent stays the same for MVP.** `consent_ai` is shown before the first cloud request (PRD §10.6). The rule
  that on-device requests need no consent applies only once a Nano engine ships.

Nothing in this decision changes cost limits: the cloud quota (10/day free, 150/day and 3,000/month Pro) and the
spend breaker apply to every request. TRD §11 says on-device requests count against the same quota, so passing the
spike would save cost, not raise limits.

## Spike plan (about 1 day, on device)

Run it on at least one Pixel 9-class phone and one Samsung Galaxy S24/S25-class phone with AICore, plus one
launch-market mid-tier phone without it, to confirm the fallback.

1. **Availability.** In a debug-only probe (Devtools, like the Sprint 2 translate latency probe), ask ML Kit GenAI
   for the rewriting and proofreading features' status. Record: available, downloadable, downloading or
   unavailable, and the model download size and time. Check the exact API names against the SDK version current
   at spike time; do not guess them.
2. **Calling from the IME.** Run the same call from `FlorisImeService` while another app (WhatsApp, Gmail, Chrome)
   is in front and the keyboard is showing. Pass requires a result with no `BACKGROUND_USE_BLOCKED` (or similar)
   error on both phones, at least 20 calls in a row. **If this fails, OD-20 fails** and the rest of the spike is
   skipped.
3. **Quality per language.** Run the `backend/evals` rewrite and polish cases for en, hi, ur and ar (and Roman
   hi/ur) through Nano with the same deterministic graders (`output_lang`, entities preserved, no preface, length
   ratio). Pass for a language: Nano's grader pass rate is within 5 points of the cloud baseline (E.1 on the Sprint
   3 device checklist) for that language. A language that fails stays cloud-only even if others pass.
4. **Latency.** First text and total time for a 300-character input, 20 runs per language. Pass: p50 first text
   ≤ 1.5 s and p90 total < 2.5 s, the same targets as the cloud path (checklist rows A.3 and A.4).
5. **Coverage.** From Play Console device catalog data, estimate the share of the target installs in PK, IN, BD
   and the Gulf whose devices report AICore. Record the number; there is no pass threshold, but under about 5 % the
   build and test cost is unlikely to pay off for MVP.

No user text is logged during the spike: the probe uses the authored eval inputs only and reports timings and
grader results.

## What happens next

- **Spike passes (step 2, and step 3 for at least one language):** add `NanoAiEngine` in a new module (`:core:ai`
  stays pure JVM), and an ordering engine (Nano for passing languages when available, otherwise cloud; fall back to
  cloud on any Nano error, busy or blocked). Log `ai_request{engine=on_device}`. Update `consent_ai` gating so
  on-device requests skip it, and add the on-device quota count (TRD §11). Update `docs/data-flows.md`.
- **Spike fails:** keep cloud only for MVP and retry in v1 (PRD OD-20 "Fail → cloud only, retry in v1"). No code
  change is needed.

## Notes

- The `AiEngine.name` KDoc says `"cloud"` or `"nano"`, while the analytics value is `on_device`. Whoever builds
  the Nano engine should map its name to `AiEngineParam.ON_DEVICE` and fix the KDoc.
- Sources: PRD FR-15, FR-16, §10.6, OD-08, OD-20, R-14; TRD §4.4, §11; Sprint 3 plan decision 9.
