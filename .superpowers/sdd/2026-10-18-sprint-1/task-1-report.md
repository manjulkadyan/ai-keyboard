# Task 1 report: SensitiveFieldPolicy

Status: DONE. Commit on `sprint-1` (see `git log -1`), subject `feat(policy): SensitiveFieldPolicy with input matrix and card heuristic`.

## Implemented (all under ime/policy/src/main/kotlin/com/manjul/aikeyboard/ime/policy/)
FieldDescriptor, PolicyContext, PolicyDecision (+SensitivityReason, Unrestricted, Locked, isMoreRestrictiveThan), InputTypeBits, CardFieldHeuristic (internal, pattern list in one `val`), SensitiveFieldPolicy (object, replaces the Sprint 0 interface placeholder).
The old placeholder test `SensitiveFieldPolicyTest.kt` was deleted (it tested the removed interface).
build.gradle.kts unchanged: the convention plugin already adds junit-jupiter-params, kotlin-test-junit5 and the engine.

## Tests
`./gradlew :ime:policy:test --offline`: PolicyDecisionOrderingTest 4, CardFieldHeuristicTest 15, SensitiveFieldPolicyMatrixTest 441 (440 matrix cases + size>=400 assertion). 0 failures, 0 skipped.

## TDD
RED: first run of `:ime:policy:test` failed in compileTestKotlin with "Unresolved reference 'PolicyContext' / 'PolicyDecision' / 'SensitivityReason' / 'evaluate'" and "Too many arguments for constructor FieldDescriptor" (expected: types not yet written).
GREEN: same command, BUILD SUCCESSFUL, counts above.

## Decisions
- Followed lead rulings (overlap precedence, Locked/Unrestricted, NULL class, card only on NUMBER, hint+label).
- `Locked.reasons = {DEVICE_LOCKED}`; a sensitive decision is `Locked.copy(reasons = all applicable)`, so evaluate() with only the locked ctx equals `PolicyDecision.Locked`.
- NO_SUGGESTIONS is honoured only for TEXT class (it is a text flag; the bit means nothing elsewhere). The matrix still crosses the flag with every class.
- Matrix: 22 class/variation pairs x 2 flags x 2 ime x 5 ctx = 440. TEXT variations cover 0x00..0xe0 in 0x10 steps; NUMBER {0,PASSWORD}; PHONE {0}; DATETIME {0,date,time}; NULL {0}.
- Card heuristic with a sensitive reason: CARD_HEURISTIC is added to `reasons` but decision is the fully locked one.
- Email rule includes WEB_EMAIL_ADDRESS; URI rule is TEXT-only.
- Hint/label text is only matched, never stored or logged.

## Concerns
None. No upstream files touched, so no FORK markers needed. `check-fork-markers.sh` not run (no upstream edits). Callers of the old interface: none in the repo.
