### Task 6: `:core:analytics` catalog, limits, consent-aware batched sink

**Files:**
- Create: `core/analytics/build.gradle.kts` (`aikeyboard.android.library`), `core/analytics/src/main/kotlin/com/manjul/aikeyboard/core/analytics/{AnalyticsEvent,EventCatalog,AnalyticsSink,LogcatSink,NoopSink,BatchingRelay,SessionSampler,Buckets}.kt`
- Modify: `settings.gradle.kts`, `app/build.gradle.kts`, `FlorisApplication.kt` (lazy `analytics`), `docs/FORK_PATCHES.md`
- Test: `core/analytics/src/test/.../{EventCatalogLimitsTest,BatchingRelayTest,SessionSamplerTest,BucketsTest}.kt`

**Interfaces:**
- Produces:
  - `data class AnalyticsEvent(val name: String, val params: Map<String, Any>)`.
  - `object EventCatalog` with factory functions for the PRD §12 events used this sprint: `onboardingStart(source)`, `onboardingStepView(step: String /* "1","2","3","try" */)`, `imeEnabled(secondsBucket)`, `imeSelected(secondsBucket)`, `languagesSet(count, hasRtl)`, `onboardingComplete(skipped)`, `imeSessionStart(subtype, isSensitive)`, `firstDictation(lang)`, `voiceSessionStart(lang, engine /* system_on_device|system_default|whisper */)`, `voiceSessionEnd(durationBucket, wordsBucket, endReason /* user|silence|error|cap|field_change */, errorCode: Int?)`, `voicePermissionResult(granted)`, `voiceUnavailable(reason)`, `privacyPrivateAppAdd()`. `val allNames: List<String>` and `val paramRegistry: Map<String, Set<String>>`.
  - `interface AnalyticsSink { fun log(event: AnalyticsEvent) }`; `LogcatSink` (debug only, tag `AiKbAnalytics`), `NoopSink` (release until Firebase).
  - `class BatchingRelay(sink: AnalyticsSink, clock: () -> Long, scope: CoroutineScope, optOut: () -> Boolean, policy: () -> PolicyDecision?)` with `fun log(event)`; flushes at most once per 60 s (PRD FR-35 AC2); drops everything when `optOut()`; when `policy()?.analyticsMinimal == true` only `ime_session_start` with `is_sensitive=true` passes.
  - `class SessionSampler(clock) { fun shouldLogImeSession(): Boolean }` — 1 per 10 min.
  - `object Buckets { fun seconds(s: Long): String; fun words(n: Int): String; fun duration(ms: Long): String }` with edges `0-5,5-15,15-30,30-60,60-300,300+` (seconds/duration) and `0,1-5,6-20,21-50,51+` (words).
- Consumes: `PolicyDecision` from `:ime:policy` (add dependency).

- [ ] **Step 1: Write failing tests.** `EventCatalogLimitsTest`: every name matches `^[a-z][a-z0-9_]{0,39}$`, ≤ 25 params, keys ≤ 40, string values ≤ 100; every param key used by each factory is in `paramRegistry[name]`; names equal the PRD set listed above (`first_keystroke` absent). `BatchingRelayTest` (virtual time): 3 events within 10 s → 0 flushes until t = 60 s, then 1 flush with 3 events; opt-out drops; minimal policy filters. `SessionSamplerTest`: true, false at +9 min, true at +10 min. `BucketsTest` for each edge.
- [ ] **Step 2: Run** `$G ./gradlew :core:analytics:test` — Expected: FAIL.
- [ ] **Step 3: Implement.** No Firebase dependency in this sprint.
- [ ] **Step 4: Run** `$G ./gradlew :core:analytics:test :app:assembleDebug` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(analytics): event catalog, limits test and batched consent-aware relay`.

