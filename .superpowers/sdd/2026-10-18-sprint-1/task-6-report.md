# Task 6 report: :core:analytics

Implemented the module, catalog, relay, sampler, buckets, sinks; wired lazy `analytics` in FlorisApplication (FORK(S1-analytics), FORK_PATCHES rows, settings/app gradle).

TDD: RED = `:core:analytics:test` with main sources moved away -> unit test compilation failed (expected, no classes). GREEN = `:core:analytics:test :app:assembleDebug` BUILD SUCCESSFUL; 13 tests (Batching 5, Buckets 3, Catalog 4, Sampler 1), 0 failures. `scripts/check-fork-markers.sh` OK.

Decisions/differences:
- PRD §12 lists `engine` as on_device/system and `end_reason` as user/silence/error; brief's wider enums (system_on_device|system_default|whisper; cap, field_change) used. `voice_unavailable` is in the brief but not in PRD §12; kept.
- Relay: flush deadline = first event time + 60 s using `clock`; opt-out re-checked at flush; `flushNow()` added (cancels schedule), not wired to lifecycle.
- Catalog factories clamp string values to 100 chars and omit null `error_code`.
- App scope reuses FlorisApplication's existing private `scope` (Dispatchers.Default).
- `:core:analytics` uses `aikeyboard.android.library` (same as core/datastore); `api(projects.ime.policy)`.

## Fix round 1
- Closed enums (`AnalyticsEnums.kt`): OnboardingSource, OnboardingStep, VoiceEngine, EndReason, UnavailableReason. Factories take them; bucket params are produced inside the catalog from raw numbers (`imeEnabled(secondsSinceStart: Long)`, `voiceSessionEnd(durationMs, words, endReason, errorCode)`).
- `subtype`/`lang` validated against `^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8}){0,3}$`, else "other"; KDoc says subtype is the primary language tag.
- Relay: policy predicate re-applied to the batch at flush; `lastFlushAt` tracked (set on non-empty flushes); deadline = max(first + 60 s, last + 60 s); only manual `flushNow` cancels the job, so the running job never cancels itself. Note: since first event time is always >= last flush time, the max is equal to first + 60 s, so after a manual flush at t=0 an event at t=10 s flushes at 70 s (test asserts this).
- Null policy stays "not minimal" (comment in FlorisApplication).
- Tests: package name/free text -> "other"; every factory exercised with oversize input; every registry key produced; manual-flush window; policy at flush. 16 tests, 0 failures.
- Verified: `./gradlew :core:analytics:test :app:assembleDebug` BUILD SUCCESSFUL; `check-fork-markers.sh` OK.
