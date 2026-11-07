# Task 5 report: Urdu standard and Hindi phonetic layouts, digit choice, coverage checks

Status: DONE_WITH_CONCERNS
Commit: `3e39a94d feat(layouts): own Urdu standard and Hindi phonetic layouts with coverage tests`

## What I implemented

- New layout extension `org.aikeyboard.layouts` (`app/src/main/assets/ime/keyboard/org.aikeyboard.layouts/`).
  `ExtensionManager.indexAssetsModule()` indexes every directory under `assets/ime/keyboard`, so the app picks it up
  on its own. The subtype editor's characters dropdown lists every indexed characters layout
  (`keyboardManager.resources.layouts`), so both layouts can be chosen there without a preset entry.
  - `org.aikeyboard.layouts:urdu_standard`: RTL, `modifier: org.florisboard.layouts:arabic`. Our own arrangement on
    4 rows (10/10/9/shift+8). It uses `case_selector`, with shift giving `ھ` (on ہ), `ۓ` (on ے), `ۂ` (on ء), and
    `آ ئ ؤ ظ ژ ؟` on related base keys. The `arabic` modifier has no shift key, so R4 starts with one
    (`code -11, type modifier`).
  - `org.aikeyboard.layouts:hindi_phonetic`: LTR, default modifier. It maps Latin QWERTY positions with
    `case_selector` (k क/K ख, g ग/G घ, a ा/A आ, ...). The full table is at the top of
    `docs/linguist/hindi-coverage-checklist.md`.
- Upstream gaps found by the test and fixed through the popup mappings (nothing taken from the teardown APKs):
  - `ur-PK.json`: `urdu_phonetic`'s h key is Arabic heh `ه` U+0647, so the existing `ہ` popup entry never matched.
    A new `ه` entry offers `ہ` (main), `ھ`, `ۂ`. `~right` (the `.` key) now starts with `۔` and `؟`.
  - `ar.json`: `؛` added to `~right`.
- Digit choice (R5): upstream already has a per-subtype mechanism, the `numericRow` layout ("Number row layout" in the
  subtype editor). It is also the top row of the symbols layer, so no setting needed deferring. `ar` (eastern_arabic)
  and `hi-IN` (devanagari) already defaulted to native digits. I set `ur-PK` `numericRow` to
  `org.florisboard.layouts:persian`, which gives Extended Arabic-Indic U+06F0–U+06F9 (the Urdu digits) with the
  western digit on long-press. Western digits are still one dropdown change away (`western_arabic`).
- R1: the preset characters defaults are unchanged (ur-PK `urdu_phonetic`, hi-IN `hindi_in`). A test guards this, and
  Task 4's test stays green.
- `FlorisLocale.supportsCapitalization` is now false for `ur`, like `hi`. Without it, auto-shift at sentence start
  would type `urdu_standard`'s shift layer and light up its shift key.
- `docs/linguist/{urdu,hindi,arabic}-coverage-checklist.md`: one row per required code point, with the key position
  and layer for each layout, a Linguist status column (`pending`), Notes, and a sign-off table. A script generated them
  from the shipped JSON using the same rules as the test, so no cell says missing.
- `docs/FORK_PATCHES.md`: S1-layouts rows for the 4 upstream files, plus a marker-exempt row for the 3 JSON files,
  which have no comment syntax. `FlorisLocale.kt` carries an inline `FORK(S1-layouts)` marker.

## Test: ScriptCoverageTest

File: `app/src/test/kotlin/com/manjul/aikeyboard/fork/layouts/ScriptCoverageTest.kt`. It rebuilds what
`LayoutManager.mergeLayouts` puts on the characters keyboard:
- the characters layout: every branch (case/shift/variation/direction) plus inline popups
- its modifier layout: `meta.modifier`, otherwise `default`
- the locale popup mapping's `all` entries, looked up by key label and by group (`~left`/`~right`/`~enter`), falling
  back to the `default` mapping
- the preset's `numericRow` layout

Required sets are the brief's, verbatim. The Urdu letters are listed one by one as code points. The Hindi sets are the
literal ranges अ–औ, ा–ौ, क–ह, plus ं ः ँ ् ़ । and ०–९. For Arabic I read "ا–ي" as ا–غ plus ف–ي, leaving out
U+063B–U+0640 (extended letters and tatweel are not part of the Arabic alphabet), plus ة ى ء أ إ آ ؤ ئ, ، ؟ ؛ and ٠–٩.
Other tests:
- direction and modifier of both new layouts
- the new extension decodes with `ExtensionJsonConfig`/`KeyboardExtension`, its layouts with `LayoutArrangement`, and
  `ur-PK`/`hi-IN` with `PopupMapping`
- preset defaults are unchanged
- `ur` does not auto-capitalize

