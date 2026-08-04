package com.movedados.witon.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AccountStatus {
    @SerialName("pending")   PENDING,
    @SerialName("approved")  APPROVED,
    @SerialName("rejected")  REJECTED,
    @SerialName("suspended") SUSPENDED
}

@Serializable
enum class UserRole {
    @SerialName("admin") ADMIN,
    @SerialName("user")  USER
}

@Serializable
data class ProfileDto(
    val id: String,
    val email: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val phone: String? = null,
    val company: String? = null,
    val city: String? = null,
    val role: UserRole = UserRole.USER,
    val status: AccountStatus = AccountStatus.PENDING,
    @SerialName("requested_at") val requestedAt: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null
)

/** Retorno da RPC my_access_status(). */
@Serializable
data class AccessStatusDto(
    val status: AccountStatus,
    val role: UserRole,
    @SerialName("rejection_reason") val rejectionReason: String? = null
)
