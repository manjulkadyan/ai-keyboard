### Task 8: Onboarding O1–O5 with funnel events

**Files:**
- Create: `app/src/main/kotlin/com/manjul/aikeyboard/fork/onboarding/{OnboardingScreen,OnboardingViewModel,OnboardingStep,ImeStatusSource,TryItStep,VoiceDisclosureSheet}.kt`
- Modify (FORK S1-onboarding): `app/FlorisAppActivity.kt` (start destination: onboarding unless `onboardingDone` **and** IME enabled+selected → Home; splash keep-on-screen capped at 1 s), `app/setup/SetupScreen.kt` route now points at the new screen (upstream screen kept, unused), app Home shows "Select AI Keyboard as your keyboard" card when enabled but not selected, `AndroidManifest.xml` (`RECORD_AUDIO` permission), `strings_fork.xml`, `docs/FORK_PATCHES.md`
- Test: `app/src/test/kotlin/com/manjul/aikeyboard/fork/onboarding/{OnboardingViewModelTest,OnboardingScreenTest}.kt`

**Interfaces:**
- Consumes: Task 2 `FeatureSettingsStore` (`onboardingStep`, `onboardingDone`, `firstOpenAt`, `translatePair`, `consentVoiceNoticeAt`); Task 4 `SubtypeManager.ensureDefaultSubtypes`, `MVP_LANGUAGE_TAGS`; Task 6 catalog + relay; upstream `InputMethodUtils.observeIsFlorisboardEnabled/Selected`, `showImeEnablerActivity`, `showImePicker`.
- Produces: `enum class OnboardingStep { WELCOME, ENABLE, SELECT, LANGUAGES, TRY_IT, DONE }`; `interface ImeStatusSource { val enabled: Flow<Boolean>; val selected: Flow<Boolean> }` (prod impl wraps `InputMethodUtils`, plus an `onResume` re-check); `class OnboardingViewModel(store, imeStatus, analytics, subtypes, clock)` with `val state: StateFlow<OnboardingUiState>`, `fun next()`, `fun skip()`, `fun onLanguagesChosen(tags: List<String>, pair: Pair<String,String>)`, `fun onMicPermissionResult(granted: Boolean)`. `VoiceDisclosureSheet` sets `consentVoiceNoticeAt` and is reused by Task 11's trampoline.

- [ ] **Step 1: Write failing tests.** `OnboardingViewModelTest` (Turbine, fake `ImeStatusSource`, virtual clock):
  - Fresh start → WELCOME, logs `onboarding_start{source=launcher}`.
  - ENABLE: emitting `enabled=true` advances to SELECT within the same dispatcher tick (≤ 1 s, PRD FR-08 AC1), logs `ime_enabled{seconds_since_start_bucket}`; `selected=true` → LANGUAGES, logs `ime_selected`.
  - Process death at SELECT (new VM with same store) resumes at SELECT.
  - `onLanguagesChosen(["en-US","ur-PK"], "en" to "ur")` → `ensureDefaultSubtypes` called with those tags, logs `languages_set{count=2, has_rtl=true}`.
  - Default language preselection from system locales maps `ur_PK` → `ur-PK`, unknown locales → `en-US` only.
  - `skip()` from TRY_IT → DONE, `onboarding_complete{skipped=true}`, `onboardingDone = true`.
  - Returning user with done + enabled + selected → start at DONE (Home).
  - `onMicPermissionResult(false)` logs `voice_permission_result{granted=false}` and stays on TRY_IT with typing still possible.
  - No ads/paywall reference: assert the VM has no dependency on `:core:ads` (module test: `:app` onboarding package imports nothing from `core.ads`).
  - `OnboardingScreenTest` (Robolectric + Compose UI test): TRY_IT shows a text field and "Skip"; mic permission is requested only after the voice disclosure "Agree" (`ActivityResultContracts.RequestPermission` launched once).
- [ ] **Step 2: Run** `$G ./gradlew :app:testDebugUnitTest --tests '*onboarding*'` — Expected: FAIL.
- [ ] **Step 3: Implement** O1–O5 per PRD §8 (copy: tagline "Speak or type in your language. Send it in theirs.", privacy line "We never see passwords. No ads in your keyboard."). The voice disclosure copy states the active recognizer kind (on-device / system speech service / Whisper on-device), using `SpeechEngineSelector` from Task 10 once it lands — until then, a `() -> EngineKind` lambda the activity provides.
- [ ] **Step 4: Run** step 2 + `$G ./gradlew :app:lintDebug :app:assembleDebug && scripts/check-fork-markers.sh` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(onboarding): O1–O5 flow with resume and funnel events`.

