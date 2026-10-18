/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

class ProcessTerminationDetectorTest : LintDetectorTest() {
    override fun getDetector(): Detector = ProcessTerminationDetector()

    override fun getIssues(): List<Issue> = listOf(ProcessTerminationDetector.ISSUE)

    // Stub so the test does not depend on a local android.jar.
    private val processStub: TestFile = java(
        """
        package android.os;
        public class Process {
            public static final int myPid() { return 0; }
            public static final void killProcess(int pid) {}
        }
        """,
    ).indented()

    fun testKotlinKillProcessIsError() {
        lint().files(
            processStub,
            kotlin(
                """
                package test
                import android.os.Process
                fun shutdown() {
                    Process.killProcess(Process.myPid())
                }
                """,
            ).indented(),
        ).run().expectErrorCount(1).expectContains("Process.killProcess")
    }

    fun testJavaSystemExitIsError() {
        lint().files(
            java(
                """
                package test;
                public class Quit {
                    void quit() { System.exit(0); }
                }
                """,
            ).indented(),
        ).run().expectErrorCount(1).expectContains("System.exit")
    }

    fun testKotlinSystemExitIsError() {
        lint().files(
            kotlin(
                """
                package test
                fun quit() { System.exit(1) }
                """,
            ).indented(),
        ).run().expectErrorCount(1)
    }

    fun testRuntimeHaltAndExitAreErrors() {
        lint().files(
            java(
                """
                package test;
                public class Quit {
                    void a() { Runtime.getRuntime().exit(1); }
                    void b() { Runtime.getRuntime().halt(1); }
                }
                """,
            ).indented(),
        ).run().expectErrorCount(2)
    }

    fun testExitProcessIsError() {
        lint().files(
            kotlin(
                """
                package test
                import kotlin.system.exitProcess
                fun quit(): Nothing = exitProcess(10)
                """,
            ).indented(),
        ).run().expectErrorCount(1).expectContains("ProcessKt.exitProcess")
    }

    fun testUnrelatedMethodsWithSameNameAreClean() {
        lint().files(
            kotlin(
                """
                package test
                object Process { fun killProcess(pid: Int) = Unit }
                class Door { fun exit(code: Int) = Unit }
                fun exitProcess(code: Int) = Unit
                fun use() {
                    Process.killProcess(1)
                    Door().exit(0)
                    exitProcess(2)
                }
                """,
            ).indented(),
        ).run().expectClean()
    }

    fun testSuppressedCallIsClean() {
        lint().files(
            processStub,
            kotlin(
                """
                package test
                import android.annotation.SuppressLint
                import android.os.Process
                @SuppressLint("ImeProcessTermination")
                fun crashHandlerLastStep() {
                    Process.killProcess(Process.myPid())
                }
                """,
            ).indented(),
            java(
                """
                package android.annotation;
                public @interface SuppressLint { String[] value(); }
                """,
            ).indented(),
        ).run().expectClean()
    }
}
