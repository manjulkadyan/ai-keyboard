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
