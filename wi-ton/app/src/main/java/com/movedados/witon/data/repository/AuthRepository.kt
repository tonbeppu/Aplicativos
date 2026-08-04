package com.movedados.witon.data.repository

import com.movedados.witon.data.remote.SupabaseModule
import com.movedados.witon.data.remote.dto.AccessStatusDto
import com.movedados.witon.data.remote.dto.AccountStatus
import com.movedados.witon.data.remote.dto.ProfileDto
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SignUpForm(
    val fullName: String,
    val email: String,
    val password: String,
    val phone: String,
    val company: String,
    val city: String
)

class AuthRepository {

    private val client = SupabaseModule.client

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    val isLoggedIn: Flow<Boolean> = sessionStatus.map { it is SessionStatus.Authenticated }

    val currentUserId: String? get() = client.auth.currentUserOrNull()?.id

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /**
     * Cria a conta. Os campos extras viajam no metadata do usuario e sao
     * colhidos pelo trigger handle_new_user, que grava o profile como 'pending'.
     */
    suspend fun signUp(form: SignUpForm) {
        client.auth.signUpWith(Email) {
            email = form.email.trim()
            password = form.password
            data = buildJsonObject {
                put("full_name", form.fullName.trim())
                put("phone", form.phone.trim())
                put("company", form.company.trim())
                put("city", form.city.trim())
            }
        }
    }

    suspend fun signOut() = client.auth.signOut()

    suspend fun sendPasswordReset(email: String) =
        client.auth.resetPasswordForEmail(email.trim())

    /** Fonte da verdade para decidir qual tela mostrar depois do login. */
    suspend fun accessStatus(): AccessStatusDto =
        client.postgrest.rpc("my_access_status")
            .decodeList<AccessStatusDto>()
            .firstOrNull()
            ?: AccessStatusDto(AccountStatus.PENDING, com.movedados.witon.data.remote.dto.UserRole.USER)

    suspend fun myProfile(): ProfileDto? {
        val uid = currentUserId ?: return null
        return client.postgrest.from("profiles")
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull()
    }
}
