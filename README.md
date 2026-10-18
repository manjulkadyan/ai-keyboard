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

## Building

```
./gradlew :app:assembleDebug
```

The native library needs the Rust Android targets (for example `aarch64-linux-android`) installed
through `rustup`, and the Android NDK version pinned in `gradle/tools.versions.toml`.

## Licence

Apache License, Version 2.0. See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
