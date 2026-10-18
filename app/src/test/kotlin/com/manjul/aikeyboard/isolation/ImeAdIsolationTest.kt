/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.isolation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * TRD §2.2 rule 1, §11.1 (RN-10): no ad code in the keyboard. The upstream IME shares the `:app` module
 * with the companion app, so the Gradle module rule cannot see it; this test scans the sources instead.
 *
 * 1. IME code (the `ime` and `lib` packages, the root-package services and `FlorisApplication`, and the
 *    upstream `:lib:*` modules) must not reference an ad/consent SDK, `:core:ads`, or an [AD_HOST_PACKAGES]
 *    package of `:app`.
 * 2. Everywhere else in `:app`, ad code may only appear in [AD_HOST_PACKAGES]. The IME reaches into
 *    `com.manjul.aikeyboard.app.*` (preferences, routes), so app code outside those packages counts
 *    as reachable from the keyboard. That code must not reference the host packages either (otherwise
 *    e.g. a preferences file importing `app.ads` would hand ad code to the IME transitively), except
 *    the Activity-only callers on [AD_HOST_CALLERS].
 *
 * Both import statements and fully qualified references are caught; comments are ignored. This is a
 * source check: the runtime check (IME-only cold start, no `com.google.android.gms.ads` class loaded)
 * is the instrumented test of TRD §11.1 1.1(c).
 */
class ImeAdIsolationTest {
    private companion object {
        const val APP_PACKAGE_DIR = "com/manjul/aikeyboard"

        val AD_PACKAGES = listOf(
            "com.manjul.aikeyboard.core.ads",
            "com.google.android.gms.ads",
            "com.google.android.libraries.ads",
            "com.google.android.ump",
        )

        /**
         * `:app` packages allowed to use the ads stack (Activity-only code). Empty in Sprint 0: nothing
         * calls `:core:ads` yet. Adding one needs a reason and must keep it out of IME reach.
         */
        val AD_HOST_PACKAGES: List<String> = listOf(
            "com.manjul.aikeyboard.app.ads",
        )

        /**
         * `:app` files outside [AD_HOST_PACKAGES] that may call into them, as paths relative to the
         * `:app` Kotlin source root. Only Activity code that the IME never loads belongs here (e.g.
         * `com/manjul/aikeyboard/app/FlorisAppActivity.kt` once it starts consent); each entry needs a
         * reason. Empty in Sprint 0: nothing calls the ad host packages yet.
         */
        val AD_HOST_CALLERS: Map<String, String> = emptyMap()

        /** Root-package files that start with, or are part of, the IME process start. */
        val IME_ROOT_FILES = listOf("FlorisImeService.kt", "FlorisSpellCheckerService.kt", "FlorisApplication.kt")

        val UPSTREAM_LIB_MODULES = listOf("android", "color", "compose", "kotlin", "native", "snygg")

        private val commentLinePrefixes = listOf("//", "/*", "*")

        fun referencePattern(packages: List<String>): Regex =
            Regex(packages.joinToString("|", prefix = """(?<![\w.])(""", postfix = """)(?![\w])""") { Regex.escape(it) })

        /**
         * Distinct referenced packages from [packages] in [source]. Comment lines (see
         * [commentLinePrefixes]) are skipped; a trailing comment on a code line still counts, which errs on
         * the safe side.
         */
        fun findReferences(source: String, packages: List<String>): Set<String> {
            val code = source.lineSequence()
                .filterNot { line -> line.trimStart().let { t -> commentLinePrefixes.any { t.startsWith(it) } } }
                .joinToString("\n")
            return referencePattern(packages).findAll(code).map { it.value }.toSet()
        }

        /** A source file of `:app`: path relative to the Kotlin source root, package, and content. */
        data class AppSource(val relativePath: String, val packageName: String, val text: String)

        fun isInAdHost(packageName: String, hostPackages: List<String>): Boolean =
            hostPackages.any { host -> packageName == host || packageName.startsWith("$host.") }

        /**
         * Rule 2 as a pure function. Files inside [hostPackages] are not checked; every other file must not
         * reference [AD_PACKAGES], and must not reference [hostPackages] unless its path is in [allowedCallers].
         */
        fun appCodeViolations(
            sources: List<AppSource>,
            hostPackages: List<String> = AD_HOST_PACKAGES,
            allowedCallers: Set<String> = AD_HOST_CALLERS.keys,
        ): List<String> = sources
            .filterNot { isInAdHost(it.packageName, hostPackages) }
            .mapNotNull { source ->
                val forbidden = if (source.relativePath in allowedCallers) AD_PACKAGES else AD_PACKAGES + hostPackages
                val refs = findReferences(source.text, forbidden)
                if (refs.isEmpty()) null else "${source.relativePath} -> ${refs.sorted().joinToString()}"
            }
    }

    private val repoRoot: File = listOf(File("."), File(".."))
        .map { it.canonicalFile }
        .firstOrNull { File(it, "app/src/main/kotlin/$APP_PACKAGE_DIR/FlorisImeService.kt").isFile }
        ?: fail("Cannot locate the repository root from working dir ${File(".").absolutePath}")

    private val appSourceRoot = File(repoRoot, "app/src/main/kotlin")
    private val appPackageRoot = File(appSourceRoot, APP_PACKAGE_DIR)

    private fun File.kotlinAndJava(): List<File> =
        walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.toList()

