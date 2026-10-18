/*
 * Copyright (C) 2020-2025 The FlorisBoard Contributors
 * Modified by AI Keyboard contributors, 2026
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

package com.manjul.aikeyboard.lib.crashutility

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.Toolbar
import androidx.activity.ComponentActivity
import com.manjul.aikeyboard.BuildConfig
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.FlorisPreferenceModel
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.lib.devtools.Devtools
import com.manjul.aikeyboard.lib.devtools.LogTopic
import com.manjul.aikeyboard.lib.devtools.flogWarning
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

private class SafePreferenceInstanceWrapper : ReadOnlyProperty<Any?, FlorisPreferenceModel?> {
    val cachedPreferenceModel = try {
        FlorisPreferenceStore
    } catch (_: Throwable) {
        null
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): FlorisPreferenceModel? {
        return cachedPreferenceModel?.getValue(thisRef, property)
    }
}

class CrashDialogActivity : ComponentActivity() {
    private var stacktraces: List<CrashUtility.Stacktrace> = listOf()
    private var errorReport: StringBuilder = StringBuilder()
    private val prefs by SafePreferenceInstanceWrapper()

    private val stacktrace by lazy { findViewById<TextView>(R.id.stacktrace) }
    private val reportInstructions by lazy { findViewById<TextView>(R.id.report_instructions) }
    private val copyToClipboard by lazy { findViewById<Button>(R.id.copy_to_clipboard) }
    private val openBugReportForm by lazy { findViewById<Button>(R.id.open_bug_report_form) }
    private val close by lazy { findViewById<Button>(R.id.close) }

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = layoutInflater.inflate(R.layout.crash_dialog, null)
        setContentView(layout)

        val toolbar = layout.findViewById<Toolbar>(R.id.crash_dialog_toolbar)
        setActionBar(toolbar)

        stacktraces = CrashUtility.getUnhandledStacktraces(this)
        // FORK(S0-branding): plain version (plus commit hash in debug builds) instead of a markdown link to
        // upstream's GitHub commit/release page, which does not describe our builds
        val versionName = if (BuildConfig.DEBUG) {
            "${BuildConfig.VERSION_NAME} [${BuildConfig.BUILD_COMMIT_HASH}]"
        } else {
            BuildConfig.VERSION_NAME
        }
        errorReport.apply {
            appendLine("#### Environment information")
            // FORK(S0-branding): report our app name instead of the upstream product name
            appendLine("- ${resources.getString(R.string.app_name)} $versionName (${BuildConfig.VERSION_CODE})")
            appendLine("- Device: ${Devtools.getDeviceName()}")
            appendLine("- Android: ${Devtools.getAndroidVersion()}")
            appendLine()
            appendLine("#### Attached logs and stacktrace files")
            appendCollapsibleSection(
                summary = "Detailed info (Debug log header)",
                details = Devtools.generateDebugLog(this@CrashDialogActivity, prefs, includeLogcat = false),
            )
            appendLine()
            if (stacktraces.isNotEmpty()) {
                stacktraces.forEach {
                    appendCollapsibleSection(it.name, it.details)
                    appendLine()
                }
            } else {
                flogWarning(LogTopic.CRASH_UTILITY) {
                    "Stacktrace file list is empty."
                }
            }
        }
        stacktrace.text = errorReport

        // FORK(S0-branding): upstream's instructions tell the user to file the crash on FlorisBoard's GitHub
        // issue tracker. We have no public tracker yet, so hide them; copy and share stay available.
        reportInstructions.visibility = View.GONE

        copyToClipboard.setOnClickListener {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE)
            val toastMessage: String = if (clipboardManager != null && clipboardManager is ClipboardManager) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText(errorReport, errorReport))
                resources.getString(R.string.crash_dialog__copy_to_clipboard_success)
            } else {
                resources.getString(R.string.crash_dialog__copy_to_clipboard_failure)
            }
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show()
        }

        // FORK(S0-branding): the "open issue tracker" button (FlorisBoard's GitHub issues) becomes a plain
        // share-sheet for the crash text; nothing is sent anywhere unless the user picks a target
        openBugReportForm.text = resources.getString(R.string.crash_dialog__share)
        openBugReportForm.setOnClickListener {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, errorReport.toString())
            }
            startActivity(Intent.createChooser(sendIntent, null))
        }

        close.setOnClickListener {
            finish()
        }
    }

    /**
     * Rules for collapsible markdown on GitHub:
     *  https://gist.github.com/pierrejoubert73/902cc94d79424356a8d20be2b382e1ab
     */
    private fun StringBuilder.appendCollapsibleSection(summary: String, details: String) {
        this.appendLine("<details>")
        this.append("<summary>").append(summary).appendLine("</summary>")
        this.appendLine()
        this.appendLine("```")
        this.appendLine(details)
        this.appendLine("```")
        this.appendLine("</details>")
    }
}
