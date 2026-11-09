/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.tools.themegen

import com.manjul.aikeyboard.core.theme.SnyggGenerator
import com.manjul.aikeyboard.core.theme.ThemePreset
import com.manjul.aikeyboard.core.theme.ThemePresets
import java.io.File

/**
 * Writes the `com.manjul.aikeyboard.themes` extension (plan Task 9): `extension.json` and
 * `stylesheets/<id>.json` plus `stylesheets/<id>_borderless.json` for every preset. Stale stylesheets are removed,
 * so the directory always matches [ThemePresets.all]. Output is deterministic and committed.
 */
object Main {
    fun run(outDir: File, presets: List<ThemePreset> = ThemePresets.all) {
        val stylesheets = File(outDir, "stylesheets")
        stylesheets.mkdirs()
        stylesheets.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { it.delete() }
        for (preset in presets) {
            for (keyBorder in listOf(true, false)) {
                val id = SnyggGenerator.stylesheetId(preset.id, keyBorder)
                File(stylesheets, "$id.json").writeText(SnyggGenerator.stylesheet(preset.spec, keyBorder))
            }
        }
        File(outDir, "extension.json").writeText(SnyggGenerator.extension(presets))
        println("themegen: wrote ${presets.size * 2} stylesheets and extension.json to $outDir")
    }
}

fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: themegen <theme extension dir>" }
    Main.run(File(args[0]))
}
