# Sprint 6 design: Roman Urdu and Hinglish transliteration

Status: draft for approval. Date: 2026-11-14.
Sources: PRD FR-24 (`docs/product_docs/PRD.md:539-548`), TRD FR-19 (`TRD.md:497`), TRD OD-13 (`TRD.md:856`).

## Intent and why

Many of our first users write Urdu and Hindi in Latin letters ("kya haal hai"). They want the native script
("کیا حال ہے", "क्या हाल है") without a switch to a second keyboard. Gboard has no Roman to Nastaliq path, so this is
a gap that we can fill (PRD G3, persona P1). The PRD success metric is that 20% or more of ur/hi daily users commit at
least one transliterated word.

The product owner chose to put only this feature in Sprint 6. The other Sprint 6 items move to a follow-up sprint.

## Goals

- Two new input modes: "Urdu (Roman)" and "Hindi (Roman)". Both use the QWERTY layout.
- While the user types Latin letters, the strip shows the top 3 native-script candidates and a Latin chip.
- Space commits the top candidate. A tap on the Latin chip keeps the Roman text.
- The engine runs fully on the device and makes no network call.
- Users can add their own mappings (Roman word to native word).
- The mode works in the source line of the translate bar.
- A data seam, so that a Dakshina pack can replace or add to our data later without engine changes.

## Non-goals

These items move to the follow-up sprint:

- The daily word game and its rewarded hint (FR-20).
- Hardening: the privacy audit, the device QA matrix, the accessibility pass and the release work.
- D23 (contact, terms and privacy URLs) and the S4 deferred items D3, D5, D12, D20 and D24.
- The CI emulator job and the `ImeOnlyColdStartTest` run on API 34.
- Bengali and Pashto key maps, FCM push opt-in and In-App Review.

Also out of scope for this sprint:

- The Dakshina pack itself. It waits for the OD-11 legal review.
- Roman Bengali, and transliteration from native script back to Latin.
- Changes to the kb-api "Output script" polish setting. It stays server-side.

## Anti-goals

These events make the feature a failure, even with correct output:

- A network call from the transliteration path.
- Typed Roman text or a candidate in logs, analytics, crash reports or exceptions.
- Learning or a custom-mapping write in incognito, or in a field where the field decision disallows learning.
- An automatic native-script commit in a sensitive field. In those fields the strip stays hidden, as today.
- A change in behavior of the current `ur-PK` and `hi-IN` native subtypes.

## Constraints

- PRD FR-24 acceptance criteria:
  1. Top-1 accuracy of 85% or more and top-3 of 95% or more on 2,000 held-out words per language.
  2. Less than 30 ms p90 per keystroke on a low-end device.
  3. Fully offline, with zero network calls.
  4. Works in the source line of the translate bar.
- Memory: 8 MB or less resident per loaded locale (`TRD.md:274`). IME idle memory 90 MB or less (`TRD.md:637`).
- Fork rules from `CLAUDE.md`:
  - `FORK(S6-translit)` markers and a `docs/FORK_PATCHES.md` row for each edited upstream file.
  - Fork strings go in `values/strings_fork.xml`.
  - A row in `docs/data-flows.md` for the custom mappings that the app stores.
  - No star imports. JUnit 5 tests.
  - `AppDatabase` goes from version 3 to 4 with an `AutoMigration`, and the schema is exported. No destructive
    migration.
- Licence: CC-BY-SA data ships only as a separate downloadable pack, with attribution, after legal review (PRD R-08,
  OD-11).

## Approaches

**Data source.** Picked by the product owner: option 3.

| Option | Trade-off |
|---|---|
| 1. Our rule table plus our native dictionaries | In the APK, no licence risk. Accuracy probably stays below 85%, mostly for Urdu. |
| 2. Dakshina pack only | Can reach the accuracy target. Blocked on OD-11 and needs attribution. |
| 3. Option 1 now, Dakshina later as a pack (picked) | Works offline from the first day. The accuracy gate waits for the pack. |

**Plug-in point.** Our call: a new `SuggestionProvider`.

- A new provider, `com.manjul.aikeyboard.nlp.providers.translit`, is selected through the subtype preset
  `nlpProviders`. Upstream does the same for `zh-CN-zhengma` (`org.florisboard.localization/extension.json:705-710`).
- `NlpManager` already looks up the provider by ID (`NlpManager.kt:139`) and caps the strip at
  `SUGGESTION_STRIP_SIZE` (`NlpManager.kt:224`). No change to the upstream flow is needed apart from registration.
- Rejected: a branch inside `LatinSuggester.suggest`. It mixes two scripts in one class and puts risk on the English
  and Latin subtypes.

**Engine.** Our call: a rule table, a beam decoder and lexicon ranking.

- Rule table plus beam search plus frequency ranking (picked). This is the TRD OD-13 decision. It is small, fast and
  fully testable in pure Kotlin.
- `android.icu.text.Transliterator`: rejected. It is public only from API 29, but our minSdk is 26. Its Latin to
  Devanagari rules follow ISO 15919 style romanisation, not Hinglish spellings ("kya", "hai"). It has no Latin to Urdu
  rule set.
