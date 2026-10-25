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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.agp.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mikepenz.aboutlibraries)
    // FORK(S0-ads): verify<Variant>NoAdAutoInit fails `check` if an ad/consent SDK provider is merged (TRD §11.1);
    // verify<Variant>ExportedComponents applies the exported-component allow-list to the merged manifest (TRD §6.3)
    alias(libs.plugins.aikeyboard.ad.isolation)
}

val projectMinSdk: String by project
val projectTargetSdk: String by project
val projectCompileSdk: String by project
val projectVersionCode: String by project
val projectVersionName: String by project
val projectVersionNameSuffix = projectVersionName.substringAfter("-", "").let { suffix ->
    if (suffix.isNotEmpty()) {
        "-$suffix"
    } else {
        suffix
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs.set(listOf(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-Xjvm-default=all-compatibility",
            "-Xwhen-guards",
        ))
    }
}

android {
    namespace = "com.manjul.aikeyboard"
    compileSdk = projectCompileSdk.toInt()
    buildToolsVersion = tools.versions.buildTools.get()
    ndkVersion = tools.versions.ndk.get()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }

    defaultConfig {
        applicationId = "com.manjul.aikeyboard"
        minSdk = projectMinSdk.toInt()
        targetSdk = projectTargetSdk.toInt()
        versionCode = projectVersionCode.toInt()
        versionName = projectVersionName.substringBefore("-")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BUILD_COMMIT_HASH", "\"${getGitCommitHash().get()}\"")
        buildConfigField("String", "FLADDONS_API_VERSION", "\"v~draft2\"")
        buildConfigField("String", "FLADDONS_STORE_URL", "\"beta.addons.florisboard.org\"")
        // FORK(S1-whisper): Whisper model host (TODO(OD-01): placeholder until the product host exists; the files
        // are SHA-256 pinned in WhisperModelSpec) and the languages Whisper may serve ("*" = all; release: none)
        buildConfigField("String", "WHISPER_MODEL_BASE_URL", "\"https://example.com/models/\"")
        buildConfigField("String", "WHISPER_ENABLED_LANGS", "\"*\"")
        // FORK(S1-privacy-logs): info/debug Flog lines carry key labels, spell-check text and editor info, so they
        // are off unless a developer opts in locally with -Paikb.verboseFlog=true (never on CI or for testers)
        buildConfigField("boolean", "VERBOSE_FLOG", (project.findProperty("aikb.verboseFlog") == "true").toString())

        sourceSets {
            maybeCreate("main").apply {
                assets {
                    srcDirs("src/main/assets")
                }
                java {
                    srcDirs("src/main/kotlin")
                }
            }
            // FORK(S2-translate): the devtools latency probe reads the instrumented test's corpus (debug APK only)
            maybeCreate("debug").assets.srcDir(rootProject.file("core/translate-mlkit/src/androidTest/assets"))
        }
    }

    bundle {
        language {
            // We disable language split because FlorisBoard does not use
            // runtime Google Play Service APIs and thus cannot dynamically
            // request to download the language resources for a specific locale.
            enableSplit = false
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        named("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug+${getGitCommitHash(short = true).get()}"

            isDebuggable = true
            isJniDebuggable = false

            resValue("mipmap", "floris_app_icon", "@mipmap/ic_app_icon_debug")
            resValue("mipmap", "floris_app_icon_round", "@mipmap/ic_app_icon_debug_round")
            // FORK(S0-branding): placeholder app name and neutral launcher glyph instead of FlorisBoard branding
            resValue("drawable", "floris_app_icon_foreground", "@drawable/ic_app_icon_keyboard_foreground")
            resValue("string", "floris_app_name", "AI Keyboard Debug")
        }

        create("beta") {
            applicationIdSuffix = ".beta"
            versionNameSuffix = projectVersionNameSuffix

            // FORK(S0-hardening): strip Log.v/d/i and flog calls from minified builds (TRD §6.3)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", "proguard-hardening.pro")
            isMinifyEnabled = true
            isShrinkResources = true

            resValue("mipmap", "floris_app_icon", "@mipmap/ic_app_icon_beta")
            resValue("mipmap", "floris_app_icon_round", "@mipmap/ic_app_icon_beta_round")
            // FORK(S0-branding): placeholder app name and neutral launcher glyph instead of FlorisBoard branding
            resValue("drawable", "floris_app_icon_foreground", "@drawable/ic_app_icon_keyboard_foreground")
            resValue("string", "floris_app_name", "AI Keyboard Beta")
        }

        named("release") {
            versionNameSuffix = projectVersionNameSuffix

            // FORK(S0-hardening): strip Log.v/d/i and flog calls from minified builds (TRD §6.3)
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro", "proguard-hardening.pro")
            isMinifyEnabled = true
            isShrinkResources = true

            resValue("mipmap", "floris_app_icon", "@mipmap/ic_app_icon_stable")
            resValue("mipmap", "floris_app_icon_round", "@mipmap/ic_app_icon_stable_round")
            // FORK(S0-branding): placeholder app name and neutral launcher glyph instead of FlorisBoard branding
            resValue("drawable", "floris_app_icon_foreground", "@drawable/ic_app_icon_keyboard_foreground")
            resValue("string", "floris_app_name", "@string/app_name")
            // FORK(S1-whisper): no language uses Whisper in release until each is checked (decision 5)
            buildConfigField("String", "WHISPER_ENABLED_LANGS", "\"\"")
        }

        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    aboutLibraries {
        collect {
            configPath = file("src/main/config")
        }
    }

    lint {
        // FORK(S0-ci): upstream pointed `baseline` at lint.xml, which is the lint *config* (still picked
        // up by default). Use a real baseline of the pre-existing upstream issues so :app:lintDebug can
        // gate CI on new ones (TRD §7.2). Regenerate with ./gradlew :app:updateLintBaseline.
        baseline = file("lint-baseline.xml")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

// FORK(S0-licences): ship the repo-root LICENSE and NOTICE inside the APK as
// assets/licenses/{LICENSE,NOTICE}.txt (Apache-2.0 §4(a), (d)). Generated so there is one source of truth.
abstract class CopyLicenseAssetsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val licenseFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copyFiles() {
        val dir = outputDir.get().asFile.resolve("licenses")
        dir.deleteRecursively()
        dir.mkdirs()
        licenseFiles.files.forEach { it.copyTo(dir.resolve("${it.name}.txt"), overwrite = true) }
    }
}

val copyLicenseAssets = tasks.register<CopyLicenseAssetsTask>("copyLicenseAssets") {
    licenseFiles.from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyLicenseAssets, CopyLicenseAssetsTask::outputDir)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// FORK(S1-policy): offline android-all jar for Robolectric (see the script header)
apply(from = rootProject.file("gradle/robolectric-offline.gradle.kts"))

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    // testImplementation(composeBom)
    // androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.autofill)
    implementation(libs.androidx.collection.ktx)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.emoji2.views)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.cache4k)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mikepenz.aboutlibraries.core)
    implementation(libs.mikepenz.aboutlibraries.compose)
    implementation(libs.patrickgold.compose.tooltip)
    implementation(libs.patrickgold.jetpref.datastore.model)
    ksp(libs.patrickgold.jetpref.datastore.model.processor)
    implementation(libs.patrickgold.jetpref.datastore.ui)
    implementation(libs.patrickgold.jetpref.material.ui)

    implementation(projects.lib.android)
    implementation(projects.lib.color)
    implementation(projects.lib.compose)
    implementation(projects.lib.kotlin)
    implementation(projects.lib.native)
    implementation(projects.lib.snygg)

    // FORK(S0-ads): ads stack for app Activities only. The IME code in this module must not use it
    // (ImeAdIsolationTest; TRD §2.2, §11.1). Nothing calls it yet.
    implementation(projects.core.ads)

    // FORK(S1-policy): fork settings store and the sensitive-field policy that FlorisImeService evaluates
    implementation(projects.core.datastore)
    implementation(projects.ime.policy)
    // FORK(S1-clipboard): clipboard sensitivity classifier used by the capture gate
    implementation(projects.core.privacy)
    implementation(projects.core.analytics) // FORK(S1-analytics): event catalog and relay
    // FORK(S1-voice): speech session, platform engines and the in-keyboard voice UI
    implementation(projects.core.voice)
    implementation(projects.ime.voiceUi)
    // FORK(S1-whisper): on-device whisper.cpp (libaikb_whisper.so); WhisperSpeechEngine builds on it
    implementation(projects.lib.whisper)
    implementation(libs.androidx.work.runtime) // FORK(S1-whisper): model download worker, on-demand init
    // FORK(S2-mlkit): translation core and the ML Kit engine (its MlKitInitProvider is removed in that module)
    implementation(projects.core.translate)
    implementation(projects.core.translateMlkit)
    implementation(projects.ime.translateUi) // FORK(S2-bar): the in-keyboard Translate bar
    // FORK(S2-translator): Translator history (Room) and the shared TextToSpeech
    implementation(projects.core.database)
    implementation(projects.core.tts)

    // FORK(S0-hardening): custom lint checks (ImeProcessTermination, TRD §2.3)
    lintChecks(projects.lintRules)

    testImplementation(libs.kotlin.test.junit5)
    // FORK(S1-policy): Robolectric (JUnit 4) tests run on the JUnit 5 platform through the vintage engine
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.vintage.engine)
    // FORK(S1-onboarding): onboarding view model and screen tests
    testImplementation(libs.turbine)
    testImplementation(platform(composeBom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}

fun getGitCommitHash(short: Boolean = false): Provider<String> {
    if (!File(".git").exists()) {
        return providers.provider { "null" }
    }

    val execProvider = providers.exec {
        if (short) {
            commandLine("git", "rev-parse", "--short", "HEAD")
        } else {
            commandLine("git", "rev-parse", "HEAD")
        }
    }
    return execProvider.standardOutput.asText.map { it.trim() }
}
