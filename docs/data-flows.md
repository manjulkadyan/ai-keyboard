# Data flows

The one table the Play Data safety form and the in-app privacy screen are written from (TRD §6.2, §11.6). Add a
row whenever a feature sends or stores data; keep each row to what the shipped code does today.

Default: keystrokes, voice and translations stay on the device. Nothing below sends typed text, translated
text, transcripts, clipboard text or host package names off the device (PC-1, PC-6, PC-10).

## Translation (Sprint 2)

| Flow | Data | Leaves the device? | Where to | When | Notes |
|---|---|---|---|---|---|
| On-device translation (ML Kit Translation 17.0.3) | Source and translated text | No | — | Each translate in the bar, Translator screen or selection sheet | Runs in-process on a downloaded model. Never logged; ML Kit exception messages are dropped (`MlKitErrors`) |
| Language identification (ML Kit Language ID 17.0.6) | First 200 characters of the source text | No | — | Auto-detect source | Model is bundled in the library; no download |
| Translation model download | Language code of the requested model; device and network metadata of an HTTPS request | Yes | Google model download servers, through ML Kit's `RemoteModelManager` (host not chosen by us; confirm with a network capture on device, checklist row Q.3) | Only on an explicit user tap (Offline languages, O4 offer, bar download chip); Wi-Fi only by default | No user text. Unique WorkManager work per language; work input data carries the language tag only |
| ML Kit SDK telemetry (decision 11) | Anonymous SDK usage and diagnostic data (per Google's ML Kit terms; exact fields not documented by the SDK); no text | Yes | Google, through `com.google.android.datatransport` (`transport-backend-cct` 2.3.3, `transport-runtime` 2.2.6, pulled in by `com.google.mlkit:common` 18.11.0 with `firebase-components` 16.1.0) | When ML Kit is used | Not switchable through the SDK. Product/legal to confirm the Data safety wording (likely "App info and performance: diagnostics", collected, not shared, not optional) |
| Cloud translation | — | Not in this build | — | — | Interface and disabled stub only (decision 1, OD-05). Its first-use disclosure and row ship with the real engine |
| Translation history (Translator screen) | Source and translated text, language pair, time | No | Room on the device | One row per edit session, history on by default | To be excluded from backup like other user-content tables (TRD §6.3; Task 9 owns this, confirm when it lands) |
| Translation analytics events | Event name, language tags through `EventCatalog.tag()`, engine, error kind, counts | Per the analytics backend in use | Analytics backend | `translate_*` events (PRD §12) | Never text, never host package names |

ML Kit initialises on first translate use, not at process start (TRD §2.3): the merged `MlKitInitProvider` is
removed, so a user who never translates triggers none of the ML Kit rows above.

## AI rewrite (Sprint 3)

The one exception to the default above, and only after the user agrees on the app's AI consent screen
(`fork/ai/AiConsentActivity`, which sets `consentAiAt`). The ✦ chip is hidden, and the controller refuses to
send, unless the live field decision has `allowAi && allowCloud` (no password, incognito, no-learning, locked or
private-app field). The device talks only to our kb-api (`BuildConfig.AI_BASE_URL`); it never calls the AI vendor.

| Flow | Data | Leaves the device? | Where to | When | Notes |
|---|---|---|---|---|---|
| AI rewrite (`POST /v1/rewrite`) | Selected text, or the text before the cursor, up to 2,000 characters; tone; UI locale; translate target for the translate tone; a random `client_req_id` | Yes, after consent | kb-api (our backend), which calls the model provider server-side | Each tone tap on the ✦ sheet (a repeat of the same text and tone within the session is served from the in-memory LRU and sends nothing) | Text and output are never logged, put in analytics or exceptions. The LRU is in memory only and dropped on field change, restriction and hide |
| kb-api auth | Firebase App Check token and an anonymous Firebase Auth ID token (no per-install identifier of ours) | Yes, after consent | Firebase (token issue) and kb-api (headers) | First AI request; one prefetch per keyboard show once consent exists | Firebase starts on first use, not at process start. Debug dev builds may use `X-Dev-Uid` instead (`AI_DEV_AUTH`, forced off in beta and release) |
| Report AI output (`POST /v1/ai/report`) | `client_req_id`, `prompt_id`, reason; the output text only when the user ticks "include text" | Yes, on the user's tap | kb-api | Report on the ✦ sheet | Without the tick no text is sent |
| AI analytics events | `ai_request`, `ai_rewrite_done` (tone, length and latency buckets, action), `ai_quota_hit`, `ai_error`, `ai_output_reported`, `consent_result` | Per the analytics backend in use | Analytics backend | Per PRD §12 | Enums, buckets and booleans only; never text |
| AI consent | `consentAiAt` timestamp | No | `FeatureSettings` on the device | Agree on the consent screen | Withdrawn from Privacy controls (`withAiConsentWithdrawn`) |
