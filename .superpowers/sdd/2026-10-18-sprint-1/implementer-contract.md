# Implementer contract (applies to every task)

Repo: /Users/manjul/StudioProjects/workspace/apk/ai-keyboard/app-android (FlorisBoard v0.5.2 fork, package com.manjul.aikeyboard). Branch `sprint-1`. Commit on it; NEVER push.

## Environment
- Run every Gradle call with:
  `JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/Users/manjul/StudioProjects/workspace/apk/ai-keyboard/.sandbox-tools/truststore.jks -Djavax.net.ssl.trustStorePassword=changeit" ./gradlew …` (the brief writes this as `$G`).
- Sandbox: writes to ~/.rustup blocked; huggingface.co blocked (HTTP 567); no emulator. Do not try `rm -rf` on home paths (hook blocks it) — report instead.
- zsh: quote globs (`--tests '*policy*'`).

## Project rules (Global Constraints, binding)
- Every edited upstream (FlorisBoard) file: `// FORK(S1-<topic>): reason` on or within 3 lines of each changed hunk, plus a row in `docs/FORK_PATCHES.md`. `scripts/check-fork-markers.sh` must pass. Files we create ourselves (new modules, `fork/` packages) need no markers.
- `:ime*` modules must not depend on `:core:ads`, `:core:billing`, `:feature:*`. No DI library.
- No user content (typed text, transcript, clipboard text, host package names) in any log, analytics event or exception message.
- `fallbackToDestructiveMigration`, `Process.killProcess`, `System.exit` banned.
- User-visible copy in strings.xml (`:app` → `values/strings_fork.xml`); English only; no concatenation.
- Never derive anything from the teardown APKs.
- Version-catalog additions go inside the `# --- AI Keyboard ---` blocks of `gradle/libs.versions.toml`.
- minSdk 26, compile/target 36.

## Process
1. Read your brief (exact values to use verbatim). Ask (status NEEDS_CONTEXT) if something is genuinely ambiguous; otherwise make the reasonable choice and note it.
2. TDD: write failing tests, run them and capture the RED output, implement, run GREEN.
3. While iterating run focused tests; run the brief's full verification command once before committing.
4. One commit for the task, message from the brief, ending with the line:
   `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
5. Self-review your own diff (completeness, YAGNI, existing patterns, pristine test output).

## You do not dispatch subagents
Do all the work yourself. Never spawn helpers or reviewers; the controller reviews after you report.

## Report
Write the full report to the report path given in the dispatch: what you implemented, tests + results, TDD evidence (RED command + relevant failing output and why expected; GREEN command + passing output), files changed, self-review findings, concerns, and any decisions you made where the brief left a choice.
Then reply with ONLY (< 15 lines): Status (DONE | DONE_WITH_CONCERNS | BLOCKED | NEEDS_CONTEXT), commits (short SHA + subject), one-line test summary, concerns, report path. If BLOCKED/NEEDS_CONTEXT put specifics in the reply.

## After review findings
If resumed with findings: fix, re-run covering tests, append a fix report (changes, tests, command, output) to the report file, amend nothing — add a new commit `fix(<scope>): address review for task N`, reply with the same short contract.
