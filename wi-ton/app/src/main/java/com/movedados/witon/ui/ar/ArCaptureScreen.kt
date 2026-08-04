package com.movedados.witon.ui.ar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ar.core.Config
import com.movedados.witon.ui.components.StatusBadge
import com.movedados.witon.ui.theme.Slate900
import com.movedados.witon.ui.theme.StatusOrange
import com.movedados.witon.ui.theme.StatusRed
import com.movedados.witon.wifi.RssiScale
import com.movedados.witon.wifi.SignalQuality
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.SphereNode

@Composable
fun ArCaptureScreen(
    surveyName: String,
    onFinished: (surveyLocalId: String) -> Unit,
    vm: ArCaptureViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    val state by vm.state.collectAsState()
    LaunchedEffect(state.finished) {
        state.survey?.let { if (state.finished) onFinished(it.localId) }
    }
    LaunchedEffect(Unit) {
        vm.start(surveyName)
    }

    Box(modifier = Modifier.fillMaxSize().background(Slate900)) {
        if (state.error != null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "Nao foi possivel iniciar a realidade aumentada:\n${state.error}\n\n" +
                    "Verifique se o Google Play Services for AR esta instalado e atualizado.",
                    color = Color.White,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else if (hasCameraPermission) {
            ArCaptureViewport(vm)
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    "A camera e necessaria para a captura em realidade aumentada.",
                    color = Color.White,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }

        if (state.error == null) {
            CaptureHud(
                pointsCount = state.pointsCount,
                currentRssi = state.currentRssi,
                trackingOk = state.trackingOk,
                trackingMessage = state.trackingMessage,
                onStop = { vm.stop() }
            )
        }
    }
}

/**
 * Viewport da camera com os baloes ancorados no espaco.
 *
 * As cores usam 5 materiais fixos (um por faixa de RssiScale.SignalQuality),
 * criados uma unica vez e reaproveitados por todos os baloes — criar um
 * MaterialInstance novo por balao a cada recomposicao seria caro e
 * desnecessario, ja que a paleta e sempre a mesma.
 */
@Composable
private fun ArCaptureViewport(vm: ArCaptureViewModel) {
    val markers by vm.markers.collectAsState()

    ARSceneView(
        modifier = Modifier.fillMaxSize(),
        planeRenderer = true,
        depthMode = Config.DepthMode.AUTOMATIC,
        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
        onSessionFailed = { exception ->
            vm.onSessionFailed(exception.message ?: "Erro desconhecido ao iniciar o ARCore")
        },
        onSessionUpdated = { session, frame -> vm.onFrame(session, frame) }
    ) {
        val qualityMaterials = remember(materialLoader) {
            SignalQuality.entries.associateWith { quality ->
                materialLoader.createUnlitColorInstance(RssiScale.colorFor(quality))
            }
        }

        markers.forEach { marker ->
            AnchorNode(anchor = marker.anchor) {
                SphereNode(
                    radius = 0.06f,
                    materialInstance = qualityMaterials[marker.quality]
                )
            }
        }
    }
}

@Composable
private fun CaptureHud(
    pointsCount: Int,
    currentRssi: Int?,
    trackingOk: Boolean,
    trackingMessage: String?,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = currentRssi?.let { "$it dBm" } ?: "Lendo sinal...",
                    color = currentRssi?.let { RssiScale.color(it) } ?: Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "$pointsCount pontos capturados",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (!trackingOk) {
                StatusBadge(label = "recalibrando", color = StatusOrange)
            }
        }

        if (!trackingOk && trackingMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(StatusOrange.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(trackingMessage, color = Color.Black, style = MaterialTheme.typography.bodySmall)
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text("Parar leitura", color = Color.White)
            }
        }
    }
}
