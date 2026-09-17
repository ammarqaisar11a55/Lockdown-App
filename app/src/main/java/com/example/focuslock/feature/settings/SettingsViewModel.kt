package com.example.focuslock.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focuslock.core.common.IoDispatcher
import com.example.focuslock.core.device.DevicePolicyController
import com.example.focuslock.domain.model.AppSettings
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.repository.LockdownCoordinator
import com.example.focuslock.domain.repository.LockdownStateRepository
import com.example.focuslock.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val isDeviceOwner: Boolean = false,
    val sessionActive: Boolean = false,
    val removalResult: Boolean? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    stateRepository: LockdownStateRepository,
    private val coordinator: LockdownCoordinator,
    private val policyController: DevicePolicyController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val deviceOwner = MutableStateFlow(false)
    private val removalResult = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        stateRepository.observeState(),
        deviceOwner,
        removalResult,
    ) { settings, state, owner, removal ->
        SettingsUiState(
            loading = false,
            settings = settings,
            isDeviceOwner = owner,
            sessionActive = state.isLocked,
            removalResult = removal,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun refresh() {
        viewModelScope.launch { deviceOwner.value = withContext(ioDispatcher) { policyController.isDeviceOwner() } }
    }

    fun setCountdownMinutes(minutes: Int) = updateAndReconcile { it.copy(countdownMinutes = minutes) }

    fun setReminderMinutes(minutes: Int) = updateAndReconcile { it.copy(reminderMinutes = minutes) }

    fun saveTexts(dailyGoal: String, motivation: String) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(dailyGoal = dailyGoal.trim(), motivationalMessage = motivation.trim()) }
        }
    }

    /** Development/support path. Never available during an active session. */
    fun removeDeviceOwner() {
        viewModelScope.launch {
            val state = coordinator.reconcile(ReconcileTrigger.USER_ACTION)
            if (state.isLocked) {
                removalResult.value = false
                return@launch
            }
            removalResult.value = withContext(ioDispatcher) { policyController.clearDeviceOwner() }
            refresh()
        }
    }

    fun removalResultShown() {
        removalResult.value = null
    }

    private fun updateAndReconcile(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
            coordinator.reconcile(ReconcileTrigger.SETTINGS_CHANGED)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
