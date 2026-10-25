/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 * Modified by AI Keyboard contributors, 2026. FORK(S0-licences): Apache-2.0 §4(b) change notice
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// FORK(S0-branding): fork project name (Apache-2.0 §6)
rootProject.name = "AiKeyboard"

pluginManagement {
    // FORK(S0-modules): convention plugins (aikeyboard.*) live in the build-logic included build
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Uncomment the following if testing snapshots from Maven Central
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        // Uncomment the following if testing snapshots from Maven Local
        // mavenLocal()
    }

    versionCatalogs {
        create("tools") {
            from(files("gradle/tools.versions.toml"))
        }
    }
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
//include(":benchmark")
include(":lib:android")
include(":lib:color")
include(":lib:compose")
include(":lib:kotlin")
include(":lib:native")
include(":lib:snygg")

// FORK(S0-modules): AI Keyboard modules (TRD §2.2). The upstream IME stays in :app for now.
include(":core:common")
// FORK(S0-ads): app-process-only ads module (consent, placement registry, caps); never used by the IME
include(":core:ads")
include(":ime:policy")
// FORK(S1-policy): feature settings store (DataStore)
include(":core:datastore")
// FORK(S1-clipboard): clipboard sensitivity classifier
include(":core:privacy")
// FORK(S1-analytics): event catalog and batched consent-aware relay
include(":core:analytics")
// FORK(S1-voice): speech session state machine, error policy, spoken punctuation
include(":core:voice")
// FORK(S1-voice): voice typing inside the keyboard (controller, composing span, panel)
include(":ime:voice-ui")
// FORK(S1-whisper): vendored whisper.cpp + JNI bridge for on-device speech-to-text
include(":lib:whisper")
// FORK(S2-translate): engine-agnostic translation core (router, LRU, chunking, bar pipeline), pure JVM
include(":core:translate")
// FORK(S2-mlkit): ML Kit translation engine and language ID behind :core:translate, initialised on first use
include(":core:translate-mlkit")
// FORK(S2-bar): composing-span helpers shared by voice and the translate bar, pure JVM
include(":ime:editing")

// FORK(S0-hardening): custom lint checks (TRD §2.3 "Process termination"), applied to :app via lintChecks
include(":lint-rules")
