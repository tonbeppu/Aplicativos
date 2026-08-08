package com.movedados.movetv.driver.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.movedados.movetv.driver.models.*
import com.movedados.movetv.driver.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

class SupabaseClient(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = PreferenceManager(appContext)
    private val gson = Gson()
    private val refreshMutex = Mutex()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val BASE_URL = "https://kfrdgbdoqiyzzxtoaikc.supabase.co"
        private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtmcmRnYmRvcWl5enp4dG9haWtjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzgwMDQxMjksImV4cCI6MjA5MzU4MDEyOX0.xfU4yDEjlBtk7Yc46GSSscrpxtsk6A1Zyns1TXL4Xb0"
        private const val TAG = "SupabaseClient"
    }

    private val jsonMediaType = "application/json".toMediaType()

    private fun utcNow(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

    private fun authRequestBuilder(path: String, method: String): Request.Builder {
        val builder = Request.Builder()
            .url("$BASE_URL$path")
            .addHeader("apikey", ANON_KEY)
            .addHeader("Content-Type", "application/json")

        prefs.getAccessToken()?.let {
            builder.addHeader("Authorization", "Bearer $it")
        }

        return builder
    }

    /**
     * Executa uma requisição autenticada e, se o servidor responder 401 (token expirado —
     * o que acontece naturalmente num turno de 10-12h), renova o token automaticamente
     * usando o refresh_token e repete a chamada uma única vez com o token novo.
     */
    private suspend fun executeAuthed(buildRequest: () -> Request): Response {
        val first = client.newCall(buildRequest()).execute()
        if (first.code != 401) return first

        first.close()
        val refreshed = refreshAccessToken()
        return if (refreshed) {
            client.newCall(buildRequest()).execute()
        } else {
            first
        }
    }

    /**
     * Troca o refresh_token pelo par de tokens novo. Protegido por mutex para evitar
     * que múltiplas chamadas simultâneas (ex: vários inserts de GPS ao mesmo tempo)
     * disparem várias renovações em paralelo.
     */
    private suspend fun refreshAccessToken(): Boolean = refreshMutex.withLock {
        try {
            val refreshToken = prefs.getRefreshToken() ?: return@withLock false

            val request = Request.Builder()
                .url("$BASE_URL/auth/v1/token?grant_type=refresh_token")
                .post(
                    JsonObject().apply { addProperty("refresh_token", refreshToken) }
                        .toString().toRequestBody(jsonMediaType)
                )
                .addHeader("apikey", ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"

            if (!response.isSuccessful) {
                Log.e(TAG, "Falha ao renovar token: HTTP ${response.code}")
                return@withLock false
            }

            val json = gson.fromJson(body, JsonObject::class.java)
            val newAccess = json.get("access_token")?.asString
            val newRefresh = json.get("refresh_token")?.asString

            if (newAccess != null && newRefresh != null) {
                val userId = prefs.getUserId() ?: ""
                val email = prefs.getUserEmail() ?: ""
                prefs.saveAuthData(newAccess, newRefresh, userId, email)
                true
            } else {
                false
            }
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
                Result.success(AuthResponse(
                    accessToken = jsonResponse.get("access_token").asString,
                    refreshToken = jsonResponse.get("refresh_token").asString,
                    userId = jsonResponse.getAsJsonObject("user")?.get("id")?.asString ?: ""
                ))
            } else {
                val errorMsg = jsonResponse.get("error_description")?.asString
                    ?: jsonResponse.get("msg")?.asString
                    ?: "Erro ao fazer login"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            Result.failure(e)
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply { addProperty("email", email) }
            val request = Request.Builder()
                .url("$BASE_URL/auth/v1/recover")
                .post(json.toString().toRequestBody(jsonMediaType))
                .addHeader("apikey", ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erro ao enviar email de recuperação"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(email: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("email", email)
                addProperty("password", password)
            }

            val request = Request.Builder()
                .url("$BASE_URL/auth/v1/signup")
                .post(json.toString().toRequestBody(jsonMediaType))
                .addHeader("apikey", ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val jsonResponse = gson.fromJson(body, JsonObject::class.java)

            if (!response.isSuccessful) {
                val errorMsg = jsonResponse.get("error_description")?.asString
                    ?: jsonResponse.get("msg")?.asString
                    ?: jsonResponse.get("error")?.asString?.replace("_", " ")
                    ?: "Erro ao criar conta"

                // Se a conta já existe (ex: um cadastro anterior falhou depois de criar o login,
                // mas antes de salvar o perfil), tenta logar com a mesma senha para retomar
                // o cadastro em vez de travar o motorista numa mensagem sem saída.
                val alreadyRegistered = errorMsg.contains("already registered", ignoreCase = true) ||
                    errorMsg.contains("already exists", ignoreCase = true) ||
                    jsonResponse.get("error_code")?.asString == "user_already_exists"

                if (alreadyRegistered) {
                    val loginResult = login(email, password)
                    if (loginResult.isSuccess) return@withContext loginResult
                }

                return@withContext Result.failure(Exception(errorMsg))
            }

            // Se o projeto exige confirmação por email, a resposta traz o usuário mas sem sessão (tokens ausentes)
            val userId = jsonResponse.get("id")?.asString
                ?: jsonResponse.getAsJsonObject("user")?.get("id")?.asString
                ?: return@withContext Result.failure(Exception("Erro ao criar conta: usuário não retornado"))

            var accessToken = jsonResponse.get("access_token")?.asString ?: ""
            var refreshToken = jsonResponse.get("refresh_token")?.asString ?: ""

            // Alguns formatos de resposta do Supabase não trazem o token junto no cadastro,
            // mesmo quando a conta já está confirmada (autoconfirm). Nesse caso, tenta logar
            // na hora com a mesma senha — evita cancelar o cadastro sem necessidade.
            if (accessToken.isEmpty()) {
                val loginAttempt = login(email, password)
                val loggedAuth = loginAttempt.getOrNull()
                if (loggedAuth != null) {
                    accessToken = loggedAuth.accessToken
                    refreshToken = loggedAuth.refreshToken
                }
            }

            if (accessToken.isNotEmpty()) {
                prefs.saveAuthData(accessToken, refreshToken, userId, email)
            }

            Result.success(AuthResponse(accessToken, refreshToken, userId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Envia a foto de perfil do motorista para o bucket público "driver_images"
     * (caminho: driver_images/{userId}/profile.jpg) e retorna a URL pública.
     * O upload precisa do access_token do motorista recém-cadastrado (RLS exige auth.uid() = pasta).
     */
    suspend fun uploadProfilePhoto(userId: String, accessToken: String, jpegBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val path = "$userId/profile.jpg"
            val body = jpegBytes.toRequestBody("image/jpeg".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/storage/v1/object/driver_images/$path")
                .post(body)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "image/jpeg")
                .addHeader("x-upsert", "true")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success("$BASE_URL/storage/v1/object/public/driver_images/$path")
            } else {
                Result.failure(Exception("Erro ao enviar foto (HTTP ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Dados de veículo e PIX agora são opcionais no cadastro — o motorista completa
    // depois pela aba Perfil. Por isso todos esses parâmetros aceitam null.
    suspend fun registerDriverProfile(
        userId: String,
        email: String,
        fullName: String,
        cpf: String,
        phone: String,
        birthDate: String,
        city: String,
        state: String,
        vehicleManufacturer: String? = null,
        vehicleModel: String? = null,
        vehicleYear: Int? = null,
        vehiclePlate: String? = null,
        vehicleColor: String? = null,
        vehicleMotorType: String? = null,
        pixType: String? = null,
        pixKey: String? = null,
        profilePhotoUrl: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("id", userId)
                addProperty("email", email)
                addProperty("full_name", fullName)
                addProperty("role", "motorista")
                profilePhotoUrl?.let { addProperty("profile_photo_url", it) }
                addProperty("cpf", cpf)
                addProperty("phone", phone)
                addProperty("birth_date", birthDate)
                addProperty("city", city)
                addProperty("state", state)
                vehicleManufacturer?.let { addProperty("vehicle_manufacturer", it) }
                vehicleModel?.let { addProperty("vehicle_model", it) }
                vehicleYear?.let { addProperty("vehicle_year", it) }
                vehiclePlate?.let { addProperty("vehicle_plate", it) }
                vehicleColor?.let { addProperty("vehicle_color", it) }
                vehicleMotorType?.let { addProperty("vehicle_motor_type", it) }
                pixType?.let { addProperty("pix_type", it) }
                pixKey?.let { addProperty("pix_key", it) }
            }

            // Upsert: funciona tanto se um trigger do banco já criou a linha do perfil
            // no signup quanto se o app precisar criá-la agora — evita erro de duplicidade.
            val request = Request.Builder()
                .url("$BASE_URL/rest/v1/profiles")
                .post(json.toString().toRequestBody(jsonMediaType))
                .addHeader("apikey", ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .apply {
                    prefs.getAccessToken()?.let { addHeader("Authorization", "Bearer $it") }
                }
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string() ?: ""
                Log.e(TAG, "Erro ao salvar perfil do motorista: HTTP ${response.code} - $errorBody")
                val friendlyMsg = try {
                    gson.fromJson(errorBody, JsonObject::class.java)
                        ?.get("message")?.asString ?: errorBody
                } catch (e: Exception) { errorBody }
                Result.failure(Exception(friendlyMsg.ifBlank { "Erro ao salvar cadastro (HTTP ${response.code})" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== PROFILE ====================

    suspend fun fetchProfile(userId: String): Result<Profile> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/profiles?id=eq.$userId&select=*", "GET").build()
            }
            val body = response.body?.string() ?: "[]"
            val listType = object : TypeToken<List<Profile>>() {}.type
            val profiles: List<Profile> = gson.fromJson(body, listType)
            if (profiles.isNotEmpty()) Result.success(profiles[0])
            else Result.failure(Exception("Perfil não encontrado (HTTP ${response.code})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(profile: Profile): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(profile)
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/profiles?id=eq.${profile.id}", "PATCH")
                    .patch(json.toRequestBody(jsonMediaType))
                    .addHeader("Prefer", "return=minimal")
                    .build()
            }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erro ao atualizar perfil"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== CAMPAIGNS ====================

    suspend fun fetchCampaigns(deviceId: String): Result<List<Campaign>> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthed {
                authRequestBuilder(
                    "/rest/v1/campaign_devices?device_id=eq.$deviceId&select=campaigns(id,name,status,start_date,end_date)",
                    "GET"
                ).build()
            }
            val body = response.body?.string() ?: "[]"

            val listType = object : TypeToken<List<JsonObject>>() {}.type
            val rows: List<JsonObject> = gson.fromJson(body, listType)
            val campaigns = rows.mapNotNull { row ->
                val campObj = row.getAsJsonObject("campaigns")
                if (campObj != null) gson.fromJson(campObj, Campaign::class.java) else null
            }
            Result.success(campaigns)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDriverCampaigns(driverId: String): Result<List<Campaign>> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthed {
                authRequestBuilder(
                    "/rest/v1/campaign_drivers?driver_id=eq.$driverId&select=campaigns(id,name,status,start_date,end_date,campaign_type,driver_payment_value,image_url,adhesion_start_date,adhesion_end_date,adhesion_start_time,adhesion_end_time,adhesion_pause_start_time,adhesion_pause_end_time)",
                    "GET"
                ).build()
            }
            val body = response.body?.string() ?: "[]"

            val listType = object : TypeToken<List<JsonObject>>() {}.type
            val rows: List<JsonObject> = gson.fromJson(body, listType)
            val campaigns = rows.mapNotNull { row ->
                val campObj = row.getAsJsonObject("campaigns")
                if (campObj != null) gson.fromJson(campObj, Campaign::class.java) else null
            }
            Result.success(campaigns)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== CONVITE DE CAMPANHA ====================

    suspend fun fetchInvitation(campaignId: String, driverId: String): Result<Invitation?> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthed {
                authRequestBuilder(
                    "/rest/v1/campaign_driver_invitations?campaign_id=eq.$campaignId&driver_id=eq.$driverId&select=id,status,invited_at,responded_at&limit=1",
                    "GET"
                ).build()
            }
            val body = response.body?.string() ?: "[]"
            val listType = object : TypeToken<List<Invitation>>() {}.type
            val rows: List<Invitation> = gson.fromJson(body, listType)
            Result.success(rows.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun respondToInvitation(invitationId: String, accepted: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("status", if (accepted) "accepted" else "rejected")
                addProperty("responded_at", utcNow())
            }
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/campaign_driver_invitations?id=eq.$invitationId", "PATCH")
                    .patch(json.toString().toRequestBody(jsonMediaType))
                    .addHeader("Prefer", "return=minimal")
                    .build()
            }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erro ao responder convite (HTTP ${response.code})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== AGENDAMENTO DE ADESIVAÇÃO ====================

    suspend fun fetchLatestSchedule(campaignId: String, driverId: String): Result<DriverSchedule?> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthed {
                authRequestBuilder(
                    "/rest/v1/campaign_driver_schedules?campaign_id=eq.$campaignId&driver_id=eq.$driverId&select=id,scheduled_date,scheduled_time&order=created_at.desc&limit=1",
                    "GET"
                ).build()
            }
            val body = response.body?.string() ?: "[]"
            val listType = object : TypeToken<List<DriverSchedule>>() {}.type
            val rows: List<DriverSchedule> = gson.fromJson(body, listType)
            Result.success(rows.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSchedule(campaignId: String, driverId: String, invitationId: String?, date: String, time: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("campaign_id", campaignId)
                addProperty("driver_id", driverId)
                invitationId?.let { addProperty("invitation_id", it) }
                addProperty("scheduled_date", date)
                addProperty("scheduled_time", time)
            }
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/campaign_driver_schedules", "POST")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .addHeader("Prefer", "return=representation")
                    .build()
            }
            val body = response.body?.string() ?: "[]"
            if (response.isSuccessful) {
                val listType = object : TypeToken<List<JsonObject>>() {}.type
                val rows: List<JsonObject> = gson.fromJson(body, listType)
                val id = rows.firstOrNull()?.get("id")?.asString
                if (id != null) Result.success(id) else Result.failure(Exception("Agendamento criado, mas sem ID retornado"))
            } else {
                Result.failure(Exception("Erro ao agendar (HTTP ${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== ENVIO DA FOTO DE ADESIVAÇÃO ====================

    suspend fun uploadAdhesionPhoto(campaignId: String, driverId: String, jpegBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val accessToken = prefs.getAccessToken() ?: return@withContext Result.failure(Exception("Sessão expirada, faça login novamente"))
            val fileName = "${campaignId}_${driverId}_${System.currentTimeMillis()}.jpg"
            val body = jpegBytes.toRequestBody("image/jpeg".toMediaType())
            // Requisição "crua" (sem passar por authRequestBuilder), igual ao uploadProfilePhoto —
            // evita o conflito de dois cabeçalhos Content-Type que causava o erro 400.
            val request = Request.Builder()
                .url("$BASE_URL/storage/v1/object/adhesion-photos/$fileName")
                .post(body)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "image/jpeg")
                .addHeader("x-upsert", "true")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val publicUrl = "$BASE_URL/storage/v1/object/public/adhesion-photos/$fileName"
                Result.success(publicUrl)
            } else {
                val errorBody = try { response.body?.string() } catch (e: Exception) { null }
                Result.failure(Exception("Erro ao enviar foto (HTTP ${response.code}): ${errorBody?.take(200) ?: ""}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertAdhesionRecord(campaignId: String, driverId: String, photoUrl: String, scheduleId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("campaign_id", campaignId)
                addProperty("driver_id", driverId)
                addProperty("photo_url", photoUrl)
                scheduleId?.let { addProperty("schedule_id", it) }
            }
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/campaign_driver_adhesions", "POST")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .addHeader("Prefer", "return=minimal")
                    .build()
            }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erro ao registrar adesivação (HTTP ${response.code})"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDriverAdhesion(campaignId: String, driverId: String): Result<DriverAdhesion?> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthed {
                authRequestBuilder(
                    "/rest/v1/campaign_driver_adhesions?campaign_id=eq.$campaignId&driver_id=eq.$driverId&select=photo_url,completed_at&limit=1",
                    "GET"
                ).build()
            }
            val body = response.body?.string() ?: "[]"
            val listType = object : TypeToken<List<DriverAdhesion>>() {}.type
            val rows: List<DriverAdhesion> = gson.fromJson(body, listType)
            Result.success(rows.firstOrNull())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCampaignTypes(campaignId: String): Result<List<CampaignType>> = withContext(Dispatchers.IO) {
        try {
            val response = executeAuthed {
                authRequestBuilder(
                    "/rest/v1/campaign_types?campaign_id=eq.$campaignId&select=*&order=type.asc",
                    "GET"
                ).build()
            }
            val body = response.body?.string() ?: "[]"
            val listType = object : TypeToken<List<CampaignType>>() {}.type
            Result.success(gson.fromJson(body, listType))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== MONITORING ====================

    suspend fun fetchLoginHistory(userId: String, daysBack: Int = 30): Result<List<LoginSession>> = withContext(Dispatchers.IO) {
        try {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -daysBack)
            val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(cal.time)

            val response = executeAuthed {
                authRequestBuilder(
                    "/rest/v1/audit_logs?user_id=eq.$userId&action=eq.login&created_at=gte.$isoDate&order=created_at.desc&select=created_at",
                    "GET"
                ).build()
            }
            val body = response.body?.string() ?: "[]"
            if (!response.isSuccessful) {
                Log.e(TAG, "Erro ao buscar histórico de login: HTTP ${response.code} - $body")
                return@withContext Result.failure(Exception("Erro ao buscar logins (HTTP ${response.code})"))
            }
            val listType = object : TypeToken<List<LoginSession>>() {}.type
            Result.success(gson.fromJson(body, listType))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Soma o tempo (em minutos) de todas as sessões de monitoramento reais do motorista
     *  nos últimos `daysBack` dias — usa started_at/ended_at, ou o horário atual se a sessão
     *  ainda estiver em andamento (is_active = true). */
    suspend fun fetchOnlineMinutes(driverId: String, daysBack: Int = 30): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -daysBack)
            val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(cal.time)

            val request = authRequestBuilder(
                "/rest/v1/driver_monitoring_sessions?driver_id=eq.$driverId&started_at=gte.$isoDate&select=started_at,ended_at,is_active",
                "GET"
            ).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val listType = object : TypeToken<List<JsonObject>>() {}.type
            val rows: List<JsonObject> = gson.fromJson(body, listType)

            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            // Turno de motorista dificilmente passa de 16h seguidas — qualquer sessão além disso
            // é sinal de uma sessão "órfã" (nunca fechada corretamente, ex: app encerrado à força
            // durante testes) e não deve inflar o tempo total exibido.
            val maxReasonableMinutesPerSession = 16 * 60L

            var totalMinutes = 0L
            rows.forEach { row ->
                try {
                    val startStr = row.get("started_at")?.asString?.substring(0, 19) ?: return@forEach
                    val start = sdf.parse(startStr)?.time ?: return@forEach
                    val endedAtElem = row.get("ended_at")
                    val end = if (endedAtElem != null && !endedAtElem.isJsonNull) {
                        sdf.parse(endedAtElem.asString.substring(0, 19))?.time ?: System.currentTimeMillis()
                    } else {
                        System.currentTimeMillis() // sessão ainda ativa: conta até agora
                    }
                    if (end > start) {
                        val minutes = (end - start) / 60000
                        totalMinutes += minutes.coerceAtMost(maxReasonableMinutesPerSession)
                    }
                } catch (e: Exception) { /* ignora linha malformada */ }
            }
            Result.success(totalMinutes.toInt())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Retorna o conjunto de dias (yyyy-MM-dd) em que houve pelo menos um login registrado,
     *  dentro do intervalo [startIso, endIsoExclusive) — usado para colorir o calendário mensal. */
    suspend fun fetchLoginDatesInRange(userId: String, startIso: String, endIsoExclusive: String): Result<Set<String>> = withContext(Dispatchers.IO) {
        try {
            val request = authRequestBuilder(
                "/rest/v1/audit_logs?user_id=eq.$userId&action=eq.login&created_at=gte.$startIso&created_at=lt.$endIsoExclusive&select=created_at",
                "GET"
            ).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val listType = object : TypeToken<List<JsonObject>>() {}.type
            val rows: List<JsonObject> = gson.fromJson(body, listType)
            val dates = rows.mapNotNull { it.get("created_at")?.asString?.substring(0, 10) }.toSet()
            Result.success(dates)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchGpsLogs(driverId: String, daysBack: Int = 7): Result<List<GpsLog>> = withContext(Dispatchers.IO) {
        try {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -daysBack)
            val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(cal.time)

            val response = executeAuthed {
                authRequestBuilder(
                    "/rest/v1/driver_gps_logs?driver_id=eq.$driverId&timestamp=gte.$isoDate&order=timestamp.asc&select=latitude,longitude,timestamp",
                    "GET"
                ).build()
            }
            val body = response.body?.string() ?: "[]"
            val listType = object : TypeToken<List<GpsLog>>() {}.type
            Result.success(gson.fromJson(body, listType))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startMonitoringSession(driverId: String, campaignId: String?): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sessionId = java.util.UUID.randomUUID().toString()
            val json = JsonObject().apply {
                addProperty("id", sessionId)
                addProperty("driver_id", driverId)
                campaignId?.let { addProperty("campaign_id", it) }
                addProperty("is_active", true)
            }
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/driver_monitoring_sessions", "POST")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .addHeader("Prefer", "return=minimal")
                    .build()
            }
            if (response.isSuccessful) Result.success(sessionId)
            else Result.failure(Exception("Erro ao criar sessão: ${response.code}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun endMonitoringSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("ended_at", utcNow())
                addProperty("is_active", false)
            }
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/driver_monitoring_sessions?id=eq.$sessionId", "PATCH")
                    .patch(json.toString().toRequestBody(jsonMediaType))
                    .addHeader("Prefer", "return=minimal")
                    .build()
            }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erro ao encerrar sessão"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Envia vários pontos de GPS de uma vez só (um único lote/uma única conexão de rede),
     *  em vez de uma requisição por ponto — é o que dá a economia de bateria e permite
     *  guardar pontos coletados offline para enviar quando a internet voltar. */
    private fun pointToJson(driverId: String, sessionId: String, p: QueuedGpsPoint): JsonObject {
        return JsonObject().apply {
            addProperty("driver_id", driverId)
            addProperty("session_id", sessionId)
            addProperty("latitude", p.latitude)
            addProperty("longitude", p.longitude)
            // Descarta valores que o Postgres/JSON não aceitam (NaN, Infinito) — um único
            // valor assim no lote inteiro faz o banco recusar TODOS os pontos, não só esse.
            p.accuracy?.takeIf { it.isFinite() }?.let { addProperty("accuracy", it) }
            p.speed?.takeIf { it.isFinite() }?.let { addProperty("speed", it) }
            addProperty("timestamp", p.timestamp)
        }
    }

    private suspend fun errorBodyMessage(response: Response): String {
        val body = try { response.body?.string() } catch (e: Exception) { null } ?: return "HTTP ${response.code}"
        return try {
            val json = gson.fromJson(body, JsonObject::class.java)
            json.get("message")?.asString ?: json.get("hint")?.asString ?: body.take(200)
        } catch (e: Exception) {
            body.take(200)
        }
    }

    suspend fun insertGpsLogsBatch(driverId: String, sessionId: String, points: List<QueuedGpsPoint>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (points.isEmpty()) return@withContext Result.success(Unit)

            val jsonArray = com.google.gson.JsonArray()
            points.forEach { jsonArray.add(pointToJson(driverId, sessionId, it)) }

            val response = executeAuthed {
                authRequestBuilder("/rest/v1/driver_gps_logs", "POST")
                    .post(jsonArray.toString().toRequestBody(jsonMediaType))
                    .addHeader("Prefer", "return=minimal")
                    .build()
            }
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val reason = errorBodyMessage(response)
                Log.e(TAG, "Falha no lote de GPS (${points.size} pontos): $reason")

                // O lote inteiro caiu — tenta ponto a ponto para não deixar 1 valor ruim
                // bloquear todos os outros pontos bons pra sempre.
                var successCount = 0
                for (p in points) {
                    try {
                        val singleResponse = executeAuthed {
                            authRequestBuilder("/rest/v1/driver_gps_logs", "POST")
                                .post(pointToJson(driverId, sessionId, p).toString().toRequestBody(jsonMediaType))
                                .addHeader("Prefer", "return=minimal")
                                .build()
                        }
                        if (singleResponse.isSuccessful) successCount++
                        else Log.e(TAG, "Ponto individual recusado: ${errorBodyMessage(singleResponse)}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao enviar ponto individual", e)
                    }
                }

                // Importante: consideramos "resolvido" mesmo quando pontos ruins são descartados —
                // senão a fila nunca esvazia e os pontos BONS que já foram enviados seriam
                // reenviados de novo no próximo ciclo, duplicando linhas no banco.
                Log.i(TAG, "Fallback individual: $successCount/${points.size} pontos salvos (motivo do lote original: $reason)")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertGpsLog(driverId: String, sessionId: String, latitude: Double, longitude: Double, accuracy: Double? = null, speed: Double? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("driver_id", driverId)
                addProperty("session_id", sessionId)
                addProperty("latitude", latitude)
                addProperty("longitude", longitude)
                accuracy?.let { addProperty("accuracy", it) }
                speed?.let { addProperty("speed", it) }
                addProperty("timestamp", utcNow())
            }
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/driver_gps_logs", "POST")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .addHeader("Prefer", "return=minimal")
                    .build()
            }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erro ao salvar GPS"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun insertAuditLog(userId: String, action: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("user_id", userId)
                addProperty("action", action)
                addProperty("created_at", utcNow())
            }
            val response = executeAuthed {
                authRequestBuilder("/rest/v1/audit_logs", "POST")
                    .post(json.toString().toRequestBody(jsonMediaType))
                    .addHeader("Prefer", "return=minimal")
                    .build()
            }
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Erro ao salvar log"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== UTILS ====================

    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2).pow(2.0) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).pow(2.0)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private fun Double.pow(n: Double): Double = Math.pow(this, n)
}

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String
)

data class RegisterAuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String
)
