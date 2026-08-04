package com.movedados.witon.data.repository

import com.movedados.witon.data.remote.SupabaseModule
import com.movedados.witon.data.remote.dto.AccountStatus
import com.movedados.witon.data.remote.dto.ProfileDto
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Moderacao de contas. Todas as chamadas passam pelo RLS: se quem chamar
 * nao for admin aprovado, o banco simplesmente nao devolve/altera nada.
 */
class AdminRepository {

    private val client = SupabaseModule.client

    suspend fun listByStatus(status: AccountStatus): List<ProfileDto> =
        client.postgrest.from("profiles")
            .select {
                filter { eq("status", status.name.lowercase()) }
                order("requested_at", Order.ASCENDING)
            }
            .decodeList()

    suspend fun listAll(): List<ProfileDto> =
        client.postgrest.from("profiles")
            .select { order("requested_at", Order.DESCENDING) }
            .decodeList()

    suspend fun approve(userId: String) {
        client.postgrest.rpc("approve_user", buildJsonObject {
            put("p_user_id", userId)
        })
    }

    suspend fun reject(userId: String, reason: String?) {
        client.postgrest.rpc("reject_user", buildJsonObject {
            put("p_user_id", userId)
            put("p_reason", reason ?: "")
        })
    }

    suspend fun suspend(userId: String, reason: String?) {
        client.postgrest.rpc("suspend_user", buildJsonObject {
            put("p_user_id", userId)
            put("p_reason", reason ?: "")
        })
    }
}
