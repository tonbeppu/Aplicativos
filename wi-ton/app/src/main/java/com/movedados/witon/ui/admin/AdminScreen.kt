package com.movedados.witon.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movedados.witon.data.remote.dto.AccountStatus
import com.movedados.witon.data.remote.dto.ProfileDto
import com.movedados.witon.ui.components.LabeledRow
import com.movedados.witon.ui.components.MessageKind
import com.movedados.witon.ui.components.StatusBadge
import com.movedados.witon.ui.components.WiTonCard
import com.movedados.witon.ui.components.WiTonMessage
import com.movedados.witon.ui.theme.Slate400
import com.movedados.witon.ui.theme.StatusGreen
import com.movedados.witon.ui.theme.StatusRed
import com.movedados.witon.ui.theme.StatusYellow

private fun statusColor(status: AccountStatus): Color = when (status) {
    AccountStatus.APPROVED  -> StatusGreen
    AccountStatus.PENDING   -> StatusYellow
    AccountStatus.REJECTED,
    AccountStatus.SUSPENDED -> StatusRed
}

@Composable
fun AdminScreen(
    onBack: () -> Unit,
    vm: AdminViewModel = viewModel()
) {
    val state by vm.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Usuarios", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${state.pending.size} aguardando liberacao",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }
            TextButton(onClick = onBack) { Text("Voltar", color = Slate400) }
        }

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            WiTonMessage(it, MessageKind.ERROR)
        }
        state.info?.let {
            Spacer(Modifier.height(12.dp))
            WiTonMessage(it, MessageKind.SUCCESS)
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.all, key = { it.id }) { profile ->
                UserCard(
                    profile = profile,
                    busy = state.actingOn == profile.id,
                    onApprove = { vm.approve(profile.id) },
                    onReject = { vm.reject(profile.id, "Cadastro nao aprovado pelo administrador") },
                    onSuspend = { vm.suspendUser(profile.id, "Acesso suspenso pelo administrador") }
                )
            }
        }
    }
}

@Composable
private fun UserCard(
    profile: ProfileDto,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onSuspend: () -> Unit
) {
    WiTonCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.fullName ?: "(sem nome)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    profile.email ?: "-",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }
            StatusBadge(
                label = profile.status.name.lowercase(),
                color = statusColor(profile.status)
            )
        }

        Spacer(Modifier.height(10.dp))
        profile.phone?.let { LabeledRow("Telefone", it) }
        profile.company?.let { LabeledRow("Empresa", it) }
        profile.city?.let { LabeledRow("Cidade", it) }

        Spacer(Modifier.height(14.dp))

        when (profile.status) {
            AccountStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onApprove,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                ) { Text("Liberar") }
                OutlinedButton(onClick = onReject, enabled = !busy) { Text("Rejeitar") }
            }
            AccountStatus.APPROVED -> OutlinedButton(onClick = onSuspend, enabled = !busy) {
                Text("Suspender", color = StatusRed)
            }
            else -> Button(
                onClick = onApprove,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
            ) { Text("Reativar") }
        }
    }
}