    private fun imeScopeFiles(): List<File> =
        listOf("ime", "lib").flatMap { File(appPackageRoot, it).kotlinAndJava() } +
            IME_ROOT_FILES.map { File(appPackageRoot, it) }.onEach { assertTrue(it.isFile, "Missing $it") } +
            UPSTREAM_LIB_MODULES.flatMap { File(repoRoot, "lib/$it/src/main").kotlinAndJava() }

    private fun File.packageName(): String =
        relativeTo(appSourceRoot).parentFile?.invariantSeparatorsPath?.replace('/', '.').orEmpty()

    private fun violations(files: List<File>, packages: List<String>): List<String> = files.mapNotNull { file ->
        val refs = findReferences(file.readText(), packages)
        if (refs.isEmpty()) null else "${file.relativeTo(repoRoot)} -> ${refs.sorted().joinToString()}"
    }

    @Test
    fun `ime code does not reference ads, consent or ad host packages`() {
        val files = imeScopeFiles()
        assertTrue(files.size > 200, "Scanned only ${files.size} files; are the IME source paths still right?")
        val found = violations(files, AD_PACKAGES + AD_HOST_PACKAGES)
        assertEquals(emptyList<String>(), found, "IME code must not use ad/consent code (TRD §2.2, §11.1)")
    }

    @Test
    fun `app code outside the ad host packages does not reference ads, consent or the ad host packages`() {
        val sources = appPackageRoot.kotlinAndJava().map { file ->
            AppSource(file.relativeTo(appSourceRoot).invariantSeparatorsPath, file.packageName(), file.readText())
        }
        val outsideHost = sources.count { !isInAdHost(it.packageName, AD_HOST_PACKAGES) }
        assertTrue(outsideHost > 150, "Scanned only $outsideHost files")
        val missingCallers = AD_HOST_CALLERS.keys - sources.map { it.relativePath }.toSet()
        assertEquals(emptySet<String>(), missingCallers, "AD_HOST_CALLERS entries that no longer exist")
        assertEquals(
            emptyList<String>(),
            appCodeViolations(sources),
            "Ad code in :app belongs in $AD_HOST_PACKAGES, called only from $AD_HOST_CALLERS (TRD §11.1)",
        )
    }

    @Test
    fun `app code rule catches a prefs file that imports the ad host package`() {
        val host = "com.manjul.aikeyboard.app.ads"
        val sources = listOf(
            AppSource(
                "com/manjul/aikeyboard/app/AppPrefs.kt",
                "com.manjul.aikeyboard.app",
                "package com.manjul.aikeyboard.app\nimport com.manjul.aikeyboard.app.ads.AdsHost\n",
            ),
            AppSource(
                "com/manjul/aikeyboard/app/settings/Screen.kt",
                "com.manjul.aikeyboard.app.settings",
                "package com.manjul.aikeyboard.app.settings\nfun f() = com.google.android.ump.UserMessagingPlatform.x()\n",
            ),
            AppSource(
                "com/manjul/aikeyboard/app/FlorisAppActivity.kt",
                "com.manjul.aikeyboard.app",
                "package com.manjul.aikeyboard.app\nimport com.manjul.aikeyboard.app.ads.AdsHost\n",
            ),
            AppSource(
                "com/manjul/aikeyboard/app/ads/AdsHost.kt",
                host,
                "package com.manjul.aikeyboard.app.ads\nimport com.manjul.aikeyboard.core.ads.AdsInitializer\n",
            ),
        )
        // No allowed callers: the prefs file and the Activity leak the host package, the screen uses UMP.
        assertEquals(
            listOf(
                "com/manjul/aikeyboard/app/AppPrefs.kt -> $host",
                "com/manjul/aikeyboard/app/settings/Screen.kt -> com.google.android.ump",
                "com/manjul/aikeyboard/app/FlorisAppActivity.kt -> $host",
            ),
            appCodeViolations(sources, listOf(host), allowedCallers = emptySet()),
        )
        // An allow-listed Activity may use the host package, but still not the SDKs directly.
        assertEquals(
            listOf(
                "com/manjul/aikeyboard/app/AppPrefs.kt -> $host",
                "com/manjul/aikeyboard/app/settings/Screen.kt -> com.google.android.ump",
            ),
            appCodeViolations(sources, listOf(host), allowedCallers = setOf("com/manjul/aikeyboard/app/FlorisAppActivity.kt")),
        )
    }

    @Test
    fun `scanner catches imports, qualified references and aliases but not comments`() {
        val source = """
            package com.manjul.aikeyboard.ime.text
            import com.google.android.ump.UserMessagingPlatform
            import com.manjul.aikeyboard.core.ads.AdsInitializer as Ads
            // import com.google.android.libraries.ads.mobile.sdk.MobileAds
            /* com.manjul.aikeyboard.app.ads.Foo */
            fun f() = com.google.android.gms.ads.MobileAds.initialize(null)
        """.trimIndent()
        assertEquals(
            setOf("com.google.android.ump", "com.manjul.aikeyboard.core.ads", "com.google.android.gms.ads"),
            findReferences(source, AD_PACKAGES + AD_HOST_PACKAGES),
        )
    }

    @Test
    fun `scanner ignores packages that only share a prefix`() {
        val source = """
            import com.google.android.gms.adservices.Foo
            import com.manjul.aikeyboard.core.adsx.Bar
            import com.manjul.aikeyboard.app.adsettings.Baz
            import org.com.google.android.ump.Qux
        """.trimIndent()
        assertEquals(emptySet<String>(), findReferences(source, AD_PACKAGES + AD_HOST_PACKAGES))
    }
}
