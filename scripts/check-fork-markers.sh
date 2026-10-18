#!/usr/bin/env bash
# Copyright (C) 2026 AI Keyboard contributors
# Licensed under the Apache License, Version 2.0
#
# Fork-marker check (TRD §2.1 "Edits to upstream files", §7.2).
#
# Compares HEAD with the upstream FlorisBoard tag named in UPSTREAM.md (UPSTREAM.md, docs/FORK_PATCHES.md
# and all file contents are read from HEAD, so commit before running). For every file that exists
# in the upstream tag:
#   - unchanged, or changed only by the global package rename
#     (dev.patrickgold.florisboard -> com.manjul.aikeyboard, in content and in the path): OK;
#   - modified: must be listed in docs/FORK_PATCHES.md (exact path or glob) AND every changed hunk
#     must carry a "FORK(" marker, unless the file matches a row of the "Marker-exempt files" table.
#     Hunks come from a zero-context diff of the normalised (rename-mapped) upstream file against
#     HEAD. A hunk that adds or changes lines needs a marker on one of those lines or on one of the
#     3 lines before it; a hunk that only deletes lines needs a marker within 3 lines of the deletion
#     point in the HEAD file. Binary files that differ need a marker-exempt row;
#   - deleted: must be listed in docs/FORK_PATCHES.md.
# Files we added are not checked.
#
# Path patterns in docs/FORK_PATCHES.md are the backticked tokens in the first column of each table
# row. They may use `*` (matches across `/`), `?`, `{a,b}` alternatives, and a trailing `/` for a whole
# directory. Patterns match the upstream path or the renamed (HEAD) path. Rows whose first cell
# contains "(global)" (the package-rename record) are not used for matching: rename-only files are
# detected by content instead, and anything beyond the rename needs its own row.
#
# Usage: scripts/check-fork-markers.sh            (run from anywhere inside the repo)
# The upstream tag must be available locally. In CI:
#   git fetch --no-tags --depth=1 https://github.com/florisboard/florisboard.git tag <tag>
# Requires: bash 3.2+, git, sed, grep, awk, cmp (Linux and macOS).

set -euo pipefail

ROOT="$(git rev-parse --show-toplevel)"
cd "$ROOT"

PATCHES_DOC="docs/FORK_PATCHES.md"
EXEMPT_HEADING="## Marker-exempt files"
OLD_PKG="dev.patrickgold.florisboard"
NEW_PKG="com.manjul.aikeyboard"
OLD_DIR="dev/patrickgold/florisboard"
NEW_DIR="com/manjul/aikeyboard"

TAG="$(git show HEAD:UPSTREAM.md | sed -n 's/^| *Tag *| *`\([^`]*\)` *|.*$/\1/p' | head -n 1)"
if [ -z "$TAG" ]; then
    echo "check-fork-markers: could not read the upstream tag from UPSTREAM.md" >&2
    exit 2
fi
if ! git rev-parse -q --verify "refs/tags/$TAG^{commit}" >/dev/null; then
    echo "check-fork-markers: tag $TAG is not available locally. Fetch it with:" >&2
    echo "  git fetch --no-tags --depth=1 https://github.com/florisboard/florisboard.git tag $TAG" >&2
    exit 2
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/fork-markers.XXXXXX")"
trap 'rm -f "$TMP"/*; rmdir "$TMP"' EXIT
git show "HEAD:$PATCHES_DOC" > "$TMP/patches.md" 2>/dev/null \
    || { echo "check-fork-markers: $PATCHES_DOC is missing at HEAD" >&2; exit 2; }

# Expands {a,b} alternatives (not nested), one result per line.
expand_braces() {
    local s="$1"
    case "$s" in
        *\{*\}*) ;;
        *) printf '%s\n' "$s"; return ;;
    esac
    local pre="${s%%\{*}"
    local rest="${s#*\{}"
    local body="${rest%%\}*}"
    local post="${rest#*\}}"
    local alt
    # Walk the comma-separated alternatives by hand so empty ones ("{,_round}") are kept.
    local remaining="$body,"
    while [ -n "$remaining" ]; do
        alt="${remaining%%,*}"
        remaining="${remaining#*,}"
        expand_braces "$pre$alt$post"
    done
}

# Emits the backticked patterns from the first column of the table rows of a markdown section.
# $1 = "main" (everything before the first "## " heading after the table, plus all other sections
#      except the exempt one) or "exempt" (only the exempt section).
extract_patterns() {
    local which="$1" in_exempt=0 line cell tok
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            "## "*)
                if [ "$line" = "$EXEMPT_HEADING" ] || [ "${line#"$EXEMPT_HEADING"}" != "$line" ]; then
                    in_exempt=1
                else
                    in_exempt=0
                fi
                continue
                ;;
            "|"*) ;;
            *) continue ;;
        esac
        if [ "$which" = "exempt" ] && [ "$in_exempt" -eq 0 ]; then continue; fi
        if [ "$which" = "main" ] && [ "$in_exempt" -eq 1 ]; then continue; fi
        cell="${line#|}"
        cell="${cell%%|*}"
        case "$cell" in *"(global)"*) continue ;; esac
        { printf '%s\n' "$cell" | grep -o '`[^`]*`' || true; } | tr -d '`' | while IFS= read -r tok; do
            if [ -n "$tok" ]; then expand_braces "$tok"; fi
        done
    done < "$TMP/patches.md"
}

extract_patterns main > "$TMP/listed"
extract_patterns exempt > "$TMP/exempt"

