# Dictionary and word-list licences

| File | Source | Licence |
|---|---|---|
| `ime/dict/data.json` | FlorisBoard v0.5.2 (upstream, unmodified) | Apache-2.0 |

## Suggestion dictionaries (S3, plan decision 2)

Built by `:tools:dictbuild` from the raw lists in `third_party/wordlists/<lang>/`. Each list is filtered (script,
digits/punctuation, length <= 24, count >= 3, our own block list), and the non-English lists have English
subtitle leakage removed down to 4 %. Counts are then log-scaled to frequency bytes. The `.sdict` files built from
FrequencyWords are adaptations of CC-BY-SA-4.0 content and are shared under CC-BY-SA-4.0
(https://creativecommons.org/licenses/by-sa/4.0/). Legal to confirm (OD-11).

| File | Source | Licence | Attribution | Words | Bytes |
|---|---|---|---|---|---|
| `dict/en.sdict` | hermitdave/FrequencyWords `content/2018/en/en_50k.txt` (OpenSubtitles2018), fetched 2026-10-26 | CC-BY-SA-4.0 (content); MIT (code) | FrequencyWords by Hermit Dave, https://github.com/hermitdave/FrequencyWords | 46,885 | 467,768 |
| `dict/es.sdict` | hermitdave/FrequencyWords `content/2018/es/es_50k.txt` (OpenSubtitles2018), fetched 2026-10-26 | CC-BY-SA-4.0 (content); MIT (code) | FrequencyWords by Hermit Dave, https://github.com/hermitdave/FrequencyWords | 49,121 | 530,307 |
| `dict/fr.sdict` | hermitdave/FrequencyWords `content/2018/fr/fr_50k.txt` (OpenSubtitles2018), fetched 2026-10-26 | CC-BY-SA-4.0 (content); MIT (code) | FrequencyWords by Hermit Dave, https://github.com/hermitdave/FrequencyWords | 45,238 | 485,600 |
| `dict/pt.sdict` | hermitdave/FrequencyWords `content/2018/pt/pt_50k.txt` (OpenSubtitles2018), fetched 2026-10-26 | CC-BY-SA-4.0 (content); MIT (code) | FrequencyWords by Hermit Dave, https://github.com/hermitdave/FrequencyWords | 43,180 | 460,364 |
| `dict/id.sdict` | hermitdave/FrequencyWords `content/2018/id/id_50k.txt` (OpenSubtitles2018), fetched 2026-10-26 | CC-BY-SA-4.0 (content); MIT (code) | FrequencyWords by Hermit Dave, https://github.com/hermitdave/FrequencyWords | 43,285 | 448,333 |
| `dict/ar.sdict` | hermitdave/FrequencyWords `content/2018/ar/ar_50k.txt` (OpenSubtitles2018), fetched 2026-10-26 | CC-BY-SA-4.0 (content); MIT (code) | FrequencyWords by Hermit Dave, https://github.com/hermitdave/FrequencyWords | 49,167 | 679,320 |
| `dict/hi.sdict` | hermitdave/FrequencyWords `content/2018/hi/hi_full.txt` (OpenSubtitles2018), fetched 2026-10-26 | CC-BY-SA-4.0 (content); MIT (code) | FrequencyWords by Hermit Dave, https://github.com/hermitdave/FrequencyWords | 9,362 | 176,270 |
| `dict/ur.sdict` | urduhack/urdu-words `words.txt`, fetched 2026-10-26; no frequencies (flat byte 16) | MIT | Copyright (c) 2018 Urdu Hack, https://github.com/urduhack/urdu-words | 99,544 | 1,394,805 |
