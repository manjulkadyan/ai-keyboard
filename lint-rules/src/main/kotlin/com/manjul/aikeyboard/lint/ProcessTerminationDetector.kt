/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Flags calls that terminate the process: `android.os.Process.killProcess`, `java.lang.System.exit`
 * and `kotlin.system.exitProcess`.
 *
 * The keyboard (IME service) shares its process with the app (TRD §2.3). Killing the process from
 * app code also kills the keyboard the user is typing with, and the system then rebinds it with lost
 * state. TRD §2.3 "Process termination" bans these calls outright.
 */
class ProcessTerminationDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> = BANNED.keys.toList()

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val owners = BANNED[method.name] ?: return
        val evaluator = context.evaluator
        val owner = owners.firstOrNull { evaluator.isMemberInClass(method, it) } ?: return
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Do not terminate the process (`${owner.substringAfterLast('.')}.${method.name}`): " +
                "the keyboard runs in this process. Finish the component or let the system manage the process instead",
        )
    }

    companion object {
        /** Method name -> classes that declare the banned method. */
        private val BANNED: Map<String, List<String>> = mapOf(
            "killProcess" to listOf("android.os.Process"),
            "exit" to listOf("java.lang.System", "java.lang.Runtime"),
            "halt" to listOf("java.lang.Runtime"),
            // Top-level function in kotlin/system/Process.kt
            "exitProcess" to listOf("kotlin.system.ProcessKt"),
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ImeProcessTermination",
            briefDescription = "Process termination call",
            explanation = """
                The IME service and the app share one process (TRD §2.3). `Process.killProcess`, \
                `System.exit`, `Runtime.exit`/`halt` and `exitProcess` kill the keyboard the user is \
                typing with. Never call them; finish the Activity or stop the Service instead. \
                A justified exception (for example the last step of an uncaught-exception handler) \
                must be suppressed at the call site and recorded in docs/FORK_PATCHES.md.
                """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = Severity.ERROR,
            implementation = Implementation(ProcessTerminationDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
    }
}
