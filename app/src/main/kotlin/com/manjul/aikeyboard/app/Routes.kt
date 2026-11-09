/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 * Modified by AI Keyboard contributors, 2026. FORK(S4-themes): Themes route. FORK(S4-settings): settings tree.
 * FORK(S4-privacy): Delete my data route
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

package com.manjul.aikeyboard.app

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.manjul.aikeyboard.app.ads.NavTransitionReporter // FORK(S4-ads)
import com.manjul.aikeyboard.app.devtools.AndroidLocalesScreen
import com.manjul.aikeyboard.app.devtools.AndroidSettingsScreen
import com.manjul.aikeyboard.app.devtools.DevtoolsScreen
import com.manjul.aikeyboard.app.devtools.ExportDebugLogScreen
import com.manjul.aikeyboard.app.ext.CheckUpdatesScreen
import com.manjul.aikeyboard.app.ext.ExtensionEditScreen
import com.manjul.aikeyboard.app.ext.ExtensionExportScreen
import com.manjul.aikeyboard.app.ext.ExtensionHomeScreen
import com.manjul.aikeyboard.app.ext.ExtensionImportScreen
import com.manjul.aikeyboard.app.ext.ExtensionImportScreenType
import com.manjul.aikeyboard.app.ext.ExtensionListScreen
import com.manjul.aikeyboard.app.ext.ExtensionListScreenType
import com.manjul.aikeyboard.app.ext.ExtensionViewScreen
import com.manjul.aikeyboard.app.settings.HomeScreen
import com.manjul.aikeyboard.app.settings.about.AboutScreen
import com.manjul.aikeyboard.app.settings.about.ProjectLicenseScreen
import com.manjul.aikeyboard.app.settings.about.ThirdPartyLicensesScreen
import com.manjul.aikeyboard.app.settings.advanced.BackupScreen
import com.manjul.aikeyboard.app.settings.advanced.OtherScreen
import com.manjul.aikeyboard.app.settings.advanced.PhysicalKeyboardScreen
import com.manjul.aikeyboard.app.settings.advanced.RestoreScreen
import com.manjul.aikeyboard.app.settings.clipboard.ClipboardScreen
import com.manjul.aikeyboard.app.settings.dictionary.DictionaryScreen
import com.manjul.aikeyboard.app.settings.dictionary.UserDictionaryScreen
import com.manjul.aikeyboard.app.settings.dictionary.UserDictionaryType
import com.manjul.aikeyboard.app.settings.gestures.GesturesScreen
import com.manjul.aikeyboard.app.settings.keyboard.InputFeedbackScreen
import com.manjul.aikeyboard.app.settings.keyboard.KeyboardScreen
import com.manjul.aikeyboard.app.settings.localization.LanguagePackManagerScreen
import com.manjul.aikeyboard.app.settings.localization.LanguagePackManagerScreenAction
import com.manjul.aikeyboard.app.settings.localization.LocalizationScreen
import com.manjul.aikeyboard.app.settings.localization.SelectLocaleScreen
import com.manjul.aikeyboard.app.settings.localization.SubtypeEditorScreen
import com.manjul.aikeyboard.app.settings.media.MediaScreen
import com.manjul.aikeyboard.app.settings.smartbar.SmartbarScreen
import com.manjul.aikeyboard.fork.privacy.PrivateAppsScreen // FORK(S1-smartbar)
import com.manjul.aikeyboard.fork.settings.VoiceSettingsScreen // FORK(S1-whisper)
import com.manjul.aikeyboard.fork.settings.TypingSettingsScreen // FORK(S3-userdict)
import com.manjul.aikeyboard.fork.settings.OfflineLanguagesScreen // FORK(S2-packs)
import com.manjul.aikeyboard.fork.translator.HistoryScreen // FORK(S2-translator)
import com.manjul.aikeyboard.fork.translator.TranslatorScreen // FORK(S2-translator)
import com.manjul.aikeyboard.fork.paywall.PaywallRoute // FORK(S4-paywall)
import com.manjul.aikeyboard.fork.paywall.PaywallTrigger // FORK(S4-paywall)
import com.manjul.aikeyboard.fork.themes.ThemesScreen // FORK(S4-themes)
import com.manjul.aikeyboard.fork.settings.AiSettingsScreen // FORK(S3-polish)
import android.app.Application // FORK(S4-settings)
import com.manjul.aikeyboard.app.ads.AdsAppGraph // FORK(S4-settings)
import com.manjul.aikeyboard.fork.settings.PrivacySettingsScreen // FORK(S4-settings)
import com.manjul.aikeyboard.fork.privacy.DeleteDataScreen // FORK(S4-privacy)
import com.manjul.aikeyboard.fork.settings.SettingsScreen // FORK(S4-settings)
import com.manjul.aikeyboard.fork.settings.SubscriptionSettingsScreen // FORK(S4-settings)
import com.manjul.aikeyboard.fork.settings.TranslateSettingsScreen // FORK(S4-settings)
import com.manjul.aikeyboard.featureSettingsStore // FORK(S4-billing)
import com.manjul.aikeyboard.fork.billing.proGate // FORK(S4-billing)
import androidx.compose.runtime.remember // FORK(S4-billing)
import androidx.compose.ui.platform.LocalContext // FORK(S4-billing)
import com.manjul.aikeyboard.app.settings.theme.ThemeManagerScreen
import com.manjul.aikeyboard.app.settings.theme.ThemeManagerScreenAction
import com.manjul.aikeyboard.app.settings.theme.ThemeScreen
import com.manjul.aikeyboard.app.settings.typing.TypingScreen
import com.manjul.aikeyboard.fork.onboarding.OnboardingScreen // FORK(S1-onboarding)
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Deeplink(val path: String)

