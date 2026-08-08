package com.movedados.movetv.driver.models

data class Invitation(
    val id: String = "",
    val status: String = "pending",
    val invited_at: String? = null,
    val responded_at: String? = null
)

data class DriverSchedule(
    val id: String = "",
    val scheduled_date: String = "",
    val scheduled_time: String = ""
)

data class QueuedGpsPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?,
    val speed: Double?,
    val timestamp: String
)

data class Profile(
    val id: String = "",
    val email: String = "",
    val full_name: String = "",
    val role: String = "",
    val cpf: String? = null,
    val phone: String? = null,
    val birth_date: String? = null,
    val city: String? = null,
    val state: String? = null,
    val profile_photo_url: String? = null,
    val device_id: String? = null,
    val vehicle_manufacturer: String? = null,
    val vehicle_model: String? = null,
    val vehicle_year: Int? = null,
    val vehicle_plate: String? = null,
    val vehicle_color: String? = null,
    val vehicle_motor_type: String? = null,
    val vehicle_photos: List<String>? = null,
    val pix_type: String? = null,
    val pix_key: String? = null
)

data class Campaign(
    val id: String = "",
    val name: String = "",
    val status: String = "",
    val start_date: String = "",
    val end_date: String? = null,
    val campaign_type: String? = null,
    val driver_payment_value: Double? = null,
    val image_url: String? = null,
    val adhesion_end_date: String? = null
)

data class DriverAdhesion(
    val photo_url: String? = null,
    val completed_at: String? = null
)

data class CampaignType(
    val id: String = "",
    val type: String = "",
    val quantity: Int = 0,
    val accepted_count: Int = 0,
    val benefit_type: String = "",
    val benefit_value: Double? = null,
    val benefit_description: String? = null
)

data class GpsLog(
    val latitude: Double,
    val longitude: Double,
    val timestamp: String
)

data class KmDay(
    val date: String,
    val km: Double,
    val points: Int
)

data class MonitoringStats(
    val totalLogins: Int = 0,
    val totalTimeMinutes: Int = 0,
    val activeCampaigns: Int = 0,
    val completedCampaigns: Int = 0,
    val gpsPointsTotal: Int = 0
)

data class LoginSession(
    val created_at: String,
    val details: String? = null
)
