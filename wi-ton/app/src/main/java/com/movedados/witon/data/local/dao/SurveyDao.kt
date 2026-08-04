package com.movedados.witon.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.movedados.witon.data.local.entity.SurveyEntity
import com.movedados.witon.data.local.entity.SurveyPointEntity
import com.movedados.witon.data.local.entity.SurveyWallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSurvey(survey: SurveyEntity)

    @Update
    suspend fun updateSurvey(survey: SurveyEntity)

    @Query("SELECT * FROM surveys ORDER BY startedAt DESC")
    fun observeSurveys(): Flow<List<SurveyEntity>>

    @Query("SELECT * FROM surveys WHERE localId = :localId")
    suspend fun getSurvey(localId: String): SurveyEntity?

    @Query("SELECT * FROM surveys WHERE synced = 0")
    suspend fun pendingSurveys(): List<SurveyEntity>

    @Insert
    suspend fun insertPoint(point: SurveyPointEntity)

    @Insert
    suspend fun insertPoints(points: List<SurveyPointEntity>)

    @Query("SELECT * FROM survey_points WHERE surveyLocalId = :surveyLocalId ORDER BY seq ASC")
    suspend fun pointsOf(surveyLocalId: String): List<SurveyPointEntity>

    @Query("SELECT COUNT(*) FROM survey_points WHERE surveyLocalId = :surveyLocalId")
    fun observePointCount(surveyLocalId: String): Flow<Int>

    @Query("UPDATE survey_points SET synced = 1 WHERE surveyLocalId = :surveyLocalId")
    suspend fun markPointsSynced(surveyLocalId: String)

    @Insert
    suspend fun insertWalls(walls: List<SurveyWallEntity>)

    @Query("SELECT * FROM survey_walls WHERE surveyLocalId = :surveyLocalId")
    suspend fun wallsOf(surveyLocalId: String): List<SurveyWallEntity>

    @Query("DELETE FROM surveys WHERE localId = :localId")
    suspend fun deleteSurvey(localId: String)
}
