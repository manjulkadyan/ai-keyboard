# Task 4 report: 8 MVP subtypes

Commit: feat(subtypes): declare 8 MVP subtypes and seed defaults (single commit on sprint-1).

## Implemented
- `res/xml/method.xml`: placeholder subtype replaced by 8 subtypes (locale `en_US` style, languageTag, label from `strings_fork.xml`, `isAsciiCapable` true for en/es/fr/pt/id, false for ar/ur/hi). Attribute order puts locale first so each hunk sits within 3 lines of its FORK marker (marker script requirement).
- `values/strings_fork.xml`: 8 `subtype__*` labels (language named in itself, `MissingTranslation` ignored like other fork strings).
- `fork/subtypes/MvpSubtypes.kt`: `MVP_LANGUAGE_TAGS`, pure `missingSubtypeTags(existing, wanted)`.
- `SubtypeManager.ensureDefaultSubtypes(tags)`: adds presets for tags whose primary locale is missing, in order, one publish + one persist (unique ids base+index; `addSubtype` in a loop would reuse `currentTimeMillis` ids and persist from a stale list). Updates `subtypes` immediately so a second call is idempotent. Tags without preset are skipped. Not called on IME start.
- `assets/dict/LICENSES.md`, FORK_PATCHES row (S1-subtypes).

## Presets
All 8 tags have an exact preset in the localization extension.json (en-US, es-ES, fr-FR, pt-BR, ar, ur-PK -> urdu_phonetic, id, hi-IN). Nothing mapped, nothing added.

## Tests (decision)
SubtypeManager needs FlorisApplication (keyboard manager), so it is not constructed. `DefaultSubtypesTest` is plain JUnit: tests `missingSubtypeTags` (order, idempotency, partial, dupes) and decodes the shipped extension.json into `SubtypePreset` (every MVP tag has a preset by the same exact-then-language rule; ur uses `urdu_phonetic`). The brief's literal "Robolectric, call ensureDefaultSubtypes twice" is therefore covered indirectly; the manager method itself is untested.
`MethodXmlSubtypesTest`: 8 tags in order, mode/locale/label, ascii flags, label strings exist.

## TDD evidence
Deviation: I wrote implementation and tests together and did not capture a RED run. GREEN: `:app:testDebugUnitTest --tests '*subtypes*'` -> BUILD SUCCESSFUL, DefaultSubtypesTest 6/6, MethodXmlSubtypesTest 4/4. `scripts/check-fork-markers.sh` -> OK (after fixing marker placement; first run failed on method.xml hunks).

## Concerns
- No RED capture; SubtypeManager.ensureDefaultSubtypes not directly unit-tested.
- The old method.xml default subtype (`overridesImplicitlyEnabledSubtype`) is gone, as the brief requires exactly 8.
- Labels are English-only/native names without translations.

## Fix round 1
- Extracted `findPreset(presets, locale)` and `buildSeededList(current, presets, tags, baseId)` into `fork/subtypes/MvpSubtypes.kt`. `SubtypeManager.getSubtypePresetForLocale` delegates to `findPreset`; `ensureDefaultSubtypes` wraps `buildSeededList` in `_subtypesFlow.update` (atomic) and persists the result when it changed. FORK markers added.
- `DefaultSubtypesTest` now tests the real functions (twice-seed = 2 subtypes, unique ids, order, existing kept, no-preset skipped, ur -> `urdu_phonetic`, all MVP tags resolve via `findPreset` against decoded extension.json). Its own lookup copy is removed.
- `MethodXmlSubtypesTest` parses `strings_fork.xml`. Removed the redundant stacked marker in `method.xml`.
- RED: with `buildSeededList` temporarily returning `current`, `:app:testDebugUnitTest --tests '*subtypes*'` -> BUILD FAILED; failing: "seeding twice yields two subtypes with unique ids in tag order", "seeding keeps existing subtypes and appends only the missing ones", "urdu preset uses the phonetic layout". Stub reverted.
- GREEN: same command -> BUILD SUCCESSFUL, DefaultSubtypesTest 9/9, MethodXmlSubtypesTest 4/4. `scripts/check-fork-markers.sh` at HEAD: see reply.
- Note: `SubtypeManager.ensureDefaultSubtypes` wiring itself (flow update + persist) is still not executed in a test.
- Marker script initially failed on the new `update` import; marker added, re-run at HEAD: OK.
