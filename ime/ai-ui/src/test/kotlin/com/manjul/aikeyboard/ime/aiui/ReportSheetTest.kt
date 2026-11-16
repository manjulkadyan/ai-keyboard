/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.ime.aiui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReportSheetTest {
    @get:Rule val compose = createComposeRule()

    private val app = RuntimeEnvironment.getApplication()

    private fun role(role: Role) = SemanticsMatcher("role is $role") {
        it.config.getOrNull(SemanticsProperties.Role) == role
    }

    private fun show() = compose.setContent {
        // About the height of the keyboard area, so the content cannot fit without scrolling.
        Box(Modifier.height(140.dp)) {
            ReportSheet(onSubmit = { _, _ -> }, onCancel = {})
        }
    }

    @Test fun everyReasonRowAndTheIncludeTextRowMeetTheTouchTarget() {
        show()
        val radios = compose.onAllNodes(role(Role.RadioButton))
        assertEquals(ReportReason.entries.size, radios.fetchSemanticsNodes().size)
        for (i in ReportReason.entries.indices) radios[i].assertHeightIsAtLeast(48.dp)
        compose.onNode(role(Role.Checkbox)).assertHeightIsAtLeast(48.dp)
    }

    @Test fun cancelAndSendStayVisibleInAShortArea() {
        show()
        compose.onNodeWithText(app.getString(R.string.ai_sheet__report_cancel)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.ai_sheet__report_send)).assertIsDisplayed()
    }
}
