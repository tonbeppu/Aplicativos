package com.movedados.movetv.display.utils

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("movedados_display_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_PROFILE_JSON = "profile_json"
        private const val KEY_SAVED_DEVICE_ID = "saved_device_id"
    }

    fun saveAuthData(accessToken: String, refreshToken: String, userId: String, email: String) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_EMAIL, email)
            apply()
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getUserEmail(): String? = prefs.getString(KEY_USER_EMAIL, null)

    fun isLoggedIn(): Boolean = getAccessToken() != null && getUserId() != null

    fun saveProfileJson(json: String) {
        prefs.edit().putString(KEY_PROFILE_JSON, json).apply()
    }
    fun getProfileJson(): String? = prefs.getString(KEY_PROFILE_JSON, null)

    /** Equivalente ao localStorage.setItem('saved_device_id', ...) do protótipo React —
     *  lembra o dispositivo pareado para não pedir de novo no próximo login. */
    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString(KEY_SAVED_DEVICE_ID, deviceId).apply()
    }
    fun getSavedDeviceId(): String? = prefs.getString(KEY_SAVED_DEVICE_ID, null)

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
