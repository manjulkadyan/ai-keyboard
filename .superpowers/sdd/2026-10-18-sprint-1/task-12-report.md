# Task 12 report: `:lib:whisper` (whisper.cpp v1.9.5, JNI, 16 KB alignment)

Status: DONE_WITH_CONCERNS (native runtime behaviour is unverified until it runs on a device, per R6)
Commit: `582550c5 feat(whisper): vendor whisper.cpp v1.9.5 with a 16 KB-aligned JNI bridge` on `sprint-1`, not pushed.

## What I implemented
- **Vendored source**: `lib/whisper/third_party/whisper.cpp/` came from `git archive` of tag `v1.9.5`, commit
  `d1be6fde11ac6e0407606b4e42fe72d34add8037` (2026-10-06). It contains only `include/ src/ ggml/ cmake/ CMakeLists.txt LICENSE`
  (1456 files, 26 MB; most of the size is ggml's unused GPU backends). Nothing was modified. `third_party/WHISPER_UPSTREAM.md`
  records the tag, SHA, date, MIT licence, the update procedure and which third-party code inside the tree ships in the binary.
- **Build**: `lib/whisper/build.gradle.kts` uses `aikeyboard.android.library` + `externalNativeBuild { cmake }` (NDK/CMake versions
  from `tools.versions`, same as `:lib:native`), with `abiFilters` arm64-v8a, armeabi-v7a and x86_64, and `targets("aikb_whisper")`.
  The debug build type passes `-DCMAKE_BUILD_TYPE=Release` because whisper is unusable at -O0.
  `src/main/cpp/CMakeLists.txt` does `add_subdirectory(whisper.cpp EXCLUDE_FROM_ALL)` with `BUILD_SHARED_LIBS=OFF`,
  tests/examples/server off, `GGML_NATIVE=OFF`, `GGML_BACKEND_DL=OFF`, `GGML_OPENMP=OFF`, `GGML_LLAMAFILE=OFF`,
  `-O3 -ffunction-sections -fdata-sections`. `libaikb_whisper.so` is linked with `-Wl,-z,max-page-size=16384
  -Wl,-z,common-page-size=16384 --gc-sections --exclude-libs,ALL` and `c++_static`. Its only NEEDED libraries are libm, libdl
  and libc. It exports only the 3 `Java_..._WhisperJni_*` symbols.
- **JNI** (`whisper_jni.cpp`): `nativeInit` → `whisper_init_from_file_with_params` (use_gpu=false). The handle points to a small
  `Session{ctx, threads}` struct, because `threads` is given to `load` but consumed by `whisper_full`. `nativeTranscribe` →
  `whisper_full` with `WHISPER_SAMPLING_GREEDY`, `no_timestamps=true`, `language=lang`, `translate`, `single_segment=false`
  and all print_* flags false, then concatenates the segment texts. It returns null if the language is unknown
  (`whisper_lang_id < 0`, "auto" allowed) or if `whisper_full` fails. It uses `GetFloatArrayElements`, not the critical
  variant, so a seconds-long decode doesn't block the GC. `nativeFree` → `whisper_free` + delete.
  `whisper_log_set(discard)` keeps whisper.cpp from logging anything (PC-10).
- **Kotlin**: `WhisperNative.kt` has `interface WhisperApi` (the exact brief signatures, `translate = false` default),
  `WhisperException`, and `object WhisperNative : WhisperApi by WhisperBridge(WhisperJni)`.
  `WhisperBridge.kt` holds the real contract logic over the internal `WhisperNativeCalls` seam:
  - `load` returns 0 for a blank path, an unavailable library or a native failure, and clamps threads to at least 1;
  - only live handles reach native code: `free(0)`, freeing a failed load and double `free` are no-ops;
  - `transcribe` on a non-live handle throws `IllegalArgumentException`;
  - empty PCM returns "" without a native call;
  - a native failure throws `WhisperException("whisper_full failed")` (no user content in the message);
  - the result is trimmed;
  - all calls go through one lock, so `free` (e.g. the 60 s idle unload) waits for a running `transcribe` instead of a
    use-after-free.

  `WhisperJni.kt` loads the library lazily on first use and catches `UnsatisfiedLinkError`, so x86 devices (no .so) report
  "unavailable" and `load` returns 0.
- **R8**: `consumer-rules.pro` keeps `WhisperJni` and its native method names. I checked it with `:app:minifyReleaseWithR8`:
  the mapping shows `com.manjul.aikeyboard.lib.whisper.WhisperJni -> com.manjul.aikeyboard.lib.whisper.WhisperJni`
  (not renamed), and native methods are kept under their own names.
- **Packaging**: no `useLegacyPackaging`, `extractNativeLibs` or `jniLibs` override anywhere, so the AGP default applies
  (minSdk 26 → stored uncompressed, page-aligned). `unzip -lv` shows `Stored` for every .so, and the zipalign -P 16 check passes.
- **`:app`**: `implementation(projects.lib.whisper)`. `settings.gradle.kts`: `include(":lib:whisper")`. Both carry
  `FORK(S1-whisper)` markers, and there is a new `docs/FORK_PATCHES.md` row.
- **NOTICE** (our file): whisper.cpp/ggml attribution with the full MIT text, plus the YaRN (Quesnelle & Peng, MIT) note.
  NOTICE is shipped in-app by `copyLicenseAssets`.
- **CI** (`.github/workflows/pr.yml`, our file): the 16 KB step now also requires `lib/<abi>/libaikb_whisper.so` for the three
  ABIs. `scripts/check-16kb-alignment.sh` needed no change, because it checks every .so generically.

## Decisions
1. **arm64 baseline is plain armv8-a, not armv8.2-a+fp16+dotprod.** The only runtime CPU dispatch ggml offers is
   `GGML_CPU_ALL_VARIANTS`, which requires `GGML_BACKEND_DL` (separate dlopen'ed backend .so files). That doesn't fit one .so.
   A static v8.2 build would SIGILL on Cortex-A53/A73 devices. Possible follow-up: two .so variants chosen in Kotlin by
   `/proc/cpuinfo` features, as upstream's whisper.android example does.
2. **The full `ggml/` tree is vendored, as the brief lists it.** Only the CPU backend is configured. I did not prune unused GPU
   backends: they don't affect the binary, and keeping the tree pristine makes future updates a plain re-archive.
3. **Failure semantics beyond the brief.** On a non-live handle `transcribe` throws `IllegalArgumentException`, and on a native
   failure it throws `WhisperException`. The alternative was returning "", but I chose throwing so that Task 13 can show
   an explicit error (Review Focus 4: never fail silently). Task 13 should catch both.
4. **One lock per bridge** serialises all calls, so two loaded models also serialise. That is acceptable because one model is
   loaded at a time.

## Tests / TDD evidence
- RED: `$G ./gradlew :lib:whisper:test`, with the test written before any main source →
  `compileDebugUnitTestKotlin FAILED`, `Unresolved reference 'WhisperNativeCalls'`, `Unresolved reference 'WhisperBridge'`
  (expected: the API did not exist yet).
- GREEN: `$G ./gradlew :lib:whisper:test` → BUILD SUCCESSFUL. `WhisperNativeContractTest` tests="13" failures="0" errors="0".
  It covers load handle, load failure → 0 + safe free, unavailable library, blank path, thread clamp, empty PCM → "" with no
  native call, lang/translate pass-through + trim, translate default false, native failure → WhisperException, stale/0 handle →
  IAE with no native call, double free → one native free, free waits for a running transcribe, and WhisperNative is a WhisperApi.
- Full verification: `$G ./gradlew :lib:whisper:test :app:assembleDebug && scripts/check-16kb-alignment.sh app/build/outputs/apk/debug/*.apk`
  → BUILD SUCCESSFUL, alignment OK (output below). The first cold build of whisper for 3 ABIs took about 6 min.
- `scripts/check-fork-markers.sh` (after commit): `96 modified (6 marker-exempt), 202 rename-only, 207 deleted` / `check-fork-markers: OK`.
- `$G ./gradlew :app:minifyReleaseWithR8` → BUILD SUCCESSFUL (keep rule verified as above).
- The only compiler warnings come from inside the vendored whisper.cpp (`-Wformat` in `whisper.cpp:3517`). Our code compiles
  with `-Wall -Wextra` and no warnings.

## APK size (debug)
- Before (HEAD ffe72272): 37,221,465 bytes. After: 42,252,980 bytes. Delta **+5,031,515 bytes (~4.8 MiB)**.
- Per-ABI `libaikb_whisper.so` (stored): arm64-v8a 1,689,608; armeabi-v7a 1,300,900; x86_64 2,002,376.

## 16 KB script output
```
== app/build/outputs/apk/debug/app-debug.apk
ok   lib/arm64-v8a/libaikb_whisper.so
ok   lib/arm64-v8a/libandroidx.graphics.path.so
ok   lib/arm64-v8a/libdatastore_shared_counter.so
ok   lib/arm64-v8a/libfl_native.so
ok   lib/armeabi-v7a/libaikb_whisper.so
ok   lib/armeabi-v7a/libandroidx.graphics.path.so
ok   lib/armeabi-v7a/libdatastore_shared_counter.so
ok   lib/armeabi-v7a/libfl_native.so
ok   lib/x86_64/libaikb_whisper.so
ok   lib/x86_64/libandroidx.graphics.path.so
ok   lib/x86_64/libdatastore_shared_counter.so
ok   lib/x86_64/libfl_native.so
ok   lib/x86/libandroidx.graphics.path.so
ok   lib/x86/libdatastore_shared_counter.so
ok   lib/x86/libfl_native.so
   15 native libraries checked
check-16kb-alignment: OK
```

## Files
- New: `lib/whisper/build.gradle.kts`, `lib/whisper/consumer-rules.pro`, `lib/whisper/src/main/cpp/{CMakeLists.txt,whisper_jni.cpp}`,
  `lib/whisper/src/main/kotlin/com/manjul/aikeyboard/lib/whisper/{WhisperNative,WhisperBridge,WhisperJni}.kt`,
  `lib/whisper/src/test/kotlin/com/manjul/aikeyboard/lib/whisper/WhisperNativeContractTest.kt`,
  `lib/whisper/third_party/WHISPER_UPSTREAM.md`, `lib/whisper/third_party/whisper.cpp/**` (vendored)
- Modified: `settings.gradle.kts`, `app/build.gradle.kts`, `docs/FORK_PATCHES.md`, `NOTICE`, `.github/workflows/pr.yml`

## Concerns / device checklist
- No native path has been exercised: there is no emulator and no model, because huggingface.co is blocked. The device checklist should cover:
  - `load` with base.bin returns non-zero;
  - `transcribe` of a known clip gives the expected text in en and hi;
  - an unknown language code throws WhisperException;
  - `free` during a running transcribe blocks and doesn't crash;
  - arm64 decode latency is acceptable without fp16/dotprod.
- I did not check release-AAB alignment. The flags are the same for every build type, and the TRD puts the AAB check at the Main stage.
- `WhisperNative` can be called from any thread, but `transcribe` blocks for seconds. Task 13 must call it off the main thread.

---

## Fix round 1 (commit `79f12c6e fix(whisper): address review for task 12`)

### Changes
1. **UTF-8 (Important 1).**
   - `nativeTranscribe` now returns a `jbyteArray` (`NewByteArray` + `SetByteArrayRegion` over the concatenated segment bytes).
   - Kotlin decodes it with `decodeWhisperText` = `String(bytes, Charsets.UTF_8)`, so malformed input becomes U+FFFD. `NewStringUTF` is gone.
   - Device checklist: transcribe a clip that yields Devanagari text and one that yields an emoji.
2. **Non-blocking free (Important 2, R27).**
   - `Session` in the JNI holds `std::atomic<bool> abort_requested`. `whisper_full_params.abort_callback` polls it, and the new `nativeAbort` sets it.
   - `WhisperBridge` tracks each session's `busy`/`closed` state under a short lock that is never held across native decode or model load.
   - `free()` removes the id and marks the session closed. If the session is idle, `free()` calls `nativeFree` immediately. If busy, it only calls `nativeAbort` and returns.
   - `transcribe` releases the lock during the decode. Its `finally` frees the session if it was closed meanwhile, then it throws `WhisperException("Transcription aborted: context freed")`.
   - The native side also returns null if the abort flag is set even though `whisper_full` returned 0.
   - The flag is never reset. This is safe because an aborted session is always freed, and it avoids losing an abort that arrives before the decode starts.
   - A second concurrent `transcribe` on the same handle now throws `IllegalStateException` instead of queueing (documented on `WhisperApi`).
   - `load` runs the model load outside the lock.
3. **Minor fixes.**
   - Threads are clamped to `1..min(availableProcessors, MAX_WHISPER_THREADS = 8)`. The bridge takes an injectable `maxThreads` for tests.
   - Handles are monotonic ids (`nextId++`) mapped to native pointers, so a reused pointer never revives an old id.
   - `params.suppress_nst = true`, so non-speech tokens such as "[BLANK_AUDIO]" and "(music)" are not typed. `suppress_blank` stays at its default (true).
   - The tautological test is replaced by `WhisperNative.load("", 1) == 0L`, which runs the real object on the JVM.
   - `nativeTranscribe` checks for a null session and throws `IllegalStateException`.
   - `-Wno-format` is added to the vendored targets via the existing `foreach` line in our CMakeLists.txt. The vendored tree is untouched, and the `whisper.cpp:3517` warning is gone from the build log.

### Tests
- RED: `git stash push -- lib/whisper/src/main`, then `$G ./gradlew :lib:whisper:test` → `compileBetaUnitTestKotlin FAILED`. Errors:
  - `Return type of 'transcribe' is not a subtype ... String?`
  - `'abort' overrides nothing`
  - `No parameter with name 'maxThreads' found`
  - `Unresolved reference 'MAX_WHISPER_THREADS'`

  These are expected, because the new seam (bytes, abort, thread cap) didn't exist yet. I then ran `git stash pop`.
- GREEN: `$G ./gradlew :lib:whisper:test :app:assembleDebug` → BUILD SUCCESSFUL. `WhisperNativeContractTest` tests="19" failures="0" errors="0".
- New and changed tests:
  - thread clamp at both bounds, plus the default cap;
  - emoji + Devanagari decode intact;
  - a truncated 3-byte sequence decodes to `"ab�"`;
  - load/free/load again with the same native pointer: the old id is rejected and the new id works;
  - free during a running decode returns in under 1 s, aborts the decode, frees exactly once, transcribe throws `WhisperException`, and the id is then rejected. This replaces the old "free waits" test;
  - a second transcribe on a busy handle throws `IllegalStateException`;
  - `WhisperNative` blank-path test.
- `scripts/check-16kb-alignment.sh app/build/outputs/apk/debug/*.apk` → rc=0. All 3 `libaikb_whisper.so` are ok, and `check-16kb-alignment: OK`.
- The `.so` exports `nativeInit`, `nativeTranscribe`, `nativeAbort` and `nativeFree`. The consumer keep rule covers all `native <methods>`.
- `scripts/check-fork-markers.sh` (after commit) → `check-fork-markers: OK`.
- Debug APK is now 42,252,397 bytes, so the delta against the pre-task baseline is +5,030,932 bytes.

### Remaining concerns
- None of the native behaviour has run on a device yet. Device checklist additions:
  - free during a real decode returns at once, and the decode stops at the next graph-compute check (encoder/decoder step granularity);
  - emoji/Devanagari output decodes correctly.
