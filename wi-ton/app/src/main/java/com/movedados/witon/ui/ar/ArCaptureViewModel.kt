package com.movedados.witon.ui.ar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.movedados.witon.core.ServiceLocator
import com.movedados.witon.data.local.entity.SurveyEntity
import com.movedados.witon.data.local.entity.SurveyPointEntity
import com.movedados.witon.wifi.RssiScale
import com.movedados.witon.wifi.SignalQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** Um balao ja ancorado na cena — o que a UI desenha. */
data class CaptureMarker(
    val anchor: Anchor,
    val quality: SignalQuality
)

data class ArCaptureUiState(
    val survey: SurveyEntity? = null,
    val pointsCount: Int = 0,
    val currentRssi: Int? = null,
    val trackingOk: Boolean = true,
    val trackingMessage: String? = null,
    val floorLocked: Boolean = false,
    val finished: Boolean = false,
    val error: String? = null
)

/**
 * Amostra por distancia percorrida (0.5 m) OU tempo parado (1.5 s), nunca por
 * frame — a 30 fps isso geraria milhares de pontos redundantes por sessao.
 *
 * O sinal de Wi-Fi vem do RssiSampler, que roda em paralelo (background,
 * independente da cadencia dos frames de AR) atualizando `latest`; a cada
 * amostra espacial aqui, so lemos o valor mais recente que ele calculou.
 */
class ArCaptureViewModel : ViewModel() {

    private val repo = ServiceLocator.surveyRepository
    private val rssiSampler = ServiceLocator.newRssiSampler()

    private val _state = MutableStateFlow(ArCaptureUiState())
    val state: StateFlow<ArCaptureUiState> = _state.asStateFlow()

    // Limite de baloes DESENHADOS ao mesmo tempo — nao limite do que e salvo.
    // Sem isso a cena acumula milhares de nos e o frame rate desmorona.
    private val maxVisibleMarkers = 250
    private val _markers = MutableStateFlow<List<CaptureMarker>>(emptyList())
    val markers: StateFlow<List<CaptureMarker>> = _markers.asStateFlow()

    private var floorY: Float? = null
    private var framesSeen = 0
    private var lastSampleX = 0f
    private var lastSampleY = 0f
    private var lastSampleZ = 0f
    private var lastSampleAtMs = 0L
    private var seq = 0
    private var started = false

    fun start(name: String) {
        if (started) return
        started = true
        viewModelScope.launch {
            runCatching { repo.createSurvey(name, ssid = null, bssid = null, frequencyMhz = null) }
                .onSuccess { survey -> _state.value = _state.value.copy(survey = survey) }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
        viewModelScope.launch {
            var networkInfoSaved = false
            rssiSampler.samples().collect { sample ->
                _state.value = _state.value.copy(currentRssi = sample.rssi)

                // A primeira amostra chega assincronamente, depois da criacao da
                // leitura — por isso o SSID/BSSID/frequencia sao preenchidos aqui
                // e nao no momento de criar o registro.
                if (!networkInfoSaved && sample.ssid != null) {
                    networkInfoSaved = true
                    _state.value.survey?.let { survey ->
                        val updated = survey.copy(
                            ssid = sample.ssid,
                            bssid = sample.bssid,
                            frequencyMhz = sample.frequencyMhz
                        )
                        runCatching { repo.updateSurveyNetworkInfo(updated) }
                            .onSuccess { _state.value = _state.value.copy(survey = updated) }
                    }
                }
            }
        }
    }

    /** Chamado a cada frame de AR pelo onSessionUpdated do ARSceneView. */
    fun onFrame(session: Session, frame: Frame) {
        val survey = _state.value.survey ?: return
        val camera = frame.camera

        val tracking = camera.trackingState == TrackingState.TRACKING
        _state.value = _state.value.copy(
            trackingOk = tracking,
            trackingMessage = if (tracking) null else trackingReasonLabel(camera.trackingFailureReason)
        )
        if (!tracking) return

        framesSeen++

        // Piso: usa o primeiro plano horizontal voltado pra cima que aparecer;
        // se depois de ~2s (a 30fps) nenhum plano surgiu, cai para uma estimativa
        // (altura media de mao abaixo da camera) para nao travar a captura.
        if (floorY == null) {
            val plane = frame.getUpdatedTrackables(Plane::class.java).firstOrNull {
                it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.trackingState == TrackingState.TRACKING
            }
            if (plane != null) {
                floorY = plane.centerPose.ty()
                _state.value = _state.value.copy(floorLocked = true)
            } else if (framesSeen > 60) {
                floorY = camera.pose.ty() - 1.4f
                _state.value = _state.value.copy(floorLocked = true)
            }
        }

        val pose = camera.pose
        val now = System.currentTimeMillis()
        val distance = hypot(
            hypot((pose.tx() - lastSampleX).toDouble(), (pose.ty() - lastSampleY).toDouble()),
            (pose.tz() - lastSampleZ).toDouble()
        )
        val dueByDistance = distance > 0.5
        val dueByTime = now - lastSampleAtMs > 1500

        if (!dueByDistance && !dueByTime) return

        val rssi = rssiSampler.latest?.rssi ?: return
        lastSampleX = pose.tx(); lastSampleY = pose.ty(); lastSampleZ = pose.tz()
        lastSampleAtMs = now

        val floor = floorY ?: (pose.ty() - 1.4f)
        val markerPose = Pose(
            floatArrayOf(pose.tx(), floor, pose.tz()),
            pose.rotationQuaternion
        )
        val anchor = runCatching { session.createAnchor(markerPose) }.getOrNull() ?: return

        seq++
        val point = SurveyPointEntity(
            surveyLocalId = survey.localId,
            seq = seq,
            x = pose.tx(), y = pose.ty(), z = pose.tz(),
            rssi = rssi,
            rawRssi = rssiSampler.latest?.rawRssi,
            linkSpeedMbps = rssiSampler.latest?.linkSpeedMbps,
            frequencyMhz = rssiSampler.latest?.frequencyMhz,
            trackingQuality = "TRACKING"
        )
        viewModelScope.launch {
            runCatching { repo.addPoint(point) }
                .onSuccess { _state.value = _state.value.copy(pointsCount = seq) }
        }

        val marker = CaptureMarker(anchor = anchor, quality = RssiScale.quality(rssi))
        _markers.value = (_markers.value + marker).takeLast(maxVisibleMarkers)
    }

    /** ARCore pode falhar ao iniciar (Play Services for AR ausente/desatualizado, etc). */
    fun onSessionFailed(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    fun stop() {
        val survey = _state.value.survey ?: return
        viewModelScope.launch {
            runCatching { repo.finishLocally(survey, floorY) }
                .onSuccess { _state.value = _state.value.copy(finished = true) }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Anchors seguram recursos nativos do ARCore — liberar explicitamente
        // evita vazamento quando a tela e destruida no meio de uma captura.
        _markers.value.forEach { runCatching { it.anchor.detach() } }
    }

    private fun trackingReasonLabel(reason: TrackingFailureReason): String = when (reason) {
        TrackingFailureReason.EXCESSIVE_MOTION -> "Movimento rapido demais — ande mais devagar"
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Pouca luz no ambiente"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Aponte para uma area com mais textura"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera indisponivel"
        else -> "Recalibrando o rastreamento..."
    }
}
