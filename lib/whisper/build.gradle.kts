/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :lib:whisper: vendored whisper.cpp (third_party/, see WHISPER_UPSTREAM.md) behind a small JNI bridge,
// libaikb_whisper.so. On-device, batch-only speech-to-text; models are downloaded, never bundled.

plugins {
    alias(libs.plugins.aikeyboard.android.library)
}

android {
    namespace = "com.manjul.aikeyboard.lib.whisper"
    ndkVersion = tools.versions.ndk.get()

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                targets("aikb_whisper")
                // 16 KB page alignment is set by the linker flags in CMakeLists.txt (NDK r26 does not default to it).
                arguments("-DANDROID_STL=c++_static")
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildTypes {
        // whisper.cpp is unusably slow at -O0, so the native code is always built optimised.
        debug {
            externalNativeBuild {
                cmake {
                    arguments("-DCMAKE_BUILD_TYPE=Release")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            version = tools.versions.cmake.get()
            path("src/main/cpp/CMakeLists.txt")
        }
    }
}
