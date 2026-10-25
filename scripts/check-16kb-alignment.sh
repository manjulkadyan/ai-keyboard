#!/usr/bin/env bash
# Copyright (C) 2026 AI Keyboard contributors
# Licensed under the Apache License, Version 2.0
#
# 16 KB page-size check for native libraries (TRD §1, §11.8).
#
# Rule (Google, https://developer.android.com/guide/practices/page-sizes): "all apps targeting Android 15
# (API level 35) and higher must support 16 KB memory page sizes on 64-bit devices on Google Play". The
# guide's check_elf_alignment.sh flags only arm64-v8a and x86_64 libraries, so this script does the same:
# a misaligned library under lib/arm64-v8a/ or lib/x86_64/ fails the check; a misaligned library of a
# 32-bit ABI (armeabi-v7a, x86) is printed as "info" and does not fail it (sprint 2 decision 14: ML Kit's
# prebuilt 32-bit libtranslate_jni.so and liblanguage_id_l2c_jni.so have 0x1000 LOAD alignment).
#
# For each APK given:
#   1. `zipalign -c -P 16 -v 4`: uncompressed .so entries must start on a 16 KB boundary in the zip;
#   2. every lib/<abi>/*.so must have all ELF LOAD segments aligned to >= 16 KB (0x4000).
# Checks every APK and fails if any 64-bit library is misaligned. With --min-libs N, each APK must also
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

# 64-bit ABIs are subject to the 16 KB rule; everything else (armeabi-v7a, x86) is informational.
# Takes an APK-relative path such as lib/arm64-v8a/libfoo.so.
is_64bit_lib() {
    case "$1" in
        lib/arm64-v8a/*|lib/x86_64/*) return 0 ;;
        *) return 1 ;;
    esac
}

if [ "${CHECK_16KB_ABI_FILTER_ONLY:-}" = "1" ]; then
    # Test hook: classify the given APK-relative paths and exit (no tools or APK needed): CHECK_16KB_ABI_FILTER_ONLY=1 $0 lib/x86/a.so ....
    for p in "$@"; do
        if is_64bit_lib "$p"; then echo "64 $p"; else echo "32 $p"; fi
    done
    exit 0
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
info=0
for apk in "$@"; do
    if [ ! -f "$apk" ]; then
        echo "FAIL: $apk: no such file"
        errors=$((errors + 1))
        continue
    fi
    echo "== $apk"

    if ! "$ZIPALIGN" -c -P 16 -v 4 "$apk" > "$TMP/zipalign.log" 2>&1; then
        grep -v ' (OK' "$TMP/zipalign.log" | grep -v '^Verifying' > "$TMP/zipalign.bad" || true
        cat "$TMP/zipalign.bad"
        # Only 32-bit lib entries misaligned: informational. Anything else (64-bit libs, other entries,
        # or a zipalign error with no entry lines) fails.
        if [ -s "$TMP/zipalign.bad" ] && ! grep -v '^Verification' "$TMP/zipalign.bad" \
                | grep -v -E '[[:space:]]lib/(armeabi-v7a|armeabi|x86)/[^[:space:]]+\.so[[:space:]]' | grep -q .; then
            echo "info: $apk: zipalign -c -P 16 found only 32-bit library entries not aligned (32-bit, not subject to the 16 KB rule)"
        else
            echo "FAIL: $apk: zipalign -c -P 16 found entries not aligned for 16 KB pages"
            errors=$((errors + 1))
        fi
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
        if [ -n "$bad" ] && is_64bit_lib "$rel"; then
            echo "FAIL: $rel: LOAD segment alignment $bad(need >= 0x4000)"
            errors=$((errors + 1))
        elif [ -n "$bad" ]; then
            echo "info $rel: LOAD segment alignment $bad(32-bit, not subject to the 16 KB rule)"
            info=$((info + 1))
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

if [ "$info" -gt 0 ]; then
    echo "check-16kb-alignment: $info unaligned 32-bit librar$( [ "$info" -eq 1 ] && echo y || echo ies) (informational, 64-bit only rule)"
fi
if [ "$errors" -gt 0 ]; then
    echo "check-16kb-alignment: $errors problem(s)"
    exit 1
fi
echo "check-16kb-alignment: OK"
