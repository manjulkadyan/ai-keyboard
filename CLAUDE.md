# CLAUDE.md

This file gives guidance to Claude Code (claude.ai/code) when it works with code in this repository.

## Project

An Android AI voice and translate keyboard (IME) plus its settings app. "AI Keyboard" is a placeholder name.
It is a fork of FlorisBoard `v0.5.2` (Apache-2.0). `UPSTREAM.md` records the exact tag and commit.
The package was renamed from `dev.patrickgold.florisboard` to `com.manjul.aikeyboard`.
Upstream class names (`FlorisImeService`, `Floris*`) and `org.florisboard.lib.*` namespaces are kept on purpose.

## Rules for upstream code

- Put new behaviour in our own modules. Wire it into upstream code through small, marked hooks.
- Every edited upstream hunk needs a `FORK(<ticket>): reason` marker on or within 3 lines of the edit.
- Use `//` in Kotlin and Gradle, `#` in TOML and properties, `<!-- -->` in XML.
- Every edited or deleted upstream file needs a row in `docs/FORK_PATCHES.md`.
- Substantive edits also add a "Modified by AI Keyboard contributors, 2026" header line.
- Our own entries in `gradle/libs.versions.toml` go inside the `# --- AI Keyboard ---` blocks.
- Run `scripts/check-fork-markers.sh` before a PR. It reads from HEAD, so commit first.
- It needs the upstream tag locally: `git fetch --no-tags --depth=1 https://github.com/florisboard/florisboard.git tag v0.5.2`.
- Branches: `upstream-mirror` tracks upstream tags only. Upstream syncs go through a `sync/<tag>` branch into `main`.

## Build and test

Toolchain versions are pinned in `gradle/tools.versions.toml` (JDK 17, NDK, CMake, build-tools, Rust).
The native build needs the pinned Rust toolchain plus these `rustup` targets:
`aarch64-linux-android`, `armv7-linux-androideabi`, `i686-linux-android`, `x86_64-linux-android`.
`local.properties` only needs `sdk.dir`.

CI (`.github/workflows/pr.yml`) first fetches the upstream tag, runs `scripts/check-fork-markers.sh` and validates
the Gradle wrapper. Then it runs:

```
./gradlew assembleDebug
./gradlew test testDebugUnitTest :build-logic:convention:test
./gradlew check :app:lintDebug
scripts/check-16kb-alignment.sh --min-libs 4 app/build/outputs/apk/debug/app-debug.apk
```

Single module and single class:

```
./gradlew :ime:policy:test                  # JVM module (aikeyboard.jvm.library)
./gradlew :core:voice:testDebugUnitTest     # Android module
./gradlew :app:testDebugUnitTest --tests 'com.manjul.aikeyboard.isolation.ImeAdIsolationTest'
```

Rebuild the bundled dictionaries (`app/src/main/assets/dict/<lang>.sdict`) from `third_party/wordlists`:

```
./gradlew :tools:dictbuild:run --args="$PWD/third_party/wordlists $PWD/app/src/main/assets/dict"
```

- `:tools:dictbuild:test` reads back the committed `.sdict` files and checks their size budget and licence rows.
- `check` on `:app` runs `verify<Variant>NoAdAutoInit` and `verify<Variant>ExportedComponents` on the merged manifest.
- `:app:lintDebug` gates only on issues missing from `app/lint-baseline.xml`.
- Robolectric tests resolve the android-all jar through `gradle/robolectric-offline.gradle.kts`.
- Every 64-bit `.so` (arm64-v8a, x86_64) must be 16 KB page aligned. Misaligned 32-bit libraries are reported only.
- CI requires `libfl_native.so` in all four ABIs and `libaikb_whisper.so` in arm64-v8a, armeabi-v7a and x86_64.
- AI and Firebase settings come from Gradle properties `aikb.ai.baseUrl`, `aikb.ai.devAuth` and `aikb.firebase.*`.
- They default to empty. `AI_DEV_AUTH` is forced off in beta and release.

## Architecture

