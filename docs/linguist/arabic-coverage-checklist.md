# Arabic coverage checklist (ar)

Layout: `org.florisboard.layouts:arabic` (upstream, unchanged) with the `ar` popup mapping and the `eastern_arabic`
number row (Arabic-Indic digits U+0660-U+0669). `؛` was missing and was added to the long-press of `.` in
`ar.json`. The required letters are ا-غ and ف-ي; U+063B-U+0640 (extended letters, tatweel) are not part of the
Arabic alphabet and are left out.

Positions: `R<row> K<key>` counts rows from the top and keys in reading order (for right-to-left layouts K1 is
the rightmost key). Layers: `base`, `shift` (press shift, then the key), `long-press` of the named key (from the
locale popup mapping), `bottom row` (the modifier row with space), and the number row, which is also the top row
of the symbols layer and is the subtype's digit choice ("Number row layout" in the subtype editor: native or
`western_arabic`). Generated from the shipped layout JSON with the same rules as `ScriptCoverageTest`
(`app/src/test/kotlin/com/manjul/aikeyboard/fork/layouts/ScriptCoverageTest.kt`); combining marks are shown on a
dotted circle.

Linguist: set **Linguist status** to `ok` or `change`, and say what to change in **Notes** (wrong letter, better
position, missing form). Sign-off (PRD FR-03 AC1): name, date, verdict at the bottom.

| # | Char | Code point | Name | `arabic` key, layer | Linguist status | Notes |
|---|---|---|---|---|---|---|
| 1 | ا | U+0627 | Arabic Letter Alef | R2 K6, base | pending | |
| 2 | ب | U+0628 | Arabic Letter Beh | R2 K4, base | pending | |
| 3 | ة | U+0629 | Arabic Letter Teh Marbuta | R3 K6, base | pending | |
| 4 | ت | U+062A | Arabic Letter Teh | R2 K7, base | pending | |
| 5 | ث | U+062B | Arabic Letter Theh | R1 K3, base | pending | |
| 6 | ج | U+062C | Arabic Letter Jeem | R1 K11, base | pending | |
| 7 | ح | U+062D | Arabic Letter Hah | R1 K10, base | pending | |
| 8 | خ | U+062E | Arabic Letter Khah | R1 K9, base | pending | |
| 9 | د | U+062F | Arabic Letter Dal | R3 K10, base | pending | |
| 10 | ذ | U+0630 | Arabic Letter Thal | R3 K1, base | pending | |
| 11 | ر | U+0631 | Arabic Letter Reh | R3 K4, base | pending | |
| 12 | ز | U+0632 | Arabic Letter Zain | R3 K8, base | pending | |
| 13 | س | U+0633 | Arabic Letter Seen | R2 K2, base | pending | |
| 14 | ش | U+0634 | Arabic Letter Sheen | R2 K1, base | pending | |
| 15 | ص | U+0635 | Arabic Letter Sad | R1 K2, base | pending | |
| 16 | ض | U+0636 | Arabic Letter Dad | R1 K1, base | pending | |
| 17 | ط | U+0637 | Arabic Letter Tah | R2 K11, base | pending | |
| 18 | ظ | U+0638 | Arabic Letter Zah | R3 K9, base | pending | |
| 19 | ع | U+0639 | Arabic Letter Ain | R1 K7, base | pending | |
| 20 | غ | U+063A | Arabic Letter Ghain | R1 K6, base | pending | |
| 21 | ف | U+0641 | Arabic Letter Feh | R1 K5, base | pending | |
| 22 | ق | U+0642 | Arabic Letter Qaf | R1 K4, base | pending | |
| 23 | ك | U+0643 | Arabic Letter Kaf | R2 K10, base | pending | |
| 24 | ل | U+0644 | Arabic Letter Lam | R2 K5, base | pending | |
| 25 | م | U+0645 | Arabic Letter Meem | R2 K9, base | pending | |
| 26 | ن | U+0646 | Arabic Letter Noon | R2 K8, base | pending | |
| 27 | ه | U+0647 | Arabic Letter Heh | R1 K8, base | pending | |
| 28 | و | U+0648 | Arabic Letter Waw | R3 K7, base | pending | |
| 29 | ى | U+0649 | Arabic Letter Alef Maksura | R3 K5, base | pending | |
| 30 | ي | U+064A | Arabic Letter Yeh | R2 K3, base | pending | |
| 31 | ء | U+0621 | Arabic Letter Hamza | R3 K2, base | pending | |
| 32 | أ | U+0623 | Arabic Letter Alef With Hamza Above | R2 K6, long-press `ا` | pending | |
| 33 | إ | U+0625 | Arabic Letter Alef With Hamza Below | R2 K6, long-press `ا` | pending | |
| 34 | آ | U+0622 | Arabic Letter Alef With Madda Above | R2 K6, long-press `ا` | pending | |
| 35 | ؤ | U+0624 | Arabic Letter Waw With Hamza Above | R3 K3, base | pending | |
| 36 | ئ | U+0626 | Arabic Letter Yeh With Hamza Above | R2 K3, long-press `ي` | pending | |
| 37 | ، | U+060C | Arabic Comma | bottom row, base | pending | |
| 38 | ؟ | U+061F | Arabic Question Mark | bottom row, long-press `.` | pending | |
| 39 | ؛ | U+061B | Arabic Semicolon | bottom row, long-press `.` | pending | |
| 40 | ٠ | U+0660 | Arabic-Indic Digit Zero | number row / symbols top row K10 (`eastern_arabic`) | pending | |
| 41 | ١ | U+0661 | Arabic-Indic Digit One | number row / symbols top row K1 (`eastern_arabic`) | pending | |
| 42 | ٢ | U+0662 | Arabic-Indic Digit Two | number row / symbols top row K2 (`eastern_arabic`) | pending | |
| 43 | ٣ | U+0663 | Arabic-Indic Digit Three | number row / symbols top row K3 (`eastern_arabic`) | pending | |
| 44 | ٤ | U+0664 | Arabic-Indic Digit Four | number row / symbols top row K4 (`eastern_arabic`) | pending | |
| 45 | ٥ | U+0665 | Arabic-Indic Digit Five | number row / symbols top row K5 (`eastern_arabic`) | pending | |
| 46 | ٦ | U+0666 | Arabic-Indic Digit Six | number row / symbols top row K6 (`eastern_arabic`) | pending | |
| 47 | ٧ | U+0667 | Arabic-Indic Digit Seven | number row / symbols top row K7 (`eastern_arabic`) | pending | |
| 48 | ٨ | U+0668 | Arabic-Indic Digit Eight | number row / symbols top row K8 (`eastern_arabic`) | pending | |
| 49 | ٩ | U+0669 | Arabic-Indic Digit Nine | number row / symbols top row K9 (`eastern_arabic`) | pending | |

## Sign-off

| Linguist | Date | Verdict |
|---|---|---|
| | | pending |
