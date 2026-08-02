package com.movedados.movetv.display.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.movedados.movetv.display.models.*
import com.movedados.movetv.display.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SupabaseClient(context: Context) {

    private val prefs = PreferenceManager(context)
    private val gson = Gson()
    private val refreshMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        // Mesmo projeto Supabase usado pelo app do motorista e pelo painel web
        private const val BASE_URL = "https://kfrdgbdoqiyzzxtoaikc.supabase.co"
        private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtmcmRnYmRvcWl5enp4dG9haWtjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzgwMDQxMjksImV4cCI6MjA5MzU4MDEyOX0.xfU4yDEjlBtk7Yc46GSSscrpxtsk6A1Zyns1TXL4Xb0"
        private const val TAG = "DisplaySupabaseClient"
    }

    private val jsonMediaType = "application/json".toMediaType()

    private fun utcNow(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

    private fun authRequestBuilder(path: String, method: String): Request.Builder {
        val builder = Request.Builder()
            .url("$BASE_URL$path")
            .addHeader("apikey", ANON_KEY)
            .addHeader("Content-Type", "application/json")
        prefs.getAccessToken()?.let { builder.addHeader("Authorization", "Bearer $it") }
        return builder
    }

    /** Repete a chamada uma vez, renovando o token, se a primeira vier com 401 (token expirado) —
     *  o app de exibição fica ligado o dia inteiro, então isso é essencial. */
    private suspend fun executeAuthed(buildRequest: () -> Request): Response {
        val first = client.newCall(buildRequest()).execute()
        if (first.code != 401) return first
        first.close()
        return if (refreshAccessToken()) client.newCall(buildRequest()).execute() else first
    }

    private suspend fun refreshAccessToken(): Boolean = refreshMutex.withLock {
        try {
            val refreshToken = prefs.getRefreshToken() ?: return@withLock false
            val request = Request.Builder()
                .url("$BASE_URL/auth/v1/token?grant_type=refresh_token")
                .post(JsonObject().apply { addProperty("refresh_token", refreshToken) }.toString().toRequestBody(jsonMediaType))
                .addHeader("apikey", ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            if (!response.isSuccessful) return@withLock false
            val json = gson.fromJson(body, JsonObject::class.java)
            val newAccess = json.get("access_token")?.asString
            val newRefresh = json.get("refresh_token")?.asString
            if (newAccess != null && newRefresh != null) {
                prefs.saveAuthData(newAccess, newRefresh, prefs.getUserId() ?: "", prefs.getUserEmail() ?: "")
                true
            } else false
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao renovar token", e)
            false
        }
    }

    // ==================== AUTH ====================

    suspend fun login(email: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("email", email)
                addProperty("password", password)
            }
            val request = Request.Builder()
                .url("$BASE_URL/auth/v1/token?grant_type=password")
                .post(json.toString().toRequestBody(jsonMediaType))
                .addHeader("apikey", ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val jsonResponse = gson.fromJson(body, JsonObject::class.java)
            if (response.isSuccessful) {
                val accessToken = jsonResponse.get("access_token").asString
                val refreshToken = jsonResponse.get("refresh_token").asString
                val userId = jsonResponse.getAsJsonObject("user")?.get("id")?.asString ?: ""
                prefs.saveAuthData(accessToken, refreshToken, userId, email)
                Result.success(AuthResponse(accessToken, refreshToken, userId))
            } else {
                val msg = jsonResponse.get("error_description")?.asString
                    ?: jsonResponse.get("msg")?.asString
                    ?: "E-mail ou senha incorretos"
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== PROFILE ====================

    suspend fun fetchProfile(userId: String): Result<Profile> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/profiles?id=eq.$userId&select=id,email,full_name,role,device_id,campaign_id", "GET").build()
            }
            val body = response.body?.string() ?: "[]"
            val listType = object : TypeToken<List<Profile>>() {}.type
            val profiles: List<Profile> = gson.fromJson(body, listType)
            if (profiles.isNotEmpty()) Result.success(profiles[0])
            else Result.failure(Exception("Perfil não encontrado"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== PAREAMENTO (equivalente ao DevicePairing.tsx) ====================

    suspend fun fetchDevice(deviceId: String): Result<Device?> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/devices?id=eq.$deviceId&select=id,name", "GET").build()
            }
            val body = response.body?.string() ?: "[]"
            val listType = object : TypeToken<List<Device>>() {}.type
            val devices: List<Device> = gson.fromJson(body, listType)
            Result.success(devices.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Verifica se outro usuário (não este) já está usando esse dispositivo. */
    suspend fun isDeviceTakenByOther(deviceId: String, userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/profiles?device_id=eq.$deviceId&id=neq.$userId&select=id", "GET").build()
            }
            val body = response.body?.string() ?: "[]"
            val listType = object : TypeToken<List<JsonObject>>() {}.type
            val rows: List<JsonObject> = gson.fromJson(body, listType)
            Result.success(rows.isNotEmpty())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pairDevice(userId: String, deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply { addProperty("device_id", deviceId) }
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/profiles?id=eq.$userId", "PATCH")
                    .patch(json.toString().toRequestBody(jsonMediaType))
                    .addHeader("Prefer", "return=minimal")
                    .build()
            }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erro ao vincular dispositivo (HTTP ${response.code})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== MÍDIA DA CAMPANHA (equivalente ao fetchCampaignMedia) ====================

    suspend fun fetchCampaignIdForDevice(deviceId: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/campaign_devices?device_id=eq.$deviceId&select=campaign_id", "GET").build()
            }
            val body = response.body?.string() ?: "[]"
            val listType = object : TypeToken<List<CampaignDeviceRow>>() {}.type
            val rows: List<CampaignDeviceRow> = gson.fromJson(body, listType)
            Result.success(rows.firstOrNull()?.campaign_id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCampaignMedia(campaignId: String): Result<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            val cmResponse = executeAuthed {
                authRequestBuilder("/rest/v1/campaign_media?campaign_id=eq.$campaignId&select=media_id,display_order&order=display_order", "GET").build()
            }
            val cmBody = cmResponse.body?.string() ?: "[]"
            val cmListType = object : TypeToken<List<CampaignMediaRow>>() {}.type
            val cmRows: List<CampaignMediaRow> = gson.fromJson(cmBody, cmListType)

            if (cmRows.isEmpty()) return@withContext Result.success(emptyList())

            val orderedIds = cmRows.map { it.media_id }
            val idsFilter = orderedIds.joinToString(",")

            val mediaResponse = executeAuthed {
                authRequestBuilder("/rest/v1/media?id=in.($idsFilter)&is_active=eq.true&select=id,title,file_url,file_type,file_format,duration", "GET").build()
            }
            val mediaBody = mediaResponse.body?.string() ?: "[]"
            val mediaListType = object : TypeToken<List<MediaItem>>() {}.type
            val mediaList: List<MediaItem> = gson.fromJson(mediaBody, mediaListType)

            // Mantém a ordem definida em campaign_media (igual ao .map(...).filter(...) do React)
            val ordered = orderedIds.mapNotNull { id -> mediaList.find { it.id == id } }
            Result.success(ordered)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== TRACKING (prova de exibição + localização) ====================

    suspend fun insertMediaPlay(mediaId: String, campaignId: String, deviceId: String, durationSeconds: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("media_id", mediaId)
                addProperty("campaign_id", campaignId)
                addProperty("device_id", deviceId)
                addProperty("duration_seconds", durationSeconds)
                addProperty("played_at", utcNow())
            }
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/media_plays", "POST")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .addHeader("Prefer", "return=minimal")
                    .build()
            }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erro ao registrar exibição (HTTP ${response.code})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertScreenLocation(
        userId: String, deviceId: String?, campaignId: String?,
        latitude: Double, longitude: Double, accuracy: Double?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("user_id", userId)
                deviceId?.let { addProperty("device_id", it) }
                campaignId?.let { addProperty("campaign_id", it) }
                addProperty("latitude", latitude)
                addProperty("longitude", longitude)
                accuracy?.let { addProperty("accuracy", it) }
                addProperty("recorded_at", utcNow())
            }
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/screen_locations", "POST")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .addHeader("Prefer", "return=minimal")
                    .build()
            }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erro ao registrar localização (HTTP ${response.code})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
