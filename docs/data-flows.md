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

## AI rewrite and Polish (Sprint 3)

The one exception to the default above, and only after the user agrees on the app's AI consent screen
(`fork/ai/AiConsentActivity`, which sets `consentAiAt`). The ✦ chip is hidden, and the controller refuses to
send, unless the live field decision has `allowAi && allowCloud` (no password, incognito, no-learning, locked or
private-app field). The device talks only to our kb-api (`BuildConfig.AI_BASE_URL`); it never calls the AI vendor.

| Flow | Data | Leaves the device? | Where to | When | Notes |
|---|---|---|---|---|---|
| AI rewrite (`POST /v1/rewrite`) | Selected text, or the text before the cursor, up to 2,000 characters; tone; UI locale; translate target for the translate tone; a random `client_req_id` | Yes, after consent | kb-api (our backend), which calls the model provider server-side | Each tone tap on the ✦ sheet (a repeat of the same text and tone within the session is served from the in-memory LRU and sends nothing) | Text and output are never logged, put in analytics or exceptions. The LRU is in memory only and dropped on field change, restriction and hide |
| kb-api auth | Firebase App Check token and an anonymous Firebase Auth ID token (no per-install identifier of ours) | Yes, after consent | Firebase (token issue) and kb-api (headers) | First AI request; one prefetch per keyboard show once consent exists | Firebase starts on first use, not at process start. Debug dev builds may use `X-Dev-Uid` instead (`AI_DEV_AUTH`, forced off in beta and release) |
| Report AI output (`POST /v1/ai/report`) | `client_req_id`, `prompt_id`, reason; the output text only when the user ticks "include text" | Yes, on the user's tap | kb-api | Report on the ✦ sheet | Without the tick no text is sent |
| AI analytics events | `ai_request{engine}`, `ai_rewrite_done` (tone, length and latency buckets, action), `ai_polish_done{mode, auto}`, `ai_quota_hit`, `ai_error`, `ai_output_reported`, `consent_result`, `consent_withdrawn` | Per the analytics backend in use, unless the user turned off "Share usage analytics" | Analytics backend | Per PRD §12 | Enums, buckets and booleans only; never text. With the opt-out on, events are dropped before they are queued and again when the queue flushes |
| ✦ Polish (`POST /v1/polish`) | The transcript of the span one dictation just wrote; the dictation language, the target language (the translate pair's target for "Clean up + translate"), the output script setting; a random `client_req_id` | Yes, after consent | kb-api, which calls the model provider server-side | A tap on "Clean up" or "Clean up + translate" within 30 s of the dictation | Same rules as rewrite. The span is replaced only if it still holds exactly the dictated text |
| Auto-polish (Pro only, off by default) | The same as ✦ Polish, for every dictation | Yes, only after the separate auto-polish consent | kb-api | Every dictation that ends normally, while the toggle is on | Needs `consentAiAt`, `consentAutoPolishAt` and Pro (never true before Sprint 4), and the same field gate. Pauses until the next UTC day on a quota error |
| AI consent | `consentAiAt` and `consentAutoPolishAt` timestamps, the `autoPolish` toggle, the output script setting | No | `FeatureSettings` on the device | Agree on the consent screen (`AUTO_POLISH` kind for auto-polish) | Withdrawn from Settings → Private apps → Data and consent, or AI settings (`withAiConsentWithdrawn`) |
| Analytics opt-out | `analyticsOptOut` | No | `FeatureSettings` on the device | "Share usage analytics" switch | Off by default means analytics are shared; turning the switch off stops every event (FR-35) |

The kb-api side (TRD §5.4): request and response text is never logged or stored. The access log has the uid hash,
route, status, latency, `prompt_id@version`, model and token counts. Quota and spend counters are kept per uid
and day in Firestore. Reports are stored for 90 days, and their text only when the user ticked it. The model provider
receives the text to produce the output. The backend is not deployed in Sprint 3, so none of these flows runs in a
shipped build yet (`docs/sprint-3-device-checklist.md` A.1).

## Suggestions and the user dictionary (Sprint 3)

| Flow | Data | Leaves the device? | Where to | When | Notes |
|---|---|---|---|---|---|
| Suggestions and autocorrect | The word being typed and the previous word | No | — | Every keystroke while suggestions are on and the field allows the strip | Dictionaries are bundled in the APK (`assets/dict/<lang>.sdict`, FrequencyWords and urduhack lists); nothing is downloaded. Candidates and typed words are never logged (the upstream candidate debug logs were removed) |
| Learned words (`user_word`, `user_bigram` in `AppDatabase`) | Words the user accepted and the word before them, lower-cased, with a use count and last-used time; words the user removed are kept as blocked | No | Room on the device | On accepting a word, only when the field decision allows learning and the keyboard is not in incognito (PC-1) | Up to 5,000 words per language, least recently used evicted first. Not in backups (`AppDatabase` is not on the backup include list). Deleted with Settings → Suggestions and autocorrect → Clear learned words |
| Suggestion settings | `suggestionsEnabled`, `autocorrectLocales` | No | `FeatureSettings` on the device | Settings → Suggestions and autocorrect | |
