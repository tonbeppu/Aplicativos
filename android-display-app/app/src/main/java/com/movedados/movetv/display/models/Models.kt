package com.movedados.movetv.display.models

data class Profile(
    val id: String = "",
    val email: String = "",
    val full_name: String = "",
    val role: String = "",
    val device_id: String? = null,
    val campaign_id: String? = null
)

data class Device(
    val id: String = "",
    val name: String? = null
)

data class MediaItem(
    val id: String = "",
    val title: String = "",
    val file_url: String = "",
    val file_type: String = "", // "image" | "video"
    val file_format: String = "", // "image" | "video" | "youtube" | "website" | "rss"
    val duration: Int? = null
)

data class CampaignMediaRow(
    val media_id: String = "",
    val display_order: Int = 0
)

data class CampaignDeviceRow(
    val campaign_id: String? = null
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String
)
