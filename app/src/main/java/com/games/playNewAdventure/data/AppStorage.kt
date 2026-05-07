package com.games.playNewAdventure.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.games.playNewAdventure.startup.domain.AppMode
import com.games.playNewAdventure.startup.domain.AttributionProviderMode
import com.games.playNewAdventure.startup.domain.ConfigProviderMode
import com.games.playNewAdventure.startup.domain.MockConfigScenario
import com.games.playNewAdventure.startup.domain.PushTokenProviderMode
import com.games.playNewAdventure.startup.domain.StartupStateRepository

class AppStorage(context: Context) : StartupStateRepository {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        STORAGE_NAME,
        Context.MODE_PRIVATE
    )

    override var appMode: AppMode
        get() = preferences.getString(KEY_APP_MODE, null)
            ?.let { storedValue -> runCatching { AppMode.valueOf(storedValue) }.getOrNull() }
            ?: AppMode.UNKNOWN
        set(value) {
            preferences.edit {
                putString(KEY_APP_MODE, value.name)
            }
        }

    override var lastWebViewUrl: String?
        get() = preferences.getString(KEY_LAST_WEBVIEW_URL, null)?.takeIf { it.isNotBlank() }
        set(value) {
            preferences.edit {
                if (value.isNullOrBlank()) {
                    remove(KEY_LAST_WEBVIEW_URL)
                } else {
                    putString(KEY_LAST_WEBVIEW_URL, value)
                }
            }
        }

    override var pushPromptDeclinedAtSeconds: Long
        get() = preferences.getLong(KEY_PUSH_PROMPT_DECLINED_AT, 0L)
        set(value) {
            preferences.edit {
                putLong(KEY_PUSH_PROMPT_DECLINED_AT, value)
            }
        }

    override var pushPermissionGranted: Boolean
        get() = preferences.getBoolean(KEY_PUSH_PERMISSION_GRANTED, false)
        set(value) {
            preferences.edit {
                putBoolean(KEY_PUSH_PERMISSION_GRANTED, value)
            }
        }

    override var mockConfigScenario: MockConfigScenario
        get() = preferences.getString(KEY_MOCK_CONFIG_SCENARIO, null)
            ?.let { storedValue -> runCatching { MockConfigScenario.valueOf(storedValue) }.getOrNull() }
            ?: MockConfigScenario.SUCCESS_WEBVIEW
        set(value) {
            preferences.edit {
                putString(KEY_MOCK_CONFIG_SCENARIO, value.name)
            }
        }

    override var configProviderMode: ConfigProviderMode
        get() = preferences.getString(KEY_CONFIG_PROVIDER_MODE, null)
            ?.let { storedValue -> runCatching { ConfigProviderMode.valueOf(storedValue) }.getOrNull() }
            ?: ConfigProviderMode.MOCK
        set(value) {
            preferences.edit {
                putString(KEY_CONFIG_PROVIDER_MODE, value.name)
            }
        }

    override var attributionProviderMode: AttributionProviderMode
        get() = preferences.getString(KEY_ATTRIBUTION_PROVIDER_MODE, null)
            ?.let { storedValue -> runCatching { AttributionProviderMode.valueOf(storedValue) }.getOrNull() }
            ?: AttributionProviderMode.MOCK
        set(value) {
            preferences.edit {
                putString(KEY_ATTRIBUTION_PROVIDER_MODE, value.name)
            }
        }

    override var pushTokenProviderMode: PushTokenProviderMode
        get() = preferences.getString(KEY_PUSH_TOKEN_PROVIDER_MODE, null)
            ?.let { storedValue -> runCatching { PushTokenProviderMode.valueOf(storedValue) }.getOrNull() }
            ?: PushTokenProviderMode.MOCK
        set(value) {
            preferences.edit {
                putString(KEY_PUSH_TOKEN_PROVIDER_MODE, value.name)
            }
        }

    override fun resetLocalState() {
        preferences.edit {
            remove(KEY_APP_MODE)
            remove(KEY_LAST_WEBVIEW_URL)
            remove(KEY_PUSH_PROMPT_DECLINED_AT)
            remove(KEY_PUSH_PERMISSION_GRANTED)
        }
    }

    private companion object {
        const val STORAGE_NAME = "chicken_rush_storage"
        const val KEY_APP_MODE = "app_mode"
        const val KEY_LAST_WEBVIEW_URL = "last_webview_url"
        const val KEY_PUSH_PROMPT_DECLINED_AT = "push_prompt_declined_at"
        const val KEY_PUSH_PERMISSION_GRANTED = "push_permission_granted"
        const val KEY_MOCK_CONFIG_SCENARIO = "mock_config_scenario"
        const val KEY_CONFIG_PROVIDER_MODE = "config_provider_mode"
        const val KEY_ATTRIBUTION_PROVIDER_MODE = "attribution_provider_mode"
        const val KEY_PUSH_TOKEN_PROVIDER_MODE = "push_token_provider_mode"
    }
}
