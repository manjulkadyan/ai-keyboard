/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.core.theme

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt

/**
 * Writes Snygg v2 stylesheets (`https://schemas.florisboard.org/snygg/v2/stylesheet`) and the theme extension
 * manifest. The rule set follows the upstream `org.florisboard.themes/stylesheets/floris_day.json` (same selectors,
 * same property kinds); every colour comes from `@defines`, which [ThemeSpec] fills.
 *
 * With [ThemeSpec.dynamic] set, the defines are `dynamic-light-color(<name>)` or `dynamic-dark-color(<name>)`, the
 * `SnyggDynamicLightColorValue` and `SnyggDynamicDarkColorValue` syntax, where `<name>` is a `ColorPalette` id.
 */
object SnyggGenerator {
    const val EXTENSION_ID: String = "com.manjul.aikeyboard.themes"
    const val EXTENSION_VERSION: String = "1.0.0"
    const val BORDERLESS_SUFFIX: String = "_borderless"
    private const val SCHEMA = "https://schemas.florisboard.org/snygg/v2/stylesheet"
    private const val AUTHOR = "AI Keyboard contributors"

    /** Relative luminance of the keyboard background below which a theme is a night theme. */
    private const val NIGHT_LUMINANCE = 0.2

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun stylesheetId(presetId: String, keyBorder: Boolean): String =
        if (keyBorder) presetId else presetId + BORDERLESS_SUFFIX

    /** The Snygg v2 stylesheet of [spec]. [keyBorder] adds the key outline and shadow. Ends with a newline. */
    fun stylesheet(spec: ThemeSpec, keyBorder: Boolean): String {
        if (spec.background.type != BackgroundType.COLOR) throw UnsupportedOperationException("v1")
        val font = when (spec.font) {
            ThemeFont.DEFAULT -> null
            ThemeFont.MONO -> "monospace"
            // Snygg has no generic rounded family; a rounded face needs a bundled @font, which S4 does not ship.
            ThemeFont.ROUNDED -> throw UnsupportedOperationException("ROUNDED needs a bundled font")
        }
        val defines = spec.dynamic?.let { dynamicDefines(it, spec.background) } ?: staticDefines(spec)
        val root = buildJsonObject {
            put("\$schema", SCHEMA)
            putJsonObject("@defines") {
                for ((name, value) in defines) put(name, value)
                val r = spec.cornerRadiusDp
                put("--shape", "rounded-corner(${r}dp, ${r}dp, ${r}dp, ${r}dp)")
                val rv = r + 4
                put("--shape-variant", "rounded-corner(${rv}dp, ${rv}dp, ${rv}dp, ${rv}dp)")
                put("--shape-chip", "rounded-corner(50%, 50%, 50%, 50%)")
            }
            rules(keyBorder, font, spec.keyGapDp)
        }
        return json.encodeToString(JsonObject.serializer(), root) + "\n"
    }

