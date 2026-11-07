### Task 4: Eight MVP subtypes in the system and the in-app picker

**Files:**
- Modify (FORK S1-subtypes): `app/src/main/res/xml/method.xml` (8 `<subtype>` entries: en-US, es-ES, fr-FR, pt-BR, ar, ur-PK, id, hi-IN; `android:imeSubtypeMode="keyboard"`, `android:imeSubtypeLocale`, `android:languageTag`, `android:label` from new strings, `android:isAsciiCapable` true only for Latin), `ime/core/SubtypeManager.kt` (add `fun ensureDefaultSubtypes(languageTags: List<String>)`), `docs/FORK_PATCHES.md`
- Create: `app/src/main/assets/dict/LICENSES.md` (row for upstream `ime/dict/data.json`, Apache-2.0 from FlorisBoard)
- Test: `app/src/test/kotlin/com/manjul/aikeyboard/fork/subtypes/{MethodXmlSubtypesTest,DefaultSubtypesTest}.kt`

**Interfaces:**
- Produces: `val MVP_LANGUAGE_TAGS = listOf("en-US","es-ES","fr-FR","pt-BR","ar","ur-PK","id","hi-IN")` in `fork/subtypes/MvpSubtypes.kt`; `SubtypeManager.ensureDefaultSubtypes(tags)` adds a subtype from each tag's preset if no subtype with that primary locale exists, preserving order, idempotent.

- [ ] **Step 1: Write failing tests.** `MethodXmlSubtypesTest` parses `method.xml`: exactly the 8 tags, each with a locale and label, ar and ur not ascii-capable. `DefaultSubtypesTest` (Robolectric): `ensureDefaultSubtypes(listOf("en-US","ur-PK"))` twice → 2 subtypes; ur uses layout `org.florisboard.layouts:urdu_phonetic`; every `MVP_LANGUAGE_TAGS` entry has a preset (`getSubtypePresetForLocale` non-null).
- [ ] **Step 2: Run** `$G ./gradlew :app:testDebugUnitTest --tests '*subtypes*'` — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** same command + `scripts/check-fork-markers.sh` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(subtypes): declare 8 MVP subtypes and seed defaults`.

