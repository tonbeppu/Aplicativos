package com.movedados.witon.data.repository

import android.os.Build
import com.movedados.witon.data.local.dao.SurveyDao
import com.movedados.witon.data.local.entity.SurveyEntity
import com.movedados.witon.data.local.entity.SurveyPointEntity
import com.movedados.witon.data.local.entity.SurveyWallEntity
import com.movedados.witon.data.remote.SupabaseModule
import com.movedados.witon.data.remote.dto.SurveyDto
import com.movedados.witon.data.remote.dto.SurveyPointDto
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Fonte da verdade e o Room: a leitura grava localmente primeiro porque o
 * Wi-Fi e o proprio objeto do teste, entao a captura nao pode depender da
 * rede que ela mesma esta medindo. O envio ao Supabase acontece so quando o
 * usuario encerra a leitura (sync()), em lote.
 */
class SurveyRepository(private val dao: SurveyDao) {

    private val client = SupabaseModule.client

    fun observeSurveys(): Flow<List<SurveyEntity>> = dao.observeSurveys()

    fun observePointCount(surveyLocalId: String): Flow<Int> =
        dao.observePointCount(surveyLocalId)

    suspend fun createSurvey(
        name: String,
        ssid: String?,
        bssid: String?,
        frequencyMhz: Int?
    ): SurveyEntity {
        val survey = SurveyEntity(
            localId = UUID.randomUUID().toString(),
            name = name,
            ssid = ssid,
            bssid = bssid,
            frequencyMhz = frequencyMhz,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidVersion = Build.VERSION.RELEASE
        )
        dao.insertSurvey(survey)
        return survey
    }

    /** Preenche SSID/BSSID/frequencia assim que a primeira amostra de Wi-Fi chega. */
    suspend fun updateSurveyNetworkInfo(survey: SurveyEntity) = dao.updateSurvey(survey)

    suspend fun addPoint(point: SurveyPointEntity) = dao.insertPoint(point)

    suspend fun addWalls(walls: List<SurveyWallEntity>) {
        if (walls.isNotEmpty()) dao.insertWalls(walls)
    }

    suspend fun finishLocally(survey: SurveyEntity, floorY: Float?) {
        dao.updateSurvey(
            survey.copy(
                status = "finished",
                floorY = floorY,
                endedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSurvey(localId: String): SurveyEntity? = dao.getSurvey(localId)

    suspend fun getPoints(surveyLocalId: String): List<SurveyPointEntity> =
        dao.pointsOf(surveyLocalId)

    /**
     * Envia a leitura completa ao Supabase e fecha o RPC finish_survey, que
     * consolida min/max/avg de RSSI no banco. So chamada explicitamente pelo
     * usuario (botao "Sincronizar") — nunca em background, para nao gastar
     * dados moveis sem avisar.
     */
    suspend fun sync(surveyLocalId: String) {
        val survey = dao.getSurvey(surveyLocalId) ?: return
        val userId = client.auth.currentUserOrNull()?.id ?: error("Sem sessao ativa")

        val remoteId = survey.remoteId ?: run {
            val inserted = client.postgrest.from("surveys")
                .insert(
                    SurveyDto(
                        userId = userId,
                        name = survey.name,
                        status = "processing",
                        ssid = survey.ssid,
                        bssid = survey.bssid,
                        frequencyMhz = survey.frequencyMhz,
                        deviceModel = survey.deviceModel,
                        androidVersion = survey.androidVersion,
                        floorY = survey.floorY
                    )
                ) { select() }
                .decodeSingle<SurveyDto>()
            inserted.id!!
        }

        val points = dao.pointsOf(surveyLocalId)
        if (points.isNotEmpty()) {
            val dtos = points.map {
                SurveyPointDto(
                    surveyId = remoteId,
                    x = it.x, y = it.y, z = it.z,
                    rssi = it.rssi,
                    rawRssi = it.rawRssi,
                    linkSpeedMbps = it.linkSpeedMbps,
                    frequencyMhz = it.frequencyMhz,
                    trackingQuality = it.trackingQuality,
                    seq = it.seq
                )
            }
            // Lotes de 500 — o Postgrest tem limite pratico de payload por requisicao.
            dtos.chunked(500).forEach { batch ->
                client.postgrest.from("survey_points").insert(batch)
            }
            dao.markPointsSynced(surveyLocalId)
        }

        client.postgrest.rpc("finish_survey", buildJsonObject {
            put("p_survey_id", remoteId)
        })

        dao.updateSurvey(survey.copy(remoteId = remoteId, synced = true, status = "finished"))
    }
}
