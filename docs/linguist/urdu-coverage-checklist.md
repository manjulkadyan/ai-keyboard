# Urdu coverage checklist (ur-PK)

Layouts: `org.aikeyboard.layouts:urdu_standard` (ours) and `org.florisboard.layouts:urdu_phonetic` (upstream,
the ur-PK preset default). Both use the `ur-PK` popup mapping and the `persian` number row (Extended Arabic-Indic
digits U+06F0-U+06F9, which Urdu uses; long-press gives the western digit).

## urdu_standard arrangement (ours)

Our own arrangement on a 4-row grid, right-to-left, with the `arabic` modifier row. The shift key starts R4
(the `arabic` modifier has none). Shift gives the aspirate `ھ` (on `ہ`), `ۓ` (on `ے`), `ۂ` (on `ء`), and
`آ ئ ؤ ظ ژ ؟` on their base letters. Urdu has no auto-capitalization, so the shift layer is only reached on purpose.

| Row | Keys, right to left: base (shift) |
|---|---|
| R1 | ض · ص · ث · ق · ف · غ · ع · خ · ح · ج |
| R2 | ش · س · ی (ئ) · ب · ل · ا (آ) · ت · ن · م · ک |
| R3 | ط (ظ) · ڈ · ٹ · ر · د · پ · و (ؤ) · گ · چ |
| R4 | shift · ذ · ز (ژ) · ڑ · ں · ہ (ھ) · ء (ۂ) · ے (ۓ) · ۔ (؟) |
| bottom | (from `org.florisboard.layouts:arabic` modifier) symbols · ، · language · media · space · . · enter; delete ends R4 |

## Questions for the linguist

- `urdu_phonetic` (upstream) types Arabic heh `ه` (U+0647) on its h key, not Urdu `ہ` (U+06C1); `ہ`, `ھ` and `ۂ` are
  only on its long-press. Should the base key change to `ہ`? (Not changed: the preset default stays as upstream.)
- Glyphs: `۴ ۵ ۶ ۷` render in the Persian style unless the font is Urdu-localised. Acceptable?
- Is `ۓ` needed as a key at all, or is `ے` + hamza preferred?

## Coverage

Positions: `R<row> K<key>` counts rows from the top and keys in reading order (for right-to-left layouts K1 is
the rightmost key). Layers: `base`, `shift` (press shift, then the key), `long-press` of the named key (from the
locale popup mapping), `bottom row` (the modifier row with space), and the number row, which is also the top row
of the symbols layer and is the subtype's digit choice ("Number row layout" in the subtype editor: native or
`western_arabic`). Generated from the shipped layout JSON with the same rules as `ScriptCoverageTest`
(`app/src/test/kotlin/com/manjul/aikeyboard/fork/layouts/ScriptCoverageTest.kt`); combining marks are shown on a
dotted circle.

Linguist: set **Linguist status** to `ok` or `change`, and say what to change in **Notes** (wrong letter, better
position, missing form). Sign-off (PRD FR-03 AC1): name, date, verdict at the bottom.

