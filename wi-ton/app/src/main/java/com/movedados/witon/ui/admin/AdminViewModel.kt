package com.movedados.witon.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movedados.witon.core.ServiceLocator
import com.movedados.witon.data.remote.dto.AccountStatus
import com.movedados.witon.data.remote.dto.ProfileDto
import com.movedados.witon.ui.auth.friendly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminUiState(
    val pending: List<ProfileDto> = emptyList(),
    val all: List<ProfileDto> = emptyList(),
    val loading: Boolean = true,
    val actingOn: String? = null,
    val error: String? = null,
    val info: String? = null
)

class AdminViewModel : ViewModel() {

    private val repo = ServiceLocator.adminRepository

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                val all = repo.listAll()
                all to all.filter { it.status == AccountStatus.PENDING }
            }.onSuccess { (all, pending) ->
                _state.value = _state.value.copy(all = all, pending = pending, loading = false)
            }.onFailure {
                _state.value = _state.value.copy(loading = false, error = it.friendly())
            }
        }
    }

    fun approve(userId: String) = act(userId, "Usuario liberado.") { repo.approve(userId) }

    fun reject(userId: String, reason: String?) =
        act(userId, "Cadastro rejeitado.") { repo.reject(userId, reason) }

    fun suspendUser(userId: String, reason: String?) =
        act(userId, "Acesso suspenso.") { repo.suspend(userId, reason) }

    private fun act(userId: String, successMsg: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actingOn = userId, error = null, info = null)
            runCatching { block() }
                .onSuccess { _state.value = _state.value.copy(info = successMsg); load() }
                .onFailure { _state.value = _state.value.copy(error = it.friendly()) }
            _state.value = _state.value.copy(actingOn = null)
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }
}
