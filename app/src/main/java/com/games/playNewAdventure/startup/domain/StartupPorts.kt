package com.games.playNewAdventure.startup.domain

interface StartupStateRepository {
    var appMode: AppMode
    var lastWebViewUrl: String?
    var pushPromptDeclinedAtSeconds: Long
    var pushPermissionGranted: Boolean
    var mockConfigScenario: MockConfigScenario
    var configProviderMode: ConfigProviderMode
    var attributionProviderMode: AttributionProviderMode
    var pushTokenProviderMode: PushTokenProviderMode

    fun resetLocalState()
}

interface AttributionProvider {
    suspend fun getConversionData(): AttributionData
}

interface PushTokenProvider {
    suspend fun getPushDataOrNull(): PushData?
}

interface ConfigProvider {
    suspend fun fetchConfig(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): ConfigFetchResult
}

interface ConfigDiagnosticsProvider {
    fun lastConfigDebugSnapshot(): ConfigDebugSnapshot?
}

interface NetworkStatusProvider {
    fun isOnline(): Boolean
}

interface DeviceDataProvider {
    fun getDeviceData(): Map<String, Any?>
}