- `:app` holds the upstream FlorisBoard app and the whole IME. The keyboard is not split out yet.
- The IME entry point is `app/src/main/kotlin/com/manjul/aikeyboard/FlorisImeService.kt`.
- App-scoped objects are lazy fields on `FlorisApplication`. There is no DI library.
- Service-scoped objects are created in `FlorisImeService.onCreate`.
- They are `ImePolicyController`, `VoiceImeSession`, `TranslateImeSession` and `AiImeSession`.
- Fork glue code inside `:app` lives in `com.manjul.aikeyboard.fork.*`.
- Examples are `ai`, `policy`, `processtext` (PROCESS_TEXT and share sheet), `smartbar`, `suggest`, `translate`, `translator` and `voice`.
- `:core:common`: dispatchers and the `Outcome` result type.
- `:ime:policy`: `SensitiveFieldPolicy` contract, pure Kotlin.
- `:core:datastore`: feature settings store (consent, policy inputs, chip order, voice, translate, suggestion and AI settings).
- `:core:privacy`: clipboard sensitivity classifier, pure Kotlin.
- `:core:analytics`: event catalog and consent-aware batching relay. It depends on `:ime:policy`.
- `:core:voice`: speech session state machine, platform recognizer, engine selection and Whisper model manager.
- `:core:voice` depends on `:lib:whisper`, `:core:datastore` and `:core:analytics`.
- `:ime:editing`: pure Kotlin composing-span helpers, own-write undo and the hash-checked `SafeReplacer`.
- `:ime:voice-ui`: Compose voice panel and controllers. It depends on `:core:voice`, `:core:analytics`, `:ime:policy` and `:ime:editing`.
- `:lib:whisper`: vendored whisper.cpp (see `third_party/WHISPER_UPSTREAM.md`) with a JNI bridge built by CMake.
- The Whisper model is downloaded by `ModelDownloadWorker` and never bundled. Files are SHA-256 pinned in `WhisperModelSpec`.
- `WHISPER_ENABLED_LANGS` is `"*"` in debug and beta and empty in release.
- `:core:translate`: pure Kotlin translation core (engine interface, router, LRU, chunking, translator pool, bar pipeline).
- `:core:translate-mlkit`: ML Kit Translation and Language ID behind `:core:translate`.
- It depends on `:core:translate`, `:core:datastore` and `:core:analytics`.
- `:ime:translate-ui`: in-keyboard Translate bar. It depends on `:core:translate`, `:core:analytics`, `:core:datastore`, `:ime:policy` and `:ime:editing`.
- `:core:database`: Room database for Translator history and the user dictionary. It depends on `:core:suggest`.
- `:core:tts`: one shared, ref-counted `TextToSpeech` per process.
- `:core:suggest`: pure Kotlin suggestion engine (dictionary format, matching, ranking and autocorrect).
- `:tools:dictbuild`: JVM tool that builds the `.sdict` dictionaries. It is never packaged in the APK.
- `:core:config`: `RemoteConfig` interface with compiled-in defaults only. Pure Kotlin.
- `:core:ai`: pure Kotlin kb-api client. `AiClient` streams `/v1/rewrite` and `/v1/polish` over SSE. It depends on `:core:config`.
- `:core:ai` defines `AiEngine`. `CloudAiEngine` is the only engine (see `docs/decisions/od-20-gemini-nano.md`).
- `:core:network`: the one `OkHttpClient` factory and the kb-api auth header interceptor. It depends on `:core:ai`.
- `:core:firebase`: App Check and anonymous Auth tokens for kb-api. It depends on `:core:ai`.
- `:ime:ai-ui`: AI rewrite sheet, quota card and report sheet. It depends on `:core:ai`, `:core:config`, `:core:analytics`, `:ime:policy` and `:ime:editing`.
- `fork/ai/AiAppGraph` builds the AI objects lazily. kb-api is our backend and is not in this repo.
- `:core:ads`: ads skeleton for the app process only. Nothing in `:app` calls it yet.
- `:lib:*` (android, color, compose, kotlin, native, snygg) are upstream libraries.
- `:lib:native` is upstream Rust code built through CMake. `libnative/dummy` is an upstream Cargo crate, not a Gradle module.
- `:benchmark` is commented out in `settings.gradle.kts`.

## Conventions

- Convention plugins live in the `build-logic` included build: `aikeyboard.android.library`,
  `aikeyboard.android.library.compose`, `aikeyboard.jvm.library`, `aikeyboard.module-rules`, `aikeyboard.ad-isolation`.
- New modules must apply one of the library plugins. They read SDK levels from `gradle.properties`.
- SDK levels: minSdk 26, compile and target 36. Library plugins set Java and Kotlin JVM target 11.
- Tests use JUnit 5. Robolectric tests run on JUnit 4 through the vintage engine.
- `aikeyboard.module-rules` forbids any path from `:ime` or `:ime:*` to `:core:ads`, `:core:billing` or `:feature:*`.
- It also forbids a `:feature:*` module to depend on another `:feature:*` module. A violation fails the build.
- `ImeAdIsolationTest` keeps ad code out of IME classes inside `:app`. Only `com.manjul.aikeyboard.app.ads` can use ads.
- `:lint-rules` adds `ImeProcessTermination`, which bans `Process.killProcess` and `System.exit`.
- `fallbackToDestructiveMigration` is banned. `DestructiveMigrationBanTest` enforces it in `:core:database`.
- Room schemas are exported to `core/database/schemas/`. Every version change needs a `Migration`.
- ML Kit and Firebase start on first use. Their init providers are removed from the merged manifest.
- The device sends AI requests only to kb-api (`BuildConfig.AI_BASE_URL`). It never calls the AI vendor directly.
- No vendor API key goes in the APK, the repo or test fixtures.
- Nothing goes to kb-api until `consentAiAt` is set on the app's consent screen (`fork/ai/AiConsentActivity`).
- Auto-polish also needs `consentAutoPolishAt` and Pro.
- AI and Polish need `allowAi && allowCloud` from the field decision. The chip hides and the controller re-checks before sending.
- Nothing is learned when the field decision disallows learning or the keyboard is in incognito.
- AI output replaces host text only through `SafeReplacer`, after the captured range matches its SHA-256.
- Never put typed text, suggestions, transcripts, AI input or output, clipboard text or host package names in logs, analytics or exceptions.
- Add a row to `docs/data-flows.md` whenever a feature sends or stores data.
- Fork strings for `:app` go in `values/strings_fork.xml`. Other modules use their own `strings.xml`.
- Code style: Kotlin official, 4 space indent, 2 for JSON, YAML and shell, max line length 120.
- Never use star imports (`.editorconfig` sets the threshold to 99).
- Docs: `docs/plans/` holds sprint plans, `docs/decisions/` holds decisions.
- `docs/sprint-<n>-device-checklist.md` holds manual device checks.
