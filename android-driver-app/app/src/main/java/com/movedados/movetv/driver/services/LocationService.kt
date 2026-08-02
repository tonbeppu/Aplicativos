package com.movedados.movetv.driver.services

import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.LifecycleService
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.movedados.movetv.driver.MoveTVDriverApp
import com.movedados.movetv.driver.R
import com.movedados.movetv.driver.models.QueuedGpsPoint
import com.movedados.movetv.driver.network.SupabaseClient
import com.movedados.movetv.driver.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LocationService : LifecycleService() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var prefs: PreferenceManager
    private lateinit var supabase: SupabaseClient
    private lateinit var gson: Gson
    private var wakeLock: PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())

    // Envia em lote tudo que foi coletado, a cada 3 minutos (mantém a economia de rede/bateria
    // mesmo com a coleta agora sendo contínua e confiável)
    private val flushLoop = object : Runnable {
        override fun run() {
            attemptFlush()
            handler.postDelayed(this, FLUSH_INTERVAL_MS)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val COLLECTION_INTERVAL_MS = 30_000L
        private const val FLUSH_INTERVAL_MS = 180_000L // 3 minutos
        private const val MIN_DISTANCE_METERS = 15.0 // abaixo disso, considera que não se moveu
        private const val HEARTBEAT_INTERVAL_MS = 5 * 60_000L // salva um ponto de prova a cada 5 min mesmo parado
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager(this)
        supabase = SupabaseClient(this)
        gson = Gson()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        prefs.resetMonitoringSession()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        setupWakeLock()
        openMonitoringSession()

        // Coleta CONTÍNUA (recomendada pelo Android para serviços em primeiro plano) — muito mais
        // confiável com a tela bloqueada do que pedir localizações avulsas repetidamente.
        setupLocationCallback()
        startLocationUpdates()

        handler.postDelayed(flushLoop, FLUSH_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun openMonitoringSession() {
        val driverId = prefs.getUserId() ?: return
        lifecycleScope.launch {
            val campaignId = supabase.fetchDriverCampaigns(driverId).getOrNull()
                ?.firstOrNull { it.status == "active" }?.id
            val result = supabase.startMonitoringSession(driverId, campaignId)
            result.getOrNull()?.let { sessionId -> prefs.setSessionId(sessionId) }
        }
    }

    private fun setupWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MoveDadosDriver::LocationWakeLock").apply {
                setReferenceCounted(true)
            }
        } catch (_: Exception) { }
    }



    @android.annotation.SuppressLint("MissingPermission")
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { enqueueLocation(it) }
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, COLLECTION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(COLLECTION_INTERVAL_MS / 2)
            .setMaxUpdateDelayMillis(COLLECTION_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    /** Guarda o ponto localmente (rápido, sem rede) — o envio de verdade acontece no flushLoop.
     *  Se o motorista estiver parado (deslocamento menor que MIN_DISTANCE_METERS desde o último
     *  ponto salvo), o ponto é descartado — exceto a cada HEARTBEAT_INTERVAL_MS, quando salva um
     *  "ponto de prova" mesmo parado, para não parecer que o monitoramento foi desligado durante
     *  uma parada longa (sinal, engarrafamento, descanso). */
    private fun enqueueLocation(location: Location) {
        prefs.setLastAccuracy(location.accuracy)
        prefs.setLastGpsTimestamp(System.currentTimeMillis())

        val lastLat = prefs.getLastSavedLat()
        val lastLng = prefs.getLastSavedLng()
        val lastSavedAt = prefs.getLastSavedAt()
        val timeSinceLastSave = System.currentTimeMillis() - lastSavedAt

        val hasMoved = if (lastLat != null && lastLng != null) {
            val distanceKm = supabase.haversineKm(lastLat.toDouble(), lastLng.toDouble(), location.latitude, location.longitude)
            (distanceKm * 1000) >= MIN_DISTANCE_METERS
        } else {
            true // primeiro ponto da sessão: sempre salva
        }

        val isHeartbeatDue = timeSinceLastSave >= HEARTBEAT_INTERVAL_MS

        if (!hasMoved && !isHeartbeatDue) {
            return // parado e ainda não é hora do "ponto de prova" — não salva, economiza envio
        }

        prefs.incrementGpsCount()
        prefs.setLastSavedPoint(location.latitude, location.longitude)
        prefs.enqueueGpsPoint(
            QueuedGpsPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy.toDouble(),
                speed = if (location.hasSpeed()) location.speed.toDouble() else null,
                timestamp = utcNow()
            )
        )
    }

    /** Tenta enviar tudo que está na fila local em um único lote. Sem internet, simplesmente
     *  falha e tenta de novo no próximo ciclo — nada se perde, tudo continua guardado. */
    private fun attemptFlush() {
        val driverId = prefs.getUserId() ?: return
        val queued = prefs.getGpsQueue()
        if (queued.isEmpty()) return

        // O wake lock precisa envolver a corrotina INTEIRA (incluindo a chamada de rede),
        // não só o instante de agendá-la — senão o processador pode "dormir" bem no meio
        // do envio quando a tela está bloqueada, cortando o upload pela metade.
        try { wakeLock?.acquire(25_000L) } catch (_: Exception) { }
        lifecycleScope.launch {
            try {
                var sessionId = prefs.getSessionId()
                if (sessionId == null) {
                    val campaignId = supabase.fetchDriverCampaigns(driverId).getOrNull()
                        ?.firstOrNull { it.status == "active" }?.id
                    sessionId = supabase.startMonitoringSession(driverId, campaignId).getOrNull()
                    sessionId?.let { prefs.setSessionId(it) }
                }

                if (sessionId != null) {
                    val result = supabase.insertGpsLogsBatch(driverId, sessionId, queued)
                    if (result.isSuccess) {
                        prefs.clearGpsQueue()
                    }
                }
            } finally {
                try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
            }
        }
    }

    private fun utcNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, MoveTVDriverApp.LOCATION_CHANNEL_ID)
            .setContentTitle("MoveDados Driver")
            .setContentText("Monitoramento GPS ativo")
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFD90000.toInt())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        val driverId = prefs.getUserId()
        val sessionId = prefs.getSessionId()
        val queued = prefs.getGpsQueue()
        if (driverId != null && sessionId != null && queued.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                val result = supabase.insertGpsLogsBatch(driverId, sessionId, queued)
                if (result.isSuccess) prefs.clearGpsQueue()
            }
        }
        if (sessionId != null) {
            CoroutineScope(Dispatchers.IO).launch { supabase.endMonitoringSession(sessionId) }
            prefs.setSessionId(null)
        }

        super.onDestroy()
        handler.removeCallbacks(flushLoop)
        if (::fusedClient.isInitialized && ::locationCallback.isInitialized) {
            fusedClient.removeLocationUpdates(locationCallback)
        }
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
    }
}
