package com.movedados.witon.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movedados.witon.core.ServiceLocator
import com.movedados.witon.ui.components.LabeledRow
import com.movedados.witon.ui.components.MessageKind
import com.movedados.witon.ui.components.StatusBadge
import com.movedados.witon.ui.components.WiTonCard
import com.movedados.witon.ui.components.WiTonMessage
import com.movedados.witon.ui.components.WiTonPrimaryButton
import com.movedados.witon.ui.theme.Slate400
import com.movedados.witon.ui.theme.StatusGreen
import com.movedados.witon.ui.theme.StatusRed
import com.movedados.witon.wifi.RssiSample
import com.movedados.witon.wifi.RssiScale

/**
 * Tela inicial do usuario liberado.
 *
 * Ja exercita o RssiSampler ao vivo de proposito: e o jeito mais rapido de
 * validar o comportamento do RSSI no aparelho real antes de escrever a camada AR.
 */
@Composable
fun HomeScreen(
    isAdmin: Boolean,
    onNewSurvey: () -> Unit,
    onOpenAdmin: () -> Unit,
    onSignOut: () -> Unit
) {
    val wifiConnected by ServiceLocator.wifiMonitor.wifiConnected()
        .collectAsState(initial = ServiceLocator.wifiMonitor.isConnectedToWifi())

    val sampler = remember { ServiceLocator.newRssiSampler() }
    val sample: RssiSample? by produceState<RssiSample?>(initialValue = null, wifiConnected) {
        if (wifiConnected) {
            sampler.reset()
            sampler.samples().collect { value = it }
        } else {
            value = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Wi Ton", style = MaterialTheme.typography.titleLarge)
            Row {
                if (isAdmin) {
                    TextButton(onClick = onOpenAdmin) { Text("Usuarios") }
                }
                TextButton(onClick = onSignOut) { Text("Sair", color = Slate400) }
            }
        }

        Spacer(Modifier.height(20.dp))

        WiTonCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Rede atual", style = MaterialTheme.typography.titleMedium)
                StatusBadge(
                    label = if (wifiConnected) "conectado" else "sem wi-fi",
                    color = if (wifiConnected) StatusGreen else StatusRed
                )
            }

            Spacer(Modifier.height(12.dp))

            val s = sample
            if (s == null) {
                Text(
                    if (wifiConnected) "Lendo o sinal..."
                    else "Conecte-se a uma rede Wi-Fi para iniciar uma leitura.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            } else {
                Text(
                    "${s.rssi} dBm",
                    style = MaterialTheme.typography.headlineMedium,
                    color = RssiScale.color(s.rssi)
                )
                Text(
                    RssiScale.quality(s.rssi).label,
                    style = MaterialTheme.typography.bodySmall,
                    color = RssiScale.color(s.rssi)
                )
                Spacer(Modifier.height(12.dp))
                LabeledRow("Rede", s.ssid ?: "(oculta — verifique a permissao de localizacao)")
                LabeledRow("Banda", s.band)
                s.linkSpeedMbps?.let { LabeledRow("Velocidade do link", "$it Mbps") }
                LabeledRow("Leitura bruta", "${s.rawRssi} dBm")
            }
        }

        Spacer(Modifier.height(20.dp))

        if (!wifiConnected) {
            WiTonMessage(
                "A leitura mede o sinal da rede em que o aparelho esta conectado. " +
                "Conecte-se ao Wi-Fi do ambiente antes de comecar.",
                MessageKind.WARNING
            )
            Spacer(Modifier.height(16.dp))
        }

        WiTonPrimaryButton(
            text = "Nova leitura",
            onClick = onNewSurvey,
            enabled = wifiConnected
        )

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { },
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Leituras anteriores (sprint 4)") }
    }
}
