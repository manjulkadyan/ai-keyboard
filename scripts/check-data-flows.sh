#!/usr/bin/env bash
# Copyright (C) 2026 AI Keyboard contributors
# Licensed under the Apache License, Version 2.0
#
# Data safety diff check (S4 Task 12, TRD §11.6). Every ads, consent, Firebase, billing, ML Kit and Play
# artifact in the app's release runtime classpath must be named in docs/data-flows.md as `group:artifact`
# (in backticks). A new SDK, or a new artifact of a tracked SDK, then fails CI until its data flow is written down.
#
# Usage: scripts/check-data-flows.sh [dependency-tree.txt]
#   The tree comes from $1, else $DEPS_TREE, else `./gradlew :app:dependencies --configuration
#   releaseRuntimeClasspath`. The doc comes from $DATA_FLOWS_DOC, else docs/data-flows.md.
# Exit codes: 0 every tracked artifact is in the doc; 1 some are missing (each is printed);
#   2 no tracked artifact was found in the tree (a broken tree must not pass), or a file is missing.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
doc="${DATA_FLOWS_DOC:-$root/docs/data-flows.md}"
tree="${1:-${DEPS_TREE:-}}"

if [[ ! -f "$doc" ]]; then
  echo "check-data-flows: doc not found: $doc" >&2
  exit 2
fi

tmp=""
if [[ -z "$tree" ]]; then
  tmp="$(mktemp)"
  trap 'rm -f "$tmp"' EXIT
  (cd "$root" && ./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath) >"$tmp"
  tree="$tmp"
fi
if [[ ! -f "$tree" ]]; then
  echo "check-data-flows: dependency tree not found: $tree" >&2
  exit 2
fi

groups='com\.google\.android\.gms:play-services-ads|com\.google\.android\.ump|com\.google\.firebase'
groups+='|com\.android\.billingclient|com\.google\.mlkit|com\.google\.android\.play'
artifacts="$(grep -oE "($groups)[a-z0-9._-]*:[A-Za-z0-9._-]+" "$tree" | cut -d: -f1,2 | sort -u || true)"

if [[ -z "$artifacts" ]]; then
  echo "check-data-flows: no tracked SDK artifact in the dependency tree; is the tree complete?" >&2
  exit 2
fi

missing=0
while IFS= read -r artifact; do
  if ! grep -qF "\`$artifact\`" "$doc"; then
    echo "missing from $(basename "$doc"): $artifact"
    missing=$((missing + 1))
  fi
done <<<"$artifacts"

count="$(wc -l <<<"$artifacts" | tr -d ' ')"
if ((missing > 0)); then
  echo "check-data-flows: $missing of $count tracked artifacts have no data-flows row. Add each as \`group:artifact\`."
  exit 1
fi
echo "check-data-flows: all $count tracked artifacts are in $(basename "$doc")"
