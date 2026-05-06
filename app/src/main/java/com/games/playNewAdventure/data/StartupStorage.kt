package com.games.playNewAdventure.data

import com.games.playNewAdventure.startup.AppMode
import com.games.playNewAdventure.startup.ConfigProviderMode
import com.games.playNewAdventure.startup.MockConfigScenario

interface StartupStorage {
    var appMode: AppMode
    var lastWebViewUrl: String?
    var pushPromptDeclinedAtSeconds: Long
    var pushPermissionGranted: Boolean
    var mockConfigScenario: MockConfigScenario
    var configProviderMode: ConfigProviderMode

    fun resetLocalState()
}
