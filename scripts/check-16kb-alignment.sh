#!/usr/bin/env bash
# Copyright (C) 2026 AI Keyboard contributors
# Licensed under the Apache License, Version 2.0
#
# 16 KB page-size check for native libraries (TRD §1, §11.8).
#
# For each APK given:
#   1. `zipalign -c -P 16 -v 4`: uncompressed .so entries must start on a 16 KB boundary in the zip;
#   2. every lib/<abi>/*.so must have all ELF LOAD segments aligned to >= 16 KB (0x4000).
# Checks every APK and fails if any library is misaligned. With --min-libs N, each APK must also
# contain at least N native libraries, so a build that silently dropped its .so files does not pass;
# without it an APK with no .so files passes. unzip errors fail the check (except "no matching files").
#
# Usage: scripts/check-16kb-alignment.sh [--min-libs N] app/build/outputs/apk/debug/app-debug.apk [...]
# Tools: zipalign from build-tools (version from gradle/tools.versions.toml) and llvm-readelf from the
# NDK (version from gradle/tools.versions.toml), located via ANDROID_HOME / ANDROID_SDK_ROOT or the
# sdk.dir in local.properties. Override with ZIPALIGN=... and READELF=... if needed.
# Requires: bash 3.2+, unzip, awk (Linux and macOS).

set -euo pipefail

usage() {
    echo "usage: $0 [--min-libs N] <apk> [<apk>...]" >&2
    exit 2
}

MIN_LIBS=0
while [ "$#" -gt 0 ]; do
    case "$1" in
        --min-libs)
            [ "$#" -ge 2 ] || usage
            MIN_LIBS="$2"
            shift 2
            ;;
        --min-libs=*)
            MIN_LIBS="${1#--min-libs=}"
            shift
            ;;
        --)
            shift
            break
            ;;
        -*) usage ;;
        *) break ;;
    esac
done
case "$MIN_LIBS" in
    ''|*[!0-9]*) echo "check-16kb-alignment: --min-libs needs a non-negative integer, got '$MIN_LIBS'" >&2; exit 2 ;;
esac
if [ "$#" -eq 0 ]; then
    usage
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_TOML="$ROOT/gradle/tools.versions.toml"

toml_version() {
    sed -n "s/^$1 *= *\"\\([^\"]*\\)\".*/\\1/p" "$TOOLS_TOML" | head -n 1
}

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -f "$ROOT/local.properties" ]; then
    SDK="$(sed -n 's/^sdk\.dir=//p' "$ROOT/local.properties" | head -n 1)"
fi

if [ -z "${ZIPALIGN:-}" ]; then
    ZIPALIGN="$SDK/build-tools/$(toml_version buildTools)/zipalign"
fi
if [ -z "${READELF:-}" ]; then
    READELF="$(ls "$SDK/ndk/$(toml_version ndk)"/toolchains/llvm/prebuilt/*/bin/llvm-readelf 2>/dev/null | head -n 1 || true)"
fi
for tool in "$ZIPALIGN" "$READELF"; do
    if [ -z "$tool" ] || [ ! -x "$tool" ]; then
        echo "check-16kb-alignment: tool not found: '${tool}' (set ANDROID_HOME, or ZIPALIGN / READELF)" >&2
        exit 2
    fi
done

TMP="$(mktemp -d "${TMPDIR:-/tmp}/align16k.XXXXXX")"
cleanup() {
    find "$TMP" -depth -mindepth 1 \( -type f -exec rm -f {} + \) -o \( -type d -exec rmdir {} + \) 2>/dev/null || true
    rmdir "$TMP" 2>/dev/null || true
}
trap cleanup EXIT

errors=0
for apk in "$@"; do
    if [ ! -f "$apk" ]; then
        echo "FAIL: $apk: no such file"
        errors=$((errors + 1))
        continue
    fi
    echo "== $apk"

    if ! "$ZIPALIGN" -c -P 16 -v 4 "$apk" > "$TMP/zipalign.log" 2>&1; then
        grep -v ' (OK' "$TMP/zipalign.log" | grep -v '^Verifying' || true
        echo "FAIL: $apk: zipalign -c -P 16 found entries not aligned for 16 KB pages"
        errors=$((errors + 1))
    fi

    out="$TMP/$(basename "$apk").d"
    mkdir -p "$out"
    # Info-ZIP unzip exits 11 when no entry matches (an APK without native libraries); any other
    # non-zero status is a real error (corrupt APK, disk full, ...).
    rc=0
    unzip -q -o "$apk" 'lib/*.so' -d "$out" > "$TMP/unzip.log" 2>&1 || rc=$?
    if [ "$rc" -ne 0 ] && [ "$rc" -ne 11 ]; then
        cat "$TMP/unzip.log"
        echo "FAIL: $apk: unzip failed (exit $rc)"
        errors=$((errors + 1))
        continue
    fi
    count=0
    while IFS= read -r so; do
        [ -n "$so" ] || continue
        count=$((count + 1))
        rel="${so#"$out"/}"
        # Program headers, wide: the last column of each LOAD line is the alignment (hex).
        bad="$("$READELF" -lW "$so" | awk '
            $1 == "LOAD" { n++; a = strtonum_hex($NF); if (a < 16384) { printf "%s ", $NF } }
            END { if (n == 0) printf "no-LOAD-segments" }
            function strtonum_hex(s,   i, c, v) {
                sub(/^0x/, "", s); v = 0
                for (i = 1; i <= length(s); i++) {
                    c = index("0123456789abcdef", tolower(substr(s, i, 1))) - 1
                    if (c < 0) return 0
                    v = v * 16 + c
                }
                return v
            }')"
        if [ -n "$bad" ]; then
            echo "FAIL: $rel: LOAD segment alignment $bad(need >= 0x4000)"
            errors=$((errors + 1))
        else
            echo "ok   $rel"
        fi
    done <<EOF
$(find "$out" -type f -name '*.so' | sort)
EOF
    echo "   $count native librar$( [ "$count" -eq 1 ] && echo y || echo ies) checked"
    if [ "$count" -lt "$MIN_LIBS" ]; then
        echo "FAIL: $apk: $count native librar$( [ "$count" -eq 1 ] && echo y || echo ies) found, expected at least $MIN_LIBS"
        errors=$((errors + 1))
    fi
done

if [ "$errors" -gt 0 ]; then
    echo "check-16kb-alignment: $errors problem(s)"
    exit 1
fi
echo "check-16kb-alignment: OK"
