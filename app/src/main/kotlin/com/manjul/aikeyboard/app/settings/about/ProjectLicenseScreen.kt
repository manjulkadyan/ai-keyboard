/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package com.manjul.aikeyboard.app.settings.about

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.lib.compose.FlorisScreen
import com.manjul.aikeyboard.lib.io.FlorisRef
import com.manjul.aikeyboard.lib.io.loadTextAsset
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.compose.florisVerticalScroll
import org.florisboard.lib.compose.stringRes

@Composable
fun ProjectLicenseScreen() = FlorisScreen {
    title = stringRes(R.string.about__project_license__title)
    scrollable = false

    val context = LocalContext.current

    content {
        // Forcing LTR because the Apache 2.0 License shipped and displayed
        // is hard to read if rendered in RTL. Also it is in English so forcing
        // LTR here makes most sense.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .florisVerticalScroll()
                    .florisHorizontalScroll(),
            ) {
                // FORK(S0-licences): show NOTICE then LICENSE, both copied from the repo root into
                // assets/licenses/ at build time by the copyLicenseAssets task (Apache-2.0 §4(a), (d)).
                val errorText = stringRes(R.string.about__project_license__error_license_text_failed, "error_message" to "{error_message}")
                val licenseText = listOf("licenses/NOTICE.txt", "licenses/LICENSE.txt").joinToString("\n\n") { path ->
                    FlorisRef.assets(path).loadTextAsset(context).getOrElse {
                        errorText.replace("{error_message}", it.message ?: "")
                    }
                }
                Text(
                    text = licenseText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    softWrap = false,
                )
            }
        }
    }
}