    /** The `ime.extension.theme` manifest listing `<id>` and `<id>_borderless` for every preset. */
    fun extension(presets: List<ThemePreset>): String {
        val root = buildJsonObject {
            put("$", "ime.extension.theme")
            putJsonObject("meta") {
                put("id", EXTENSION_ID)
                put("version", EXTENSION_VERSION)
                put("title", "AI Keyboard themes")
                put("description", "Original colour themes (day, night and Material You) for the keyboard UI")
                putJsonArray("maintainers") { add(AUTHOR) }
                put("license", "apache-2.0")
            }
            putJsonArray("themes") {
                for (preset in presets) {
                    for (keyBorder in listOf(true, false)) {
                        addJsonObject {
                            put("id", stylesheetId(preset.id, keyBorder))
                            put("label", label(preset.id) + if (keyBorder) "" else " (Borderless)")
                            putJsonArray("authors") { add(AUTHOR) }
                            put("isNight", isNight(preset.spec))
                        }
                    }
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), root) + "\n"
    }

    /** "material_you_dark" -> "Material You Dark"; "amoled" -> "AMOLED". */
    fun label(presetId: String): String = presetId.split('_').joinToString(" ") { word ->
        if (word == "amoled") "AMOLED" else word.replaceFirstChar { it.uppercaseChar() }
    }

    fun isNight(spec: ThemeSpec): Boolean = when (spec.dynamic) {
        DynamicScheme.DARK -> true
        DynamicScheme.LIGHT -> false
        null -> relativeLuminance(spec.colors.keyboardBackground) < NIGHT_LUMINANCE
    }

    private fun staticDefines(spec: ThemeSpec): List<Pair<String, String>> {
        val c = spec.colors
        val keyboardAlpha = (((c.keyboardBackground ushr 24) and 0xFF) * spec.background.opacity).roundToInt()
        val accentPressed = mix(c.accent, c.text, 0.2)
        return listOf(
            "--keyboard-background" to hex(withAlpha(c.keyboardBackground, keyboardAlpha.toLong())),
            "--key" to hex(c.key),
            "--key-pressed" to hex(c.keyPressed),
            "--key-border" to hex(withAlpha(c.text, 0x33)),
            "--text" to hex(c.text),
            "--text-variant" to hex(withAlpha(c.text, 0xB3)),
            "--text-disabled" to hex(withAlpha(c.text, 0x61)),
            "--accent" to hex(c.accent),
            "--on-accent" to hex(onColor(c.accent)),
            "--accent-pressed" to hex(accentPressed),
            "--on-accent-pressed" to hex(onColor(accentPressed)),
            "--popup" to hex(c.popup),
            "--popup-focused" to hex(mix(c.accent, c.popup, 0.75)),
            "--tile" to hex(c.keyPressed),
            "--one-hand-background" to hex(c.keyPressed),
            "--one-hand-foreground" to hex(c.text),
            "--spacer" to hex(withAlpha(c.text, 0x40)),
            "--drag-marker" to hex(c.accent),
            "--incognito-icon" to hex(withAlpha(c.text, 0x11)),
        )
    }

    private fun dynamicDefines(scheme: DynamicScheme, background: Background): List<Pair<String, String>> {
        // A dynamic colour carries no alpha, so a see-through Material You keyboard cannot be expressed.
        require(background.opacity == 1f) { "dynamic themes need an opaque background" }
        val fn = when (scheme) {
            DynamicScheme.LIGHT -> "dynamic-light-color"
            DynamicScheme.DARK -> "dynamic-dark-color"
        }
        return listOf(
            "--keyboard-background" to "surfaceContainer",
            "--key" to "surfaceBright",
            "--key-pressed" to "surfaceContainerHighest",
            "--key-border" to "outlineVariant",
            "--text" to "onSurface",
            "--text-variant" to "onSurfaceVariant",
            "--text-disabled" to "outline",
            "--accent" to "primary",
            "--on-accent" to "onPrimary",
            "--accent-pressed" to "primaryContainer",
            "--on-accent-pressed" to "onPrimaryContainer",
            "--popup" to "surfaceContainerHigh",
            "--popup-focused" to "secondaryContainer",
            "--tile" to "surfaceContainerHigh",
            "--one-hand-background" to "secondaryContainer",
            "--one-hand-foreground" to "onSecondaryContainer",
            "--spacer" to "outlineVariant",
            "--drag-marker" to "error",
            "--incognito-icon" to "outlineVariant",
        ).map { (name, color) -> name to "$fn($color)" }
    }

    @Suppress("LongMethod")
    private fun JsonObjectBuilder.rules(keyBorder: Boolean, font: String?, keyGapDp: Int) {
        rule("window", "background" to "var(--keyboard-background)", "foreground" to "var(--text)")

        rule(
            "key",
            "background" to "var(--key)",
            "foreground" to "var(--text)",
            "font-size" to "22sp",
            "font-family" to font,
            "shape" to "var(--shape)",
            "margin" to keyGapDp.takeIf { it > 0 }?.let { "${it}dp" },
            "border-color" to "var(--key-border)".takeIf { keyBorder },
            "border-width" to "1dp".takeIf { keyBorder },
            "shadow-elevation" to "2dp".takeIf { keyBorder },
            "text-max-lines" to "1",
        )
        rule("key:pressed", "background" to "var(--key-pressed)", "foreground" to "var(--text)")
        rule("key[code=10]", "background" to "var(--accent)", "foreground" to "var(--on-accent)")
        rule("key[code=10]:pressed", "background" to "var(--accent-pressed)", "foreground" to "var(--on-accent-pressed)")
        rule(
            "key[code=32]",
            "background" to "var(--key)",
            "foreground" to "var(--text-variant)",
            "font-size" to "12sp",
            "text-overflow" to "ellipsis",
        )
        rule("key[code=-201,-202,-203]", "font-size" to "18sp")
        rule("key[code=-204,-205]", "font-size" to "12sp")
        rule("key[code=-205]", "text-max-lines" to "2")
        rule("key[code=-11][shiftstate=`caps_lock`]", "foreground" to "var(--accent)")
        rule(
            "key-hint",
            "background" to "transparent",
            "foreground" to "var(--text-variant)",
            "font-family" to "monospace",
            "font-size" to "12sp",
            "padding" to "0dp 1dp 1dp 0dp",
            "text-max-lines" to "1",
        )
        rule(
            "key-popup-box",
            "background" to "var(--popup)",
            "foreground" to "var(--text)",
            "font-size" to "22sp",
            "shape" to "var(--shape)",
            "shadow-elevation" to "2dp",
        )
        rule("key-popup-element:focus", "background" to "var(--popup-focused)", "shape" to "var(--shape)")
        rule("key-popup-element[code=-255]", "font-size" to "18sp", "text-max-lines" to "1")
        rule("key-popup-extended-indicator", "font-size" to "16sp")

        rule("smartbar", "font-size" to "18sp")
        rule(
            "smartbar-shared-actions-toggle",
            "background" to "var(--key)",
            "foreground" to "var(--text)",
            "margin" to "6dp",
            "shape" to "circle()",
            "shadow-elevation" to "2dp",
        )
        rule(
            "smartbar-extended-actions-toggle",
            "background" to "transparent",
            "foreground" to "var(--text-variant)",
            "margin" to "6dp",
            "shape" to "circle()",
        )
        rule(
            "smartbar-action-key",
            "background" to "transparent",
            "foreground" to "var(--text)",
            "shape" to "var(--shape)",
        )
        rule("smartbar-action-key:disabled", "foreground" to "var(--text-disabled)")
        rule("smartbar-actions-overflow", "margin" to "4dp")
        rule(
            "smartbar-actions-overflow-customize-button",
            "background" to "var(--accent)",
            "foreground" to "var(--on-accent)",
            "font-size" to "14sp",
            "margin" to "0dp 8dp 0dp 0dp",
            "shape" to "rounded-corner(24dp, 24dp, 24dp, 24dp)",
        )
        rule(
            "smartbar-action-tile",
            "background" to "var(--tile)",
            "foreground" to "var(--text)",
            "font-size" to "14sp",
            "margin" to "4dp",
            "padding" to "4dp",
            "shape" to "rounded-corner(20%, 20%, 20%, 20%)",
            "text-align" to "center",
            "text-max-lines" to "2",
            "text-overflow" to "ellipsis",
        )
        rule("smartbar-action-tile:disabled", "foreground" to "var(--text-disabled)")
        rule("smartbar-action-tile-icon", "font-size" to "24sp", "margin" to "0dp 0dp 0dp 8dp")
        rule(
            "smartbar-actions-editor",
            "background" to "var(--keyboard-background)",
            "foreground" to "var(--text)",
            "shape" to "rounded-corner(24dp, 24dp, 0dp, 0dp)",
        )
        rule(
            "smartbar-actions-editor-header",
            "background" to "var(--key)",
            "foreground" to "var(--text)",
            "font-size" to "16sp",
            "text-max-lines" to "1",
            "text-overflow" to "ellipsis",
        )
        rule("smartbar-actions-editor-header-button", "margin" to "4dp", "shape" to "circle()")
        rule(
            "smartbar-actions-editor-subheader",
            "foreground" to "var(--accent)",
            "font-size" to "16sp",
            "font-weight" to "bold",
            "padding" to "12dp 16dp 12dp 8dp",
            "text-max-lines" to "1",
            "text-overflow" to "ellipsis",
        )
        rule("smartbar-actions-editor-tile-grid", "margin" to "4dp 0dp")
        rule(
            "smartbar-actions-editor-tile",
            "margin" to "4dp",
            "padding" to "8dp",
            "text-align" to "center",
            "text-max-lines" to "2",
            "text-overflow" to "ellipsis",
        )
        rule("smartbar-actions-editor-tile[code=-999]", "foreground" to "var(--text-disabled)")
        rule("smartbar-actions-editor-tile[code=-991]", "foreground" to "var(--drag-marker)")
        rule(
            "smartbar-candidate-word",
            "background" to "transparent",
            "foreground" to "var(--text)",
            "font-size" to "14sp",
            "margin" to "4dp",
            "padding" to "8dp 0dp",
            "shape" to "rectangle()",
            "text-max-lines" to "1",
            "text-overflow" to "ellipsis",
        )
        rule("smartbar-candidate-word:pressed", "background" to "var(--key)", "foreground" to "var(--text)")
        rule("smartbar-candidate-word-secondary-text", "font-size" to "8sp", "margin" to "0dp 2dp 0dp 0dp")
        rule(
            "smartbar-candidate-clip",
            "background" to "transparent",
            "foreground" to "var(--text)",
            "font-size" to "14sp",
            "margin" to "4dp",
            "padding" to "8dp 0dp",
            "shape" to "rounded-corner(8%, 8%, 8%, 8%)",
            "text-max-lines" to "1",
            "text-overflow" to "ellipsis",
        )
        rule("smartbar-candidate-clip:pressed", "background" to "var(--key)", "foreground" to "var(--text)")
        rule("smartbar-candidate-clip-icon", "margin" to "0dp 0dp 4dp 0dp")
        rule("smartbar-candidate-spacer", "foreground" to "var(--spacer)")

        rule("clipboard-header", "foreground" to "var(--text)", "font-size" to "16sp")
        rule("clipboard-header-button", "margin" to "4dp", "shape" to "circle()")
        rule("clipboard-header-button:disabled", "foreground" to "var(--text-disabled)")
        rule("clipboard-header-text", "text-max-lines" to "1", "text-overflow" to "ellipsis")
        rule("clipboard-subheader", "font-size" to "14sp", "margin" to "6dp 10dp 6dp 6dp")
        rule("clipboard-content", "padding" to "10dp 0dp")
        rule(
            "clipboard-filter-row",
            "background" to "var(--tile)",
            "foreground" to "var(--text)",
            "padding" to "0dp 0dp 4dp 0dp",
            "shape" to "var(--shape-variant)",
        )
        rule(
            "clipboard-filter-chip",
            "background" to "var(--key)",
            "foreground" to "var(--text)",
            "margin" to "4dp 4dp 0dp 4dp",
            "padding" to "8dp 4dp",
            "shape" to "var(--shape-chip)",
        )
        rule(
            "clipboard-filter-chip[state=`active`]",
            "background" to "var(--accent)",
            "foreground" to "var(--on-accent)",
        )
        rule("clipboard-filter-chip-text", "margin" to "4dp 0dp 0dp 0dp")
        rule("clipboard-grid", "shape" to "var(--shape-variant)")
        rule(
            "clipboard-item",
            "background" to "var(--key)",
            "foreground" to "var(--text)",
            "font-size" to "14sp",
            "margin" to "4dp",
            "shape" to "var(--shape-variant)",
            "shadow-elevation" to "2dp",
            "text-max-lines" to "10",
            "text-overflow" to "ellipsis",
        )
        rule("clipboard-item[type=`text`]", "padding" to "12dp 8dp")
        rule("clipboard-item-description", "font-size" to "12sp", "font-style" to "italic")
        rule(
            "clipboard-item-popup",
            "background" to "var(--key)",
            "foreground" to "var(--text)",
            "font-size" to "14sp",
            "margin" to "4dp",
            "shape" to "var(--shape-variant)",
            "shadow-elevation" to "2dp",
        )
        rule("clipboard-item-popup[type=`text`]", "padding" to "12dp 8dp")
        rule("clipboard-item-timestamp", "font-size" to "11sp", "padding" to "16dp 8dp")
        rule(
            "clipboard-item-actions",
            "background" to "var(--key)",
            "foreground" to "var(--text)",
            "margin" to "4dp",
            "shape" to "var(--shape-variant)",
            "shadow-elevation" to "2dp",
        )
        rule("clipboard-item-action", "font-size" to "16sp", "padding" to "12dp")
        rule("clipboard-item-action-text", "margin" to "4dp 0dp 0dp 0dp")
        rule(
            "clipboard-clear-all-dialog",
            "background" to "var(--key)",
            "foreground" to "var(--text)",
            "shape" to "var(--shape-variant)",
            "shadow-elevation" to "1dp",
        )
        rule("clipboard-clear-all-dialog-message", "padding" to "16dp")
        rule("clipboard-clear-all-dialog-buttons", "padding" to "4dp")
        rule(
            "clipboard-clear-all-dialog-button",
            "background" to "transparent",
            "foreground" to "var(--text)",
            "shape" to "var(--shape-variant)",
        )
        rule("clipboard-history-disabled-title", "font-weight" to "bold")
        rule("clipboard-history-disabled-message", "padding" to "0dp 4dp 0dp 8dp")
        rule(
            "clipboard-history-disabled-button",
            "background" to "var(--accent)",
            "foreground" to "var(--on-accent)",
            "shape" to "rounded-corner(24dp, 24dp, 24dp, 24dp)",
        )
        rule("clipboard-history-locked-title", "font-weight" to "bold", "text-align" to "center")
        rule("clipboard-history-locked-message", "padding" to "0dp 4dp 0dp 0dp", "text-align" to "center")

        rule("extracted-landscape-input-layout", "background" to "var(--keyboard-background)")
        rule(
            "extracted-landscape-input-field",
            "background" to "transparent",
            "foreground" to "var(--text)",
            "font-size" to "16sp",
            "shape" to "rounded-corner(12dp, 12dp, 12dp, 12dp)",
            "border-color" to "var(--accent)",
            "border-width" to "2dp",
        )
        rule(
            "extracted-landscape-input-action",
            "background" to "var(--accent)",
            "foreground" to "var(--on-accent)",
            "shape" to "rounded-corner(4dp, 4dp, 4dp, 4dp)",
        )

        rule("glide-trail", "foreground" to "var(--accent)")
        rule("incognito-mode-indicator", "foreground" to "var(--incognito-icon)")
        rule("inline-autofill-chip", "background" to "var(--key)", "foreground" to "var(--text)")

        rule("media-emoji-subheader", "font-weight" to "bold", "margin" to "4dp")
        rule(
            "media-emoji-key",
            "background" to "transparent",
            "foreground" to "var(--text)",
            "font-size" to "22sp",
            "shape" to "var(--shape)",
        )
        rule("media-emoji-key:pressed", "background" to "var(--key)", "foreground" to "var(--text)")
        rule(
            "media-emoji-key-popup-box",
            "background" to "var(--popup)",
            "foreground" to "var(--text)",
            "font-size" to "22sp",
            "shape" to "var(--shape)",
            "shadow-elevation" to "2dp",
        )
        rule("media-emoji-key-popup-element:focus", "background" to "var(--popup-focused)", "shape" to "var(--shape)")
        rule("media-emoji-tab", "foreground" to "var(--text)")
        rule("media-emoji-tab:focus", "foreground" to "var(--accent)")
        rule("media-bottom-row-button", "padding" to "16dp 0dp", "shape" to "var(--shape)")
        rule("media-emoji-key-popup-extended-indicator", "foreground" to "inherit")

        rule(
            "one-handed-panel",
            "background" to "var(--one-hand-background)",
            "foreground" to "var(--one-hand-foreground)",
        )

        rule(
            "subtype-panel",
            "background" to "var(--keyboard-background)",
            "foreground" to "var(--text)",
            "shape" to "rounded-corner(24dp, 24dp, 0dp, 0dp)",
        )
        rule(
            "subtype-panel-header",
            "background" to "var(--key)",
            "foreground" to "var(--text)",
            "font-size" to "18sp",
            "padding" to "12dp",
            "text-align" to "center",
            "text-max-lines" to "1",
            "text-overflow" to "ellipsis",
        )
        rule("subtype-panel-list-item", "font-size" to "16sp", "padding" to "16dp")
        rule("subtype-panel-list-item-icon-leading", "font-size" to "24sp", "padding" to "0dp 0dp 16dp 0dp")
        rule("subtype-panel-list-item-text", "text-max-lines" to "1", "text-overflow" to "ellipsis")
    }

    /** One rule; properties whose value is null are left out. */
    private fun JsonObjectBuilder.rule(selector: String, vararg properties: Pair<String, String?>) {
        putJsonObject(selector) {
            for ((name, value) in properties) if (value != null) put(name, value)
        }
    }

    /** `#rrggbb` when opaque, else `#rrggbbaa` (Snygg's 8-digit hex is RGBA). */
    private fun hex(argb: Long): String {
        val alpha = (argb ushr 24) and 0xFF
        val rgb = (argb and 0xFFFFFF).toString(16).padStart(6, '0')
        return if (alpha == 0xFFL) "#$rgb" else "#$rgb" + alpha.toString(16).padStart(2, '0')
    }

    private fun withAlpha(argb: Long, alpha: Long): Long = (alpha.coerceIn(0, 0xFF) shl 24) or (argb and 0xFFFFFF)

    /** Opaque mix per sRGB channel: (1 - [weight]) of [a] plus [weight] of [b]. */
    private fun mix(a: Long, b: Long, weight: Double): Long {
        fun ch(c: Long, shift: Int) = ((c shr shift) and 0xFF).toDouble()
        var out = 0xFF000000L
        for (shift in listOf(16, 8, 0)) {
            val v = (ch(a, shift) * (1 - weight) + ch(b, shift) * weight).roundToInt().toLong()
            out = out or (v shl shift)
        }
        return out
    }

    /** Black or white text for [bg], whichever has the higher contrast. */
    private fun onColor(bg: Long): Long {
        val black = 0xFF111111L
        val white = 0xFFFFFFFFL
        return if (contrastRatio(black, bg) >= contrastRatio(white, bg)) black else white
    }
}
