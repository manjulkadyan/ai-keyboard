/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes.color

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.core.theme.HexColor
import org.florisboard.lib.compose.stringRes

/** [color] as hex: `#RRGGBB` when opaque, else `#AARRGGBB`. */
internal fun hexOf(color: Long): String = HexColor.format(color, withAlpha = color ushr 24 != 0xFFL)

/**
 * The hex field of the colour picker. It accepts `#RRGGBB` and `#AARRGGBB` in either case; an invalid value shows an
 * error and does not change the colour. A colour set elsewhere (wheel, recents) replaces the text.
 */
@Composable
internal fun HexField(color: Long, onColorChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf(hexOf(color)) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(color) {
        if (HexColor.parse(text) != color) {
            text = hexOf(color)
            error = false
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            text = new
            val parsed = HexColor.parse(new.trim())
            error = parsed == null
            if (parsed != null && parsed != color) onColorChange(parsed)
        },
        modifier = modifier.testTag("hex_field"),
        label = { Text(stringRes(R.string.color_picker__hex)) },
        isError = error,
        supportingText = if (error) {
            { Text(stringRes(R.string.color_picker__hex_error)) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
    )
}
