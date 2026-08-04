package com.movedados.witon.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.movedados.witon.ui.components.MessageKind
import com.movedados.witon.ui.components.WiTonMessage
import com.movedados.witon.ui.components.WiTonPrimaryButton
import com.movedados.witon.ui.components.WiTonTextField
import com.movedados.witon.ui.theme.BrandRed
import com.movedados.witon.ui.theme.Slate400

@Composable
fun LoginScreen(
    state: AuthUiState,
    onSignIn: (String, String) -> Unit,
    onGoToSignUp: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append("Wi ") }
                withStyle(SpanStyle(color = BrandRed)) { append("Ton") }
            },
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            "Mapa de calor de Wi-Fi em realidade aumentada",
            style = MaterialTheme.typography.bodySmall,
            color = Slate400,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(40.dp))

        WiTonTextField(
            value = email,
            onValueChange = { email = it },
            label = "EMAIL",
            keyboardType = KeyboardType.Email,
            enabled = !state.busy
        )
        Spacer(Modifier.height(16.dp))
        WiTonTextField(
            value = password,
            onValueChange = { password = it },
            label = "SENHA",
            isPassword = true,
            enabled = !state.busy
        )

        state.error?.let {
            Spacer(Modifier.height(16.dp))
            WiTonMessage(it, MessageKind.ERROR)
        }
        state.info?.let {
            Spacer(Modifier.height(16.dp))
            WiTonMessage(it, MessageKind.SUCCESS)
        }

        Spacer(Modifier.height(24.dp))
        WiTonPrimaryButton(
            text = "Entrar",
            onClick = { onSignIn(email, password) },
            loading = state.busy
        )

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onGoToSignUp, enabled = !state.busy) {
            Text("Nao tenho cadastro", color = Slate400)
        }
    }
}
