# Task 12 review (ffe72272..582550c5)

### Spec Compliance

- ✅ Spec compliant. Every file in the brief is present (diff stat, lines 11-25). `WhisperApi` matches the brief's signatures (WhisperNative.kt:510-528). The JNI calls `whisper_init_from_file_with_params`, then `whisper_full` with GREEDY, `no_timestamps`, `language`, `single_segment=false`, and concatenates the segments (whisper_jni.cpp:331, 354-378). CMake sets `-O3`, `GGML_NATIVE=OFF` and `max-page-size=16384` (CMakeLists.txt:255, 269/273, 277). The module is in `settings.gradle.kts:777` and `:app` depends on it at app/build.gradle.kts:129. NOTICE has the MIT text. FORK_PATCHES has its row (FORK_PATCHES.md:155). The 16 KB output in the report lists `libaikb_whisper.so` as ok for all 3 ABIs.
- ✅ Vendored tree check: I cloned `v1.9.5` to /tmp/whisper-rev-t12. `rev-parse HEAD` = `d1be6fde11ac6e0407606b4e42fe72d34add8037`, which matches WHISPER_UPSTREAM.md:738. `diff -r` on include, src, ggml, cmake, LICENSE and CMakeLists.txt found every one identical. The vendored tree has 1456 files, and upstream `git ls-files` of the same paths also gives 1456. No .bin/.gguf models, examples, bindings or tests.
- ✅ arm64 baseline decision verified in upstream ggml/src/ggml-cpu/CMakeLists.txt:209-215. With GGML_NATIVE=OFF, an empty GGML_CPU_ARM_ARCH and no ALL_VARIANTS, no -march is added, so the build is plain armv8-a. Runtime dispatch only exists through GGML_CPU_ALL_VARIANTS, which needs GGML_BACKEND_DL. This matches the brief's "otherwise plain armv8-a" fallback.
- ✅ x86_64 check: when cross-compiling, ggml/CMakeLists.txt:141-154 sets INS_ENB=OFF, so SSE4.2/AVX are off by default. There is no SIGILL risk on older emulators.
- ⚠️ Cannot verify from the diff: all on-device behaviour (R6), and that the fork-marker script passes. The report says it passes, and I did not re-run it.
- Extra (accepted): throwing `IllegalArgumentException`/`WhisperException` instead of returning "" goes beyond the brief. It is documented (WhisperNative.kt:521-522), and it fits Review Focus 4 (never fail silently). Task 13 must catch both.

### Strengths
- The Kotlin guards are real logic, not test scaffolding. The `live` set blocks double free, stale handles and use of handle 0 (WhisperBridge.kt:421-446). Empty PCM short-circuits, threads are clamped, results are trimmed, and one lock prevents a use-after-free between `free` and `transcribe`. 12 of the 13 tests check real behaviour, including a real concurrency ordering test (Test.kt:692-718).
- The JNI basics are correct:
  - `GetFloatArrayElements` (not the critical variant) is released with `JNI_ABORT`, which is right for read-only input (whisper_jni.cpp:367-370).
  - The `std::string language` copy outlives `whisper_full`, so `params.language` never dangles (350-356).
  - String chars are released on every path.
  - The `nothrow` Session allocation frees the ctx if it fails (335-339).
  - No local-ref leaks. Only one jstring is created and it is returned, so no GlobalRef is needed.
- `whisper_log_set` also routes ggml logging (upstream src/whisper.cpp:9329-9332), so PC-10 holds. Exception messages contain no user content.
- Symbols are hidden with `--exclude-libs,ALL`, `c++_static` keeps NEEDED to libc/libm/libdl, and the R8 keep rule is checked against the mapping.

### Issues

#### Critical (Must Fix)
None.

