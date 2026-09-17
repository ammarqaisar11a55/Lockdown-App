package com.example.focuslock.feature.applications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focuslock.domain.model.AllowedApplication
import com.example.focuslock.domain.model.InstalledApplication
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.repository.AllowedAppsRepository
import com.example.focuslock.domain.repository.LockdownCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApplicationsUiState(
    val loading: Boolean = true,
    val applications: List<InstalledApplication> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val dirty: Boolean = false,
    val saved: Boolean = false,
) {
    val visible: List<InstalledApplication>
        get() = if (query.isBlank()) applications else applications.filter { it.label.contains(query.trim(), ignoreCase = true) }
}

@HiltViewModel
class ApplicationsViewModel @Inject constructor(
    private val repository: AllowedAppsRepository,
    private val coordinator: LockdownCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApplicationsUiState())
    val uiState: StateFlow<ApplicationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val installed = repository.getLaunchableApplications()
            val allowed = repository.observeAllowed().first().map { it.packageName }.toSet()
            _uiState.update { it.copy(loading = false, applications = installed, selected = allowed) }
        }
    }

    fun toggle(packageName: String) = _uiState.update { state ->
        val selected = if (packageName in state.selected) state.selected - packageName else state.selected + packageName
        state.copy(selected = selected, dirty = true, saved = false)
    }

    fun search(query: String) = _uiState.update { it.copy(query = query) }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val allowed = state.applications
                .filter { it.packageName in state.selected && !it.essential }
                .map { AllowedApplication(it.packageName, it.label) }
            repository.setAllowed(allowed)
            coordinator.reconcile(ReconcileTrigger.SETTINGS_CHANGED)
            _uiState.update { it.copy(dirty = false, saved = true) }
        }
    }
}
