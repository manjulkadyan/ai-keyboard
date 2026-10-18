# Copyright (C) 2026 AI Keyboard contributors
# Licensed under the Apache License, Version 2.0
#
# libaikb_whisper.so: the JNI bridge with whisper.cpp and ggml (CPU backend) linked in statically.

cmake_minimum_required(VERSION 3.22)
project(aikb_whisper C CXX)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/whisper.cpp)

# whisper.cpp / ggml options: one static CPU-only build, no tests/examples/server.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
# ggml's own thread pool; OpenMP would need libomp.so packaged next to us.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
# arm64 stays on the armv8-a baseline (+ the NEON every arm64 core has). armv8.2-a+fp16+dotprod would
# need runtime CPU dispatch, which ggml only offers as GGML_CPU_ALL_VARIANTS with dlopen'ed backend
# libraries (GGML_BACKEND_DL); a single .so compiled for v8.2 would SIGILL on Cortex-A53/A73 phones.
set(GGML_CPU_ARM_ARCH "" CACHE STRING "" FORCE)

add_subdirectory(${WHISPER_DIR} whisper.cpp EXCLUDE_FROM_ALL)

foreach(lib ggml ggml-base ggml-cpu whisper)
    target_compile_options(${lib} PRIVATE -O3 -ffunction-sections -fdata-sections)
endforeach()

add_library(aikb_whisper SHARED whisper_jni.cpp)
target_compile_options(aikb_whisper PRIVATE -O3 -fvisibility=hidden -Wall -Wextra)
target_link_libraries(aikb_whisper PRIVATE whisper ggml)
target_link_options(aikb_whisper PRIVATE
    # 16 KB page sizes (Android 15+, TRD §11.8): every LOAD segment aligned to 16 KB.
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
)