inline fun <reified T : Any> NavGraphBuilder.composableWithDeepLink(
    kClass: KClass<T>,
    noinline content: @Composable (AnimatedContentScope.(NavBackStackEntry) -> Unit),
) {
    val deeplink = requireNotNull(kClass.annotations.firstOrNull { it is Deeplink } as? Deeplink) {
        "faulty class: $kClass with annotations ${kClass.annotations}"
    }
    composable<T>(
        deepLinks = listOf(navDeepLink<T>(basePath = "ui://florisboard/${deeplink.path}")),
        content = content,
    )
}

object Routes {
    object Setup {
        @Serializable
        object Screen
    }

    // FORK(S2-translator): the Translator screen (PRD FR-12) and its history, outside the settings tree
    @Serializable
    @Deeplink("translator")
    object Translator

    @Serializable
    @Deeplink("translator/history")
    object TranslatorHistory // FORK(S2-translator)

    // FORK(S4-paywall): the paywall (S-03) replaces the S3 Pro placeholder. RouteRegistry maps "pro" to
    // "paywall?trigger=ai_quota"; an unknown trigger shows the AI quota header.
    @Serializable
    @Deeplink("paywall")
    data class Paywall(val trigger: String = "ai_quota")

    // FORK(S4-themes): the Themes screen (presets, key border, key sound; PRD FR-32 route "themes")
    @Serializable
    @Deeplink("themes")
    object Themes

    object Settings {
        @Serializable
        @Deeplink("settings/home")
        object Home

        // FORK(S4-settings): the settings tree (PRD FR-09): its root and the new Translate, Privacy and Subscription
        // sections. The other sections open existing screens (SettingsSection.path).
        @Serializable
        @Deeplink("settings/all")
        object All

        @Serializable
        @Deeplink("settings/translate")
        object Translate // FORK(S4-settings)

        @Serializable
        @Deeplink("settings/privacy")
        object Privacy // FORK(S4-settings)

        @Serializable
        @Deeplink("settings/privacy/delete-data")
        object DeleteData // FORK(S4-privacy): not on the RouteRegistry allow-list; opened from Privacy only

        @Serializable
        @Deeplink("settings/subscription")
        object Subscription // FORK(S4-settings)

