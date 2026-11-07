# Review notes: product changes needed (from TRD §11, 2026-10-17)

These items came out of the engineering and Play-policy review of `TRD.md`. They need a decision from product, or a PRD edit by its owner. The review did not edit `PRD.md`. Items the Review log pass already fixed in the PRD are not repeated here: Pro quota 150/3,000, FR-10 timings, `/v1/polish`, the rewrite tones, clipboard and history values, and `rewarded_ai_credit`.

| ID | PRD ref | Issue | Proposed change | TRD ref |
|---|---|---|---|---|
| RN-01 | §13.1/13.2 milestones (about M+3 / M+4.5) | With the scope in the PRD, the estimate is 76 raw dev-days for MVP, or 95–99 with buffer. Add the closed test and review time, and the realistic public launch is about M+5. v1 comes about 6.5 months after start | Either accept M+5, or cut scope to reach about M+4. Possible cuts: phonetic layouts move to v1 (−2 d); preset themes only at MVP (−1.5 d); 6 UI locales at MVP instead of 12 (−1 d); a status-only packs manager (−1 d); settings screens reuse upstream FlorisBoard (−1 d) | §11.9 |
| RN-02 | OD-08, FR-15, §10.2 | AI unit economics. With Opus 5.5 ($4/$20 per MTok), a Pro user at the 3,000/month cap costs about $17–45 a month. Revenue is $1.42–2.54 a month. A free user doing 10 a day costs about $1.75 a month, which is more than ad ARPU in PK/IN/BD. With Haiku 5.5, the same users cost about $0.45–1.14 (Pro) and $0.05 (free) | Finance signs off a cost ceiling per user. The PRD states that the model is the cheapest one that passes the eval, with Opus allowed only as the eval reference. If eval forces Sonnet 5.5, lower the Pro monthly cap to about 400 actions, or raise the price | §11.4 |
| RN-03 | FR-16 "Auto-polish every dictation" (Pro) | This sends every dictation off the device without a tap. That needs its own Prominent Disclosure and consent, and it multiplies AI cost by the number of sessions | Make it off by default with its own consent screen. Count it against the quota. Never run it in sensitive fields. Or drop it from MVP | §11.4, §11.6 |
| RN-04 | FR-10 AC4/AC5, §8 O5 | An IME cannot show a permission dialog. A "first mic use" inside the keyboard can only open a small trampoline activity, and the keyboard hides while it is open | Make the onboarding try-it step (O5) the main place to ask. In the keyboard, the copy reads "Allow microphone, then tap the mic again". Dictation does not resume automatically | §11.3 #3.1 |
| RN-05 | FR-02 AC4 "Banking-category apps are suggested" | Android has no banking app category, and `QUERY_ALL_PACKAGES` is not allowed for a keyboard | Use a curated list of known banking and wallet packages per launch market, matched against launchable apps. Also add a smartbar action, "Make this app private" | §11.2 #2.7 |
| RN-06 | FR-36 AC1 "download progress visible", cancel | ML Kit gives no byte progress and cannot cancel a download | Show an indeterminate indicator and an approximate size. Rename "Cancel" to "Stop" (the app hides the download and deletes the model when it finishes). Before download, show the total for the models still missing (a non-English pair also needs the English model) | §11.5 |
| RN-07 | §10.6 Data safety "Audio … shared with Google" | On the system-recognizer path the audio is captured by the RecognitionService app, not by us, so it is not our collection. Declaring audio overstates what we collect and hurts install conversion | Declare Audio only if cloud ASR (OD-04) ships. Add the full draft from TRD §11.6, including: AdMob means Device IDs and approximate location are shared; text sent to AI or translation providers counts as collected but not shared, and is ephemeral | §11.6 |
| RN-08 | FR-34, FR-15 | Play requires two things the PRD does not cover. (a) Google sign-in creates accounts, so deletion is needed both in the app **and through a web URL**. (b) The AI-generated content policy needs a way to report output inside the app | Add a web deletion page to FR-34, and "Report" on the AI sheet to FR-15/16, with text attached only if the user opts in | §11.4, §11.6, §5.3 |
| RN-09 | FR-14 (v1 PROCESS_TEXT "Rewrite"), FR-15 | Consent classes are not defined at product level | Use four consent classes: AI, cloud translate, auto-polish, and a voice notice. Each one has its own disclosure screen with an affirmative "Agree", and can be withdrawn in Privacy settings | §11.6 |
| RN-10 | FR-27 AC1 "no `MobileAds` class loaded in the IME service" | This guarantee holds only if the SDK auto-init providers are removed, or if the IME runs in its own process. It is an engineering fix, but the store copy ("no ads in your keyboard") depends on it | Keep the claim. It must not appear in the store listing until the CI test in TRD §11.8 passes | §11.1 |

## Decisions (product, 2026-10-18)

All ten items are applied to `PRD.md` (see its 2026-10-18 review log).

| ID | Decision |
|---|---|
| RN-01 | **No cuts.** Accept public MVP launch at about M+5, v1 at about M+6.5 |
| RN-02 | **Cheapest models only**; Opus 5.5 is the eval reference only. If eval forces Sonnet 5.5, Pro cap drops to about 400 per month. **Still open:** Finance sign-off on the cost ceiling per user |
| RN-03 | **Keep in MVP** as a Pro toggle, off by default, own consent, counts against quota, never in sensitive fields |
| RN-04 – RN-10 | Proposed changes accepted as written |

**Inputs still needed:** launch-market list for the banking-app list (RN-05); a domain for the web deletion page (RN-08); TRD OD-04 (cloud speech recognition) is still "none in MVP", which keeps Audio out of Data safety (RN-07).

### Follow-up (2026-10-18)

- **Voice:** on-device Whisper is the MVP fallback, with the system recognizer kept as primary. No cloud speech recognition, so Audio stays undeclared (RN-07 holds).
- **AI:** Gemini Nano runs on-device first where supported, after a spike (PRD OD-20); the cheapest cloud model is the fallback. This lowers RN-02 cost on supported phones only.
- **Timeline:** about +7–9 raw dev-days, so MVP is about M+5.5.

