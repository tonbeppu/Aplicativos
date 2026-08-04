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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.movedados.witon.data.repository.SignUpForm
import com.movedados.witon.ui.components.MessageKind
import com.movedados.witon.ui.components.WiTonMessage
import com.movedados.witon.ui.components.WiTonPrimaryButton
import com.movedados.witon.ui.components.WiTonTextField
import com.movedados.witon.ui.theme.Slate400

@Composable
fun SignUpScreen(
    state: AuthUiState,
    onSignUp: (SignUpForm) -> Unit,
    onBackToLogin: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("Criar conta", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Seu acesso passa por aprovacao manual antes de liberar as leituras.",
            style = MaterialTheme.typography.bodySmall,
            color = Slate400,
            modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)
        )

        WiTonTextField(fullName, { fullName = it }, "NOME COMPLETO", enabled = !state.busy)
        Spacer(Modifier.height(14.dp))
        WiTonTextField(email, { email = it }, "EMAIL",
            keyboardType = KeyboardType.Email, enabled = !state.busy)
        Spacer(Modifier.height(14.dp))
        WiTonTextField(password, { password = it }, "SENHA",
            isPassword = true, enabled = !state.busy,
            supportingText = "Minimo de 8 caracteres")
        Spacer(Modifier.height(14.dp))
        WiTonTextField(phone, { phone = it }, "TELEFONE",
            keyboardType = KeyboardType.Phone, enabled = !state.busy)
        Spacer(Modifier.height(14.dp))
        WiTonTextField(company, { company = it }, "EMPRESA (OPCIONAL)", enabled = !state.busy)
        Spacer(Modifier.height(14.dp))
        WiTonTextField(city, { city = it }, "CIDADE", enabled = !state.busy)

        state.error?.let {
            Spacer(Modifier.height(16.dp))
            WiTonMessage(it, MessageKind.ERROR)
        }

        Spacer(Modifier.height(24.dp))
        WiTonPrimaryButton(
            text = "Enviar cadastro",
            loading = state.busy,
            onClick = {
                onSignUp(SignUpForm(fullName, email, password, phone, company, city))
            }
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBackToLogin, enabled = !state.busy) {
            Text("Ja tenho conta", color = Slate400)
        }
    }
}
