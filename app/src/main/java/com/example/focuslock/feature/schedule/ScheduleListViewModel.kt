package com.example.focuslock.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focuslock.core.scheduling.ScheduleValidationError
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.repository.LockdownStateRepository
import com.example.focuslock.domain.repository.ScheduleRepository
import com.example.focuslock.domain.usecase.ScheduleChangeResult
import com.example.focuslock.domain.usecase.SetScheduleEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleListUiState(
    val loading: Boolean = true,
    val schedules: List<FocusSchedule> = emptyList(),
    val enforcedScheduleId: String? = null,
)

@HiltViewModel
class ScheduleListViewModel @Inject constructor(
    scheduleRepository: ScheduleRepository,
    stateRepository: LockdownStateRepository,
    private val setEnabled: SetScheduleEnabledUseCase,
) : ViewModel() {

    private val _error = MutableStateFlow<ScheduleValidationError?>(null)
    val error: StateFlow<ScheduleValidationError?> = _error.asStateFlow()

    val uiState: StateFlow<ScheduleListUiState> = combine(
        scheduleRepository.observeSchedules(),
        stateRepository.observeState(),
    ) { schedules, state ->
        ScheduleListUiState(
            loading = false,
            schedules = schedules,
            enforcedScheduleId = state.session?.scheduleId?.takeIf { state.isLocked },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ScheduleListUiState())

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            val result = setEnabled.invoke(id, enabled)
            if (result is ScheduleChangeResult.Invalid) _error.value = result.error
        }
    }

    fun errorShown() {
        _error.value = null
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
