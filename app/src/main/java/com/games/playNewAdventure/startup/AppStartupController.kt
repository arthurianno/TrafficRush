package com.games.playNewAdventure.startup

import android.app.Activity
import com.games.playNewAdventure.data.StartupStorage
import com.games.playNewAdventure.service.AttributionService
import com.games.playNewAdventure.service.ConfigService
import com.games.playNewAdventure.service.DeviceDataProvider
import com.games.playNewAdventure.service.NetworkChecker
import com.games.playNewAdventure.service.PushService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppStartupController(
    private val storage: StartupStorage,
    private val networkChecker: NetworkChecker,
    private val attributionService: AttributionService,
    private val pushService: PushService,
    private val configService: ConfigService,
    private val deviceDataProvider: DeviceDataProvider,
    private val currentEpochSeconds: () -> Long = { System.currentTimeMillis() / MILLIS_IN_SECOND }
) {
    suspend fun resolveStartup(): StartupResult = withContext(Dispatchers.IO) {
        when (storage.appMode) {
            AppMode.FANTIC -> StartupResult.ShowFantic
            AppMode.WEBVIEW -> resolveStoredWebViewMode()
            AppMode.UNKNOWN -> resolveFirstLaunch()
        }
    }

    suspend fun requestPushPermission(activity: Activity): Boolean {
        val granted = pushService.requestNotificationPermission(activity)
        if (granted) {
            storage.pushPermissionGranted = true
        }
        return granted
    }

    fun declinePushPrompt() {
        storage.pushPromptDeclinedAtSeconds = currentEpochSeconds()
    }

    fun resetLocalState() {
        storage.resetLocalState()
    }

    fun clearLastWebViewUrl() {
        storage.lastWebViewUrl = null
    }

    fun setMockConfigScenario(scenario: MockConfigScenario) {
        storage.mockConfigScenario = scenario
    }

    fun setConfigProviderMode(mode: ConfigProviderMode) {
        storage.configProviderMode = mode
    }

    fun debugSnapshot(): StartupDebugSnapshot {
        return StartupDebugSnapshot(
            appMode = storage.appMode,
            lastWebViewUrl = storage.lastWebViewUrl,
            pushPromptDeclinedAtSeconds = storage.pushPromptDeclinedAtSeconds,
            pushPermissionGranted = storage.pushPermissionGranted,
            mockConfigScenario = storage.mockConfigScenario,
            configProviderMode = storage.configProviderMode
        )
    }

    private suspend fun resolveFirstLaunch(): StartupResult {
        if (!networkChecker.isOnline()) {
            return StartupResult.ShowNoInternet
        }

        return runCatching {
            val response = requestFreshConfig()
            val url = response.successUrlOrNull()

            if (url == null) {
                storage.appMode = AppMode.FANTIC
                StartupResult.ShowFantic
            } else {
                storage.appMode = AppMode.WEBVIEW
                storage.lastWebViewUrl = url
                StartupResult.ShowWebView(
                    url = url,
                    shouldAskPushPermission = shouldAskPushPermission()
                )
            }
        }.getOrElse {
            storage.appMode = AppMode.FANTIC
            StartupResult.ShowFantic
        }
    }

    private suspend fun resolveStoredWebViewMode(): StartupResult {
        if (!networkChecker.isOnline()) {
            return StartupResult.ShowNoInternet
        }

        val freshUrl = runCatching {
            requestFreshConfig().successUrlOrNull()
        }.getOrNull()

        val targetUrl = freshUrl ?: storage.lastWebViewUrl

        return if (targetUrl.isNullOrBlank()) {
            StartupResult.ShowNoInternet
        } else {
            if (freshUrl != null) {
                storage.lastWebViewUrl = freshUrl
            }
            StartupResult.ShowWebView(
                url = targetUrl,
                shouldAskPushPermission = shouldAskPushPermission()
            )
        }
    }

    private suspend fun requestFreshConfig(): ConfigResponse {
        val attributionData = attributionService.getConversionData()
        val pushData = runCatching { pushService.getPushDataOrNull() }.getOrNull()

        return configService.requestConfig(
            attributionData = attributionData,
            pushData = pushData,
            deviceData = deviceDataProvider.getDeviceData()
        )
    }

    private fun shouldAskPushPermission(): Boolean {
        if (storage.pushPermissionGranted) {
            return false
        }

        val declinedAt = storage.pushPromptDeclinedAtSeconds
        if (declinedAt <= 0L) {
            return true
        }

        return currentEpochSeconds() - declinedAt >= PUSH_PROMPT_REPEAT_DELAY_SECONDS
    }

    private fun ConfigResponse.successUrlOrNull(): String? {
        return url?.trim()?.takeIf { ok && it.isNotEmpty() }
    }

    private companion object {
        const val MILLIS_IN_SECOND = 1_000L
        const val PUSH_PROMPT_REPEAT_DELAY_SECONDS = 259_200L
    }
}
