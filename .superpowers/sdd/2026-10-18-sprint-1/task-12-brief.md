### Task 12: `:lib:whisper` — vendored whisper.cpp v1.9.5, JNI, 16 KB alignment

**Files:**
- Create: `lib/whisper/build.gradle.kts` (`aikeyboard.android.library`, `externalNativeBuild { cmake }`, ABIs `arm64-v8a`, `armeabi-v7a`, `x86_64`), `lib/whisper/src/main/cpp/{CMakeLists.txt,whisper_jni.cpp}`, `lib/whisper/src/main/kotlin/com/manjul/aikeyboard/lib/whisper/WhisperNative.kt`, `lib/whisper/third_party/whisper.cpp/` (vendored source at tag `v1.9.5`, only `include/`, `src/`, `ggml/`, `LICENSE`, `CMakeLists.txt`, `cmake/`), `lib/whisper/third_party/WHISPER_UPSTREAM.md` (tag, dereferenced commit SHA, date, MIT licence)
- Modify: `settings.gradle.kts`, `NOTICE` (whisper.cpp MIT attribution), `scripts/check-16kb-alignment.sh` if it needs the new `.so` name, `docs/FORK_PATCHES.md` (settings row)
- Test: `lib/whisper/src/test/.../WhisperNativeContractTest.kt` (JVM: loads nothing; checks the Kotlin API contract with a fake), `scripts/check-16kb-alignment.sh` over `app/build/outputs/apk/debug/*.apk`

**Interfaces:**
- Produces: `object WhisperNative { fun load(modelPath: String, threads: Int): Long /* ctx handle, 0 on failure */; fun transcribe(ctx: Long, pcm16kMono: FloatArray, lang: String, translate: Boolean = false): String; fun free(ctx: Long) }`; library `libaikb_whisper.so` linked with `-Wl,-z,max-page-size=16384`, `-O3`, `GGML_NATIVE=OFF`, arm64 with `-march=armv8.2-a+fp16+dotprod` guarded by runtime feature check only if whisper.cpp's CMake supports it (otherwise plain armv8-a).

- [ ] **Step 1: Vendor** whisper.cpp: `git clone --depth 1 --branch v1.9.5 https://github.com/ggml-org/whisper.cpp /tmp/whisper-src`, record `git -C /tmp/whisper-src rev-parse HEAD` in `WHISPER_UPSTREAM.md`, copy the listed directories (no models, no examples, no bindings).
- [ ] **Step 2: Write** `WhisperNativeContractTest` (fake implementation of a `WhisperApi` interface that `WhisperNative` implements; asserts `free` after `load` failure is safe and empty PCM returns ""), run `$G ./gradlew :lib:whisper:test` — Expected: FAIL.
- [ ] **Step 3: Implement** the JNI bridge (`whisper_init_from_file_with_params`, `whisper_full` with `WHISPER_SAMPLING_GREEDY`, `no_timestamps = true`, `language = lang`, `single_segment = false`, concatenate segment texts) and `:app` dependency.
- [ ] **Step 4: Run** `$G ./gradlew :lib:whisper:test :app:assembleDebug && scripts/check-16kb-alignment.sh app/build/outputs/apk/debug/*.apk` — Expected: PASS, `libaikb_whisper.so` reported 16 KB aligned for every ABI. Record APK size delta in the commit message body.
- [ ] **Step 5: Commit** `feat(whisper): vendor whisper.cpp v1.9.5 with a 16 KB-aligned JNI bridge`.

