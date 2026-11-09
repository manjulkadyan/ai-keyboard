# Play Data safety draft (Sprint 4 build)

Source of truth: `docs/data-flows.md`. This draft is the TRD §11.6 table, marked with what the Sprint 4 build ships.
Check it again before every release. `scripts/check-data-flows.sh` fails CI when a new SDK artifact has no
data-flows row, so a change to this draft starts there.

Play definitions (TRD §11.6): data sent to a service provider that processes it for us (Firebase, kb-api's model
provider) is collection, not sharing. Data that the AdMob SDK sends for its own advertising purposes is sharing.

| Play data type | Collected | Shared | Ephemeral | Required or optional | Purpose | Source | Sprint 4 |
|---|---|---|---|---|---|---|---|
| App activity → Other user-generated content (keep the same choice in the listing and the policy) | Yes: text selected for AI and transcripts for Polish | No | Yes | Optional (user-started, after AI consent) | App functionality | `/v1/rewrite`, `/v1/polish` | Ships. Cloud translation (`/v1/translate`) is not in this build, so it is not a source |
| Audio | No. The platform recognizer records in the recognizer app. On-device Whisper never transmits | No | Yes | Optional | App functionality | TRD §11.3 #3.4 | Ships as "not collected" |
| Photos | — | — | — | — | — | — | Not in this build: no cloud OCR (OD-10). Leave the row out |
| Personal info → Email address | — | — | — | — | — | — | Not in this build: no Google sign-in. Auth is anonymous only. Leave the row out |
| Personal info → User IDs | Yes (anonymous Firebase uid) | No | No | Required for AI | App functionality, fraud prevention | Firebase Auth, kb-api quota | Ships |
| Financial info → Purchase history | Yes | No | No | Optional | App functionality | Play Billing, `/v1/entitlement/*` | Ships |
| Location → Approximate location | Yes (IP-based, ads SDK) | Yes (AdMob) | No | Required for the free tier | Advertising | GMA SDK guidance | Ships (app process only, never in the keyboard) |
| App activity → App interactions | Yes | Yes (AdMob, ad interactions) | No | Optional for analytics (opt-out) | Analytics, advertising | Firebase Analytics, GMA | Ships |
| App info and performance → Crash logs, Diagnostics | Yes | Yes (AdMob diagnostics) | No | Required | Analytics | Crashlytics, ML Kit SDK telemetry, GMA | Ships. No Firebase Performance in this build |
| Device or other IDs | Yes (Firebase installation id; advertising id in the free tier) | Yes (AdMob) | No | Required for the free tier | Advertising, analytics, fraud prevention | GMA, Firebase | Ships |
| Typed keystrokes | No (never sent) | No | — | — | — | PC-2 | Ships as "not collected" |

Form answers:

- Data is encrypted in transit: yes (HTTPS to kb-api and Google).
- Users can request deletion: yes, in the app (Settings → Privacy → Delete my data, `POST /v1/account/delete`).
- The web deletion URL (RN-08) is `https://DOMAIN/delete-account`. kb-api serves it (`GET /delete-account`, a
  static page). It is not deployed yet, and the domain is still open (RN-08). Set `DELETION_CONTACT_EMAIL` before
  you submit the form.
- The app declares the `AD_ID` permission (the GMA SDK adds it).
