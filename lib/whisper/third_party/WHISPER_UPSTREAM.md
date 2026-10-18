# whisper.cpp (vendored)

| | |
|---|---|
| Upstream | https://github.com/ggml-org/whisper.cpp |
| Tag | `v1.9.5` |
| Commit | `d1be6fde11ac6e0407606b4e42fe72d34add8037` (tag dereferenced, `git rev-parse HEAD` of a `--branch v1.9.5` clone) |
| Commit date | 2026-10-06T16:10:35+02:00 |
| Vendored on | 2026-10-18 |
| Licence | MIT, `whisper.cpp/LICENSE` (Copyright (c) 2023-2026 The ggml authors) |

Only `include/`, `src/`, `ggml/`, `cmake/`, `CMakeLists.txt` and `LICENSE` are copied, unmodified, from
`git archive` of that commit. No models, examples, bindings, tests or scripts. Do not edit files under
`whisper.cpp/`; build options live in `../src/main/cpp/CMakeLists.txt`.

Third-party code inside the vendored tree that ends up in `libaikb_whisper.so`:
`ggml/src/ggml-cpu/ops.cpp` `rope_yarn` (YaRN, MIT, Copyright (c) 2023 Jeffrey Quesnelle and Bowen Peng).
llamafile sgemm and KleidiAI are present in the tree but switched off (`GGML_LLAMAFILE`, `GGML_CPU_KLEIDIAI`).

## Updating

```
git clone --depth 1 --branch <tag> https://github.com/ggml-org/whisper.cpp /tmp/whisper-src
rm -r lib/whisper/third_party/whisper.cpp && mkdir lib/whisper/third_party/whisper.cpp
git -C /tmp/whisper-src archive HEAD include src ggml cmake CMakeLists.txt LICENSE \
    | tar -x -C lib/whisper/third_party/whisper.cpp
```

then update this file and the NOTICE entry, rebuild, and run `scripts/check-16kb-alignment.sh`.
