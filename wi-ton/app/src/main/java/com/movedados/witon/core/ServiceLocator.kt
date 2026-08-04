package com.movedados.witon.core

import android.content.Context
import com.movedados.witon.data.local.WiTonDatabase
import com.movedados.witon.data.repository.AdminRepository
import com.movedados.witon.data.repository.AuthRepository
import com.movedados.witon.data.repository.SurveyRepository
import com.movedados.witon.wifi.RssiSampler
import com.movedados.witon.wifi.WifiStateMonitor

/**
 * DI manual. O projeto ainda e pequeno demais para justificar Hilt;
 * se crescer, trocar isto por Hilt e um refactor localizado.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val authRepository: AuthRepository by lazy { AuthRepository() }
    val adminRepository: AdminRepository by lazy { AdminRepository() }
    val database: WiTonDatabase by lazy { WiTonDatabase.get(appContext) }
    val surveyRepository: SurveyRepository by lazy { SurveyRepository(database.surveyDao()) }
    val wifiMonitor: WifiStateMonitor by lazy { WifiStateMonitor(appContext) }

    fun newRssiSampler(): RssiSampler = RssiSampler(appContext)
}