- A character n-gram model: rejected for now. It needs pair data to train, and we have none until the Dakshina pack.
  The `TranslitLexicon` seam lets a later pack add a model score.

## Design

### Module `:core:translit`

A new pure Kotlin module. It applies `aikeyboard.jvm.library` and depends on `:core:suggest` for the `.sdict` reader.

- `RuleTable`: per language, an ordered list of Roman segments to native segments with a cost.
  Example rows: `kh -> خ`, `aa -> ا`, `ee -> ی`, final `a -> ہ` (ur). `kh -> ख`, `aa -> ा` (hi).
  The tables are our own work, stored as text resources in the module and loaded once.
  Context flags cover word start, middle and end, and the inherent vowel in Devanagari (virama handling).
- `BeamDecoder`: segments the Roman input from left to right and keeps the best N partial outputs (N = 16 to start).
- `TranslitLexicon`: an interface with `frequency(nativeWord): Int?` and `prefixExists(nativePrefix): Boolean`.
  - `SdictLexicon` reads the shipped `dict/ur.sdict` and `dict/hi.sdict`. It is the only implementation in S6.
  - A later `PackLexicon` reads Dakshina pair data from a pack downloaded by `PackDownloader` (S5). It adds direct
    Roman to native pairs with counts.
- `TranslitRanker`: the score is rule cost, plus a lexicon bonus for a known word, plus the frequency, plus a large
  bonus for a custom mapping. Words that are not in the lexicon stay as candidates, so the user can type new words.
- `CustomMappings`: an in-memory map that the app loads from Room. It is the first source for an exact Roman match.

### Subtypes and the language collision

- Two new presets in `org.aikeyboard.layouts/extension.json`: `ur-PK-roman` and `hi-IN-roman`, on the `qwerty`
  layout, with the translit provider for `suggestion`.
- The tags use a variant, as upstream does for `zh-CN-zhengma`. `FlorisLocale.fromTag` reads `ur-Latn-PK` as country
  `Latn`, so a script subtag does not work.
- `LatinSuggester.languageOf` cuts the tag to `ur` or `hi` (`LatinSuggester.kt:268`). The translit provider does not
  use `LatinSuggester`. It knows that it is Roman from its own provider ID in the preset. A unit test makes sure that
  `ur-PK-roman` never loads the Latin path.
- `MvpSubtypes` matches presets on language only (`MvpSubtypes.kt:26`). The fallback can return a Roman preset for a
  native locale. The fix: an exact match first, and skip presets with a variant (such as `-roman`) in the fallback. This is a fork file,
  so no upstream marker is needed.
- Onboarding (our call, product can override): the Roman subtypes are opt-in in the language list.
- Both Roman subtypes carry a "Beta" label in the language list and on the settings screen. The label strings go in
  `values/strings_fork.xml`.

### Strip behavior

- The strip shows up to 3 native candidates, then the Latin chip with the raw Roman text.
- A tap on a candidate commits it. A tap on the Latin chip commits the Roman text.
- If the field decision disables suggestions, or the field is sensitive, the provider returns nothing. The Roman text
  is committed as typed.
- Roman mode stays on with the global suggestions setting off. The provider forces suggestions on through
  `providerForcesSuggestionOn`, as zhengma does (our call).
- Backspace right after a commit restores the Roman text once. In the host field, this uses the upstream revert path
  (`phantomSpace.candidateForRevert` and `notifySuggestionReverted`). The `:ime:editing` own-write undo applies only in
  the translate bar.

Space-commit (our call):

- Space-commit has its own gate, `TranslitSpaceGate`. It does not use the autocorrect setting.
- `AutocorrectGate` passes non-Latin candidates unchanged, also on punctuation. So `AutocorrectGate.onSpace` routes a
  `TranslitCandidate` to `TranslitSpaceGate`, and `onOtherKey` never commits one.
- Space commits the top candidate only in a plain text field where the field decision allows suggestions. In all other
  fields, space keeps the Roman text.
- Space commits only a candidate that is in the lexicon or in the user mappings. A candidate from rules alone shows in
  the strip, but space never commits it.
- English in mixed text: the Roman word can be in `en.sdict` above a frequency threshold, with no lexicon candidate
  that beats it. In that case, space keeps the Latin text. The native candidates still show. The builder picks the
  threshold.
- `LatinSuggester` holds one language at a time, so no English reader is loaded in a Roman subtype. The provider
  loads `en.sdict` (468 KB) lazily, on first use. It counts toward the 8 MB budget.

Learning and long-press (our call):

- A chosen candidate gets a boost in memory only.
- Only two actions write a custom mapping: the settings screen and the "Save mapping" action on the long-press card.
- Long-press keeps the S5 definition card. For a transliteration candidate, the card gets one more action, "Save
  mapping". The card is not replaced.
- Incognito and a field without learning block both write paths.

### Custom mappings storage

- A new Room entity `TranslitMapping(lang, roman, native, createdAt)` in `:core:database`, with a unique key on
  `(lang, roman)`.
