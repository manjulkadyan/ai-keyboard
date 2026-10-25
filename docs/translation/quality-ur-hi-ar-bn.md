# ur / hi / ar / bn translation quality (ML Kit on-device)

**Status: Pending native-speaker ratings** (plan decision 10). The corpus, the device harness and the rating
template are done; the results tables below are empty until two native raters per language score the output.
The TRD §8 Sprint 2 exit criterion "ur/hi/ar/bn quality comparison" is partially met until then.

Sources: PRD FR-11 AC5 (quality gate, "Beta" label), PRD R-03, PRD OD-05/OD-07 (cloud tier), TRD §8 Sprint 2.
Pashto is not in this eval: ML Kit has no Pashto model, so `ps` is cloud-only (Pro) and badged as such.

## Method

1. **Corpus.** `core/translate-mlkit/src/androidTest/assets/eval/{ur,hi,ar,bn}.tsv`, authored by us for this
   eval (never taken from either teardown APK, an app, or a published or copyrighted test set). Each file has:
   - 200 English sources (`en-001`..`en-200`, direction `en-<lang>`), the same 200 in all four files: 50 each of
     chat, email, everyday and formal register, 14 to 78 characters, with contractions, numbers, times and
     informal punctuation in the chat rows.
   - 50 native-script sources (`<lang>-001`..`<lang>-050`, direction `<lang>-en`) for the reverse direction:
     15 chat, 15 everyday, 10 email, 10 formal.
   - Columns `id, direction, register, source, reference`. `reference` is empty; raters may add one.
   `EvalCorpusTest` (unit test) pins the shape: counts, unique ids, registers, native script, identical English.
2. **Run.** `TranslateEvalHarness` (instrumented, `:core:translate-mlkit`) translates every row with the same
   ML Kit path the app uses (`mlKitTranslatorOpener`, one `Translator` per direction), after 3 untimed warm-up
   calls per direction, and writes `eval-<lang>-<date>.csv` with `id, direction, register, source, mlkit_output,
   latency_ms, error` to the test APK's external files dir. Latency here is informational; the latency gate is
   Task 4 (`docs/translation/latency.md`).
3. **Rate.** Copy each output row into `docs/translation/eval-rating-template.csv` (`lang, direction, id,
   register, source, mlkit_output, rater, adequacy, fluency, notes`), once per rater. Two native raters per
   language score every row blind to each other. Raters do not see the other rater's scores or the latency.
4. **Aggregate.** Per language and direction: mean adequacy over both raters, share of sentences whose mean
   adequacy is ≤ 2, mean fluency, and a per-register breakdown. Rows with an `error` count as adequacy 1.
   Rater disagreement of 2 or more points on a row is listed for a third look, not averaged away silently.

## Rubric (1 to 5 per sentence)

| Score | Adequacy (is the meaning carried over?) | Fluency (does it read as natural target language?) |
|---|---|---|
| 5 | All meaning, nothing added or lost | Natural; a native speaker could have written it |
| 4 | Nearly all meaning; a minor nuance lost (tone, politeness level) | Minor awkwardness, no errors that slow reading |
| 3 | Main point kept, a detail wrong or missing | Understandable but clearly machine-like |
| 2 | Meaning partly wrong; a reader would misunderstand | Hard to read; grammar errors get in the way |
| 1 | Wrong, nonsense, untranslated or empty | Not usable |

Notes for raters: judge the register too (a chat line rendered in stiff formal Urdu loses a point of adequacy
if the tone matters to the message). Script errors (Urdu output in Devanagari, Hindi in Latin) score 1.

## How to run (device)

Needs a phone (any API 26+) with network for the first model download. The sandbox that built this has no
device; this is row Q.1 in `docs/sprint-2-device-checklist.md`.

```sh
./gradlew :core:translate-mlkit:installDebugAndroidTest
adb shell am instrument -w -r \
  -e class com.manjul.aikeyboard.core.translate.mlkit.TranslateEvalHarness \
  -e download true \
  com.manjul.aikeyboard.core.translate.mlkit.test/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/data/com.manjul.aikeyboard.core.translate.mlkit.test/files/eval/ .
```

`-e download true` downloads missing ur/hi/ar/bn/en models over any network; leave it out to require models
already on the phone. `-e langs ur,hi` limits the run. A full run is 1,000 translations; expect a few minutes
once the models are present. Record the device, Android version, ML Kit version (translate 17.0.3) and date.

## Decision rule (PRD FR-11 AC5)

A language **passes** in a direction when, over both raters:

- mean adequacy ≥ 4.0, **and**
- fewer than 10 % of sentences have mean adequacy ≤ 2.

A language that fails in **either** direction is labelled **"Beta"** wherever its pair is chosen (bar,
Translator screen, selection sheet, Offline languages), and the routing recommendation below applies. Fluency is
reported, not gated; a mean fluency below 3.0 is called out to product even when adequacy passes.

## Results

Run: device `—`, Android `—`, ML Kit translate `17.0.3`, date `—`. Raters: two per language, initials only.

| Language | Direction | n | Mean adequacy | ≤ 2 share | Mean fluency | Pass? |
|---|---|---|---|---|---|---|
| Urdu (ur) | en → ur | 200 | — | — | — | Pending |
| Urdu (ur) | ur → en | 50 | — | — | — | Pending |
| Hindi (hi) | en → hi | 200 | — | — | — | Pending |
| Hindi (hi) | hi → en | 50 | — | — | — | Pending |
| Arabic (ar) | en → ar | 200 | — | — | — | Pending |
| Arabic (ar) | ar → en | 50 | — | — | — | Pending |
| Bengali (bn) | en → bn | 200 | — | — | — | Pending |
| Bengali (bn) | bn → en | 50 | — | — | — | Pending |

Per-register mean adequacy (en → xx):

| Language | chat | email | everyday | formal |
|---|---|---|---|---|
| ur | — | — | — | — |
| hi | — | — | — | — |
| ar | — | — | — | — |
| bn | — | — | — | — |

Non-English pairs (for example ur → hi) pivot through English in ML Kit; they are not rated separately. Expect
them to be no better than the weaker of the two legs.

## Routing recommendation (template; fill in after rating)

For each language:

| Language | Verdict | Label | Free tier | Pro (input to OD-05/OD-07) |
|---|---|---|---|---|
| ur | Pending | — | ML Kit on device | — |
| hi | Pending | — | ML Kit on device | — |
| ar | Pending | — | ML Kit on device | — |
| bn | Pending | — | ML Kit on device | — |

- **Pass:** no label; ML Kit for everyone; cloud stays an opt-in Pro upgrade.
- **Fail (Beta):** "Beta" label next to the language name; free users keep ML Kit (still offline and private);
  recommend the cloud tier for Pro once it exists (decision 1: today the cloud engine is a disabled stub, so the
  recommendation is copy only). Feeds OD-07 (vendor; Google v3 is the default for ur/hi/ar) and the R-03 risk.
- **Weak register only** (e.g. chat fails, formal passes): no label; note it for the S6 Roman Urdu / Hinglish
  work (FR-24), which targets informal text.

The "Beta" label itself is not implemented this sprint (decision 10); it is added once a language fails the
rule above.

## Not covered

- Romanised input (Roman Urdu, Hinglish): ML Kit translation expects native script, so the corpus has none;
  decision 3 and S6 (FR-24).
- Cloud engine comparison: no cloud engine this sprint (OD-05/decision 1). Re-run the same corpus through the
  cloud engine when it exists, so both are scored on identical sentences.
- Long multi-sentence input: the bar splits by sentence (`SentenceChunker`), so sentence-level scores apply.