        @Serializable
        @Deeplink("settings/localization")
        object Localization

        @Serializable
        @Deeplink("settings/localization/select-locale")
        object SelectLocale

        @Serializable
        @Deeplink("settings/localization/language-pack-manage")
        data class LanguagePackManager(val action: LanguagePackManagerScreenAction)

        @Serializable
        @Deeplink("settings/localization/subtype/add")
        object SubtypeAdd

        @Serializable
        @Deeplink("settings/localization/subtype/edit")
        data class SubtypeEdit(val id: Long)

        @Serializable
        @Deeplink("settings/theme")
        object Theme

        @Serializable
        @Deeplink("settings/theme/manage")
        data class ThemeManager(val action: ThemeManagerScreenAction)

        @Serializable
        @Deeplink("settings/keyboard")
        object Keyboard

        @Serializable
        @Deeplink("settings/keyboard/input-feedback")
        object InputFeedback

        @Serializable
        @Deeplink("settings/smartbar")
        object Smartbar

        // FORK(S1-smartbar): Private apps screen, reached from the Settings home (no Privacy parent screen)
        @Serializable
        @Deeplink("settings/privacy/private-apps")
        object PrivateApps

        // FORK(S1-whisper): Voice settings (engine, auto-stop, offline Whisper models), reached from the Settings home
        @Serializable
        @Deeplink("settings/voice")
        object Voice

        // FORK(S3-userdict): Suggestions and autocorrect (fork Typing settings, clear learned words), from the Settings home
        @Serializable
        @Deeplink("settings/suggestions")
        object Suggestions

        // FORK(S2-packs): Offline languages (translation packs, voice models); the keyboard's offline chip links here
        @Serializable
        @Deeplink("settings/offline-languages")
        object OfflineLanguages

        // FORK(S3-polish): AI settings (Polish output script, auto-polish, AI consent), reached from the Settings home
        @Serializable
        @Deeplink("settings/ai")
        object Ai

        @Serializable
        @Deeplink("settings/typing")
        object Typing

        @Serializable
        @Deeplink("settings/dictionary")
        object Dictionary

        @Serializable
        @Deeplink("settings/dictionary/user-dictionary")
        data class UserDictionary(val type: UserDictionaryType)

        @Serializable
        @Deeplink("settings/gestures")
        object Gestures

        @Serializable
        @Deeplink("settings/clipboard")
        object Clipboard

        @Serializable
        @Deeplink("settings/media")
        object Media

        @Serializable
        @Deeplink("settings/other")
        object Other

        @Serializable
        @Deeplink("settings/other/physical-keyboard")
        object PhysicalKeyboard

        @Serializable
        @Deeplink("settings/other/backup")
        object Backup

        @Serializable
        @Deeplink("settings/other/restore")
        object Restore

        @Serializable
        @Deeplink("settings/about")
        object About

        @Serializable
        @Deeplink("settings/about/project-license")
        object ProjectLicense

        @Serializable
        @Deeplink("settings/about/third-party-licenses")
        object ThirdPartyLicenses
    }

    object Devtools {
        @Serializable
        @Deeplink("devtools")
        object Home

        @Serializable
        @Deeplink("devtools/android/locales")
        object AndroidLocales

        @Serializable
        @Deeplink("devtools/android/settings")
        data class AndroidSettings(val name: String)

        @Serializable
        @Deeplink("export-debug-log")
        object ExportDebugLog
    }

    object Ext {
        @Serializable
        @Deeplink("ext")
        object Home

        @Serializable
        @Deeplink("ext/list")
        data class List(val type: ExtensionListScreenType, val showUpdate: Boolean? = null)

        @Serializable
        @Deeplink("ext/edit")
        data class Edit(val id: String, @SerialName("create") val serialType: String? = null)

        @Serializable
        @Deeplink("ext/export")
        data class Export(val id: String)

        @Serializable
        @Deeplink("ext/import")
        data class Import(val type: ExtensionImportScreenType, val uuid: String? = null)

