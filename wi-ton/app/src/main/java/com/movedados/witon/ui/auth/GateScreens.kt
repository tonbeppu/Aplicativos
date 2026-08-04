package com.movedados.witon.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movedados.witon.ui.theme.Slate400
import com.movedados.witon.ui.theme.StatusRed
import com.movedados.witon.ui.theme.StatusYellow

@Composable
private fun GateMessage(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate400,
            textAlign = TextAlign.Center
        )
        if (primaryLabel != null && onPrimary != null) {
            Spacer(Modifier.height(28.dp))
            OutlinedButton(onClick = onPrimary) { Text(primaryLabel) }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onSignOut) { Text("Sair", color = Slate400) }
    }
}

@Composable
fun PendingScreen(onRefresh: () -> Unit, onSignOut: () -> Unit) = GateMessage(
    icon = Icons.Default.HourglassTop,
    tint = StatusYellow,
    title = "Aguardando liberacao",
    body = "Seu cadastro foi recebido e esta na fila de aprovacao. " +
           "Assim que for liberado, o app abre normalmente.",
    primaryLabel = "Verificar de novo",
    onPrimary = onRefresh,
    onSignOut = onSignOut
)

@Composable
fun RejectedScreen(reason: String?, onSignOut: () -> Unit) = GateMessage(
    icon = Icons.Default.Block,
    tint = StatusRed,
    title = "Cadastro nao aprovado",
    body = reason?.takeIf { it.isNotBlank() }
        ?: "Seu cadastro nao foi aprovado. Fale com o administrador para mais detalhes.",
    onSignOut = onSignOut
)

@Composable
fun SuspendedScreen(onSignOut: () -> Unit) = GateMessage(
    icon = Icons.Default.PauseCircle,
    tint = StatusRed,
    title = "Acesso suspenso",
    body = "Seu acesso foi suspenso pelo administrador.",
    onSignOut = onSignOut
)
