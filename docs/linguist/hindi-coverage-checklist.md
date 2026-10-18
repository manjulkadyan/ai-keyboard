# Hindi coverage checklist (hi-IN)

Layouts: `org.aikeyboard.layouts:hindi_phonetic` (ours) and `org.florisboard.layouts:hindi_in` (upstream InScript,
the hi-IN preset default). Both use the `hi-IN` popup mapping and the `devanagari` number row. Hindi has no
auto-capitalization, so shift is only reached on purpose.

## hindi_phonetic mapping (ours)

Each Latin QWERTY position types the Devanagari letter it sounds like where one exists (k क / K ख, g ग / G घ,
a ा / A आ, ...). Uppercase gives the aspirated or long form. Letters with no Latin counterpart: q ट / Q ठ, f ड / F ढ
(retroflex), x ष / X ङ, z virama / Z nukta, v anusvara / V अ, m म / M chandrabindu, y य / Y ञ, w व / W ॉ.
Independent vowels other than अ आ are on the long-press of their matra (ि → इ ई, ु → उ ऊ, े → ए ऐ, ो → ओ औ),
ऋ on long-press of र, । on long-press of `.`. Default modifier row (shift, delete, space, `,` `.`).

| Latin key | Base | Shift |
|---|---|---|
| q / Q | ट U+091F | ठ U+0920 |
| w / W | व U+0935 | ◌ॉ U+0949 |
| e / E | ◌े U+0947 | ◌ै U+0948 |
| r / R | र U+0930 | ◌ृ U+0943 |
| t / T | त U+0924 | थ U+0925 |
| y / Y | य U+092F | ञ U+091E |
| u / U | ◌ु U+0941 | ◌ू U+0942 |
| i / I | ◌ि U+093F | ◌ी U+0940 |
| o / O | ◌ो U+094B | ◌ौ U+094C |
| p / P | प U+092A | फ U+092B |
| a / A | ◌ा U+093E | आ U+0906 |
| s / S | स U+0938 | श U+0936 |
| d / D | द U+0926 | ध U+0927 |
| f / F | ड U+0921 | ढ U+0922 |
| g / G | ग U+0917 | घ U+0918 |
| h / H | ह U+0939 | ◌ः U+0903 |
| j / J | ज U+091C | झ U+091D |
| k / K | क U+0915 | ख U+0916 |
| l / L | ल U+0932 | ळ U+0933 |
| z / Z | ◌् U+094D | ◌़ U+093C |
| x / X | ष U+0937 | ङ U+0919 |
| c / C | च U+091A | छ U+091B |
| v / V | ◌ं U+0902 | अ U+0905 |
| b / B | ब U+092C | भ U+092D |
| n / N | न U+0928 | ण U+0923 |
| m / M | म U+092E | ◌ँ U+0901 |

## Questions for the linguist

- Is the phonetic table natural for Hindi typists (especially q/f/x/v/z, which have no Hindi sound)?
- Should ज्ञ, क्ष, त्र, श्र be direct keys? (They are long-press of त and स, or typed with virama.)

## Coverage

The required set is the literal Unicode ranges अ-औ (U+0905-U+0914), ा-ौ (U+093E-U+094C) and क-ह (U+0915-U+0939),
so it includes letters Hindi rarely uses (ऌ ऍ ऎ ऒ ऩ ऱ ऴ ॄ ॆ ॊ); they come from the upstream `hi-IN` long-press
popups.

Positions: `R<row> K<key>` counts rows from the top and keys in reading order (for right-to-left layouts K1 is
the rightmost key). Layers: `base`, `shift` (press shift, then the key), `long-press` of the named key (from the
locale popup mapping), `bottom row` (the modifier row with space), and the number row, which is also the top row
of the symbols layer and is the subtype's digit choice ("Number row layout" in the subtype editor: native or
`western_arabic`). Generated from the shipped layout JSON with the same rules as `ScriptCoverageTest`
(`app/src/test/kotlin/com/manjul/aikeyboard/fork/layouts/ScriptCoverageTest.kt`); combining marks are shown on a
dotted circle.

