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

## Remote Config, Analytics and Crashlytics (Sprint 4)

| Flow | Data | Leaves the device? | Where to | When | Notes |
|---|---|---|---|---|---|
| Remote Config fetch | Firebase installation id, app id and version, device locale and SDK metadata (per Google's terms) | Yes, only when Firebase is configured | Google (Firebase Remote Config) | App activity start: activate the cached values, fetch after the first frame (1 h in debug, 12 h in release). Real-time kill-switch listener while an app activity is started | No user content. The IME process never fetches |
| IME config mirror | Catalog keys and values only, plus the activation time | No | `datastore/rc_mirror.json` on the device | Each activation in the app process. Kill-switch updates change only `ai_enabled_global` and `policy_force_sensitive_packages` | The IME reads the mirror. Unknown keys are dropped |
| Firebase Analytics | Catalog events and params only (`EventCatalog`), Firebase app instance id, device and app metadata | Yes, only when Firebase is configured and the user did not opt out | Google (Firebase Analytics) | Each batched event from the analytics relay | Collection and ad id collection are off in the manifest. Code turns them on only without the opt-out, and off at once on opt-out. Consent Mode v2 (`ad_storage`, `ad_user_data`, `ad_personalization`, `analytics_storage`) follows the UMP row below |
| Crashlytics | Crash and non-fatal stack traces with exception class names, area tag, device and app metadata | Yes, only when Firebase is configured and the user did not opt out | Google (Firebase Crashlytics) | Crashes and `NonFatalReporter.report` | Every exception message is dropped (`CrashlyticsReporter.scrub`). Collection is off in the manifest until the app starts it |

## Play Billing and Pro entitlement (Sprint 4)

| Flow | Data | Leaves the device? | Where to | When | Notes |
|---|---|---|---|---|---|
| Play Billing (`:core:billing`, PBL 9.1.0) | Product id `pro`, the chosen base plan and offer token; Google account and payment data stay inside Play | Yes | Google Play | Paywall open (product details), purchase sheet, app start and resume (purchase list) | App process only, never the IME (`ImeAdIsolationTest`). The client never acknowledges: kb-api does. Purchase tokens and order ids are never logged, put in analytics, exceptions or `toString` |
| Entitlement verify and read (`POST /v1/entitlement/verify`, `GET /v1/entitlement`) | The Play purchase token and product id (verify only), plus the kb-api auth headers | Yes | kb-api, which checks the token with the Play Developer API | After a purchase, paywall open and restore. App start and resume (at most once per 5 min) only after AI consent or an earlier entitlement answer. Offline retries at 2, 4 and 8 s, then a unique `VerifyPurchaseWorker` with no input data | kb-api is the only Pro authority (ARCH-006). The answer is ECDSA P-256 signed |
| Pro cache (`FeatureSettings.pro*`) | `proActive`, `proState`, `proExpiresAt`, `proIssuedAt`, `proSignature`, `proCheckedAt` | No | `FeatureSettings` on the device | Each verified kb-api answer | Written only after the signature check. Pro holds until `expires_at` plus 3 days offline. No token is stored. The IME and the app screens read Pro from here only |
| "Fix payment" link | The app package name and the product id `pro` in the URL | Yes, when the user taps the banner | Google Play (`play.google.com/store/account/subscriptions`) | A tap on the Settings home banner in grace or account hold | Opens Play in the browser or the Play app. Nothing else is sent |
| Paywall (S-03) | The trigger (`ai_quota`, `win_back` and so on) and the open time in `paywall_view` and `paywall_close{seconds_bucket}`; Play product details for `pro` | Analytics: as the Firebase Analytics row. Play: yes | Firebase Analytics (catalog events only); Google Play | Each paywall open and close. Open also runs `refresh(PAYWALL_OPEN)` (the entitlement row) and loads the product details. Restore runs the entitlement verify for an owned purchase | Prices and trial terms come from `ProductDetails` only. No purchase token or user content in events |
| Paywall frequency (`FeatureSettings.paywallOnboardingShownAt`, `winBackShownCount`, `winBackLastShownAt`) | One time stamp and a count | No | `FeatureSettings` on the device | When O6 or the Home win-back card shows | O6 shows once ever. The win-back card shows on day 3 and day 10 after `firstOpenAt`, at most 2 times, never for Pro |

## Ads and consent (Sprint 4)

App process only. The keyboard never loads UMP or GMA (`ImeAdIsolationTest`, `ImeOnlyColdStartTest`), and neither SDK runs at process start (`MobileAdsInitProvider` removed, `verify<Variant>NoAdAutoInit`). Pro users and `ads_enabled_global = false` start neither SDK.

| Flow | Data | Leaves the device? | Where to | When | Notes |
|---|---|---|---|---|---|
| UMP consent (`:core:ads`, UMP 4.0.0) | Consent request with device and app metadata and the advertising id (per Google's UMP terms); the user's consent choices | Yes | Google (User Messaging Platform) | Each cold start of the main Activity after onboarding, for a non-Pro user with `ads_enabled_global` on. The form shows only where consent is required | UMP stores the IAB TCF v2 strings (`IABTCF_*`) in the app's default shared preferences. The app reads `gdprApplies`, `PurposeConsents` and `VendorConsents` only to set Consent Mode v2 for Analytics. Debug may force the EEA geography |
| Consent Mode v2 to Analytics | Four granted or denied flags | Per the Firebase Analytics row | Firebase Analytics | Each UMP result | No ad requests: all denied. Outside GDPR: all granted. Under GDPR, all need Google vendor consent (755): storage from purpose 1, ad user data from purposes 1 and 7, personalisation from purposes 3 and 4 |
| Google Mobile Ads SDK start (GMA 25.5.0) | AdMob app id, advertising id (`AD_ID` permission), device, app and network metadata, SDK diagnostics (per Google's AdMob terms) | Yes | Google (AdMob) | Once per process, after UMP allows ad requests (`canRequestAds`), on a background thread | Ad units load only from S4 Task 7 placements (rows below). Debug and beta use Google's sample app id, so only test ads can serve. SDK exceptions are caught and reported as class name and stack only (`safeAdCallback`) |
| Ad requests (S4 Task 7: `app_open`, `inter_feature_exit`, `banner_home`, `native_translator`, `native_history`) | The ad unit id from Remote Config (`ad_unit_<placement>`) plus the GMA request metadata of the SDK start row | Yes | Google (AdMob) | App-open preload after the SDK is `READY`; interstitial preload; banner on Home; native as the last item of Translator and History. Never for Pro, in the first session, or with an empty unit id | No user content goes into a request. An empty unit id turns the placement off. Rules in `:core:ads` `AdEligibility`; caps in `AdCapPolicy` |
| `ad_paid_event` (`app/ads/PaidEventLogger`) | `placement`, `format`, `value_micros` (Long), `currency` (ISO 4217 or `other`), `precision` | Per the Firebase Analytics row | Firebase Analytics, through the consent-aware relay | Each GMA paid callback (impression-level revenue) | No ad unit id, ad content, advertising id or user content. A failing sink never throws into the SDK |
| `ads_state.json` (`:core:ads` `DataStoreAdCapStateStore`) | Ad show records (placement, format, time), session count, last background time, feature-use count, last interstitial and app-open times | No | App files (`files/datastore/ads_state.json`), app process only | Each ad show, session start, background and feature use | Counts and time stamps only. Feeds the frequency caps and the session and feature-use rules. Removed with the app data |

## In-app update (Sprint 4)

App process only (Home). The keyboard never loads the Play update client.

| Flow | Data | Leaves the device? | Where to | When | Notes |
|---|---|---|---|---|---|
| Update check (`fork/update/UpdateController`) | The package name and installed version, which Play already has | Yes | Google Play (the Play Core app-update client) | On Home resume, at most once per 24 h, after onboarding, never for a text-selection or share entry. A declined immediate update asks again on the next Home resume | No account, text or device id is added by the app. Nothing goes to kb-api |
| `updateCheckedAt` | The time of the last finished check | No | `FeatureSettings` on the device | After a check | Local only, never sent |
| `update_prompt` | `type` (`flexible`, `immediate`), `result` (`accepted`, `declined`, `failed`) | Yes, with analytics consent | Firebase Analytics, through the consent-aware relay | Once per shown update dialog | No version number or user content |

## Themes and key sounds (Sprint 4)

| Flow | Data | Leaves the device? | Where to | When | Notes |
|---|---|---|---|---|---|
| Theme choice | The day and night theme ids (`theme__day_theme_id`, `theme__night_theme_id`, upstream preferences) | No | Upstream preferences on the device | Apply on the Themes screen; `PremiumThemeGuard` on keyboard start after Pro lapses | Local only, never sent |
| `keySound` | `off`, `system`, `soft`, `crisp`, `wood` or `pop` | No | `FeatureSettings` on the device | Key sound choice on the Themes screen | Local only, never sent. The sounds are bundled (`res/raw`), nothing is downloaded |
| `pendingPremiumThemeId` | The stylesheet id of a premium preset tried without Pro | No | `FeatureSettings` on the device | Apply of a premium preset without Pro. Cleared by any applied theme, which includes the pending one when Pro turns on while the Themes screen is open or on its next open | Local only, never sent |
| `theme_apply` | `theme_id` (a built-in preset id, else `other`), `premium` (bool) | Yes, with analytics consent | Firebase Analytics, through the consent-aware relay | Once per applied theme | No user content |

## Settings (Sprint 4)

| Flow | Data | Leaves the device? | Where to | When | Notes |
|---|---|---|---|---|---|
| AI quota (`GET /v1/quota`, `:core:ai` `QuotaClient`) | The kb-api auth headers only; no body | Yes, only after AI consent | kb-api | Each open of Settings → AI while `consentAiAt` is set | The answer (`ai_left_today`, `resets_at`, `tier`) is shown and not stored. No request without consent or a base URL |
| `aiDefaultTone` | One of `fix`, `formal`, `friendly`, `shorter`, `longer`, `emojify` | No | `FeatureSettings` on the device | Settings → AI → Default tone | Puts that tone first on the ✦ sheet. Local only, never sent |
| `consentCloudTranslateAt` | A time stamp, 0 when not given | No | `FeatureSettings` on the device | Withdraw on Settings → Privacy (cloud translation is not in this build, so nothing sets it yet) | Withdraw also sets `cloudTranslateOptIn` to false |
| Consent withdraw (Settings → Privacy) | The consent time stamps (`consentAiAt`, `consentAutoPolishAt`, `consentCloudTranslateAt`, `consentVoiceNoticeAt`) set to 0 | No | `FeatureSettings` on the device | A confirmed Withdraw tap | `consent_withdrawn{kind}` is logged for the AI and auto-polish kinds only, through the consent-aware relay |
| Ad privacy options | The UMP privacy options form (see the UMP row) | Per the UMP row | Google (User Messaging Platform) | A tap on Settings → Privacy → Ad privacy options, shown only when UMP requires it | App process only |
| Restore purchases (Settings → Subscription) | As the entitlement verify row | Yes | Google Play and kb-api | A tap on Restore purchases | `restore_purchases{found}` (a boolean) through the consent-aware relay |

## Suggestions and the user dictionary (Sprint 3)

| Flow | Data | Leaves the device? | Where to | When | Notes |
|---|---|---|---|---|---|
| Suggestions and autocorrect | The word being typed and the previous word | No | — | Every keystroke while suggestions are on and the field allows the strip | Dictionaries are bundled in the APK (`assets/dict/<lang>.sdict`, FrequencyWords and urduhack lists); nothing is downloaded. Candidates and typed words are never logged (the upstream candidate debug logs were removed) |
| Learned words (`user_word`, `user_bigram` in `AppDatabase`) | Words the user accepted and the word before them, lower-cased, with a use count and last-used time; words the user removed are kept as blocked | No | Room on the device | On accepting a word, only when the field decision allows learning and the keyboard is not in incognito (PC-1) | Up to 5,000 words per language, least recently used evicted first. Not in backups (`AppDatabase` is not on the backup include list). Deleted with Settings → Suggestions and autocorrect → Clear learned words |
| Suggestion settings | `suggestionsEnabled`, `autocorrectLocales` | No | `FeatureSettings` on the device | Settings → Suggestions and autocorrect | |
