package com.movedados.movetv.driver.services

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class LocationGuardWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = com.movedados.movetv.driver.utils.PreferenceManager(applicationContext)
        val isMonitoring = prefs.isMonitoringActive()
        val lastGpsTimestamp = prefs.getLastGpsTimestamp()
        val now = System.currentTimeMillis()

        if (isMonitoring && (lastGpsTimestamp == 0L || now - lastGpsTimestamp > 2 * 60 * 1000)) {
            val intent = Intent(applicationContext, LocationService::class.java)
            ContextCompat.startForegroundService(applicationContext, intent)
        }

        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "location_guard_worker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LocationGuardWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
