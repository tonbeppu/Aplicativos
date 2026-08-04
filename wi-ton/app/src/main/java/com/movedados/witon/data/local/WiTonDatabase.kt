package com.movedados.witon.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.movedados.witon.data.local.dao.SurveyDao
import com.movedados.witon.data.local.entity.SurveyEntity
import com.movedados.witon.data.local.entity.SurveyPointEntity
import com.movedados.witon.data.local.entity.SurveyWallEntity

@Database(
    entities = [SurveyEntity::class, SurveyPointEntity::class, SurveyWallEntity::class],
    version = 1,
    exportSchema = true
)
abstract class WiTonDatabase : RoomDatabase() {

    abstract fun surveyDao(): SurveyDao

    companion object {
        @Volatile private var instance: WiTonDatabase? = null

        fun get(context: Context): WiTonDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WiTonDatabase::class.java,
                    "witon.db"
                ).build().also { instance = it }
            }
    }
}
