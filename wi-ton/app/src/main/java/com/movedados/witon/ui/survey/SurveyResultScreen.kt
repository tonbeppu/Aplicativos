package com.movedados.witon.ui.survey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movedados.witon.ui.components.LabeledRow
import com.movedados.witon.ui.components.MessageKind
import com.movedados.witon.ui.components.WiTonCard
import com.movedados.witon.ui.components.WiTonMessage
import com.movedados.witon.ui.components.WiTonPrimaryButton
import com.movedados.witon.ui.theme.Slate400
import com.movedados.witon.ui.theme.StatusGreen

/**
 * Fim da captura AR. Mostra o resumo local (Room) e oferece sincronizar
 * com o Supabase — envio manual, nunca automatico em background, para o
 * usuario decidir quando gastar dados.
 *
 * O mapa 2D e o heatmap (sprints 4-5) e o PDF (sprint 6) entram aqui depois.
 */
@Composable
fun SurveyResultScreen(
    surveyLocalId: String,
    onDone: () -> Unit,
    vm: SurveyResultViewModel = viewModel()
) {
    LaunchedEffect(surveyLocalId) { vm.load(surveyLocalId) }
    val state by vm.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen)
        Spacer(Modifier.height(12.dp))
        Text("Leitura concluida", style = MaterialTheme.typography.headlineMedium)
        Text(
            state.survey?.name ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = Slate400,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        WiTonCard {
            LabeledRow("Pontos capturados", "${state.pointsCount}")
            state.survey?.ssid?.let { LabeledRow("Rede", it) }
            LabeledRow(
                "Status",
                if (state.synced) "Sincronizado" else "Somente neste aparelho"
            )
        }

        Spacer(Modifier.height(16.dp))

        if (!state.synced) {
            WiTonMessage(
                "O mapa 2D, o heatmap e a exportacao em PDF chegam nas proximas etapas. " +
                "Por enquanto, sincronize para nao perder esta leitura.",
                MessageKind.INFO
            )
            Spacer(Modifier.height(16.dp))
        }

        state.error?.let {
            WiTonMessage(it, MessageKind.ERROR)
            Spacer(Modifier.height(16.dp))
        }

        if (state.synced) {
            WiTonMessage("Leitura sincronizada com sucesso.", MessageKind.SUCCESS)
            Spacer(Modifier.height(16.dp))
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            if (!state.synced) {
                WiTonPrimaryButton(
                    text = "Sincronizar agora",
                    loading = state.syncing,
                    onClick = { vm.sync(surveyLocalId) }
                )
                Spacer(Modifier.height(10.dp))
            }
            OutlinedButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Voltar para o inicio") }
        }
    }
}
