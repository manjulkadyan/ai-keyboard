# Fork patches

Every upstream FlorisBoard file we edit is listed here (TRD §2.1). Each edited hunk carries a
`FORK(<ticket>): reason` marker (`//` in Kotlin/Gradle, `#` in TOML/properties, `<!-- -->` in XML).

| File | Ticket | Why |
|---|---|---|
| `gradle/libs.versions.toml` | S0-build | The pinned jetpref snapshot `20251119T222500Z-SNAPSHOT` has expired from Sonatype. Pinned to the `0.3.0` release, which compiles against v0.5.2 unchanged |
