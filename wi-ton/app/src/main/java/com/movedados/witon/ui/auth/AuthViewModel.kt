package com.movedados.witon.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movedados.witon.core.ServiceLocator
import com.movedados.witon.data.remote.dto.AccountStatus
import com.movedados.witon.data.remote.dto.UserRole
import com.movedados.witon.data.repository.SignUpForm
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Para onde o app deve levar o usuario neste instante. */
sealed interface Gate {
    data object Loading : Gate
    data object SignedOut : Gate
    data object Pending : Gate
    data class Rejected(val reason: String?) : Gate
    data object Suspended : Gate
    data class Allowed(val isAdmin: Boolean) : Gate
}

data class AuthUiState(
    val gate: Gate = Gate.Loading,
    val busy: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

class AuthViewModel : ViewModel() {

    private val repo = ServiceLocator.authRepository

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> refreshGate()
                    is SessionStatus.NotAuthenticated -> _state.value =
                        _state.value.copy(gate = Gate.SignedOut)
                    else -> _state.value = _state.value.copy(gate = Gate.Loading)
                }
            }
        }
    }

    /**
     * Consulta o status no banco, nao em cache local: a liberacao pode ter
     * acontecido enquanto o app estava fechado, e o inverso tambem — uma
     * suspensao precisa valer no proximo boot.
     */
    fun refreshGate() {
        viewModelScope.launch {
            _state.value = _state.value.copy(gate = Gate.Loading)
            runCatching { repo.accessStatus() }
                .onSuccess { access ->
                    _state.value = _state.value.copy(
                        gate = when (access.status) {
                            AccountStatus.APPROVED  -> Gate.Allowed(access.role == UserRole.ADMIN)
                            AccountStatus.PENDING   -> Gate.Pending
                            AccountStatus.REJECTED  -> Gate.Rejected(access.rejectionReason)
                            AccountStatus.SUSPENDED -> Gate.Suspended
                        }
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        gate = Gate.Pending,
                        error = "Nao foi possivel confirmar seu acesso: ${it.friendly()}"
                    )
                }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "Preencha email e senha.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, info = null)
            runCatching { repo.signIn(email, password) }
                .onFailure { _state.value = _state.value.copy(error = it.friendly()) }
            _state.value = _state.value.copy(busy = false)
        }
    }

    fun signUp(form: SignUpForm, onDone: () -> Unit) {
        validate(form)?.let {
            _state.value = _state.value.copy(error = it)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, error = null, info = null)
            runCatching { repo.signUp(form) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        info = "Cadastro enviado. Aguarde a liberacao do administrador."
                    )
                    onDone()
                }
                .onFailure { _state.value = _state.value.copy(error = it.friendly()) }
            _state.value = _state.value.copy(busy = false)
        }
    }

    fun signOut() {
        viewModelScope.launch { runCatching { repo.signOut() } }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    private fun validate(f: SignUpForm): String? = when {
        f.fullName.isBlank() -> "Informe seu nome completo."
        !f.email.contains("@") || !f.email.contains(".") -> "Email invalido."
        f.password.length < 8 -> "A senha precisa de pelo menos 8 caracteres."
        else -> null
    }
}

/** Traduz os erros mais comuns do Supabase para algo que o usuario entenda. */
internal fun Throwable.friendly(): String {
    val raw = message.orEmpty()
    return when {
        raw.contains("Invalid login", true) ||
        raw.contains("invalid_credentials", true) -> "Email ou senha incorretos."
        raw.contains("already registered", true) ||
        raw.contains("already been registered", true) -> "Esse email ja tem cadastro."
        raw.contains("Email not confirmed", true) -> "Confirme seu email antes de entrar."
        raw.contains("weak", true) && raw.contains("password", true) -> "Senha muito fraca."
        raw.contains("rate limit", true) -> "Muitas tentativas. Aguarde alguns minutos."
        raw.contains("Unable to resolve host", true) ||
        raw.contains("timeout", true) -> "Sem conexao com o servidor."
        raw.isBlank() -> "Erro inesperado."
        else -> raw
    }
}
