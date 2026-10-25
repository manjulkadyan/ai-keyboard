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

package com.manjul.aikeyboard.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock // FORK(S1-onboarding)
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.net.toUri // FORK(S2-routes)
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.apptheme.FlorisAppTheme
import com.manjul.aikeyboard.app.ext.ExtensionImportScreenType
import com.manjul.aikeyboard.app.setup.NotificationPermissionState
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.cacheManager
import com.manjul.aikeyboard.featureSettingsStore // FORK(S1-onboarding)
import com.manjul.aikeyboard.fork.routes.AppIntent // FORK(S2-routes)
import com.manjul.aikeyboard.fork.routes.AppIntents // FORK(S2-routes)
import com.manjul.aikeyboard.fork.routes.TranslatorHandoff // FORK(S2-routes)
import com.manjul.aikeyboard.lib.FlorisLocale
import com.manjul.aikeyboard.lib.compose.LocalPreviewFieldController
import com.manjul.aikeyboard.lib.compose.PreviewKeyboardField
import com.manjul.aikeyboard.lib.compose.rememberPreviewFieldController
import com.manjul.aikeyboard.lib.util.AppVersionUtils
import com.manjul.aikeyboard.lib.util.InputMethodUtils // FORK(S1-onboarding)
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.hideAppIcon
import org.florisboard.lib.android.showAppIcon
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.conditional
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.kotlin.collectIn
import kotlinx.coroutines.flow.first // FORK(S1-onboarding)
import kotlinx.coroutines.withTimeoutOrNull // FORK(S1-onboarding)
import java.util.concurrent.atomic.AtomicBoolean

// FORK(S1-onboarding)
private const val SPLASH_MAX_MS = 1000L

enum class AppTheme(val id: String) {
    AUTO("auto"),
    AUTO_AMOLED("auto_amoled"),
    LIGHT("light"),
    DARK("dark"),
    AMOLED_DARK("amoled_dark");
}

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("LocalNavController not initialized")
}

class FlorisAppActivity : ComponentActivity() {
    private val prefs by FlorisPreferenceStore
    private val appContext by appContext()
    private val cacheManager by cacheManager()
    private var appTheme by mutableStateOf(AppTheme.AUTO)
    private var showAppIcon = true
    private var resourcesContext by mutableStateOf(this as Context)
    // FORK(S2-routes): the classified intent (allow-listed route or extension import), not the raw intent
    private var intentToBeHandled by mutableStateOf<AppIntent?>(null)
    // FORK(S1-onboarding)
    private val featureSettingsStore by featureSettingsStore()
    private var startAtHome = false
    private var splashStart = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash screen should be installed before calling super.onCreate()
        // FORK(S1-onboarding): splash is held at most 1 s, even if the stores are slow to load
        splashStart = SystemClock.uptimeMillis()
        installSplashScreen().apply {
            setKeepOnScreenCondition {
                !appContext.preferenceStoreLoaded.value && SystemClock.uptimeMillis() - splashStart < SPLASH_MAX_MS
            }
        }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefs.other.settingsTheme.asFlow().collectIn(lifecycleScope) {
            appTheme = it
        }
        prefs.other.settingsLanguage.asFlow().collectIn(lifecycleScope) {
            val config = Configuration(resources.configuration)
            val locale = if (it == "auto") FlorisLocale.default() else FlorisLocale.fromTag(it)
            config.setLocale(locale.base)
            resourcesContext = createConfigurationContext(config)
        }
        if (AndroidVersion.ATMOST_API28_P) {
            prefs.other.showAppIcon.asFlow().collectIn(lifecycleScope) {
                showAppIcon = it
            }
        }

