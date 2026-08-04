package com.movedados.witon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.movedados.witon.ui.theme.Slate400
import com.movedados.witon.ui.theme.Slate600
import com.movedados.witon.ui.theme.Slate700
import com.movedados.witon.ui.theme.Slate800
import com.movedados.witon.ui.theme.StatusBlue
import com.movedados.witon.ui.theme.StatusGreen
import com.movedados.witon.ui.theme.StatusRed
import com.movedados.witon.ui.theme.StatusYellow

@Composable
fun WiTonCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Slate800, RoundedCornerShape(16.dp))
            .border(1.dp, Slate700, RoundedCornerShape(16.dp))
            .padding(20.dp),
        content = content
    )
}

@Composable
fun WiTonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false
) {
    var revealed by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Slate400,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = when {
                isPassword && !revealed -> PasswordVisualTransformation()
                else -> VisualTransformation.None
            },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { revealed = !revealed }) {
                        Icon(
                            imageVector = if (revealed) Icons.Default.VisibilityOff
                                          else Icons.Default.Visibility,
                            contentDescription = if (revealed) "Ocultar senha" else "Mostrar senha",
                            tint = Slate400
                        )
                    }
                }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Slate700,
                unfocusedContainerColor = Slate700,
                disabledContainerColor = Slate700,
                focusedBorderColor = StatusBlue,
                unfocusedBorderColor = Slate600
            )
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) StatusRed else Slate400,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun WiTonPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

enum class MessageKind { SUCCESS, ERROR, INFO, WARNING }

@Composable
fun WiTonMessage(
    text: String,
    kind: MessageKind = MessageKind.INFO,
    modifier: Modifier = Modifier
) {
    val accent = when (kind) {
        MessageKind.SUCCESS -> StatusGreen
        MessageKind.ERROR   -> StatusRed
        MessageKind.WARNING -> StatusYellow
        MessageKind.INFO    -> StatusBlue
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = accent)
    }
}

@Composable
fun StatusBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun FullScreenLoader(message: String = "Carregando...") {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate400,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Slate400)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}
