package com.movedados.witon.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Espelho local das tabelas do Supabase. A leitura grava AQUI primeiro:
 * o Wi-Fi e justamente o objeto do teste, entao a captura nao pode depender
 * de rede. A sincronizacao acontece em lote quando o usuario encerra.
 */
@Entity(tableName = "surveys")
data class SurveyEntity(
    @PrimaryKey val localId: String,          // UUID gerado no aparelho
    val remoteId: String? = null,             // preenchido apos o upload
    val name: String,
    val status: String = "recording",
    val ssid: String? = null,
    val bssid: String? = null,
    val frequencyMhz: Int? = null,
    val deviceModel: String? = null,
    val androidVersion: String? = null,
    val floorY: Float? = null,
    val areaM2: Float? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val synced: Boolean = false
)

@Entity(
    tableName = "survey_points",
    foreignKeys = [
        ForeignKey(
            entity = SurveyEntity::class,
            parentColumns = ["localId"],
            childColumns = ["surveyLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("surveyLocalId")]
)
data class SurveyPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surveyLocalId: String,
    val seq: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val rssi: Int,
    val rawRssi: Int?,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val trackingQuality: String?,
    val capturedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

@Entity(
    tableName = "survey_walls",
    foreignKeys = [
        ForeignKey(
            entity = SurveyEntity::class,
            parentColumns = ["localId"],
            childColumns = ["surveyLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("surveyLocalId")]
)
data class SurveyWallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surveyLocalId: String,
    val x1: Float, val z1: Float,
    val x2: Float, val z2: Float,
    val height: Float?,
    val confidence: Float?,
    val synced: Boolean = false
)
