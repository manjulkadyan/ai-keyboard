#!/usr/bin/env bash
# Copyright (C) 2026 AI Keyboard contributors
# Licensed under the Apache License, Version 2.0
#
# 16 KB page-size check for native libraries (TRD §1, §11.8).
#
# For each APK given:
#   1. `zipalign -c -P 16 -v 4`: uncompressed .so entries must start on a 16 KB boundary in the zip;
#   2. every lib/<abi>/*.so must have all ELF LOAD segments aligned to >= 16 KB (0x4000).
# Fails on the first APK with any misaligned library. An APK without .so files passes.
#
# Usage: scripts/check-16kb-alignment.sh app/build/outputs/apk/debug/app-debug.apk [...]
# Tools: zipalign from build-tools (version from gradle/tools.versions.toml) and llvm-readelf from the
# NDK (version from gradle/tools.versions.toml), located via ANDROID_HOME / ANDROID_SDK_ROOT or the
# sdk.dir in local.properties. Override with ZIPALIGN=... and READELF=... if needed.
# Requires: bash 3.2+, unzip, awk (Linux and macOS).

set -euo pipefail

if [ "$#" -eq 0 ]; then
    echo "usage: $0 <apk> [<apk>...]" >&2
    exit 2
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
    unzip -q -o "$apk" 'lib/*.so' -d "$out" 2>/dev/null || true
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
done

if [ "$errors" -gt 0 ]; then
    echo "check-16kb-alignment: $errors problem(s)"
    exit 1
fi
echo "check-16kb-alignment: OK"
