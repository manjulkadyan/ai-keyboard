/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package com.manjul.aikeyboard.ime.smartbar.quickaction

import com.manjul.aikeyboard.fork.smartbar.PinnedActions // FORK(S1-smartbar)
import com.manjul.aikeyboard.ime.text.keyboard.TextKeyData
import com.manjul.aikeyboard.lib.io.DefaultJsonConfig
import dev.patrickgold.jetpref.datastore.model.PreferenceSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic

val QuickActionJsonConfig = Json(DefaultJsonConfig) {
    classDiscriminator = "$"
    encodeDefaults = false
    ignoreUnknownKeys = true
    isLenient = false

    serializersModule += SerializersModule {
        polymorphic(QuickAction::class) {
            subclass(QuickAction.InsertKey::class, QuickAction.InsertKey.serializer())
            subclass(QuickAction.InsertText::class, QuickAction.InsertText.serializer())
            defaultDeserializer { QuickAction.InsertKey.serializer() }
        }
    }
}

@Serializable
data class QuickActionArrangement(
    val stickyAction: QuickAction?,
    val dynamicActions: List<QuickAction>,
    val hiddenActions: List<QuickAction>,
) {
    operator fun contains(action: QuickAction): Boolean {
        return stickyAction == action || dynamicActions.contains(action) || hiddenActions.contains(action)
    }

    fun distinct(): QuickActionArrangement {
        val distinctSet = mutableSetOf<QuickAction>()
        if (stickyAction != null) {
            distinctSet.add(stickyAction)
        }
        val distinctDynamicActions = dynamicActions.filter { distinctSet.add(it) }
        val distinctHiddenActions = hiddenActions.filter { distinctSet.add(it) }
        return QuickActionArrangement(
            stickyAction = stickyAction,
            dynamicActions = distinctDynamicActions,
            hiddenActions = distinctHiddenActions,
        )
    }

    companion object {
        // FORK(S1-smartbar): Mic sticky, then Emoji, Clipboard and Settings (AI has no handler yet).
        // FORK(S2-bar): Translate is fixed as the first dynamic action (PinnedActions), not one of the 3 pins.
        // The private-app action is last so it lands in the overflow panel and does not count as one of the 3
        // pins (PinnedActions). Upstream's other actions stay available through the actions editor.
        val Default = QuickActionArrangement(
            stickyAction = QuickAction.InsertKey(TextKeyData.VOICE_INPUT),
            dynamicActions = listOf(
                QuickAction.InsertKey(TextKeyData.TOGGLE_TRANSLATE_BAR), // FORK(S2-bar): fixed, first after the mic
                QuickAction.InsertKey(TextKeyData.IME_UI_MODE_MEDIA), // FORK(S1-smartbar)
                QuickAction.InsertKey(TextKeyData.IME_UI_MODE_CLIPBOARD),
                QuickAction.InsertKey(TextKeyData.SETTINGS),
                QuickAction.InsertKey(TextKeyData.TOGGLE_PRIVATE_APP),
            ),
            hiddenActions = listOf( // FORK(S1-smartbar)
                QuickAction.InsertKey(TextKeyData.UNDO),
                QuickAction.InsertKey(TextKeyData.REDO), // FORK(S1-smartbar)
                QuickAction.InsertKey(TextKeyData.TOGGLE_INCOGNITO_MODE),
                QuickAction.InsertKey(TextKeyData.TOGGLE_COMPACT_LAYOUT),
                QuickAction.InsertKey(TextKeyData.TOGGLE_AUTOCORRECT),
                QuickAction.InsertKey(TextKeyData.ARROW_UP),
                QuickAction.InsertKey(TextKeyData.ARROW_DOWN),
                QuickAction.InsertKey(TextKeyData.ARROW_LEFT),
                QuickAction.InsertKey(TextKeyData.ARROW_RIGHT),
                QuickAction.InsertKey(TextKeyData.CLIPBOARD_CLEAR_PRIMARY_CLIP),
                QuickAction.InsertKey(TextKeyData.CLIPBOARD_COPY),
                QuickAction.InsertKey(TextKeyData.CLIPBOARD_CUT),
                QuickAction.InsertKey(TextKeyData.CLIPBOARD_PASTE),
                QuickAction.InsertKey(TextKeyData.CLIPBOARD_SELECT_ALL),
                QuickAction.InsertKey(TextKeyData.LANGUAGE_SWITCH),
                QuickAction.InsertKey(TextKeyData.FORWARD_DELETE),
                QuickAction.InsertKey(TextKeyData.IME_HIDE_UI), // FORK(S1-smartbar)
            ),
        )
    }

    object Serializer : PreferenceSerializer<QuickActionArrangement> {
        override fun serialize(value: QuickActionArrangement): String {
            return QuickActionJsonConfig.encodeToString(value)
        }

        override fun deserialize(value: String): QuickActionArrangement {
            // FORK(S1-smartbar): mic sticky and at most 3 pins, also for arrangements saved before the rule
            return PinnedActions.normalize(QuickActionJsonConfig.decodeFromString(value))
        }
    }
}
