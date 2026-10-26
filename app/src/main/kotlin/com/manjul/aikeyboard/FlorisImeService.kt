/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package com.manjul.aikeyboard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.inputmethodservice.ExtractEditText
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.manjul.aikeyboard.app.FlorisAppActivity
import com.manjul.aikeyboard.app.FlorisPreferenceStore
import com.manjul.aikeyboard.app.devtools.DevtoolsOverlay
import com.manjul.aikeyboard.ime.ImeUiMode
import com.manjul.aikeyboard.ime.clipboard.ClipboardInputLayout
import com.manjul.aikeyboard.ime.core.SelectSubtypePanel
import com.manjul.aikeyboard.ime.core.isSubtypeSelectionShowing
import com.manjul.aikeyboard.ime.editor.EditorRange
import com.manjul.aikeyboard.ime.editor.FlorisEditorInfo
import com.manjul.aikeyboard.ime.input.InputFeedbackController
import com.manjul.aikeyboard.ime.input.LocalInputFeedbackController
import com.manjul.aikeyboard.ime.keyboard.FlorisImeSizing
import com.manjul.aikeyboard.ime.keyboard.ProvideKeyboardRowBaseHeight
import com.manjul.aikeyboard.ime.landscapeinput.LandscapeInputUiMode
import com.manjul.aikeyboard.ime.lifecycle.LifecycleInputMethodService
import com.manjul.aikeyboard.ime.media.MediaInputLayout
import com.manjul.aikeyboard.ime.nlp.NlpInlineAutofill
import com.manjul.aikeyboard.ime.onehanded.OneHandedMode
import com.manjul.aikeyboard.ime.onehanded.OneHandedPanel
import com.manjul.aikeyboard.ime.sheet.BottomSheetHostUi
import com.manjul.aikeyboard.ime.sheet.isBottomSheetShowing
import com.manjul.aikeyboard.ime.smartbar.ExtendedActionsPlacement
import com.manjul.aikeyboard.ime.smartbar.SmartbarLayout
import com.manjul.aikeyboard.ime.smartbar.quickaction.QuickActionsEditorPanel
import com.manjul.aikeyboard.ime.text.TextInputLayout
import com.manjul.aikeyboard.ime.theme.FlorisImeTheme
import com.manjul.aikeyboard.ime.theme.FlorisImeUi
import com.manjul.aikeyboard.ime.theme.WallpaperChangeReceiver
import com.manjul.aikeyboard.lib.compose.SystemUiIme
import com.manjul.aikeyboard.lib.devtools.LogTopic
import com.manjul.aikeyboard.lib.devtools.flogError
import com.manjul.aikeyboard.lib.devtools.flogInfo
import com.manjul.aikeyboard.lib.devtools.flogWarning
import com.manjul.aikeyboard.lib.observeAsTransformingState
import com.manjul.aikeyboard.lib.util.ViewUtils
import com.manjul.aikeyboard.lib.util.debugSummarize
import com.manjul.aikeyboard.lib.util.launchActivity
import dev.patrickgold.jetpref.datastore.model.observeAsState
// FORK(S1-policy): imports for the policy controller
import android.app.KeyguardManager
import com.manjul.aikeyboard.fork.policy.ImePolicyController
import com.manjul.aikeyboard.fork.voice.VoiceImeSession // FORK(S1-voice)
import com.manjul.aikeyboard.fork.translate.TranslateImeSession // FORK(S2-bar)
import com.manjul.aikeyboard.core.analytics.EndReason // FORK(S2-bar)
import com.manjul.aikeyboard.fork.clipboard.ClipboardPanelGate // FORK(S1-policy)
import com.manjul.aikeyboard.fork.analytics.ImeSessionStartReporter // FORK(S1-analytics)
import com.manjul.aikeyboard.fork.privacy.PrivateAppUndo // FORK(S1-smartbar)
import com.manjul.aikeyboard.fork.privacy.privateAppsRepository // FORK(S1-smartbar)
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.update
import org.florisboard.lib.android.AndroidInternalR
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.isOrientationLandscape
import org.florisboard.lib.android.isOrientationPortrait
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.android.systemServiceOrNull
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.kotlin.collectIn
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggSurfaceView
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

/**
 * Global weak reference for the [FlorisImeService] class. This is needed as certain actions (request hide, switch to
 * another input method, getting the editor instance / input connection, etc.) can only be performed by an IME
 * service class and no context-bound managers. This reference is exclusively used by the companion helper methods
 * of [FlorisImeService], which provide a safe and memory-leak-free way of performing certain actions on the Floris
 * input method service instance.
 */