### RED
`$G ./gradlew :app:testDebugUnitTest --tests '*ScriptCoverage*'` -> `9 tests completed, 7 failed`:
- urdu_phonetic: `cannot type: U+061F ؟, U+06BE ھ, U+06C1 ہ, U+06C2 ۂ, U+06D4 ۔, U+06F0 ۰ … U+06F9 ۹` (real upstream gaps)
- arabic: `cannot type: U+061B ؛` (real upstream gap)
- urdu_standard, hindi_phonetic, direction/modifier, decode: `missing org.aikeyboard.layouts/extension.json` (not created yet)
- auto-capitalization: `Expected value to be false.`
- PASS: hindi_in coverage (upstream InScript plus hi-IN popups already covers the literal ranges), preset defaults unchanged.

### GREEN
- Same command: 9 tests, 0 failures.
- Full `$G ./gradlew :app:testDebugUnitTest`: 65 tests, 0 failures. This includes Task 4's `DefaultSubtypesTest` and
  `MethodXmlSubtypesTest`.
- `scripts/check-fork-markers.sh` (after the commit): `83 modified (6 marker-exempt) ... check-fork-markers: OK`.

## Files changed
- A `app/src/main/assets/ime/keyboard/org.aikeyboard.layouts/extension.json`
- A `app/src/main/assets/ime/keyboard/org.aikeyboard.layouts/layouts/characters/urdu_standard.json`
- A `app/src/main/assets/ime/keyboard/org.aikeyboard.layouts/layouts/characters/hindi_phonetic.json`
- M `app/src/main/assets/ime/keyboard/org.florisboard.localization/extension.json` (ur-PK `numericRow`)
- M `app/src/main/assets/ime/keyboard/org.florisboard.localization/popupMappings/ur-PK.json`
- M `app/src/main/assets/ime/keyboard/org.florisboard.localization/popupMappings/ar.json`
- M `app/src/main/kotlin/com/manjul/aikeyboard/lib/FlorisLocale.kt`
- A `app/src/test/kotlin/com/manjul/aikeyboard/fork/layouts/ScriptCoverageTest.kt`
- M `docs/FORK_PATCHES.md`
- A `docs/linguist/urdu-coverage-checklist.md`, `docs/linguist/hindi-coverage-checklist.md`, `docs/linguist/arabic-coverage-checklist.md`

## Decisions where the brief left a choice
1. **No "alternates" in the presets.** `SubtypePreset.preferred` is one `SubtypeLayoutMap`, and the schema has no
   alternates field (unknown keys are silently ignored). The subtype editor already lists every characters layout, so
   adding JSON that nothing reads would be dead data. The only preset change is ur-PK `numericRow`.
2. **ur-PK now defaults to native (Urdu) digits.** This matches `ar` and `hi-IN`. It is a product-visible default:
   before, ur-PK used western digits on the symbols layer. See concerns.
3. **`ur` added to the no-capitalization list** in `FlorisLocale`. This is an upstream code edit outside the brief's
   file list, but `case_selector` on an Urdu layout cannot work correctly without it.
4. **The Arabic required set leaves out U+063B–U+0640** (see above). The Hindi sets use the literal ranges, which
   include rarely used letters. Those are reachable through the upstream hi-IN popups.
5. **The coverage model counts the modifier row and the number row.** `،` lives in the `arabic` modifier row, and
   digits come from the number row, which is the symbols top row.
6. **`urdu_phonetic`'s `ه` base key fixed through the popup mapping only.** The upstream layout file itself is unchanged.

## Self-review
- The diff is limited to the brief's scope plus the `FlorisLocale` line. Every upstream hunk is recorded and the marker
  check passes. Test output has no warnings from the new test.
- I matched the existing patterns: the asset lookup is the same as `DefaultSubtypesTest`, the layout JSON style is the
  same as `hindi_in.json`, and the marker style is the inline style S1 uses. Following the earlier S1 tasks, I did not
  add a "Modified by" header line to `FlorisLocale.kt`, even though FORK_PATCHES "Decisions" asks for one on
  substantive edits. The controller may want this fixed across all S1 files.
- Not verified on a device (no emulator). In particular I have not seen on screen the shift key inside an RTL
  characters row, or the RTL key order (K1 = rightmost, inferred from upstream `arabic.json`).

## Concerns
- **urdu_phonetic types Arabic heh.** The ur-PK default layout's h key types `ه` U+0647, not Urdu `ہ` U+06C1. The
  correct letter is now only on long-press. Fixing the base key would change upstream layout content, so the linguist
  checklist asks the question instead.
- **ur-PK digit default.** The default changed from western to Urdu digits. The product owner or linguist should
  confirm; the alternative is `western_arabic`.
- **Persian-style digit labels.** The number-row dropdown shows the native option as "Persian", and `۴ ۵ ۶ ۷` render in
  Persian style unless the font is Urdu-localised. Adding our own "Urdu" numericRow would be cosmetic, so I did not.
- **Layouts await linguist sign-off.** Both arrangements are ours and need sign-off (all status cells are `pending`).
