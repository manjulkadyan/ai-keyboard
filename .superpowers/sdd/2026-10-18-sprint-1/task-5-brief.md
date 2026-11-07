### Task 5: Our Urdu standard and Hindi phonetic layouts, digit choice, coverage checks

**Files:**
- Create: `app/src/main/assets/ime/keyboard/org.aikeyboard.layouts/extension.json` (our own layout pack, id `org.aikeyboard.layouts`), `…/layouts/characters/urdu_standard.json`, `…/layouts/characters/hindi_phonetic.json`
- Create: `docs/linguist/{urdu,hindi,arabic}-coverage-checklist.md` (alphabet table with key position, layer and status column for the linguist sign-off, PRD FR-03 AC1)
- Modify (FORK S1-layouts): `assets/ime/keyboard/org.florisboard.localization/extension.json` (ur-PK and hi-IN presets list both layouts in `preferred`/alternates so they are reachable from the subtype editor; a `digits` choice per subtype: native or western number row), `docs/FORK_PATCHES.md`
- Test: `app/src/test/kotlin/com/manjul/aikeyboard/fork/layouts/ScriptCoverageTest.kt`

**Interfaces:**
- Produces: layout ids `org.aikeyboard.layouts:urdu_standard`, `org.aikeyboard.layouts:hindi_phonetic`.

- [ ] **Step 1: Write the failing test.** `ScriptCoverageTest` loads each layout JSON (lower + upper/shift + popup mapping for its locale) and collects every reachable code point. Assert reachable ⊇ required set:
  - Urdu (both layouts): U+0627–U+06D2 Urdu letters used in Urdu orthography (list them explicitly in the test: ا آ ب پ ت ٹ ث ج چ ح خ د ڈ ذ ر ڑ ز ژ س ش ص ض ط ظ ع غ ف ق ک گ ل م ن ں و ہ ھ ء ی ے ۓ ۂ ئ ؤ) plus ، ؟ ۔ and Urdu digits ۰–۹ (U+06F0–U+06F9).
  - Hindi (InScript `hindi_in` and `hindi_phonetic`): vowels अ–औ, matras ा–ौ, consonants क–ह, ं ः ँ ् ़ ।, digits ०–९.
  - Arabic (`arabic`): ا–ي, ة ى ء أ إ آ ؤ ئ, ، ؟ ؛, Arabic-Indic digits ٠–٩.
  - Both new layouts declare `"direction"`/modifier consistent with upstream (`urdu_standard` RTL with `modifier: arabic`; `hindi_phonetic` LTR).
- [ ] **Step 2: Run** `$G ./gradlew :app:testDebugUnitTest --tests '*ScriptCoverage*'` — Expected: FAIL (missing layouts; any gap in upstream layouts also shows here — fix gaps in our files or via popup mapping, never by copying from the APKs).
- [ ] **Step 3: Author the layouts** in upstream's JSON schema (rows of `case_selector` keys). Urdu standard: our own arrangement on a 4-row grid, aspirate `ھ` and `ے/ۓ/ۂ` on shift. Hindi phonetic: Latin-QWERTY-position mapping (k→क, K→ख, g→ग, …, a→ा shift-A→आ), our own table documented at the top of the checklist.
- [ ] **Step 4: Run** the test + `scripts/check-fork-markers.sh` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(layouts): own Urdu standard and Hindi phonetic layouts with coverage tests`.