        @Serializable
        @Deeplink("ext/view")
        data class View(val id: String)

        @Serializable
        @Deeplink("ext/check-updates")
        object CheckUpdates
    }

    @Composable
    fun AppNavHost(
        modifier: Modifier,
        navController: NavHostController,
        startDestination: KClass<*>,
    ) {
        // FORK(S4-ads): feature-to-Home steps are the only interstitial trigger; onboarding blocks app-open ads
        NavTransitionReporter(navController)
        NavHost(
            modifier = modifier,
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                slideIn { IntOffset(it.width, 0) } + fadeIn()
            },
            exitTransition = {
                slideOut { IntOffset(-it.width, 0) } + fadeOut()
            },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = {
                scaleOut(
                    targetScale = 0.85F,
                    transformOrigin = TransformOrigin(pivotFractionX = 0.8f, pivotFractionY = 0.5f)
                ) + fadeOut(spring(stiffness = Spring.StiffnessMedium))
            },
        ) {
            // FORK(S1-onboarding): the setup route shows the new onboarding flow; upstream SetupScreen stays, unused;
            // its notification-permission step is deferred (ruling R17)
            composable<Setup.Screen> { OnboardingScreen() }

            composableWithDeepLink(Settings.Home::class) { HomeScreen() }
            // FORK(S4-settings): the settings tree. Ad privacy comes from the app-process ads stack (AD_HOST_CALLERS).
            composableWithDeepLink(Settings.All::class) { SettingsScreen() }
            composableWithDeepLink(Settings.Translate::class) { TranslateSettingsScreen() } // FORK(S4-settings)
            composableWithDeepLink(Settings.Privacy::class) { // FORK(S4-settings)
                val app = LocalContext.current.applicationContext as Application
                PrivacySettingsScreen(adPrivacy = remember(app) { AdsAppGraph.get(app).privacyOptions })
            }
            composableWithDeepLink(Settings.DeleteData::class) { // FORK(S4-privacy): ad state clear from app.ads
                val app = LocalContext.current.applicationContext as Application
                DeleteDataScreen(clearAdState = remember(app) { AdsAppGraph.get(app)::clearAdState })
            }
            composableWithDeepLink(Settings.Subscription::class) { SubscriptionSettingsScreen() } // FORK(S4-settings)

            composableWithDeepLink(Settings.Localization::class) { LocalizationScreen() }
            composableWithDeepLink(Settings.SelectLocale::class) { SelectLocaleScreen() }
            composableWithDeepLink(Settings.LanguagePackManager::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.LanguagePackManager>()
                LanguagePackManagerScreen(payload.action)
            }
            composableWithDeepLink(Settings.SubtypeAdd::class) { SubtypeEditorScreen(null) }
            composableWithDeepLink(Settings.SubtypeEdit::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.SubtypeEdit>()
                SubtypeEditorScreen(payload.id)
            }

            composableWithDeepLink(Settings.Theme::class) { ThemeScreen() }
            composableWithDeepLink(Settings.ThemeManager::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.ThemeManager>()
                ThemeManagerScreen(payload.action)
            }

            composableWithDeepLink(Settings.Keyboard::class) { KeyboardScreen() }
            composableWithDeepLink(Settings.InputFeedback::class) { InputFeedbackScreen() }

            composableWithDeepLink(Settings.Smartbar::class) { SmartbarScreen() }

            composableWithDeepLink(Settings.PrivateApps::class) { PrivateAppsScreen() } // FORK(S1-smartbar)
            composableWithDeepLink(Settings.Voice::class) { VoiceSettingsScreen() } // FORK(S1-whisper)
            composableWithDeepLink(Settings.OfflineLanguages::class) { OfflineLanguagesScreen() } // FORK(S2-packs)
            composableWithDeepLink(Settings.Suggestions::class) { TypingSettingsScreen() } // FORK(S3-userdict)
            composableWithDeepLink(Translator::class) { TranslatorScreen() } // FORK(S2-translator)
            composableWithDeepLink(TranslatorHistory::class) { HistoryScreen() } // FORK(S2-translator)
            // FORK(S4-paywall): the paywall route; close goes back, or to Home when it was the first screen
            composableWithDeepLink(Paywall::class) { entry ->
                val trigger = PaywallTrigger.fromWire(entry.toRoute<Paywall>().trigger) ?: PaywallTrigger.AI_QUOTA
                PaywallRoute(trigger) {
                    if (!navController.popBackStack()) navController.navigate(Settings.Home)
                }
            }
            composableWithDeepLink(Themes::class) { ThemesScreen() } // FORK(S4-themes)
            composableWithDeepLink(Settings.Ai::class) { // FORK(S3-polish)
                // FORK(S4-billing): auto-polish unlocks from the signed Pro cache
                val store = LocalContext.current.featureSettingsStore().value
                AiSettingsScreen(isPro = remember(store) { proGate(store) })
            }

            composableWithDeepLink(Settings.Typing::class) { TypingScreen() }

            composableWithDeepLink(Settings.Dictionary::class) { DictionaryScreen() }
            composableWithDeepLink(Settings.UserDictionary::class) { navBackStack ->
                val payload = navBackStack.toRoute<Settings.UserDictionary>()
                UserDictionaryScreen(payload.type)
            }

            composableWithDeepLink(Settings.Gestures::class) { GesturesScreen() }

            composableWithDeepLink(Settings.Clipboard::class) { ClipboardScreen() }

            composableWithDeepLink(Settings.Media::class) { MediaScreen() }

            composableWithDeepLink(Settings.Other::class) { OtherScreen() }
            composableWithDeepLink(Settings.PhysicalKeyboard::class) { PhysicalKeyboardScreen() }
            composableWithDeepLink(Settings.Backup::class) { BackupScreen() }
            composableWithDeepLink(Settings.Restore::class) { RestoreScreen() }

            composableWithDeepLink(Settings.About::class) { AboutScreen() }
            composableWithDeepLink(Settings.ProjectLicense::class) { ProjectLicenseScreen() }
            composableWithDeepLink(Settings.ThirdPartyLicenses::class) { ThirdPartyLicensesScreen() }

            composableWithDeepLink(Devtools.Home::class) { DevtoolsScreen() }
            composableWithDeepLink(Devtools.AndroidLocales::class) { AndroidLocalesScreen() }
            composableWithDeepLink(Devtools.AndroidSettings::class) { navBackStack ->
                val payload = navBackStack.toRoute<Devtools.AndroidSettings>()
                AndroidSettingsScreen(payload.name)
            }
            composableWithDeepLink(Devtools.ExportDebugLog::class) { ExportDebugLogScreen() }

            composableWithDeepLink(Ext.Home::class) { ExtensionHomeScreen() }
            composableWithDeepLink(Ext.List::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.List>()
                val showUpdate = payload.showUpdate != null && payload.showUpdate
                ExtensionListScreen(payload.type, showUpdate)
            }
            composableWithDeepLink(Ext.Edit::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.Edit>()
                val extensionId = payload.id
                val serialType = payload.serialType
                ExtensionEditScreen(
                    id = extensionId,
                    createSerialType = serialType.takeIf { !it.isNullOrBlank() },
                )
            }
            composableWithDeepLink(Ext.Export::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.Export>()
                val extensionId = payload.id
                ExtensionExportScreen(id = extensionId)
            }
            composableWithDeepLink(Ext.Import::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.Import>()
                val uuid = payload.uuid
                ExtensionImportScreen(payload.type, uuid)
            }
            composableWithDeepLink(Ext.View::class) { navBackStack ->
                val payload = navBackStack.toRoute<Ext.View>()
                val extensionId = payload.id
                ExtensionViewScreen(id = extensionId)
            }
            composableWithDeepLink(Ext.CheckUpdates::class) {
                CheckUpdatesScreen()
            }
        }
    }
}