private var FlorisImeServiceReference = WeakReference<FlorisImeService?>(null)

/**
 * Core class responsible for linking together all managers and UI compose-ables to provide an IME service. Sets
 * up the window and context to be lifecycle-aware, so LiveData and Jetpack Compose can be used without issues.
 */
class FlorisImeService : LifecycleInputMethodService() {
    companion object {
        private val InlineSuggestionUiSmallestSize = Size(0, 0)
        private val InlineSuggestionUiBiggestSize = Size(Int.MAX_VALUE, Int.MAX_VALUE)

        // FORK(S1-policy): policy controller accessor for features (voice, smartbar, ...)
        fun policyController(): ImePolicyController {
            return checkNotNull(policyControllerOrNull()) { "IME service is not running" }
        }

        fun policyControllerOrNull(): ImePolicyController? {
            val service = FlorisImeServiceReference.get() ?: return null
            return if (service::policyController.isInitialized) service.policyController else null
        }

        // FORK(S1-voice): voice typing of the running service (mic key, smartbar panel)
        fun voiceSessionOrNull(): VoiceImeSession? {
            val service = FlorisImeServiceReference.get() ?: return null
            return if (service::voiceSession.isInitialized) service.voiceSession else null
        }

        // FORK(S2-bar): the Translate bar of the running service (chip, key routing, smartbar)
        fun translateSessionOrNull(): TranslateImeSession? {
            val service = FlorisImeServiceReference.get() ?: return null
            return if (service::translateSession.isInitialized) service.translateSession else null
        }

        // FORK(S1-smartbar): "App made private" with Undo after the smartbar made the app private
        fun privateAppUndoOrNull(): PrivateAppUndo? {
            val service = FlorisImeServiceReference.get() ?: return null
            return if (service::privateAppUndo.isInitialized) service.privateAppUndo else null
        }

        fun currentInputConnection(): InputConnection? {
            return FlorisImeServiceReference.get()?.currentInputConnection
        }

        fun inputFeedbackController(): InputFeedbackController? {
            return FlorisImeServiceReference.get()?.inputFeedbackController
        }

        /**
         * Hides the IME and launches [FlorisAppActivity].
         */
        fun launchSettings() {
            val ims = FlorisImeServiceReference.get() ?: return
            ims.requestHideSelf(0)
            ims.launchActivity(FlorisAppActivity::class) {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        fun showUi() {
            val ims = FlorisImeServiceReference.get() ?: return
            if (AndroidVersion.ATLEAST_API28_P) {
                ims.requestShowSelf(0)
            } else {
                @Suppress("DEPRECATION")
                ims.systemServiceOrNull(InputMethodManager::class)
                    ?.showSoftInputFromInputMethod(ims.currentInputBinding.connectionToken, 0)
            }
        }

        fun hideUi() {
            val ims = FlorisImeServiceReference.get() ?: return
            if (AndroidVersion.ATLEAST_API28_P) {
                ims.requestHideSelf(0)
            } else {
                @Suppress("DEPRECATION")
                ims.systemServiceOrNull(InputMethodManager::class)
                    ?.hideSoftInputFromInputMethod(ims.currentInputBinding.connectionToken, 0)
            }
            FlorisImeServiceReference.get()?.requestHideSelf(0)
        }

        fun switchToPrevInputMethod(): Boolean {
            val ims = FlorisImeServiceReference.get() ?: return false
            val imm = ims.systemServiceOrNull(InputMethodManager::class)
            try {
                if (AndroidVersion.ATLEAST_API28_P) {
                    return ims.switchToPreviousInputMethod()
                } else {
                    ims.window.window?.let { window ->
                        @Suppress("DEPRECATION")
                        return imm?.switchToLastInputMethod(window.attributes.token) == true
                    }
                }
            } catch (e: Exception) {
                flogError { "Unable to switch to the previous IME" }
                imm?.showInputMethodPicker()
            }
            return false
        }

        fun switchToNextInputMethod(): Boolean {
            val ims = FlorisImeServiceReference.get() ?: return false
            val imm = ims.systemServiceOrNull(InputMethodManager::class)
            try {
                if (AndroidVersion.ATLEAST_API28_P) {
                    return ims.switchToNextInputMethod(false)
                } else {
                    ims.window.window?.let { window ->
                        @Suppress("DEPRECATION")
                        return imm?.switchToNextInputMethod(window.attributes.token, false) == true
                    }
                }
            } catch (e: Exception) {
                flogError { "Unable to switch to the next IME" }
                imm?.showInputMethodPicker()
            }
            return false
        }

        fun switchToVoiceInputMethod(): Boolean {
            val ims = FlorisImeServiceReference.get() ?: return false
            val imm = ims.systemServiceOrNull(InputMethodManager::class) ?: return false
            val list: List<InputMethodInfo> = imm.enabledInputMethodList
            for (el in list) {
                for (i in 0 until el.subtypeCount) {
                    if (el.getSubtypeAt(i).mode != "voice") continue
                    if (AndroidVersion.ATLEAST_API28_P) {
                        ims.switchInputMethod(el.id, el.getSubtypeAt(i))
                        return true
                    } else {
                        ims.window.window?.let { window ->
                            @Suppress("DEPRECATION")
                            imm.setInputMethod(window.attributes.token, el.id)
                            return true
                        }
                    }
                }
            }
            ims.showShortToastSync("Failed to find voice IME, do you have one installed?")
            return false
        }
    }

    private val prefs by FlorisPreferenceStore
    private val editorInstance by editorInstance()
    private val keyboardManager by keyboardManager()
    private val nlpManager by nlpManager()
    private val subtypeManager by subtypeManager()
    private val themeManager by themeManager()

    private val activeState get() = keyboardManager.activeState
    private var inputWindowView by mutableStateOf<View?>(null)
    private var inputViewSize by mutableStateOf(IntSize.Zero)
    private val inputFeedbackController by lazy { InputFeedbackController.new(this) }
    private var isWindowShown: Boolean = false
    private var isFullscreenUiMode by mutableStateOf(false)
    private var isExtractUiShown by mutableStateOf(false)
    private var resourcesContext by mutableStateOf(this as Context)

    private val wallpaperChangeReceiver = WallpaperChangeReceiver()

    // FORK(S1-policy): evaluates SensitiveFieldPolicy on every onStartInput. Created in onCreate so the store
    // load and the settings collector start with the service. Keyguard state is read on each evaluation, never
    // cached. Remote-config forced packages arrive in S3.
    private lateinit var policyController: ImePolicyController
    // FORK(S1-voice): in-keyboard voice typing; cancelled by every policy restriction and when the keyboard hides
    private lateinit var voiceSession: VoiceImeSession
    // FORK(S2-bar): the Translate bar; closed by every policy restriction, on hide and on field change. Its
    // controller and the translation core are created on the first chip tap, never when the keyboard shows.
    private lateinit var translateSession: TranslateImeSession
    // FORK(S1-smartbar): "Make this app private" adds the app at once and offers Undo for 5 s in the smartbar;
    // a field change dismisses the offer (the app stays private). The package is never logged.
    private lateinit var privateAppUndo: PrivateAppUndo
    // FORK(S1-analytics): sampled ime_session_start from onStartInputView (R36)
    private lateinit var imeSessionReporter: ImeSessionStartReporter

    init {
        setTheme(R.style.FlorisImeTheme)
    }

    override fun onCreate() {
        super.onCreate()
        FlorisImeServiceReference = WeakReference(this)
        // FORK(S1-policy): see the policyController declaration
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        policyController = ImePolicyController(
            store = featureSettingsStore().value,
            keyguard = { keyguardManager?.isDeviceLocked ?: true },
            forcedPackages = { emptySet() },
            scope = lifecycleScope,
        )
        // Settings can arrive after the field started: recompute the editor state from the new decision.
        policyController.addRestrictionListener { editorInstance.applyPolicyDecision(it) }
        // FORK(S3-suggest): suggestions, autocorrect and learning follow every decision for the current field
        policyController.decision.collectIn(lifecycleScope) { suggestContext().onDecision(it) }
        // FORK(S1-policy): an open clipboard history panel closes when the field turns more restrictive
        ClipboardPanelGate.closeOnRestriction(policyController, { activeState.imeUiMode }) { activeState.imeUiMode = it }
        // FORK(S1-voice): see the voiceSession declaration
        voiceSession = VoiceImeSession(
            context = this,
            policy = policyController,
            store = featureSettingsStore().value,
            analytics = appContext().value.analytics.value::log,
            connection = { currentInputConnection },
            isRawInputEditor = { editorInstance.activeInfo.isRawInputEditor },
            setOwnsComposing = { editorInstance.voiceOwnsComposing = it },
            lang = { subtypeManager.activeSubtype.primaryLocale.languageTag() },
            scope = lifecycleScope,
            barTarget = { translateSessionOrNull()?.voiceTarget() }, // FORK(S2-bar): dictate into the open bar
        )
        // FORK(S2-bar): see the translateSession declaration
        translateSession = TranslateImeSession(
            context = this,
            policy = policyController,
            store = featureSettingsStore().value,
            analytics = appContext().value.analytics.value::log,
            connection = { currentInputConnection },
            isRawInputEditor = { editorInstance.activeInfo.isRawInputEditor },
            setOwnsComposing = { editorInstance.voiceOwnsComposing = it },
            scope = lifecycleScope,
            subtypeLang = { subtypeManager.activeSubtype.primaryLocale.languageTag() },
            beforeToggle = { voiceSession.controller.cancel(EndReason.FIELD_CHANGE.value) },
        )
        // FORK(S1-smartbar): see the privateAppUndo declaration
        privateAppUndo = privateAppsRepository().let { repo ->
            PrivateAppUndo(add = repo::add, remove = repo::remove, scope = lifecycleScope)
        }
        // FORK(S1-analytics): see the imeSessionReporter declaration
        imeSessionReporter = ImeSessionStartReporter(System::currentTimeMillis, appContext().value.analytics.value::log)
        WindowCompat.setDecorFitsSystemWindows(window.window!!, false)
        subtypeManager.activeSubtypeFlow.collectIn(lifecycleScope) { subtype ->
            val config = Configuration(resources.configuration)
            if (prefs.localization.displayKeyboardLabelsInSubtypeLanguage.get()) {
                config.setLocale(subtype.primaryLocale.base)
            }
            resourcesContext = createConfigurationContext(config)
        }
        prefs.localization.displayKeyboardLabelsInSubtypeLanguage.asFlow().collectIn(lifecycleScope) { shouldSync ->
            val config = Configuration(resources.configuration)
            if (shouldSync) {
                config.setLocale(subtypeManager.activeSubtype.primaryLocale.base)
            }
            resourcesContext = createConfigurationContext(config)
        }
        prefs.physicalKeyboard.showOnScreenKeyboard.asFlow().collectIn(lifecycleScope) {
            updateInputViewShown()
        }
        @Suppress("DEPRECATION") // We do not retrieve the wallpaper but only listen to changes
        registerReceiver(wallpaperChangeReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
    }

    override fun onCreateInputView(): View {
        super.installViewTreeOwners()
        // Instantiate and install bottom sheet host UI view
        val bottomSheetView = FlorisBottomSheetHostUiView()
        window.window!!.findViewById<ViewGroup>(android.R.id.content).addView(bottomSheetView)
        // Instantiate and return input view
        val composeView = ComposeInputView()
        inputWindowView = composeView
        return composeView
    }

    override fun onCreateCandidatesView(): View? {
        // Disable the default candidates view
        return null
    }

    override fun onCreateExtractTextView(): View {
        super.installViewTreeOwners()
        // Consider adding a fallback to the default extract edit layout if user reports come
        // that this causes a crash, especially if the device manufacturer of the user device
        // is a known one to break AOSP standards...
        val defaultExtractView = super.onCreateExtractTextView()
        if (defaultExtractView == null || defaultExtractView !is ViewGroup) {
            return ComposeExtractedLandscapeInputView(null)
        }
        val extractEditText = defaultExtractView.findViewById<ExtractEditText>(android.R.id.inputExtractEditText)
        (extractEditText?.parent as? ViewGroup)?.removeView(extractEditText)
        defaultExtractView.apply {
            removeAllViews()
            addView(ComposeExtractedLandscapeInputView(extractEditText))
        }
        return defaultExtractView
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        themeManager.configurationChangeCounter.update { it + 1 }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wallpaperChangeReceiver)
        policyController.onFinishInput() // FORK(S1-policy): drop in-flight work for the closed field
        voiceSession.destroy() // FORK(S1-voice)
        translateSession.destroy() // FORK(S2-bar)
        FlorisImeServiceReference = WeakReference(null)
        inputWindowView = null
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        flogInfo { "restarting=$restarting info=${info?.debugSummarize()}" }
        super.onStartInput(info, restarting)
        voiceSession.onStartInput(restarting) // FORK(S1-voice): a new field's connection holds no region of ours
        translateSession.onStartInput(restarting) // FORK(S2-bar): same for the bar's live preview
        if (!restarting) privateAppUndo.onFieldChange() // FORK(S1-smartbar): a new field ends the Undo offer
        policyController.onStartInput(info, restarting) // FORK(S1-policy): decide before any feature sees the field
        suggestContext().update(info, policyController.decision.value) // FORK(S3-suggest): field facts for autocorrect
        if (info == null) return
        val editorInfo = FlorisEditorInfo.wrap(info)
        editorInstance.handleStartInput(editorInfo)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        flogInfo { "restarting=$restarting info=${info?.debugSummarize()}" }
        super.onStartInputView(info, restarting)
        if (info == null) return
        val editorInfo = FlorisEditorInfo.wrap(info)
        // FORK(S1-analytics): the decision for this field is already set by onStartInput
        imeSessionReporter.onStartInputView(policyController.decision.value, subtypeManager.activeSubtype.primaryLocale.languageTag())
        translateSession.onStartInputView() // FORK(S2-bar): the keyboard is back, keep the bar text kept on hide
        activeState.batchEdit {
            // FORK(S1-policy): a restart never keeps the clipboard panel open where history is denied
            activeState.imeUiMode = ClipboardPanelGate.onStartInputView(
                activeState.imeUiMode,
                prefs.clipboard.historyHideOnNextTextField.get(),
                policyController.decision.value,
            )
            activeState.isSelectionMode = editorInfo.initialSelection.isSelectionMode
            editorInstance.handleStartInputView(editorInfo, isRestart = restarting)
        }
    }

    override fun onEvaluateInputViewShown(): Boolean {
        val config = resources.configuration
        return super.onEvaluateInputViewShown()
            || config.keyboard == Configuration.KEYBOARD_NOKEYS
            || prefs.physicalKeyboard.showOnScreenKeyboard.get()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        flogInfo { "old={start=$oldSelStart,end=$oldSelEnd} new={start=$newSelStart,end=$newSelEnd} composing={start=$candidatesStart,end=$candidatesEnd}" }
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        voiceSession.onSelectionUpdate(newSelStart, newSelEnd, candidatesStart, candidatesEnd) // FORK(S1-voice)
        translateSession.onSelectionUpdate(newSelStart, newSelEnd, candidatesStart, candidatesEnd) // FORK(S2-bar)
        activeState.batchEdit {
            activeState.isSelectionMode = (newSelEnd - newSelStart) != 0
            editorInstance.handleSelectionUpdate(
                oldSelection = EditorRange.normalized(oldSelStart, oldSelEnd),
                newSelection = EditorRange.normalized(newSelStart, newSelEnd),
                composing = EditorRange.normalized(candidatesStart, candidatesEnd),
            )
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        flogInfo { "finishing=$finishingInput" }
        super.onFinishInputView(finishingInput)
        policyController.onFinishInputView() // FORK(S1-policy)
        voiceSession.onFinishInputView() // FORK(S1-voice): hiding the keyboard ends dictation
        translateSession.onFinishInputView() // FORK(S2-bar): hiding closes the bar, pair and mode are kept
        editorInstance.handleFinishInputView()
    }

    override fun onFinishInput() {
        flogInfo { "(no args)" }
        voiceSession.onFinishInput() // FORK(S1-voice): discard the partial before super finishes composing (R39)
        translateSession.onFinishInput() // FORK(S2-bar): before policy's "no field", so a sensitive field drops the cache
        privateAppUndo.onFieldChange() // FORK(S1-smartbar): the field closed, the app stays private
        super.onFinishInput()
        policyController.onFinishInput() // FORK(S1-policy)
        suggestContext().clear() // FORK(S3-suggest): no field, no suggestions or learning
        editorInstance.handleFinishInput()
        NlpInlineAutofill.clearInlineSuggestions()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (isWindowShown) {
            flogWarning(LogTopic.IMS_EVENTS) { "Ignoring (is already shown)" }
            return
        } else {
            flogInfo(LogTopic.IMS_EVENTS)
        }
        isWindowShown = true
        inputFeedbackController.updateSystemPrefsState()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (!isWindowShown) {
            flogWarning(LogTopic.IMS_EVENTS) { "Ignoring (is already hidden)" }
            return
        } else {
            flogInfo(LogTopic.IMS_EVENTS)
        }
        isWindowShown = false
        activeState.batchEdit {
            activeState.imeUiMode = ImeUiMode.TEXT
            activeState.isActionsOverflowVisible = false
            activeState.isActionsEditorVisible = false
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        val config = resources.configuration
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            return false
        }
        return when (prefs.keyboard.landscapeInputUiMode.get()) {
            LandscapeInputUiMode.DYNAMICALLY_SHOW -> super.onEvaluateFullscreenMode()
            LandscapeInputUiMode.NEVER_SHOW -> false
            LandscapeInputUiMode.ALWAYS_SHOW -> true
        }
    }

    override fun updateFullscreenMode() {
        super.updateFullscreenMode()
        isFullscreenUiMode = isFullscreenMode
        updateSoftInputWindowLayoutParameters()
    }

    override fun onUpdateExtractingVisibility(info: EditorInfo?) {
        if (info != null) {
            editorInstance.handleStartInputView(FlorisEditorInfo.wrap(info), isRestart = true)
        }
        when (prefs.keyboard.landscapeInputUiMode.get()) {
            LandscapeInputUiMode.DYNAMICALLY_SHOW -> super.onUpdateExtractingVisibility(info)
            LandscapeInputUiMode.NEVER_SHOW -> isExtractViewShown = false
            LandscapeInputUiMode.ALWAYS_SHOW -> isExtractViewShown = true
        }
    }

    override fun setExtractViewShown(shown: Boolean) {
        super.setExtractViewShown(shown)
        isExtractUiShown = shown
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!prefs.smartbar.enabled.get() || !prefs.suggestion.api30InlineSuggestionsEnabled.get()) {
            flogInfo(LogTopic.IMS_EVENTS) {
                "Ignoring inline suggestions request because Smartbar and/or inline suggestions are disabled."
            }
            return null
        }

        flogInfo(LogTopic.IMS_EVENTS) { "Creating inline suggestions request" }
        val stylesBundle = themeManager.createInlineSuggestionUiStyleBundle(this)
        if (stylesBundle == null) {
            flogWarning(LogTopic.IMS_EVENTS) { "Failed to retrieve inline suggestions style bundle" }
            return null
        }
        val spec = InlinePresentationSpec.Builder(
            InlineSuggestionUiSmallestSize,
            InlineSuggestionUiBiggestSize,
        ).run {
            setStyle(stylesBundle)
            build()
        }

        return InlineSuggestionsRequest.Builder(listOf(spec)).run {
            setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
            build()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        val inlineSuggestions = response.inlineSuggestions
        flogInfo(LogTopic.IMS_EVENTS) {
            "Received inline suggestions response with ${inlineSuggestions.size} suggestion(s) provided."
        }
        return NlpInlineAutofill.showInlineSuggestions(this, inlineSuggestions)
    }

    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        if (outInsets == null) return

        val inputWindowView = inputWindowView ?: return
        // TODO: Check also if the keyboard is currently suppressed by a hardware keyboard
        if (!isInputViewShown) {
            outInsets.contentTopInsets = inputWindowView.height
            outInsets.visibleTopInsets = inputWindowView.height
            return
        }

        val visibleTopY = inputWindowView.height - inputViewSize.height
        val needAdditionalOverlay =
            prefs.smartbar.enabled.get() &&
                prefs.smartbar.layout.get() == SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED &&
                prefs.smartbar.extendedActionsExpanded.get() &&
                prefs.smartbar.extendedActionsPlacement.get() == ExtendedActionsPlacement.OVERLAY_APP_UI &&
                keyboardManager.activeState.imeUiMode == ImeUiMode.TEXT

        outInsets.contentTopInsets = visibleTopY
        outInsets.visibleTopInsets = visibleTopY
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        val left = 0
        val top = if (keyboardManager.activeState.isBottomSheetShowing() || keyboardManager.activeState.isSubtypeSelectionShowing()) {
            0
        } else {
            visibleTopY - if (needAdditionalOverlay) FlorisImeSizing.Static.smartbarHeightPx else 0
        }
        val right = inputViewSize.width
        val bottom = inputWindowView.height
        outInsets.touchableRegion.set(left, top, right, bottom)
    }

    /**
     * Updates the layout params of the window and compose input view.
     */
    private fun updateSoftInputWindowLayoutParameters() {
        val w = window?.window ?: return
        // TODO: Verify that this doesn't give us a padding problem
        WindowCompat.setDecorFitsSystemWindows(w, false)
        ViewUtils.updateLayoutHeightOf(w, WindowManager.LayoutParams.MATCH_PARENT)
        val layoutHeight = if (isFullscreenUiMode) {
            WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            WindowManager.LayoutParams.MATCH_PARENT
        }
        val inputArea = w.findViewById<View>(android.R.id.inputArea) ?: return
        ViewUtils.updateLayoutHeightOf(inputArea, layoutHeight)
        ViewUtils.updateLayoutGravityOf(inputArea, Gravity.BOTTOM)
        val inputWindowView = inputWindowView ?: return
        ViewUtils.updateLayoutHeightOf(inputWindowView, layoutHeight)
    }

    override fun getTextForImeAction(imeOptions: Int): String? {
        return try {
            when (imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_NONE -> null
                EditorInfo.IME_ACTION_GO -> resourcesContext.getString(AndroidInternalR.string.ime_action_go)
                EditorInfo.IME_ACTION_SEARCH -> resourcesContext.getString(AndroidInternalR.string.ime_action_search)
                EditorInfo.IME_ACTION_SEND -> resourcesContext.getString(AndroidInternalR.string.ime_action_send)
                EditorInfo.IME_ACTION_NEXT -> resourcesContext.getString(AndroidInternalR.string.ime_action_next)
                EditorInfo.IME_ACTION_DONE -> resourcesContext.getString(AndroidInternalR.string.ime_action_done)
                EditorInfo.IME_ACTION_PREVIOUS -> resourcesContext.getString(AndroidInternalR.string.ime_action_previous)
                else -> resourcesContext.getString(AndroidInternalR.string.ime_action_default)
            }
        } catch (_: Throwable) {
            super.getTextForImeAction(imeOptions)?.toString()
        }
    }

    @Composable
    private fun ImeUiWrapper() {
        ProvideLocalizedResources(
            resourcesContext,
            appName = R.string.app_name,
        ) {
            ProvideKeyboardRowBaseHeight {
                CompositionLocalProvider(LocalInputFeedbackController provides inputFeedbackController) {
                    FlorisImeTheme {
                        // Do not apply system bar padding here yet, we want to draw it ourselves
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (!(isFullscreenUiMode && isExtractUiShown)) {
                                DevtoolsOverlay(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            }
                            ImeUi()
                            SystemUiIme()
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun ImeUi() {
        val state by keyboardManager.activeState.collectAsState()
        val attributes = mapOf(
            FlorisImeUi.Attr.Mode to state.keyboardMode.toString(),
            FlorisImeUi.Attr.ShiftState to state.inputShiftState.toString(),
        )
        val layoutDirection = LocalLayoutDirection.current
        LaunchedEffect(layoutDirection) {
            keyboardManager.activeState.layoutDirection = layoutDirection
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            SnyggBox(
                elementName = FlorisImeUi.Window.elementName,
                attributes = attributes,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .onGloballyPositioned { coords -> inputViewSize = coords.size },
                clickAndSemanticsModifier = Modifier
                    // Do not remove below line or touch input may get stuck
                    .pointerInteropFilter { false },
                supportsBackgroundImage = !AndroidVersion.ATLEAST_API30_R,
                allowClip = false,
            ) {
                // The SurfaceView is used to render the background image under inline-autofill chips. These are only
                // available on Android >=11, and SurfaceView causes trouble on Android 8/9, thus we render the image
                // in the SurfaceView for Android >=11, and in the Compose View Tree for Android <=10.
                if (AndroidVersion.ATLEAST_API30_R) {
                    SnyggSurfaceView(
                        elementName = FlorisImeUi.Window.elementName,
                        attributes = attributes,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                val configuration = LocalConfiguration.current
                val bottomOffset by if (configuration.isOrientationPortrait()) {
                    prefs.keyboard.bottomOffsetPortrait
                } else {
                    prefs.keyboard.bottomOffsetLandscape
                }.observeAsTransformingState { it.dp }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        // Apply system bars padding here (we already drew our keyboard background)
                        .safeDrawingPadding()
                        .padding(bottom = bottomOffset),
                ) {
                    val oneHandedMode by prefs.keyboard.oneHandedMode.observeAsState()
                    val oneHandedModeEnabled by prefs.keyboard.oneHandedModeEnabled.observeAsState()
                    val oneHandedModeScaleFactor by prefs.keyboard.oneHandedModeScaleFactor.observeAsState()
                    val keyboardWeight = when {
                        !oneHandedModeEnabled || configuration.isOrientationLandscape() -> 1f
                        else -> oneHandedModeScaleFactor / 100f
                    }
                    if (oneHandedModeEnabled && oneHandedMode == OneHandedMode.END && configuration.isOrientationPortrait()) {
                        OneHandedPanel(
                            panelSide = OneHandedMode.START,
                            weight = 1f - keyboardWeight,
                        )
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                        Box(
                            modifier = Modifier
                                .weight(keyboardWeight)
                                .wrapContentHeight(),
                        ) {
                            when (state.imeUiMode) {
                                ImeUiMode.TEXT -> TextInputLayout()
                                ImeUiMode.MEDIA -> MediaInputLayout()
                                ImeUiMode.CLIPBOARD -> ClipboardInputLayout()
                            }
                        }
                    }
                    if (oneHandedModeEnabled && oneHandedMode == OneHandedMode.START && configuration.isOrientationPortrait()) {
                        OneHandedPanel(
                            panelSide = OneHandedMode.END,
                            weight = 1f - keyboardWeight,
                        )
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return keyboardManager.onHardwareKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return keyboardManager.onHardwareKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }

    private inner class ComposeInputView : AbstractComposeView(this) {
        init {
            isHapticFeedbackEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        @Composable
        override fun Content() {
            ImeUiWrapper()
        }

        override fun getAccessibilityClassName(): CharSequence {
            return javaClass.name
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            updateSoftInputWindowLayoutParameters()
        }
    }

    private inner class FlorisBottomSheetHostUiView : AbstractComposeView(this) {
        init {
            isHapticFeedbackEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        @Composable
        override fun Content() {
            val context = LocalContext.current
            val keyboardManager by context.keyboardManager()
            val state by keyboardManager.activeState.collectAsState()

            ProvideLocalizedResources(
                resourcesContext,
                appName = R.string.app_name,
                forceLayoutDirection = LayoutDirection.Ltr,
            ) {
                FlorisImeTheme {
                    BottomSheetHostUi(
                        isShowing = state.isBottomSheetShowing() || state.isSubtypeSelectionShowing(),
                        onHide = {
                            if (state.isBottomSheetShowing()) {
                                keyboardManager.activeState.isActionsEditorVisible = false
                            }
                            if (state.isSubtypeSelectionShowing()) {
                                keyboardManager.activeState.isSubtypeSelectionVisible = false
                            }
                        },
                    ) {
                        if (state.isBottomSheetShowing()) {
                            QuickActionsEditorPanel()
                        }
                        if (state.isSubtypeSelectionShowing()) {
                            SelectSubtypePanel()
                        }
                    }
                }
            }
        }

        override fun getAccessibilityClassName(): CharSequence {
            return javaClass.name
        }
    }

    private inner class ComposeExtractedLandscapeInputView(eet: ExtractEditText?) : FrameLayout(this) {
        val composeView: ComposeView
        val extractEditText: ExtractEditText

        init {
            isHapticFeedbackEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

            extractEditText = (eet ?: ExtractEditText(context)).also {
                it.id = android.R.id.inputExtractEditText
                it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                it.background = null
                it.gravity = Gravity.TOP
                it.isVerticalScrollBarEnabled = true
            }
            addView(extractEditText)

            composeView = ComposeView(context).also { it.setContent { Content() } }
            addView(composeView)
        }

        @Composable
        fun Content() {
            ProvideLocalizedResources(
                resourcesContext,
                appName = R.string.app_name,
                forceLayoutDirection = LayoutDirection.Ltr,
            ) {
                FlorisImeTheme {
                    val activeEditorInfo by editorInstance.activeInfoFlow.collectAsState()
                    SnyggBox(FlorisImeUi.ExtractedLandscapeInputLayout.elementName) {
                        SnyggRow(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SnyggBox(
                                elementName = FlorisImeUi.ExtractedLandscapeInputLayout.elementName,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f),
                            ) {
                                val fieldStyle = rememberSnyggThemeQuery(FlorisImeUi.ExtractedLandscapeInputField.elementName)
                                val foreground = fieldStyle.foreground()
                                AndroidView(
                                    factory = { extractEditText },
                                    update = { view ->
                                        view.background = null
                                        view.backgroundTintList = null
                                        view.foregroundTintList = null
                                        view.setTextColor(foreground.toArgb())
                                        view.setHintTextColor(foreground.copy(foreground.alpha * 0.6f).toArgb())
                                        view.setTextSize(
                                            TypedValue.COMPLEX_UNIT_SP,
                                            fieldStyle.fontSize(default = 16.sp).value,
                                        )
                                    },
                                )
                            }
                            SnyggButton(
                                FlorisImeUi.ExtractedLandscapeInputAction.elementName,
                                onClick = {
                                    if (activeEditorInfo.extractedActionId != 0) {
                                        currentInputConnection?.performEditorAction(activeEditorInfo.extractedActionId)
                                    } else {
                                        editorInstance.performEnterAction(activeEditorInfo.imeOptions.action)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                SnyggText(
                                    text = activeEditorInfo.extractedActionLabel
                                        ?: getTextForImeAction(activeEditorInfo.imeOptions.action.toInt())
                                        ?: "ACTION",
                                )
                            }
                        }
                    }
                }
            }
        }

        override fun getAccessibilityClassName(): CharSequence {
            return javaClass.name
        }

        override fun onAttachedToWindow() {
            removeView(extractEditText)
            super.onAttachedToWindow()
            try {
                (parent as LinearLayout).let { extractEditLayout ->
                    extractEditLayout.layoutParams = LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                    ).also { it.setMargins(0, 0, 0, 0) }
                    extractEditLayout.setPadding(0, 0, 0, 0)
                }
            } catch (e: Throwable) {
                flogError { e.message.toString() }
            }
        }
    }
}