# Returns 0 if any of the paths in $2.. matches a pattern in file $1.
matches_any() {
    local list="$1" pat p
    shift
    while IFS= read -r pat; do
        [ -n "$pat" ] || continue
        for p in "$@"; do
            case "$pat" in
                */) [[ "$p" == "$pat"* ]] && return 0 ;;
                *) [[ "$p" == $pat ]] && return 0 ;;
            esac
        done
    done < "$list"
    return 1
}

map_path() {
    local p="$1"
    p="${p//$OLD_DIR/$NEW_DIR}"
    p="${p//$OLD_PKG/$NEW_PKG}"
    printf '%s' "$p"
}

blob_of() { git rev-parse -q --verify "$1:$2" 2>/dev/null || true; }

normalise() {
    sed -e "s#${OLD_DIR}#${NEW_DIR}#g" -e "s#${OLD_PKG//./\\.}#${NEW_PKG}#g"
}

# Lines (in the HEAD file) a FORK( marker may sit on to cover a hunk, before the hunk's first line.
MARKER_LOOKBACK=3

# Checks every changed hunk between $TMP/up (normalised upstream) and $TMP/head for a FORK( marker.
# Prints one FAIL line per uncovered hunk. $1 = HEAD path (for messages).
check_hunks() {
    local path="$1" rc=0 bad line
    git diff --no-index --no-color --no-ext-diff -U0 -- "$TMP/up" "$TMP/head" > "$TMP/diff" || rc=$?
    if [ "$rc" -gt 1 ]; then
        fail "$path: git diff failed (exit $rc)"
        return
    fi
    if ! grep -q '^@@ ' "$TMP/diff"; then
        # No text hunks although the contents differ: binary file (or a diff we cannot read).
        fail "$path: modified upstream file has no text hunks (binary?); add a marker-exempt row"
        return
    fi
    # Pass 1 reads the hunk headers ("@@ -a[,b] +c[,d] @@"), pass 2 the HEAD file.
    bad="$(grep '^@@ ' "$TMP/diff" | awk -v back="$MARKER_LOOKBACK" '
        FNR == NR {
            split($3, n, ",")
            c = substr(n[1], 2) + 0
            d = (2 in n) ? n[2] + 0 : 1
            h++
            if (d > 0) { lo[h] = c - back; hi[h] = c + d - 1; what[h] = "lines " c "-" (c + d - 1) }
            else       { lo[h] = c - back + 1; hi[h] = c + back; what[h] = "deletion after line " c }
            next
        }
        index($0, "FORK(") { m[FNR] = 1 }
        END {
            for (i = 1; i <= h; i++) {
                ok = 0
                for (l = lo[i]; l <= hi[i]; l++) if (l in m) { ok = 1; break }
                if (!ok) print what[i]
            }
        }' - "$TMP/head")"
    [ -n "$bad" ] || return 0
    while IFS= read -r line; do
        fail "$path: changed hunk without a FORK(<ticket>) marker ($line)"
    done <<EOF
$bad
EOF
}

git ls-tree -r --name-only "$TAG" > "$TMP/upstream_files"
# Paths that differ between the tag and HEAD (no rename detection: we map paths ourselves).
git diff --name-only --no-renames "$TAG" HEAD > "$TMP/changed"

errors=0
n_modified=0
n_rename_only=0
n_exempt=0
n_deleted=0
fail() { echo "FAIL: $*"; errors=$((errors + 1)); }

while IFS= read -r up; do
    grep -qxF -- "$up" "$TMP/changed" || continue   # unchanged at the same path
    up_blob="$(blob_of "$TAG" "$up")"
    head_path="$up"
    head_blob="$(blob_of HEAD "$up")"
    if [ -z "$head_blob" ]; then
        mapped="$(map_path "$up")"
        if [ "$mapped" != "$up" ]; then
            head_path="$mapped"
            head_blob="$(blob_of HEAD "$mapped")"
        fi
    fi

    if [ -z "$head_blob" ]; then
        n_deleted=$((n_deleted + 1))
        if ! matches_any "$TMP/listed" "$up" && ! matches_any "$TMP/exempt" "$up"; then
            fail "$up: deleted upstream file is not listed in $PATCHES_DOC"
        fi
        continue
    fi

    [ "$up_blob" = "$head_blob" ] && continue       # pure move by the rename
    git cat-file blob "$up_blob" | normalise > "$TMP/up"
    git cat-file blob "$head_blob" > "$TMP/head"
    if cmp -s "$TMP/up" "$TMP/head"; then
        n_rename_only=$((n_rename_only + 1))
        continue
    fi

    n_modified=$((n_modified + 1))
    if matches_any "$TMP/exempt" "$up" "$head_path"; then
        n_exempt=$((n_exempt + 1))
        continue
    fi
    if ! matches_any "$TMP/listed" "$up" "$head_path"; then
        fail "$head_path: modified upstream file is not listed in $PATCHES_DOC"
    fi
    check_hunks "$head_path"
done < "$TMP/upstream_files"

echo "check-fork-markers: upstream $TAG; $n_modified modified ($n_exempt marker-exempt), $n_rename_only rename-only, $n_deleted deleted"
if [ "$errors" -gt 0 ]; then
    echo "check-fork-markers: $errors problem(s). Add a row to $PATCHES_DOC and a FORK(<ticket>): reason marker on each edited hunk (TRD §2.1)."
    exit 1
fi
echo "check-fork-markers: OK"