Linguist: set **Linguist status** to `ok` or `change`, and say what to change in **Notes** (wrong letter, better
position, missing form). Sign-off (PRD FR-03 AC1): name, date, verdict at the bottom.

| # | Char | Code point | Name | `hindi_phonetic` key, layer | `hindi_in` key, layer | Linguist status | Notes |
|---|---|---|---|---|---|---|---|
| 1 | अ | U+0905 | Devanagari Letter A | R3 K4, shift | R2 K1, shift | pending | |
| 2 | आ | U+0906 | Devanagari Letter Aa | R2 K1, shift | R2 K1, long-press `ा` | pending | |
| 3 | इ | U+0907 | Devanagari Letter I | R1 K8, long-press `ि` | R1 K8, long-press `ि` | pending | |
| 4 | ई | U+0908 | Devanagari Letter Ii | R1 K8, long-press `ि` | R1 K8, long-press `ि` | pending | |
| 5 | उ | U+0909 | Devanagari Letter U | R1 K7, long-press `ु` | R1 K7, long-press `ु` | pending | |
| 6 | ऊ | U+090A | Devanagari Letter Uu | R1 K7, long-press `ु` | R1 K7, long-press `ु` | pending | |
| 7 | ऋ | U+090B | Devanagari Letter Vocalic R | R1 K4, long-press `र` | R1 K4, long-press `र` | pending | |
| 8 | ऌ | U+090C | Devanagari Letter Vocalic L | R2 K9, long-press `ल` | R2 K9, long-press `ल` | pending | |
| 9 | ऍ | U+090D | Devanagari Letter Candra E | R1 K3, long-press `े` | R1 K3, long-press `े` | pending | |
| 10 | ऎ | U+090E | Devanagari Letter Short E | R1 K3, long-press `े` | R1 K3, long-press `े` | pending | |
| 11 | ए | U+090F | Devanagari Letter E | R1 K3, long-press `े` | R1 K3, long-press `े` | pending | |
| 12 | ऐ | U+0910 | Devanagari Letter Ai | R1 K3, long-press `े` | R1 K3, long-press `े` | pending | |
| 13 | ऑ | U+0911 | Devanagari Letter Candra O | R1 K9, long-press `ो` | R1 K9, long-press `ो` | pending | |
| 14 | ऒ | U+0912 | Devanagari Letter Short O | R1 K9, long-press `ो` | R1 K9, long-press `ो` | pending | |
| 15 | ओ | U+0913 | Devanagari Letter O | R1 K9, long-press `ो` | R1 K9, long-press `ो` | pending | |
| 16 | औ | U+0914 | Devanagari Letter Au | R1 K9, long-press `ो` | R1 K9, long-press `ो` | pending | |
| 17 | ◌ा | U+093E | Devanagari Vowel Sign Aa | R2 K1, base | R2 K1, base | pending | |
| 18 | ◌ि | U+093F | Devanagari Vowel Sign I | R1 K8, base | R1 K8, base | pending | |
| 19 | ◌ी | U+0940 | Devanagari Vowel Sign Ii | R1 K8, shift | R1 K8, shift | pending | |
| 20 | ◌ु | U+0941 | Devanagari Vowel Sign U | R1 K7, base | R1 K7, base | pending | |
| 21 | ◌ू | U+0942 | Devanagari Vowel Sign Uu | R1 K7, shift | R1 K7, shift | pending | |
| 22 | ◌ृ | U+0943 | Devanagari Vowel Sign Vocalic R | R1 K4, shift | R1 K4, shift | pending | |
| 23 | ◌ॄ | U+0944 | Devanagari Vowel Sign Vocalic Rr | R1 K4, long-press `र` | R1 K4, long-press `र` | pending | |
| 24 | ◌ॅ | U+0945 | Devanagari Vowel Sign Candra E | R2 K1, long-press `ा` | R2 K1, long-press `ा` | pending | |
| 25 | ◌ॆ | U+0946 | Devanagari Vowel Sign Short E | R1 K3, long-press `े` | R1 K3, long-press `े` | pending | |
| 26 | ◌े | U+0947 | Devanagari Vowel Sign E | R1 K3, base | R1 K3, base | pending | |
| 27 | ◌ै | U+0948 | Devanagari Vowel Sign Ai | R1 K3, shift | R1 K3, shift | pending | |
| 28 | ◌ॉ | U+0949 | Devanagari Vowel Sign Candra O | R1 K2, shift | R1 K9, long-press `ो` | pending | |
| 29 | ◌ॊ | U+094A | Devanagari Vowel Sign Short O | R1 K9, long-press `ो` | R1 K9, long-press `ो` | pending | |
| 30 | ◌ो | U+094B | Devanagari Vowel Sign O | R1 K9, base | R1 K9, base | pending | |
| 31 | ◌ौ | U+094C | Devanagari Vowel Sign Au | R1 K9, shift | R1 K9, shift | pending | |
| 32 | क | U+0915 | Devanagari Letter Ka | R2 K8, base | R2 K8, base | pending | |
| 33 | ख | U+0916 | Devanagari Letter Kha | R2 K8, shift | R2 K8, shift | pending | |
| 34 | ग | U+0917 | Devanagari Letter Ga | R2 K5, base | R2 K5, base | pending | |
| 35 | घ | U+0918 | Devanagari Letter Gha | R2 K5, shift | R2 K5, shift | pending | |
| 36 | ङ | U+0919 | Devanagari Letter Nga | R3 K2, shift | R3 K6, long-press `न` | pending | |
| 37 | च | U+091A | Devanagari Letter Ca | R3 K3, base | R3 K3, base | pending | |
| 38 | छ | U+091B | Devanagari Letter Cha | R3 K3, shift | R3 K3, shift | pending | |
| 39 | ज | U+091C | Devanagari Letter Ja | R2 K7, base | R2 K7, base | pending | |
| 40 | झ | U+091D | Devanagari Letter Jha | R2 K7, shift | R2 K7, shift | pending | |
| 41 | ञ | U+091E | Devanagari Letter Nya | R1 K6, shift | R3 K1, base | pending | |
| 42 | ट | U+091F | Devanagari Letter Tta | R1 K1, base | R1 K1, base | pending | |
| 43 | ठ | U+0920 | Devanagari Letter Ttha | R1 K1, shift | R1 K1, shift | pending | |
| 44 | ड | U+0921 | Devanagari Letter Dda | R2 K4, base | R1 K2, base | pending | |
| 45 | ढ | U+0922 | Devanagari Letter Ddha | R2 K4, shift | R1 K2, shift | pending | |
| 46 | ण | U+0923 | Devanagari Letter Nna | R3 K6, shift | R3 K6, shift | pending | |
| 47 | त | U+0924 | Devanagari Letter Ta | R1 K5, base | R1 K5, base | pending | |
| 48 | थ | U+0925 | Devanagari Letter Tha | R1 K5, shift | R1 K5, shift | pending | |
| 49 | द | U+0926 | Devanagari Letter Da | R2 K3, base | R2 K3, base | pending | |
| 50 | ध | U+0927 | Devanagari Letter Dha | R2 K3, shift | R2 K3, shift | pending | |
| 51 | न | U+0928 | Devanagari Letter Na | R3 K6, base | R3 K6, base | pending | |
| 52 | ऩ | U+0929 | Devanagari Letter Nnna | R3 K6, long-press `न` | R3 K6, long-press `न` | pending | |
| 53 | प | U+092A | Devanagari Letter Pa | R1 K10, base | R1 K10, base | pending | |
| 54 | फ | U+092B | Devanagari Letter Pha | R1 K10, shift | R2 K4, base | pending | |
| 55 | ब | U+092C | Devanagari Letter Ba | R3 K5, base | R3 K5, base | pending | |
| 56 | भ | U+092D | Devanagari Letter Bha | R3 K5, shift | R3 K5, shift | pending | |
| 57 | म | U+092E | Devanagari Letter Ma | R3 K7, base | R3 K7, base | pending | |
| 58 | य | U+092F | Devanagari Letter Ya | R1 K6, base | R1 K6, base | pending | |
| 59 | र | U+0930 | Devanagari Letter Ra | R1 K4, base | R1 K4, base | pending | |
| 60 | ऱ | U+0931 | Devanagari Letter Rra | R1 K4, long-press `र` | R1 K4, long-press `र` | pending | |
| 61 | ल | U+0932 | Devanagari Letter La | R2 K9, base | R2 K9, base | pending | |
| 62 | ळ | U+0933 | Devanagari Letter Lla | R2 K9, shift | R2 K9, shift | pending | |
| 63 | ऴ | U+0934 | Devanagari Letter Llla | R2 K9, long-press `ल` | R2 K9, long-press `ल` | pending | |
| 64 | व | U+0935 | Devanagari Letter Va | R1 K2, base | R3 K4, base | pending | |
| 65 | श | U+0936 | Devanagari Letter Sha | R2 K2, shift | R2 K2, shift | pending | |
| 66 | ष | U+0937 | Devanagari Letter Ssa | R3 K2, base | R3 K2, base | pending | |
| 67 | स | U+0938 | Devanagari Letter Sa | R2 K2, base | R2 K2, base | pending | |
| 68 | ह | U+0939 | Devanagari Letter Ha | R2 K6, base | R2 K6, base | pending | |
| 69 | ◌ं | U+0902 | Devanagari Sign Anusvara | R3 K4, base | R3 K7, shift | pending | |
| 70 | ◌ः | U+0903 | Devanagari Sign Visarga | R2 K6, shift | R2 K6, shift | pending | |
| 71 | ◌ँ | U+0901 | Devanagari Sign Candrabindu | R3 K7, shift | R3 K7, long-press `म` | pending | |
| 72 | ◌् | U+094D | Devanagari Sign Virama | R3 K1, base | R3 K1, base | pending | |
| 73 | ◌़ | U+093C | Devanagari Sign Nukta | R3 K1, shift | R2 K4, shift | pending | |
| 74 | । | U+0964 | Devanagari Danda | bottom row, long-press `.` | bottom row, long-press `.` | pending | |
| 75 | ० | U+0966 | Devanagari Digit Zero | number row / symbols top row K10 (`devanagari`) | number row / symbols top row K10 (`devanagari`) | pending | |
| 76 | १ | U+0967 | Devanagari Digit One | number row / symbols top row K1 (`devanagari`) | number row / symbols top row K1 (`devanagari`) | pending | |
| 77 | २ | U+0968 | Devanagari Digit Two | number row / symbols top row K2 (`devanagari`) | number row / symbols top row K2 (`devanagari`) | pending | |
| 78 | ३ | U+0969 | Devanagari Digit Three | number row / symbols top row K3 (`devanagari`) | number row / symbols top row K3 (`devanagari`) | pending | |
| 79 | ४ | U+096A | Devanagari Digit Four | number row / symbols top row K4 (`devanagari`) | number row / symbols top row K4 (`devanagari`) | pending | |
| 80 | ५ | U+096B | Devanagari Digit Five | number row / symbols top row K5 (`devanagari`) | number row / symbols top row K5 (`devanagari`) | pending | |
| 81 | ६ | U+096C | Devanagari Digit Six | number row / symbols top row K6 (`devanagari`) | number row / symbols top row K6 (`devanagari`) | pending | |
| 82 | ७ | U+096D | Devanagari Digit Seven | number row / symbols top row K7 (`devanagari`) | number row / symbols top row K7 (`devanagari`) | pending | |
| 83 | ८ | U+096E | Devanagari Digit Eight | number row / symbols top row K8 (`devanagari`) | number row / symbols top row K8 (`devanagari`) | pending | |
| 84 | ९ | U+096F | Devanagari Digit Nine | number row / symbols top row K9 (`devanagari`) | number row / symbols top row K9 (`devanagari`) | pending | |

## Sign-off

| Linguist | Date | Verdict |
|---|---|---|
| | | pending |