#### Important (Should Fix)
1. **whisper_jni.cpp:379: `NewStringUTF` is given raw whisper output, which is standard UTF-8 and not always valid.**
   - Whisper's byte-level BPE can produce byte sequences that are invalid or truncated. This happens mostly in non-Latin languages (hi is a target), on hallucinations, or when output is cut off at the token cap. It can also produce 4-byte sequences (emoji such as U+1F3B5).
   - Both of these are invalid *modified* UTF-8. Debuggable builds run with CheckJNI on, and CheckJNI aborts the process with "input is not valid Modified UTF-8". That kills the IME process. Without CheckJNI, ART's behaviour on malformed input is unspecified (garbage text at best).
   - The JVM fake cannot catch this (R6), so it would surface on devices in debug/beta.
   - Fix: return a `jbyteArray` (`NewByteArray` + `SetByteArrayRegion` over `text`), and decode in Kotlin with `String(bytes, Charsets.UTF_8)`. That decoder replaces malformed input with U+FFFD. Add a device-checklist item: transcribe audio that produces an emoji or Devanagari text.
2. **WhisperBridge.kt:442-446 (and the WhisperNative.kt:508 doc): `free` blocks its caller for the whole running decode, which can be seconds.**
   - The global constraint unloads on `onTrimMemory(>= RUNNING_LOW)`, and that callback runs on the main thread. Memory pressure is most likely exactly while a ~140-470 MB model is decoding.
   - A main-thread `free` waiting on `synchronized(lock)` can therefore cause an ANR in the keyboard.
   - The use-after-free fix is right, but blocking is the wrong way to get it.
   - Fix: make `free` non-blocking. Mark the handle as closing, have the in-flight `transcribe` release it in a `finally`, and optionally set `whisper_full_params.abort_callback` (upstream whisper.h:574) so the running decode stops early. Otherwise, document loudly that `free` must be called off the main thread and hand that to Task 13. The non-blocking fix is preferred.

#### Minor (Nice to Have)
3. **WhisperBridge.kt:426: threads are clamped only from below.** Use `coerceIn(1, Runtime.getRuntime().availableProcessors())`, or cap at 4-8 for big.LITTLE. A caller passing a large value oversubscribes the CPU in the IME process.
4. **WhisperBridge.kt:421: handle ABA.** Handles are raw `Session*` addresses, so after `free` the allocator can hand the same address to a later `load`. A stale handle from the old session would then pass `ctx in live`. Fix: use a monotonically increasing Kotlin-side id mapped to the native pointer.
5. **whisper_jni.cpp:354-363: `suppress_nst` is left at its upstream default of false** (upstream whisper.cpp:6130). Non-speech annotations such as "[BLANK_AUDIO]" or "(music)" can then reach the editor as typed text. Set `params.suppress_nst = true`, or have Task 13 filter them.
6. **WhisperNativeContractTest.kt:720-724: tautological test.** `assertTrue(api is WhisperNative)` after assigning `WhisperNative` can never fail, and the compiler already enforces `WhisperApi`. Delete it, or replace it with a test that checks `WhisperNative.load("", 1) == 0L`. That call runs on the JVM because the blank-path guard returns before the library loads.
7. **whisper_jni.cpp:346: `to_session` does not check for null** in `nativeTranscribe` (`nativeFree` does). Kotlin guarantees it today, but a one-line `if (session == nullptr) return nullptr;` would keep the native side safe on its own if the Kotlin guards change.
8. **Report: compiler warnings from vendored code** (`-Wformat` at whisper.cpp:3517). They are inside unmodifiable upstream code, so accept them. The build log would be cleaner if `-Wno-format` were added to the `whisper` target only.

### Checks run
- Vendored tree: clone + SHA + `diff -r` + file count (results above).
- ARM/x86 CPU flags: upstream ggml-cpu/CMakeLists.txt:120-215 and ggml/CMakeLists.txt:106-154.
- Logging/flash-attn/suppress_nst defaults: upstream src/whisper.cpp:3716, 6130 and 9329.
- I did not re-run the tests. Nothing in the Kotlin code raised a doubt that a run would settle, and the JNI issues cannot be exercised on the JVM.

### Assessment

**Task quality:** Needs fixes

**Reasoning:** The vendoring is pristine, the build flags are correct and the Kotlin guards are solid. However, `NewStringUTF` over unvalidated whisper output will abort the IME process under CheckJNI for emoji or truncated multi-byte output. Separately, a blocking `free` called from main-thread `onTrimMemory` can ANR the keyboard. Both are small, local fixes.
