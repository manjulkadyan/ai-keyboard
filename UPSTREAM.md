# Upstream

This project is a fork of [FlorisBoard](https://github.com/florisboard/florisboard) (Apache-2.0).
The `LICENSE` file and upstream copyright headers are kept; `NOTICE` carries the FlorisBoard attribution
and the statement of our changes (including the package rename `dev.patrickgold.florisboard` ->
`com.manjul.aikeyboard`). See TRD §2.1 for the fork rules.

| Field | Value |
|---|---|
| Upstream repo | https://github.com/florisboard/florisboard |
| Tag | `v0.5.2` |
| Commit | `2e82060251897226c0739b9f52d1d051b02305fb` |
| Commit date | 2025-11-28 |
| Imported | 2026-10-18 |

## Branches

- `upstream-mirror`: tracks upstream tags only, no local commits.
- `main`: our product.
- To take a new upstream release: fast-forward `upstream-mirror` to the new tag, merge it into a
  `sync/<tag>` branch, then open a PR to `main`.

Every edited upstream file is listed in `docs/FORK_PATCHES.md`, and each edited hunk carries a
`// FORK(<ticket>): reason` marker.
