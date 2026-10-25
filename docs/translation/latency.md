# Day-1 ML Kit latency check (TRD §8, R-03)

TRD §8 asks for this on day 1 of the bar work. The result decides whether the R-03 mitigation (longer bar
debounce, live composing off by default) stays "low tier only" (plan decision 5) or applies to every device.

Status: **not measured yet.** The sandbox has no device; the instrumented test is built in CI
(`:core:translate-mlkit:assembleDebugAndroidTest`) and run by hand on the devices below
(`docs/sprint-2-device-checklist.md`, section "Translation latency").

## What is measured

Pairs (`LatencyCheck.Pairs`): en→ur, en→hi, en→ar, en→bn, en→es, ur→en, hi→en.

Corpus: `core/translate-mlkit/src/androidTest/assets/latency/{en,ur,hi,ar,bn,es}.txt`, 50 sentences per language,
3 to 100 characters (cap 200), short chat lines to two-clause messages. Written by us for this check; nothing is
taken from a third-party app or dataset. The source language of the pair picks the file (en→* uses `en.txt`).
`ar.txt`, `bn.txt` and `es.txt` are there for reverse pairs passed with `-e pairs`.

Two ways to run it:

| | `TranslateLatencyTest` (instrumented) | Devtools probe (debug app) |
|---|---|---|
| Path timed | One loaded ML Kit `Translator`, `translate()` only | `TranslationEngineRouter` over `MlKitTranslationEngine`: model-presence check, sentence chunking, 5 s timeout, then `translate()` (what the bar pays per request, minus debounce) |
| Warm-up | 5 untimed calls per pair (model load, JIT) | Same |
| Timed calls | 50 per pair, one per sentence | Same; the probe has its own LRU, cleared after warm-up, so no call is served from memory |
| Output | `en-ur p50=48 p90=92 n=50` lines in the `am instrument` output and logcat (tag `TranslateLatency`) | Same lines in the row summary, copied to the clipboard with a device line (model, API level, RAM, `isLowRamDevice`) |
| Models | Must be downloaded first, or pass `-e download true` (any network) | Must be downloaded first (Offline languages or the bar's download chip); a missing pack shows `en-ur no_pack` |

Percentiles are nearest-rank (`LatencyStats.percentile`): p90 of 50 samples is the 45th smallest. Output is pair
tags and milliseconds only, never source or translated text (PC-10).

## How to run

Instrumented test (build once, then install both APKs; models must be on the device):

```sh
$G ./gradlew :core:translate-mlkit:installDebugAndroidTest
adb shell am instrument -w -r \
  -e tier mid \
  -e class com.manjul.aikeyboard.core.translate.mlkit.TranslateLatencyTest \
  com.manjul.aikeyboard.core.translate.mlkit.test/androidx.test.runner.AndroidJUnitRunner
```

- `-e tier mid` fails the test when any pair's p90 is above 150 ms; `-e tier low` uses 400 ms; leave it out to
  measure only.
- `-e pairs en-ur,hi-en` limits the pairs.
- `-e download true` downloads missing models first. The test APK is its own app, so models downloaded in the
  keyboard app do not count for it.

Devtools probe: debug build, Settings → Devtools → enable devtools → "Translate latency probe". Download the test
languages in the app first. Keep the screen open until the toast says the numbers were copied (about a minute).

Run each on a plugged-in, cool device with the screen on, after a reboot and one minute idle. Repeat twice and
keep the slower run.

## Devices and results

Mid tier: Pixel 6a or Galaxy A54 class (6 to 8 GB RAM, 2022 to 2023 SoC). Low tier: 3 GB RAM, Android 10.

| Date | Device (RAM, Android) | Tier | Path | en→ur p50/p90 | en→hi | en→ar | en→bn | en→es | ur→en | hi→en | Build |
|---|---|---|---|---|---|---|---|---|---|---|---|
| | | mid | test | | | | | | | | |
| | | mid | probe | | | | | | | | |
| | | low | test | | | | | | | | |
| | | low | probe | | | | | | | | |

## Decision rule (R-03)

- **Mid-tier p90 ≤ 150 ms on every pair** (test path): keep plan decision 5. Low tier (< 4 GiB or
  `isLowRamDevice`) gets the 600 ms debounce and starts in Preview; other devices use 450 ms.
- **Mid-tier p90 > 150 ms on any pair**: R-03 applies everywhere. Set `DebouncePolicy.Default` to 600 ms
  (`core/translate/src/main/kotlin/com/manjul/aikeyboard/core/translate/bar/DebouncePolicy.kt`), make live
  composing default off for every device, not only `LowTierDevice` (Preview is already the default bar mode,
  decision 15; this also covers the Sprint 4 Settings switch's default), and record the change and these numbers
  in the Sprint 2 exit (Task 14).
- **Low-tier p90 > 400 ms**: R-03 is already on for low tier; record it in the sprint exit and raise it with
  product (candidates: a longer low-tier debounce, or the bar showing "Translating…" sooner).
- If the probe path is much slower than the test path (more than about 50 ms at p90), the model-presence lookup
  is the likely cost: that is the trigger for the Room mirror in plan decision 4.

Result and decision: _to fill in after the device runs._
