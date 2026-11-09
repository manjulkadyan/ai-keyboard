/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.update

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.manjul.aikeyboard.core.analytics.AnalyticsEvent
import com.manjul.aikeyboard.core.analytics.EventCatalog
import com.manjul.aikeyboard.core.analytics.UpdateResultParam
import com.manjul.aikeyboard.core.analytics.UpdateTypeParam
import com.manjul.aikeyboard.core.common.entry.EntrySource
import com.manjul.aikeyboard.core.config.ConfigKeys
import com.manjul.aikeyboard.core.config.RemoteConfig
import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * In-app update through Play (S4 Task 8). Home calls [onHomeResumed]; the controller decides whether to ask.
 *
 * - At most one check per 24 h, never before onboarding is done, never for a [EntrySource.PROCESS_TEXT] or
 *   [EntrySource.SEND] entry (the user came for something else).
 * - IMMEDIATE only when [versionCode] is below the Remote Config `min_supported_version` and Play allows it.
 *   Otherwise FLEXIBLE.
 * - A declined IMMEDIATE never closes the app. It does not stamp the check time, so the next Home resume asks again
 *   with no 24 h wait.
 * - A finished FLEXIBLE download sets [readyToRestart]; [restart] then calls `completeUpdate()`.
 *
 * It sends nothing but `update_prompt{type, result}` and logs no user content. This class takes a plain
 * [EntrySource] and never touches the ads packages.
 */
class UpdateController(
    private val manager: AppUpdateManager,
    private val config: RemoteConfig,
    private val settings: FeatureSettingsStore,
    private val versionCode: Long,
    private val clock: () -> Long,
    private val analytics: (AnalyticsEvent) -> Unit,
) {
    private val checking = AtomicBoolean(false)
    private val _readyToRestart = MutableStateFlow(false)

    /** True once a FLEXIBLE download finished and the app only needs a restart to install it. */
    val readyToRestart: StateFlow<Boolean> = _readyToRestart.asStateFlow()

    private val installListener: InstallStateUpdatedListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                _readyToRestart.value = true
                manager.unregisterListener(installListener)
            }
            InstallStatus.FAILED, InstallStatus.CANCELED, InstallStatus.INSTALLED ->
                manager.unregisterListener(installListener)
            else -> Unit
        }
    }

    suspend fun onHomeResumed(activity: ComponentActivity, entry: EntrySource) {
        if (entry == EntrySource.PROCESS_TEXT || entry == EntrySource.SEND) return
        if (!checking.compareAndSet(false, true)) return
        try {
            check(activity)
        } finally {
            checking.set(false)
        }
    }

    private suspend fun check(activity: Activity) {
        val state = settings.flow.first()
        if (!state.onboardingDone) return
        val now = clock()
        if (state.updateCheckedAt > 0L && now - state.updateCheckedAt < CHECK_INTERVAL_MS) return
        val info = try {
            manager.requestAppUpdateInfo()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "update info failed: ${e.javaClass.simpleName}")
            return
        }
        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            _readyToRestart.value = true
            stamp(now)
            return
        }
        val available = info.updateAvailability()
        if (available != UpdateAvailability.UPDATE_AVAILABLE &&
            available != UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
        ) {
            stamp(now)
            return
        }
        val immediate = versionCode < config.long(ConfigKeys.MIN_SUPPORTED_VERSION) &&
            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
        val type = when {
            immediate -> AppUpdateType.IMMEDIATE
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> AppUpdateType.FLEXIBLE
            else -> {
                stamp(now)
                return
            }
        }
        val result = startFlow(info, activity, type)
        analytics(EventCatalog.updatePrompt(type.param(), result))
        // A declined IMMEDIATE stays unstamped: the next Home resume asks again, with no 24 h wait.
        if (!(type == AppUpdateType.IMMEDIATE && result == UpdateResultParam.DECLINED)) stamp(now)
    }

    private suspend fun startFlow(info: AppUpdateInfo, activity: Activity, type: Int): UpdateResultParam {
        if (type == AppUpdateType.FLEXIBLE) manager.registerListener(installListener)
        val code = try {
            manager.startUpdateFlow(info, activity, AppUpdateOptions.newBuilder(type).build()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "update flow failed: ${e.javaClass.simpleName}")
            null
        }
        val result = when (code) {
            Activity.RESULT_OK -> UpdateResultParam.ACCEPTED
            Activity.RESULT_CANCELED -> UpdateResultParam.DECLINED
            else -> UpdateResultParam.FAILED
        }
        if (type == AppUpdateType.FLEXIBLE && result != UpdateResultParam.ACCEPTED) {
            manager.unregisterListener(installListener)
        }
        return result
    }

    /** Installs the downloaded FLEXIBLE update. The app restarts. */
    fun restart() {
        manager.completeUpdate().addOnFailureListener { e ->
            Log.w(TAG, "complete update failed: ${e.javaClass.simpleName}")
        }
    }

    /** The user closed the restart offer; the update installs on the next app start anyway. */
    fun dismissRestart() {
        _readyToRestart.value = false
    }

    private suspend fun stamp(now: Long) {
        settings.update { it.copy(updateCheckedAt = now) }
    }

    private fun Int.param() = if (this == AppUpdateType.IMMEDIATE) UpdateTypeParam.IMMEDIATE else UpdateTypeParam.FLEXIBLE

    private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { e -> if (cont.isActive) cont.resumeWith(Result.failure(e)) }
        addOnCanceledListener { if (cont.isActive) cont.resume(null) }
    }

    companion object {
        private const val TAG = "UpdateController"
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
