package com.movedados.movetv.driver.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.movedados.movetv.driver.models.QueuedGpsPoint

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("movetv_driver_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_PROFILE_JSON = "profile_json"
        private const val KEY_GPS_ENABLED = "gps_enabled"
        private const val KEY_MONITORING_START = "monitoring_start"
        private const val KEY_GPS_COUNT = "gps_count_session"
        private const val KEY_LAST_ACCURACY = "last_accuracy"
        private const val KEY_LAST_GPS_TIMESTAMP = "last_gps_timestamp"
        private const val KEY_SESSION_ID = "monitoring_session_id"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_REMEMBERED_EMAIL = "remembered_email"
        private const val KEY_REMEMBERED_PASSWORD = "remembered_password"
        private const val KEY_GPS_QUEUE = "gps_offline_queue"
        private const val KEY_LAST_SAVED_LAT = "last_saved_lat"
        private const val KEY_LAST_SAVED_LNG = "last_saved_lng"
        private const val KEY_LAST_SAVED_AT = "last_saved_at"
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

    fun setGpsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GPS_ENABLED, enabled).apply()
    }

    fun isGpsEnabled(): Boolean = prefs.getBoolean(KEY_GPS_ENABLED, false)

    // O monitoramento está ativo quando o motorista deixou o GPS ligado
    fun isMonitoringActive(): Boolean = isGpsEnabled()

    // ==================== SESSÃO DE MONITORAMENTO ====================

    fun setSessionId(sessionId: String?) {
        prefs.edit().putString(KEY_SESSION_ID, sessionId).apply()
    }

    fun getSessionId(): String? = prefs.getString(KEY_SESSION_ID, null)

    fun setMonitoringStart(timestamp: Long) {
        prefs.edit().putLong(KEY_MONITORING_START, timestamp).apply()
    }

    fun getMonitoringStart(): Long = prefs.getLong(KEY_MONITORING_START, 0L)

    fun incrementGpsCount() {
        prefs.edit().putInt(KEY_GPS_COUNT, getGpsCount() + 1).apply()
    }

    fun getGpsCount(): Int = prefs.getInt(KEY_GPS_COUNT, 0)

    fun setLastAccuracy(accuracy: Float) {
        prefs.edit().putFloat(KEY_LAST_ACCURACY, accuracy).apply()
    }

    fun getLastAccuracy(): Float = prefs.getFloat(KEY_LAST_ACCURACY, -1f)

    fun setLastGpsTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_GPS_TIMESTAMP, timestamp).apply()
    }

    fun getLastGpsTimestamp(): Long = prefs.getLong(KEY_LAST_GPS_TIMESTAMP, 0L)

    fun resetMonitoringSession() {
        prefs.edit()
            .putLong(KEY_MONITORING_START, System.currentTimeMillis())
            .putInt(KEY_GPS_COUNT, 0)
            .putFloat(KEY_LAST_ACCURACY, -1f)
            .remove(KEY_SESSION_ID)
            .remove(KEY_LAST_SAVED_LAT)
            .remove(KEY_LAST_SAVED_LNG)
            .putLong(KEY_LAST_SAVED_AT, 0L)
            .apply()
    }

    // ==================== LEMBRAR USUÁRIO E SENHA ====================

    fun saveRememberedCredentials(email: String, password: String) {
        prefs.edit()
            .putBoolean(KEY_REMEMBER_ME, true)
            .putString(KEY_REMEMBERED_EMAIL, email)
            .putString(KEY_REMEMBERED_PASSWORD, password)
            .apply()
    }

    fun clearRememberedCredentials() {
        prefs.edit()
            .putBoolean(KEY_REMEMBER_ME, false)
            .remove(KEY_REMEMBERED_EMAIL)
            .remove(KEY_REMEMBERED_PASSWORD)
            .apply()
    }

    fun isRememberMeEnabled(): Boolean = prefs.getBoolean(KEY_REMEMBER_ME, false)
    fun getRememberedEmail(): String? = prefs.getString(KEY_REMEMBERED_EMAIL, null)
    fun getRememberedPassword(): String? = prefs.getString(KEY_REMEMBERED_PASSWORD, null)

    // ==================== FILA OFFLINE DE PONTOS GPS ====================
    // Guarda os pontos localmente até conseguir enviar em lote — é o que permite
    // o monitoramento continuar funcionando mesmo sem internet no momento da coleta.

    @Synchronized
    fun enqueueGpsPoint(point: QueuedGpsPoint) {
        val current = getGpsQueue().toMutableList()
        current.add(point)
        prefs.edit().putString(KEY_GPS_QUEUE, gson.toJson(current)).apply()
    }

    fun getGpsQueue(): List<QueuedGpsPoint> {
        val json = prefs.getString(KEY_GPS_QUEUE, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<QueuedGpsPoint>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun clearGpsQueue() {
        prefs.edit().remove(KEY_GPS_QUEUE).apply()
    }

    fun getGpsQueueSize(): Int = getGpsQueue().size

    // ==================== ÚLTIMO PONTO SALVO (para o filtro de "não se moveu") ====================

    fun setLastSavedPoint(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat(KEY_LAST_SAVED_LAT, lat.toFloat())
            .putFloat(KEY_LAST_SAVED_LNG, lng.toFloat())
            .putLong(KEY_LAST_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getLastSavedLat(): Float? = if (prefs.contains(KEY_LAST_SAVED_LAT)) prefs.getFloat(KEY_LAST_SAVED_LAT, 0f) else null
    fun getLastSavedLng(): Float? = if (prefs.contains(KEY_LAST_SAVED_LNG)) prefs.getFloat(KEY_LAST_SAVED_LNG, 0f) else null
    fun getLastSavedAt(): Long = prefs.getLong(KEY_LAST_SAVED_AT, 0L)

    fun clearAll() {
        // Preserva a preferência "lembrar-me" mesmo ao fazer logout,
        // para o motorista não precisar redigitar tudo no próximo turno.
        val rememberMe = isRememberMeEnabled()
        val email = getRememberedEmail()
        val password = getRememberedPassword()
        prefs.edit().clear().apply()
        if (rememberMe && email != null && password != null) {
            saveRememberedCredentials(email, password)
        }
    }
}