        // We defer the setContent call until the datastore model is loaded, until then the splash screen stays drawn
        val isModelLoaded = AtomicBoolean(false)
        appContext.preferenceStoreLoaded.collectIn(lifecycleScope) { loaded ->
            if (!loaded || isModelLoaded.getAndSet(true)) return@collectIn
            // Check if android 13+ is running and the NotificationPermission is not set
            if (AndroidVersion.ATLEAST_API33_T &&
                prefs.internal.notificationPermissionState.get() == NotificationPermissionState.NOT_SET
            ) {
                // update pref value to show the setup screen again
                prefs.internal.isImeSetUp.set(false)
            }
            AppVersionUtils.updateVersionOnInstallAndLastUse(this, prefs)
            // FORK(S1-onboarding): Home only for a finished onboarding with the keyboard enabled and selected
            // On timeout we fall back to onboarding (it sends a finished user on to Home), never a blank window.
            val remainingMs = (SPLASH_MAX_MS - (SystemClock.uptimeMillis() - splashStart)).coerceAtLeast(0)
            val settings = withTimeoutOrNull(remainingMs) { featureSettingsStore.flow.first() }
            startAtHome = settings?.onboardingDone == true &&
                InputMethodUtils.isFlorisboardEnabled(this) && InputMethodUtils.isFlorisboardSelected(this)
            setContent {
                ProvideLocalizedResources(
                    resourcesContext,
                    appName = R.string.app_name,
                ) {
                    FlorisAppTheme(theme = appTheme) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            AppContent()
                        }
                    }
                }
            }
            onNewIntent(intent)
        }
    }

    override fun onPause() {
        super.onPause()

        // App icon visibility control was restricted in Android 10.
        // See https://developer.android.com/reference/android/content/pm/LauncherApps#getActivityList(java.lang.String,%20android.os.UserHandle)
        if (AndroidVersion.ATMOST_API28_P) {
            if (showAppIcon) {
                this.showAppIcon()
            } else {
                this.hideAppIcon()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // FORK(S2-routes): open_screen and ui:// / app:// links go through RouteRegistry (unknown -> Home and
        // deeplink_unknown). The activity keeps a bare intent so the nav library never handles the raw one
        // (its own deep-link handling would bypass the allow-list), and a rotation does not navigate again.
        intentToBeHandled = AppIntents.classify(intent) { appContext.analytics.value.log(it) }
        setIntent(Intent(Intent.ACTION_MAIN))
    }

    // FORK(S2-routes): an allow-listed route by its nav deep link; Home (or a route not in the graph yet) goes to
    // Home only once onboarding is done, otherwise onboarding stays.
    private fun openRoute(navController: NavController, request: AppIntent.OpenRoute) {
        request.translatorText?.let(TranslatorHandoff::offer)
        val uri = "ui://florisboard/${request.route.path}".toUri()
        if (!request.route.isHome && navController.graph.hasDeepLink(uri)) {
            navController.navigate(uri)
        } else if (startAtHome && !navController.popBackStack(Routes.Settings.Home, inclusive = false)) {
            navController.navigate(Routes.Settings.Home)
        }
        // FORK(S2-routes): the SEND-with-clip import check moved to AppIntents.classify
    }

    @Composable
    private fun AppContent() {
        val navController = rememberNavController()
        val previewFieldController = rememberPreviewFieldController()

        // FORK(S1-onboarding): isImeSetUp no longer decides the start destination (see startAtHome)
        CompositionLocalProvider(
            LocalNavController provides navController,
            LocalPreviewFieldController provides previewFieldController,
        ) {
            ProvideDefaultDialogPrefStrings(
                confirmLabel = stringRes(R.string.action__ok),
                dismissLabel = stringRes(R.string.action__cancel),
                neutralLabel = stringRes(R.string.action__default),
            ) {
                Column(
                    modifier = Modifier
                        //.statusBarsPadding()
                        .navigationBarsPadding()
                        .conditional(LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            displayCutoutPadding()
                        }
                        .imePadding(),
                ) {
                    Routes.AppNavHost(
                        modifier = Modifier.weight(1.0f),
                        navController = navController,
                        // FORK(S1-onboarding): onboarding unless done and the keyboard is enabled and selected
                        startDestination = if (startAtHome) Routes.Settings.Home::class else Routes.Setup.Screen::class,
                    )
                    PreviewKeyboardField(previewFieldController)
                }
            }
        }

        LaunchedEffect(intentToBeHandled) {
            // FORK(S2-routes): routes through the allow-list; no !! on intent data (an empty clip crashed upstream)
            when (val request = intentToBeHandled) {
                is AppIntent.OpenRoute -> openRoute(navController, request)
                is AppIntent.ImportExtension -> {
                    val workspace = runCatching { cacheManager.readFromUriIntoCache(request.uri) }.getOrNull()
                    navController.navigate(Routes.Ext.Import(ExtensionImportScreenType.EXT_ANY, workspace?.uuid))
                }
                null -> Unit // FORK(S2-routes)
            }
            intentToBeHandled = null
        }
    }
}
