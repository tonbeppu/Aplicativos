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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
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

    // O ARCore em si ("Google Play Services for AR") pode nao estar instalado —
    // isso e diferente de nao ter permissao de camera. O manifest declara
    // com.google.ar.core como "optional" de proposito, para o app instalar
    // tambem em aparelhos sem suporte a AR — por isso a instalacao precisa
    // ser pedida aqui, na hora que o usuario tenta captar.
    var arCoreState by remember { mutableStateOf<ArCoreGateState>(ArCoreGateState.Checking) }
    var installRequested by rememberSaveable { mutableStateOf(false) }
    // So para diagnostico: mostra o valor cru que o ArCoreApk devolveu, sem
    // interpretacao — remover depois que o fluxo de instalacao estiver confiavel.
    var lastRawAvailability by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                arCoreState = checkArCoreAvailability(context, installRequested,
                    onInstallRequested = { installRequested = true },
                    onAvailabilityChecked = { lastRawAvailability = it }
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        arCoreState = checkArCoreAvailability(context, installRequested,
            onInstallRequested = { installRequested = true },
            onAvailabilityChecked = { lastRawAvailability = it }
        )
    }

    val state by vm.state.collectAsState()
    LaunchedEffect(state.finished) {
        state.survey?.let { if (state.finished) onFinished(it.localId) }
    }
    LaunchedEffect(arCoreState) {
        if (arCoreState is ArCoreGateState.Ready) {
            vm.start(surveyName)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Slate900)) {
        when (val gate = arCoreState) {
            is ArCoreGateState.Unsupported -> ArGateMessage(gate.message)
            ArCoreGateState.Checking, ArCoreGateState.InstallRequested ->
                ArGateMessage("Preparando a realidade aumentada...")
            ArCoreGateState.Ready -> when {
                state.error != null -> ArGateMessage(
                    "Nao foi possivel iniciar a realidade aumentada:\n${state.error}\n\n" +
                    "Verifique se o Google Play Services for AR esta instalado e atualizado."
                )
                hasCameraPermission -> ArCaptureViewport(vm)
                else -> ArGateMessage("A camera e necessaria para a captura em realidade aumentada.")
            }
        }

        if (arCoreState is ArCoreGateState.Ready && state.error == null) {
            CaptureHud(
                pointsCount = state.pointsCount,
                currentRssi = state.currentRssi,
                trackingOk = state.trackingOk,
                trackingMessage = state.trackingMessage,
                onStop = { vm.stop() }
            )
        }

        // Badge de diagnostico — sempre visivel, independente do estado da tela.
        lastRawAvailability?.let { raw ->
            Box(
                Modifier
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("AR: $raw", color = Color.Yellow, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ArGateMessage(text: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(text, color = Color.White, modifier = Modifier.padding(32.dp))
    }
}

private sealed interface ArCoreGateState {
    data object Checking : ArCoreGateState
    data object InstallRequested : ArCoreGateState
    data object Ready : ArCoreGateState
    data class Unsupported(val message: String) : ArCoreGateState
}

/**
 * Confere se o ARCore esta instalado e, se nao estiver, dispara o fluxo
 * oficial de instalacao via Play Store (ArCoreApk.requestInstall). Esse
 * fluxo e assincrono: o Play Store abre, o usuario instala, o Android volta
 * pra esta Activity via ON_RESUME — e por isso o resultado e checado de novo
 * no LifecycleEventObserver, nao so na primeira chamada.
 */
private fun checkArCoreAvailability(
    context: android.content.Context,
    installRequested: Boolean,
    onInstallRequested: () -> Unit,
    onAvailabilityChecked: (String) -> Unit
): ArCoreGateState {
    val availability = ArCoreApk.getInstance().checkAvailability(context)
    onAvailabilityChecked(availability.name)
    return when {
        availability == ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArCoreGateState.Ready

        availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE ->
            ArCoreGateState.Unsupported(
                "Este aparelho nao suporta realidade aumentada (ARCore). " +
                "A captura AR nao esta disponivel neste dispositivo."
            )

        else -> {
            // SUPPORTED_NOT_INSTALLED, SUPPORTED_APK_TOO_OLD ou UNKNOWN_*
            val activity = context as? android.app.Activity
            if (activity == null) {
                ArCoreGateState.Unsupported("Nao foi possivel verificar o suporte a AR.")
            } else {
                try {
                    when (val status = ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                        ArCoreApk.InstallStatus.INSTALLED -> {
                            onAvailabilityChecked("${availability.name} -> requestInstall=$status")
                            ArCoreGateState.Ready
                        }
                        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                            onAvailabilityChecked("${availability.name} -> requestInstall=$status")
                            onInstallRequested()
                            ArCoreGateState.InstallRequested
                        }
                    }
                } catch (e: UnavailableDeviceNotCompatibleException) {
                    ArCoreGateState.Unsupported(
                        "Este aparelho nao suporta realidade aumentada (ARCore)."
                    )
                } catch (e: UnavailableUserDeclinedInstallationException) {
                    ArCoreGateState.Unsupported(
                        "A instalacao do Google Play Services for AR foi recusada. " +
                        "Ela e obrigatoria para a captura em realidade aumentada."
                    )
                } catch (e: Exception) {
                    ArCoreGateState.Unsupported(
                        "Erro ao verificar o ARCore: ${e::class.simpleName ?: e.message ?: "desconhecido"}"
                    )
                }
            }
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
        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL,
        onSessionFailed = { exception ->
            // exception::class.simpleName pode vir null para certos tipos internos —
            // javaClass.name e garantido nao-nulo e da o nome completo da classe,
            // que e o minimo necessario pra saber QUAL excecao o ARCore lancou.
            val detail = exception.message?.let { "${exception.javaClass.name}: $it" }
                ?: exception.javaClass.name
            vm.onSessionFailed(detail)
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
