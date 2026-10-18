# Fork patches

Every upstream FlorisBoard file we edit is listed here (TRD §2.1). Each edited hunk carries a
`FORK(<ticket>): reason` marker (`//` in Kotlin/Gradle, `#` in TOML/properties, `<!-- -->` in XML).

| File | Ticket | Why |
|---|---|---|
| `gradle/libs.versions.toml` | S0-build | The pinned jetpref snapshot `20251119T222500Z-SNAPSHOT` has expired from Sonatype. Pinned to the `0.3.0` release, which compiles against v0.5.2 unchanged |
| *(global)* `app/**`, `benchmark/**`, `lib/android/.../PackageManager.kt`, `app/schemas/*`, `app/src/main/baseline-prof.txt`, `utils/generate_spellcheck_config.py` | S0-rename | Global package rename `dev.patrickgold.florisboard` -> `com.manjul.aikeyboard` (TRD §2.1 "Package and branding"). Source dirs and Room schema dirs moved with `git mv`; package declarations, imports, `namespace`/`applicationId`, manifest components, `method.xml`/`spellchecker.xml` settings activity, baseline profile and string constants rewritten mechanically. Recorded once here instead of per hunk; the NOTICE file carries the Apache-2.0 §4(b) change statement |

## Decisions

- The `org.florisboard.lib.*` library module namespaces (`:lib:android`, `:lib:color`, `:lib:compose`,
  `:lib:kotlin`, `:lib:native`, `:lib:snygg`) are **not** renamed. They are internal module packages,
  not user-visible branding, and renaming them would make every upstream merge touch every file.
- FlorisBoard extension IDs inside assets (e.g. `org.florisboard.layouts`, `org.florisboard.themes`)
  are **not** renamed. They are data identifiers stored in user preferences and referenced across
  extension JSON; renaming them would break upstream-compatible extensions and merges.
- Third-party library coordinates `dev.patrickgold.jetpref` and `dev.patrickgold.compose.tooltip`
  are external dependencies and stay as they are.
