package com.games.playNewAdventure.service

import android.content.Context
import androidx.core.content.edit
import com.games.playNewAdventure.startup.domain.TokenDebugInfo
import com.games.playNewAdventure.startup.domain.toTokenDebugInfo

internal object FcmTokenStore {
    fun saveLatestToken(context: Context, token: String) {
        if (token.isBlank()) {
            return
        }
        context.applicationContext
            .getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_LATEST_TOKEN, token)
                putLong(KEY_LATEST_TOKEN_SAVED_AT, System.currentTimeMillis())
            }
    }

    fun latestTokenDebugInfo(context: Context): TokenDebugInfo? {
        return context.applicationContext
            .getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LATEST_TOKEN, null)
            .toTokenDebugInfo()
    }

    private const val STORAGE_NAME = "traffic_rush_fcm_token"
    private const val KEY_LATEST_TOKEN = "latest_fcm_token"
    private const val KEY_LATEST_TOKEN_SAVED_AT = "latest_fcm_token_saved_at"
}
