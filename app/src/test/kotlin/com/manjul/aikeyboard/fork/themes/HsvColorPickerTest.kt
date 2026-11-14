/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.theme.Hsv
import com.manjul.aikeyboard.fork.testing.ComposeHostActivityRule
import com.manjul.aikeyboard.fork.themes.color.HsvColorPicker
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HsvColorPickerTest {
    @get:Rule(order = 0)
    val host = ComposeHostActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private var color by mutableLongStateOf(0xFFFF0000)
    private var eyedropperTaps = 0

    private fun show(recents: List<Long> = emptyList(), eyedropper: (() -> Unit)? = null) = compose.setContent {
        ProvideLocalizedResources(LocalContext.current, appName = R.string.app_name) {
            HsvColorPicker(color = color, recents = recents, onColorChange = { color = it }, eyedropper = eyedropper)
        }
    }

    @Test fun `a valid hex changes the colour and an invalid one shows an error and changes nothing`() {
        show()
        compose.onNodeWithTag("hex_field").performTextReplacement("#80ff8800")
        compose.waitForIdle()
        assertEquals(0x80FF8800, color)

        compose.onNodeWithTag("hex_field").performTextReplacement("#gg0000")
        compose.waitForIdle()
        assertEquals(0x80FF8800, color)
        compose.onNodeWithText("Use #RRGGBB or #AARRGGBB").assertExists()
    }

    @Test fun `a tap on the square and the ring changes the colour`() {
        show()
        compose.onNodeWithContentDescription("Saturation and brightness").performTouchInput {
            down(centerLeft); up()
        }
        compose.waitForIdle()
        // The left edge of the square is saturation 0: a grey.
        assertEquals(0f, Hsv.fromArgb(color).s, 0.02f)

        color = 0xFFFF0000
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Hue").performTouchInput { down(bottomCenter); up() }
        compose.waitForIdle()
        assertTrue(Hsv.fromArgb(color).h in 80f..100f, "hue ${Hsv.fromArgb(color).h}")
    }

    @Test fun `a recent swatch picks its colour and has a 48 dp target`() {
        show(recents = listOf(0xFF112233))
        compose.onNodeWithContentDescription("Recent colour #112233")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .performClick()
        compose.waitForIdle()
        assertEquals(0xFF112233, color)
    }

    @Test fun `the eyedropper button shows only when one is given`() {
        show(eyedropper = null)
        compose.onNodeWithContentDescription("Pick a colour from the screen").assertDoesNotExist()
    }

    @Test fun `a given eyedropper shows and is called`() {
        show(eyedropper = { eyedropperTaps++ })
        compose.onNodeWithContentDescription("Pick a colour from the screen").performClick()
        assertEquals(1, eyedropperTaps)
    }
}
