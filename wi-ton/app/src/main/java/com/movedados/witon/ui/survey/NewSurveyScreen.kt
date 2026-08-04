package com.movedados.witon.ui.survey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movedados.witon.core.ServiceLocator
import com.movedados.witon.ui.components.MessageKind
import com.movedados.witon.ui.components.StatusBadge
import com.movedados.witon.ui.components.WiTonMessage
import com.movedados.witon.ui.components.WiTonPrimaryButton
import com.movedados.witon.ui.components.WiTonTextField
import com.movedados.witon.ui.theme.Slate400
import com.movedados.witon.ui.theme.StatusGreen
import com.movedados.witon.ui.theme.StatusRed

/**
 * Pede o nome da leitura e barra o avanco se o aparelho nao estiver em
 * uma rede Wi-Fi — o app mede o sinal da rede conectada, entao sem ela
 * nao ha o que capturar.
 */
@Composable
fun NewSurveyScreen(
    onStart: (name: String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val wifiConnected by ServiceLocator.wifiMonitor.wifiConnected()
        .collectAsState(initial = ServiceLocator.wifiMonitor.isConnectedToWifi())

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Nova leitura", style = MaterialTheme.typography.headlineMedium)
        Text(
            "De um nome para identificar este ambiente depois.",
            style = MaterialTheme.typography.bodySmall,
            color = Slate400,
            modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
        )

        WiTonTextField(
            value = name,
            onValueChange = { name = it },
            label = "NOME DA LEITURA",
            supportingText = "Ex: Apartamento Centro, Loja Matriz..."
        )

        Spacer(Modifier.height(20.dp))

        Column(horizontalAlignment = Alignment.Start) {
            Text("Rede", style = MaterialTheme.typography.labelSmall, color = Slate400)
            Spacer(Modifier.height(6.dp))
            StatusBadge(
                label = if (wifiConnected) "conectado" else "sem wi-fi",
                color = if (wifiConnected) StatusGreen else StatusRed
            )
        }

        if (!wifiConnected) {
            Spacer(Modifier.height(16.dp))
            WiTonMessage(
                "Conecte-se ao Wi-Fi do ambiente que voce vai medir antes de iniciar.",
                MessageKind.WARNING
            )
        }

        Spacer(Modifier.height(28.dp))
        WiTonPrimaryButton(
            text = "Iniciar captura",
            enabled = wifiConnected && name.isNotBlank(),
            onClick = { onStart(name.trim()) }
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Cancelar", color = Slate400) }
    }
}