| # | Char | Code point | Name | `urdu_standard` key, layer | `urdu_phonetic` key, layer | Linguist status | Notes |
|---|---|---|---|---|---|---|---|
| 1 | ا | U+0627 | Arabic Letter Alef | R2 K6, base | R2 K1, base | pending | |
| 2 | آ | U+0622 | Arabic Letter Alef With Madda Above | R2 K6, shift | R2 K1, long-press `ا` | pending | |
| 3 | ب | U+0628 | Arabic Letter Beh | R2 K4, base | R3 K5, base | pending | |
| 4 | پ | U+067E | Arabic Letter Peh | R3 K6, base | R1 K10, base | pending | |
| 5 | ت | U+062A | Arabic Letter Teh | R2 K7, base | R1 K5, base | pending | |
| 6 | ٹ | U+0679 | Arabic Letter Tteh | R3 K3, base | R1 K5, long-press `ت` | pending | |
| 7 | ث | U+062B | Arabic Letter Theh | R1 K3, base | R3 K3, long-press `چ` | pending | |
| 8 | ج | U+062C | Arabic Letter Jeem | R1 K10, base | R2 K7, base | pending | |
| 9 | چ | U+0686 | Arabic Letter Tcheh | R3 K9, base | R3 K3, base | pending | |
| 10 | ح | U+062D | Arabic Letter Hah | R1 K9, base | R2 K6, base | pending | |
| 11 | خ | U+062E | Arabic Letter Khah | R1 K8, base | R2 K8, long-press `ک` | pending | |
| 12 | د | U+062F | Arabic Letter Dal | R3 K5, base | R2 K3, base | pending | |
| 13 | ڈ | U+0688 | Arabic Letter Ddal | R3 K2, base | R2 K3, long-press `د` | pending | |
| 14 | ذ | U+0630 | Arabic Letter Thal | R4 K2, base | R3 K1, base | pending | |
| 15 | ر | U+0631 | Arabic Letter Reh | R3 K4, base | R1 K4, base | pending | |
| 16 | ڑ | U+0691 | Arabic Letter Rreh | R4 K4, base | R1 K4, long-press `ر` | pending | |
| 17 | ز | U+0632 | Arabic Letter Zain | R4 K3, base | R3 K1, long-press `ذ` | pending | |
| 18 | ژ | U+0698 | Arabic Letter Jeh | R4 K3, shift | R3 K2, long-press `ش` | pending | |
| 19 | س | U+0633 | Arabic Letter Seen | R2 K2, base | R2 K2, base | pending | |
| 20 | ش | U+0634 | Arabic Letter Sheen | R2 K1, base | R3 K2, base | pending | |
| 21 | ص | U+0635 | Arabic Letter Sad | R1 K2, base | R2 K2, long-press `س` | pending | |
| 22 | ض | U+0636 | Arabic Letter Dad | R1 K1, base | R2 K7, long-press `ج` | pending | |
| 23 | ط | U+0637 | Arabic Letter Tah | R3 K1, base | R3 K4, base | pending | |
| 24 | ظ | U+0638 | Arabic Letter Zah | R3 K1, shift | R3 K4, long-press `ط` | pending | |
| 25 | ع | U+0639 | Arabic Letter Ain | R1 K7, base | R1 K3, base | pending | |
| 26 | غ | U+063A | Arabic Letter Ghain | R1 K6, base | R2 K5, long-press `گ` | pending | |
| 27 | ف | U+0641 | Arabic Letter Feh | R1 K5, base | R2 K4, base | pending | |
| 28 | ق | U+0642 | Arabic Letter Qaf | R1 K4, base | R1 K1, base | pending | |
| 29 | ک | U+06A9 | Arabic Letter Keheh | R2 K10, base | R2 K8, base | pending | |
| 30 | گ | U+06AF | Arabic Letter Gaf | R3 K8, base | R2 K5, base | pending | |
| 31 | ل | U+0644 | Arabic Letter Lam | R2 K5, base | R2 K9, base | pending | |
| 32 | م | U+0645 | Arabic Letter Meem | R2 K9, base | R3 K7, base | pending | |
| 33 | ن | U+0646 | Arabic Letter Noon | R2 K8, base | R3 K6, base | pending | |
| 34 | ں | U+06BA | Arabic Letter Noon Ghunna | R4 K5, base | R3 K6, long-press `ن` | pending | |
| 35 | و | U+0648 | Arabic Letter Waw | R3 K7, base | R1 K2, base | pending | |
| 36 | ہ | U+06C1 | Arabic Letter Heh Goal | R4 K6, base | R1 K9, long-press `ه` | pending | |
| 37 | ھ | U+06BE | Arabic Letter Heh Doachashmee | R4 K6, shift | R1 K9, long-press `ه` | pending | |
| 38 | ء | U+0621 | Arabic Letter Hamza | R4 K7, base | R1 K7, base | pending | |
| 39 | ی | U+06CC | Arabic Letter Farsi Yeh | R2 K3, base | R1 K8, base | pending | |
| 40 | ے | U+06D2 | Arabic Letter Yeh Barree | R4 K8, base | R1 K6, base | pending | |
| 41 | ۓ | U+06D3 | Arabic Letter Yeh Barree With Hamza Above | R4 K8, shift | R1 K6, long-press `ے` | pending | |
| 42 | ۂ | U+06C2 | Arabic Letter Heh Goal With Hamza Above | R4 K7, shift | R1 K9, long-press `ه` | pending | |
| 43 | ئ | U+0626 | Arabic Letter Yeh With Hamza Above | R2 K3, shift | R1 K8, long-press `ی` | pending | |
| 44 | ؤ | U+0624 | Arabic Letter Waw With Hamza Above | R3 K7, shift | R1 K2, long-press `و` | pending | |
| 45 | ، | U+060C | Arabic Comma | bottom row, base | bottom row, base | pending | |
| 46 | ؟ | U+061F | Arabic Question Mark | R4 K9, shift | bottom row, long-press `.` | pending | |
| 47 | ۔ | U+06D4 | Arabic Full Stop | R4 K9, base | bottom row, long-press `.` | pending | |
| 48 | ۰ | U+06F0 | Extended Arabic-Indic Digit Zero | number row / symbols top row K10 (`persian`) | number row / symbols top row K10 (`persian`) | pending | |
| 49 | ۱ | U+06F1 | Extended Arabic-Indic Digit One | number row / symbols top row K1 (`persian`) | number row / symbols top row K1 (`persian`) | pending | |
| 50 | ۲ | U+06F2 | Extended Arabic-Indic Digit Two | number row / symbols top row K2 (`persian`) | number row / symbols top row K2 (`persian`) | pending | |
| 51 | ۳ | U+06F3 | Extended Arabic-Indic Digit Three | number row / symbols top row K3 (`persian`) | number row / symbols top row K3 (`persian`) | pending | |
| 52 | ۴ | U+06F4 | Extended Arabic-Indic Digit Four | number row / symbols top row K4 (`persian`) | number row / symbols top row K4 (`persian`) | pending | |
| 53 | ۵ | U+06F5 | Extended Arabic-Indic Digit Five | number row / symbols top row K5 (`persian`) | number row / symbols top row K5 (`persian`) | pending | |
| 54 | ۶ | U+06F6 | Extended Arabic-Indic Digit Six | number row / symbols top row K6 (`persian`) | number row / symbols top row K6 (`persian`) | pending | |
| 55 | ۷ | U+06F7 | Extended Arabic-Indic Digit Seven | number row / symbols top row K7 (`persian`) | number row / symbols top row K7 (`persian`) | pending | |
| 56 | ۸ | U+06F8 | Extended Arabic-Indic Digit Eight | number row / symbols top row K8 (`persian`) | number row / symbols top row K8 (`persian`) | pending | |
| 57 | ۹ | U+06F9 | Extended Arabic-Indic Digit Nine | number row / symbols top row K9 (`persian`) | number row / symbols top row K9 (`persian`) | pending | |

## Sign-off

| Linguist | Date | Verdict |
|---|---|---|
| | | pending |