- `AppDatabase` version 4, `AutoMigration(3, 4)` (it only adds a table), schema `4.json` exported. A migration test
  goes from 3 to 4.
- A settings screen under Languages: "Roman typing", with a list, add, edit and delete, and a prompt before delete.
- The data stays on the device. "Clear data" deletes it. A new row in `docs/data-flows.md` records it.

### Translate bar

Our call, from Appendix B note a in `docs/manual-test-plan.md` (hide host suggestions while the bar is open):

- While the bar is open and a Roman subtype is active, the strip follows the Roman word in progress in `BarBuffer`,
  through `BarKeyRouter`. It does not follow the host word.
- Space-commit and the Latin chip work inside the bar with the same rules as in the host field.
- Host suggestions hide only in this case. Other subtypes keep the current bar behavior.
- The committed native text goes to the translator. `SourceResolver` already rejects `*-Latn` detection results
  (`SourceResolver.kt:54`), so the source language stays the subtype language.

### Analytics

- One new event, `translit_commit{lang}`, sampled at 1%, through the consent-aware relay in `:core:analytics`.
- It carries only the language and whether the commit was a candidate or the Latin chip. No text, no length.
- The event is added to the event registry and passes the schema test.

### Urdu frequency weakness

`ur.sdict` has 99,544 words but a flat frequency of 16 for all of them. Ranking among known Urdu words then depends
only on rule cost.

- Mitigation in S6: give rule cost more weight for `ur`, and prefer shorter native spellings for common endings.
- The rebuild of `ur.sdict` with real counts moves to the follow-up sprint. It needs an MIT or public-domain source
  that passes licence review, and none is known today.

### Evaluation

- An eval runner in `:core:translit` tests reads a pair file and reports top-1 and top-3 accuracy.
- S6 ships a smoke set of about 200 pairs per language that we write ourselves. It is a regression test with a
  floor, not the PRD gate. The builder sets the floor so that it catches regressions (our call).
- The PRD gate (2,000 held-out words, 85% and 95%) needs pair data that we do not have. After the Dakshina pack and its
  held-out split arrive, it becomes a CI gate.

### Performance and memory

- A JVM benchmark test decodes 1,000 Roman words and fails over a time budget on the CI host.
- A device check measures the p90 per keystroke on a low-end device and records it in the checklist.
- Lexicon memory: `DictionaryFormat.read` loads the whole `.sdict` file into arrays. It is not memory-mapped. The
  `ur` file holds 99,544 words.
- A test measures the heap of a loaded `ur` lexicon. Above 6 MB, the lexicon switches to a packed form (one
  `CharArray` plus an offset `IntArray`) and drops the `SuggestDictionary`.
- `prefixExists` uses a new public `SuggestDictionary.hasPrefix(prefix)`, a binary search over the sorted words.
- The target is under 8 MB resident per locale, checked with `dumpsys meminfo`.

## Tasks

1. `:core:translit` module, `RuleTable` format, ur and hi tables, unit tests (1.5 days).
2. `BeamDecoder`, `TranslitLexicon`, `SdictLexicon`, `SuggestDictionary.hasPrefix`, `TranslitRanker`, benchmark and
   heap tests (1 day).
3. Translit `SuggestionProvider`, registration in `NlpManager`, new presets, `MvpSubtypes` fix (1 day).
4. Strip behavior: Latin chip, space-commit gate, English check, undo, Beta label, Save mapping action (0.5 days).
5. Room `TranslitMapping`, migration 3 to 4, settings screen, data-flows row (0.75 days).
6. Translate bar source line through `BarBuffer`, analytics event, eval runner and smoke sets (1 day).
7. Device checklist `docs/sprint-6-device-checklist.md`, fork markers, final review (0.25 days).

Total: 6 days.

The TRD budget is 3 days. The extra 3 days come from the PRD scope that the TRD line does not cover: custom
mappings, the translate bar and the eval runner.

## Left to the builder

- The exact rule rows, costs and the beam width, tuned against the smoke sets.
- The text format of the rule tables. It must be plain text and easy to diff.
- The provider class names and the package under `com.manjul.aikeyboard.fork.translit`.
- The settings screen layout. It must follow the existing settings screens.
- The time budget value for the JVM benchmark.

## Open questions for product

1. **OD-11:** who reviews the Dakshina CC-BY-SA terms for a downloadable pack, and by what date?
2. **Eval sets:** who writes the 2,000 held-out words per language? Native speakers or the Dakshina split?
3. **Event param:** can `translit_commit` add `source` (`candidate` or `latin`)? The PRD lists only `lang`.
4. **Strip size:** the strip holds 3 items. With 3 native candidates, the Latin chip takes the third place, so the
   user sees 2 native candidates. Is that right, or does the Roman strip get a 4th place?

## Decisions that product can override

These are our calls. Product can change them.

1. **Accuracy at launch:** v1 ships the Roman modes below 85% top-1, with a "Beta" label, until the pack exists.
2. **Onboarding:** the Roman subtypes are opt-in in the language list. They are not on by default.
