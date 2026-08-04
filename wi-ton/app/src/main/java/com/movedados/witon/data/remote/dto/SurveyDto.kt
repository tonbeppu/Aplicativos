package com.movedados.witon.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SurveyDto(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val name: String,
    val status: String = "recording",
    val ssid: String? = null,
    val bssid: String? = null,
    @SerialName("frequency_mhz") val frequencyMhz: Int? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("android_version") val androidVersion: String? = null,
    @SerialName("floor_y") val floorY: Float? = null,
    @SerialName("area_m2") val areaM2: Float? = null,
    @SerialName("points_count") val pointsCount: Int = 0,
    @SerialName("rssi_min") val rssiMin: Int? = null,
    @SerialName("rssi_max") val rssiMax: Int? = null,
    @SerialName("rssi_avg") val rssiAvg: Float? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null
)

@Serializable
data class SurveyPointDto(
    @SerialName("survey_id") val surveyId: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val rssi: Int,
    @SerialName("raw_rssi") val rawRssi: Int? = null,
    @SerialName("link_speed_mbps") val linkSpeedMbps: Int? = null,
    @SerialName("frequency_mhz") val frequencyMhz: Int? = null,
    @SerialName("tracking_quality") val trackingQuality: String? = null,
    val seq: Int? = null,
    @SerialName("captured_at") val capturedAt: String? = null
)
