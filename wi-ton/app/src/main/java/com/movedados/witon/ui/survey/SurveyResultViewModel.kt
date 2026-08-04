package com.movedados.witon.ui.survey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movedados.witon.core.ServiceLocator
import com.movedados.witon.data.local.entity.SurveyEntity
import com.movedados.witon.processing.HeatmapRenderer
import com.movedados.witon.ui.auth.friendly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SurveyResultUiState(
    val survey: SurveyEntity? = null,
    val pointsCount: Int = 0,
    val heatmap: HeatmapRenderer.Result? = null,
    val heatmapLoading: Boolean = true,
    val syncing: Boolean = false,
    val synced: Boolean = false,
    val error: String? = null
)

class SurveyResultViewModel : ViewModel() {

    private val repo = ServiceLocator.surveyRepository

    private val _state = MutableStateFlow(SurveyResultUiState())
    val state: StateFlow<SurveyResultUiState> = _state.asStateFlow()

    fun load(surveyLocalId: String) {
        viewModelScope.launch {
            val survey = repo.getSurvey(surveyLocalId)
            _state.value = _state.value.copy(
                survey = survey,
                synced = survey?.synced == true
            )
        }
        viewModelScope.launch {
            repo.observePointCount(surveyLocalId).collect { count ->
                _state.value = _state.value.copy(pointsCount = count)
            }
        }
        loadHeatmap(surveyLocalId)
    }

    private fun loadHeatmap(surveyLocalId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(heatmapLoading = true)
            val points = repo.getPoints(surveyLocalId)
            // A varredura pixel-a-pixel e trabalho de CPU, nao deve rodar na
            // thread principal — daria engasgo perceptivel na UI.
            val result = withContext(Dispatchers.Default) { HeatmapRenderer.render(points) }
            _state.value = _state.value.copy(heatmap = result, heatmapLoading = false)
        }
    }

    fun sync(surveyLocalId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(syncing = true, error = null)
            runCatching { repo.sync(surveyLocalId) }
                .onSuccess { _state.value = _state.value.copy(synced = true) }
                .onFailure { _state.value = _state.value.copy(error = it.friendly()) }
            _state.value = _state.value.copy(syncing = false)
        }
    }
}
