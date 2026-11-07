### Task 1: SensitiveFieldPolicy and the ~400-case matrix

**Files:**
- Modify: `ime/policy/src/main/kotlin/com/manjul/aikeyboard/ime/policy/SensitiveFieldPolicy.kt` (replace placeholder)
- Create: `ime/policy/src/main/kotlin/com/manjul/aikeyboard/ime/policy/{FieldDescriptor,PolicyContext,PolicyDecision,InputTypeBits,CardFieldHeuristic}.kt`
- Test: `ime/policy/src/test/kotlin/com/manjul/aikeyboard/ime/policy/{SensitiveFieldPolicyMatrixTest,CardFieldHeuristicTest,PolicyDecisionOrderingTest}.kt`

**Interfaces:**
- Produces:
  - `data class FieldDescriptor(val packageName: String?, val inputType: Int, val imeOptions: Int, val hintText: String?, val label: String?)`
  - `data class PolicyContext(val deviceLocked: Boolean, val incognitoManual: Boolean, val privatePackages: Set<String>, val forceSensitivePackages: Set<String>)`
  - `enum class SensitivityReason { PASSWORD, NUMBER_PASSWORD, NO_PERSONALIZED_LEARNING, INCOGNITO_MANUAL, DEVICE_LOCKED, PRIVATE_APP, FORCED_PACKAGE, CARD_HEURISTIC }`
  - `data class PolicyDecision(val sensitive: Boolean, val reasons: Set<SensitivityReason>, val allowLearning: Boolean, val allowSuggestionHistory: Boolean, val allowSuggestionStrip: Boolean, val allowClipboardHistory: Boolean, val allowTranslate: Boolean, val allowVoice: Boolean, val allowAi: Boolean, val allowCloud: Boolean, val analyticsMinimal: Boolean)` with `fun isMoreRestrictiveThan(other: PolicyDecision): Boolean` (true if any `allow*` flips true→false) and `companion val Unrestricted` / `val Locked`.
  - `object SensitiveFieldPolicy { fun evaluate(field: FieldDescriptor, ctx: PolicyContext): PolicyDecision }`
  - `InputTypeBits` holds the `android.text.InputType` / `EditorInfo` constants as `const val`s (module stays pure JVM).

- [ ] **Step 1: Write the failing tests.**
  - `SensitiveFieldPolicyMatrixTest`: `@ParameterizedTest @MethodSource` over every combination of class {TEXT, NUMBER, PHONE, DATETIME, NULL} × the variations valid for that class × flags {none, NO_SUGGESTIONS} × imeOptions {none, NO_PERSONALIZED_LEARNING} × ctx {plain, locked, incognito, private-app, forced-package} (≈ 400 cases; assert `cases.size >= 400`). Expected values come from an independent table in the test (`expectedFor(case)`), written from TRD §4.1.2:
    - sensitive (all `allow*` false, `analyticsMinimal = true`) for TEXT+PASSWORD/VISIBLE_PASSWORD/WEB_PASSWORD, NUMBER+PASSWORD, NO_PERSONALIZED_LEARNING, incognito, locked, private-app, forced-package.
    - TEXT+NO_SUGGESTIONS non-sensitive: only `allowSuggestionStrip = false`.
    - PHONE, DATETIME, TEXT+URI: `allowTranslate = allowAi = false`, rest true. TEXT+EMAIL_ADDRESS / WEB_EMAIL_ADDRESS: same plus `allowLearning = allowSuggestionHistory = false`.
  - `CardFieldHeuristicTest`: NUMBER class with hint/label matching (case-insensitive) `card`, `card number`, `cvv`, `cvc`, `iban`, `pin`, `otp`, `one-time` → reason `CARD_HEURISTIC`, `allowLearning/allowTranslate/allowAi/allowCloud/allowVoice/allowClipboardHistory = false`, `sensitive = false` (soft signal). TEXT class with hint "card" → no reason. `"spinner"` must not match `pin` (word boundary).
  - `PolicyDecisionOrderingTest`: `Unrestricted.isMoreRestrictiveThan(Locked) == false`, `Locked.isMoreRestrictiveThan(Unrestricted) == true`, equal decisions → false.
- [ ] **Step 2: Run** `$G ./gradlew :ime:policy:test` — Expected: FAIL (unresolved references).
- [ ] **Step 3: Implement** the files above. Card regex: `\b(card|cvv|cvc|iban|pin|otp|one[- ]?time)\b`, English only this sprint (other UI locales are added with the S1 translations drop; keep the pattern list in one `val` so they can be appended).
- [ ] **Step 4: Run** `$G ./gradlew :ime:policy:test` — Expected: PASS, matrix reports ≥ 400 cases.
- [ ] **Step 5: Commit** `feat(policy): SensitiveFieldPolicy with input matrix and card heuristic`.

