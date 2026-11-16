/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.themes

import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.theme.CustomTheme
import com.manjul.aikeyboard.core.theme.CustomTheme.Element
import com.manjul.aikeyboard.core.theme.ThemeSpec
import com.manjul.aikeyboard.core.theme.contrastRatio
import com.manjul.aikeyboard.fork.themes.color.RecentColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaveStatus { IDLE, SAVING, SAVED, FAILED }

/**
 * The "Custom colours" editor (S5 Task 8). The user picks one of the four [Element]s and a colour for it; each
 * change is previewed through [preview]. "Done" adds the current colour to the recents. "Save" stores the colours
 * and calls [install]. A key text colour with a contrast below [MIN_CONTRAST] on the keys shows a warning only.
 * No colour value is logged.
 */
class CustomThemeViewModel(
    private val settings: FeatureSettingsStore,
    private val scope: CoroutineScope,
    private val preview: (ThemeSpec?) -> Unit,
    private val install: suspend (ThemeSpec) -> Unit,
    private val base: ThemeSpec = CustomTheme.DEFAULT_BASE,
) {
    data class State(
        val selected: Element = Element.KEYS,
        val colors: Map<Element, Long>,
        val recents: List<Long>,
        val saveStatus: SaveStatus = SaveStatus.IDLE,
    ) {
        val color: Long get() = requireNotNull(colors[selected])

        val lowContrast: Boolean
            get() = contrastRatio(requireNotNull(colors[Element.KEY_TEXT]), requireNotNull(colors[Element.KEYS])) <
                MIN_CONTRAST
    }

    private val _state = MutableStateFlow(
        settings.current.let {
            State(colors = CustomTheme.fromStored(it.customThemeColors, base), recents = it.recentColors)
        },
    )
    val state: StateFlow<State> = _state.asStateFlow()

    /** The colours last stored (or loaded). A pick that differs from them is not stored yet. */
    private var storedColors: Map<Element, Long> = _state.value.colors

    fun spec(): ThemeSpec = CustomTheme.spec(_state.value.colors, base)

    fun select(element: Element) {
        _state.update { it.copy(selected = element) }
    }

    fun pick(color: Long) {
        _state.update {
            // A pick during a save keeps SAVING: the save re-runs when it ends, so this pick is stored too.
            val status = if (it.saveStatus == SaveStatus.SAVING) SaveStatus.SAVING else SaveStatus.IDLE
            it.copy(colors = it.colors + (it.selected to color), saveStatus = status)
        }
        preview(spec())
    }

    /** Shows the current colours in the preview, for example when the screen opens. */
    fun showPreview() {
        preview(spec())
    }

    fun clearPreview() {
        preview(null)
    }

    /** The user is done with the current colour: it goes to the front of the recents. */
    fun done() {
        val recents = RecentColors.add(_state.value.recents, _state.value.color)
        _state.update { it.copy(recents = recents) }
        scope.launch { settings.update { it.copy(recentColors = recents) } }
    }

    fun save() {
        val snapshot = _state.value
        _state.update { it.copy(saveStatus = SaveStatus.SAVING) }
        scope.launch {
            val ok = try {
                settings.update {
                    // Applying the custom theme cancels a pending premium one, as any applied theme does.
                    it.copy(customThemeColors = CustomTheme.toStored(snapshot.colors), pendingPremiumThemeId = "")
                }
                install(CustomTheme.spec(snapshot.colors, base))
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (ok) storedColors = snapshot.colors
            if (ok && _state.value.colors != snapshot.colors) {
                // The user picked a colour while the save ran: store that one too.
                save()
            } else {
                _state.update { it.copy(saveStatus = if (ok) SaveStatus.SAVED else SaveStatus.FAILED) }
            }
        }
    }

    /**
     * Stores a pick that no Save has stored yet, for example one made after Save finished. The screen calls this
     * when it is left, so a pick is never lost silently. A save that is running re-runs by itself for a newer pick.
     */
    fun saveIfUnsaved() {
        val s = _state.value
        if (s.saveStatus != SaveStatus.SAVING && s.colors != storedColors) save()
    }

    companion object {
        /** WCAG AA for normal text. */
        const val MIN_CONTRAST: Double = 4.5
    }
}
