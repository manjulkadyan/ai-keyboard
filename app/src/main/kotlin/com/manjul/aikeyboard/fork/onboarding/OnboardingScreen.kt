/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.manjul.aikeyboard.R
import com.manjul.aikeyboard.app.LocalNavController
import com.manjul.aikeyboard.app.Routes
import com.manjul.aikeyboard.appContext
import com.manjul.aikeyboard.featureSettingsStore
import com.manjul.aikeyboard.fork.subtypes.MVP_LANGUAGE_TAGS
import com.manjul.aikeyboard.lib.util.InputMethodUtils
import com.manjul.aikeyboard.subtypeManager
import org.florisboard.lib.compose.stringRes
import java.util.Locale

/** ON_DEVICE when the platform reports an on-device recognizer (API 31+), otherwise the system speech service. */
fun systemDisclosureEngineKind(context: Context): DisclosureEngineKind =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
        DisclosureEngineKind.ON_DEVICE
    } else {
        DisclosureEngineKind.SYSTEM_SERVICE
    }

/** O1-O5 route content: builds the view model on the app singletons and leaves for Home when it reaches DONE. */
@Composable
fun OnboardingScreen(navController: NavController = LocalNavController.current) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = context.featureSettingsStore().value
    val analytics = context.appContext().value.analytics.value
    val subtypeManager = context.subtypeManager().value
    val localeList = LocalConfiguration.current.locales
    val imeStatus = remember { InputMethodImeStatus(context) }
    val vm = remember {
        OnboardingViewModel(
            store = store,
            imeStatus = imeStatus,
            analytics = analytics::log,
            ensureSubtypes = subtypeManager::ensureDefaultSubtypes,
            clock = System::currentTimeMillis,
            scope = scope,
            systemLocaleTags = localeList.let { l -> List(l.size()) { l[it].toLanguageTag() } },
        )
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { imeStatus.recheck() }

    val state by vm.state.collectAsState()
    LaunchedEffect(state.step) {
        if (state.step == OnboardingStep.DONE) {
            navController.navigate(Routes.Settings.Home) { popUpTo(Routes.Setup.Screen) { inclusive = true } }
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), vm::onMicPermissionResult)

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        if (!state.loaded) return@Column
        when (state.step) {
            OnboardingStep.WELCOME -> InfoStep(
                title = R.string.onboarding__welcome_title,
                body = R.string.onboarding__welcome_tagline,
                note = R.string.onboarding__welcome_privacy,
                button = R.string.onboarding__get_started,
                onButton = vm::next,
            )
            OnboardingStep.ENABLE -> InfoStep(
                title = R.string.onboarding__enable_title,
                body = R.string.onboarding__enable_body,
                button = R.string.onboarding__enable_button,
                onButton = { InputMethodUtils.showImeEnablerActivity(context) },
            )
            OnboardingStep.SELECT -> InfoStep(
                title = R.string.onboarding__select_title,
                body = R.string.onboarding__select_body,
                button = R.string.onboarding__select_button,
                onButton = { InputMethodUtils.showImePicker(context) },
            )
            OnboardingStep.LANGUAGES -> LanguagesStep(
                preselected = state.defaultLanguageTags,
                onContinue = { tags -> vm.onLanguagesChosen(tags, defaultTranslatePair(tags)) },
                onSkip = vm::skip,
            )
            OnboardingStep.TRY_IT -> TryItStep(
                engineKind = { systemDisclosureEngineKind(context) },
                store = store,
                clock = System::currentTimeMillis,
                micGranted = state.micGranted,
                requestMicPermission = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onSkip = vm::skip,
                onDone = vm::next,
            )
            OnboardingStep.DONE -> Unit
        }
    }
}

@Composable
private fun InfoStep(title: Int, body: Int, button: Int, onButton: () -> Unit, note: Int? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringRes(title), style = MaterialTheme.typography.headlineMedium)
        Text(stringRes(body), style = MaterialTheme.typography.bodyLarge)
        if (note != null) Text(stringRes(note), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onButton) { Text(stringRes(button)) }
    }
}

@Composable
private fun LanguagesStep(preselected: List<String>, onContinue: (List<String>) -> Unit, onSkip: () -> Unit) {
    val chosen = remember { mutableStateListOf<String>().apply { addAll(preselected) } }
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(stringRes(R.string.onboarding__languages_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringRes(R.string.onboarding__languages_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        MVP_LANGUAGE_TAGS.forEach { tag ->
            val locale = Locale.forLanguageTag(tag)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = tag in chosen,
                    onCheckedChange = { if (it) chosen.add(tag) else chosen.remove(tag) },
                )
                Text(locale.getDisplayName(locale))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onSkip) { Text(stringRes(R.string.onboarding__skip)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onContinue(chosen.toList()) }, enabled = chosen.isNotEmpty()) {
                Text(stringRes(R.string.onboarding__continue))
            }
        }
    }
}
