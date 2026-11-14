# Sprint 6 device checklist and exit check

Sprint 6 adds Roman Urdu and Hinglish typing: the "Urdu (Roman)" and "Hindi (Roman)" subtypes, the space gate, user
mappings and the Roman strip in the Translate bar. The build was checked in a sandbox with no working emulator and no
attached device. Every row below needs a device before Sprint 6 is signed off. Each row names the plan task it comes
from (`T<n>` = plan task).

Suggested devices: a Pixel or Samsung (API 33+), one API 26-30 phone, and the low-end test device for T.14 and T.15.

Fill in the Result column only after running the check. Record device, Android version, build and date there.

## Numbers from the sandbox

| Number | Value | Source |
|---|---|---|
| Benchmark, 1,000 words after a 200-word warm-up | ur 30 to 42 ms total, hi 16 to 20 ms total. Budget: 3,000 ms. No empty result | Task 2 |
| Heap of a loaded `ur` lexicon | 5.62 MB, against a 6 MB trigger for the packed form. This is a JVM number. T.15 confirms it on ART | Task 2 |
| Smoke-set accuracy, ur | top-1 63.2%, top-3 90.6% (floor 60 / 87) | Task 6 |
| Smoke-set accuracy, hi | top-1 88.3%, top-3 95.2% (floor 85 / 92) | Task 6 |

The floors are regression floors (measured minus 3 points). They are not the PRD gate of 85% top-1 and 95% top-3 on
2,000 held-out words. That gate waits for OD-11.

## Automated exit check (sandbox, 2026-11-14)

All six commands from the exit check passed: `assembleDebug`, `test testDebugUnitTest :build-logic:convention:test`,
`check :app:lintDebug`, the 16 KB alignment check (23 libraries, 4 unaligned 32-bit, informational), the fork markers
check and the data-flows check (37 tracked artifacts). Output tails are in
`.superpowers/sdd/2026-11-14-sprint-6/task-7-report.md`.

## T. Roman typing

| ID | Check | How | Pass criterion | Result |
|---|---|---|---|---|
| T.1 | Add "Urdu (Roman)" in Languages | Fresh install. Open Settings, Languages, add the subtype. Look at the list row | The "Beta" label shows after the name. A fresh install has no Roman subtype before you add one | |
| T.2 | Candidates on space | Switch to "Urdu (Roman)". In a chat app type "kya haal hai" with a space after each word | The strip shows native candidates and the Latin chip. A space commits the top native candidate | |
| T.3 | Latin chip and rule-only words | Type "kya" and tap the Latin chip. Then type a word that only the rules can spell and press space | The chip keeps "kya" in Latin. The rule-only word stays Roman on space | |
| T.4 | English words stay Latin | Type "meeting", "phone" and "ok", each followed by space | Each stays Latin after the space | |
| T.5 | Backspace undo | Type a Roman word and a space so it commits as native. Press Backspace once | The Roman word comes back once. A second Backspace deletes as normal | |
| T.6 | Hindi (Roman) | Add "Hindi (Roman)" and repeat T.2 to T.5 | Same results, with Devanagari candidates | |
| T.7 | Global suggestions off | Turn the global suggestions setting off. Type in "Urdu (Roman)" | The Roman strip still shows | |
| T.8 | Sensitive fields | Type in a password field, a URL bar and an email field | No strip and no native commit in each | |
| T.9 | Incognito and mappings | Turn incognito on. Long-press a candidate. Open Roman typing in settings and try to add a mapping | No "Save mapping" on the card. The add in settings is blocked | |
| T.10 | Save, add, edit, delete mappings | Save a mapping from the long-press card. In "Roman typing" add, edit and delete one | Each change applies at once on the next word | |
| T.11 | Clear data | Add two mappings. Run Clear data in settings. Reopen "Roman typing" | The list is empty | |
| T.12 | Translate bar | Open the Translate bar in "Urdu (Roman)". Type a word. Press space | The strip follows the bar word. Space commits it, and the translation uses Urdu as the source | |
| T.13 | Native subtypes unchanged | Type and accept suggestions in the native `ur-PK` and `hi-IN` subtypes | They type and suggest as in S5 | |
| T.14 | Per-keystroke time | On the low-end test device, take a debug trace over 200 keystrokes in a Roman subtype | The p90 per keystroke is below 30 ms | |
| T.15 | Memory | With a Roman subtype loaded run `adb shell dumpsys meminfo <ime package>` before and after | The IME stays at 90 MB or less. The lexicon adds 8 MB or less. This confirms the 5.62 MB JVM heap on ART | |
| T.16 | Offline and network | Turn on flight mode and repeat T.2 to T.12. Then run a proxy capture while typing | Every check still works. The capture shows no request from the keyboard | |
| T.17 | Log audit | Install a release build. Capture logcat during T.2 to T.12 and search it | No Roman word, candidate or mapping appears | |
| T.18 | Preset merge | On a clean install open Settings, Languages, Add subtype. Look for both Roman presets. A unit test joins the JSON itself, so only the device proves the real `KeyboardManager` merge | "Urdu (Roman)" and "Hindi (Roman)" both appear and can be added | |
| T.19 | Incognito mid-session | In "Urdu (Roman)" type and accept a word twice so it gets a boost. Turn incognito on with the toggle key, then type the same word. Turn incognito off. Repeat with a field that forces incognito (for example a Chrome incognito tab) | After incognito turns on, the boost is gone and the word ranks as a fresh word. This holds for both the toggle key and the forced field | |
| T.20 | Field with no suggestions | Open a host field that sets `TYPE_TEXT_FLAG_NO_SUGGESTIONS`. Open the Translate bar and type a Roman word | Record what the Roman strip does (shows or hides) and whether space commits. This is an open product question (question 7) | |

## Open questions for product

1. **OD-11:** who reviews the Dakshina CC-BY-SA terms for a downloadable pack, and by what date? The PRD accuracy gate
   (85% top-1, 95% top-3 on 2,000 held-out words) waits for it.
2. **Eval sets:** who writes the 2,000 held-out words per language? Native speakers or the Dakshina split?
3. **Event param (Analytics):** can `translit_commit` add `source` (`candidate` or `latin`)? The PRD lists only
   `lang`.
4. **Strip size:** the strip holds 3 items (PRD FR-05). With 3 native candidates, the Latin chip takes the third slot,
   so the user sees 2 native candidates. Is that right, or does the Roman strip get a 4th slot?
5. **Subtype names (decision 1):** the internal tags are `ur-PK-roman` and `hi-IN-roman`. Users see "Urdu (Roman)" and
   "Hindi (Roman)". Is a different label wanted?
6. **Plan size:** the plan is 6 days against 3 days in the TRD. The extra 3 days cover user mappings, the translate bar
   and the eval runner, which the PRD asks for. Is that accepted?
7. **Translate bar and the no-suggestions flag:** should the translate bar's Roman strip follow the host field's
   no-suggestions flag, or treat the bar as a plain text field? T.20 records what happens today.
