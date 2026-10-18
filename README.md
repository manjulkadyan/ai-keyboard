# AI Keyboard (Android)

"AI Keyboard" is a placeholder name. This repository holds the Android app for an AI voice and
translate keyboard: an input method (IME) plus its settings app.

## Based on FlorisBoard

The keyboard is a fork of [FlorisBoard](https://github.com/florisboard/florisboard), an open-source
Android keyboard by The FlorisBoard Contributors, licensed under the
[Apache License, Version 2.0](LICENSE). This project is not affiliated with or endorsed by the
FlorisBoard project.

- [`UPSTREAM.md`](UPSTREAM.md): which upstream tag and commit we forked, and how upstream releases are merged.
- [`docs/FORK_PATCHES.md`](docs/FORK_PATCHES.md): every upstream file we changed, and why.
- [`NOTICE`](NOTICE): attribution and the statement of changes required by Apache-2.0 §4.

Our own code lives in separate Gradle modules so that most upstream files stay unedited. Edits to
upstream files carry a `FORK(<ticket>): reason` marker.

## Modules

- `build-logic/`: convention plugins (`aikeyboard.android.library`, `aikeyboard.android.library.compose`,
  `aikeyboard.jvm.library`, `aikeyboard.module-rules`).
- `:app`: upstream FlorisBoard app and IME (the keyboard is not split into its own module yet).
- `:core:common`: dispatchers and the `Outcome` result type.
- `:ime:policy`: `SensitiveFieldPolicy` contract (pure Kotlin).
- `:lib:*`: upstream FlorisBoard libraries.

The build fails if an `:ime*` module depends on `:core:ads`, `:core:billing` or a `:feature:*` module,
or if one `:feature:*` module depends on another. Because the IME still lives in `:app`, that rule does
not by itself keep ads out of the keyboard; a class-reference test added with `:core:ads` covers that.
See [`docs/FORK_PATCHES.md`](docs/FORK_PATCHES.md#module-dependency-rules).

## Building

```
./gradlew :app:assembleDebug
./gradlew :build-logic:convention:test :core:common:testDebugUnitTest :ime:policy:test
```

The native library needs the Rust Android targets (for example `aarch64-linux-android`) installed
through `rustup`, and the Android NDK version pinned in `gradle/tools.versions.toml`.

## Licence

Apache License, Version 2.0. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
