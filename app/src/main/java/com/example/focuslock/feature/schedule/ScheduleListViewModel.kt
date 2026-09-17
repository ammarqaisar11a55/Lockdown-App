package com.example.focuslock.feature.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focuslock.core.scheduling.ScheduleValidationError
import com.example.focuslock.core.scheduling.ScheduleValidator
import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.repository.LockdownStateRepository
import com.example.focuslock.domain.repository.ScheduleRepository
import com.example.focuslock.domain.usecase.DeleteScheduleUseCase
import com.example.focuslock.domain.usecase.SaveScheduleUseCase
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
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class ScheduleListUiState(
    val loading: Boolean = true,
    val today: List<FocusSchedule> = emptyList(),
    val otherDays: List<FocusSchedule> = emptyList(),
    val enforcedScheduleId: String? = null,
) {
    val schedules: List<FocusSchedule> get() = today + otherDays
}

sealed interface ScheduleListMessage {
    data class Error(val error: ScheduleValidationError) : ScheduleListMessage
    data object Duplicated : ScheduleListMessage
    data object Deleted : ScheduleListMessage
    data object Locked : ScheduleListMessage
}

@HiltViewModel
class ScheduleListViewModel @Inject constructor(
    scheduleRepository: ScheduleRepository,
    stateRepository: LockdownStateRepository,
    private val setEnabled: SetScheduleEnabledUseCase,
    private val saveSchedule: SaveScheduleUseCase,
    private val deleteSchedule: DeleteScheduleUseCase,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val _message = MutableStateFlow<ScheduleListMessage?>(null)
    val message: StateFlow<ScheduleListMessage?> = _message.asStateFlow()

    val uiState: StateFlow<ScheduleListUiState> = combine(
        scheduleRepository.observeSchedules(),
        stateRepository.observeState(),
    ) { schedules, state ->
        val today = LocalDate.now(timeSource.zone())
        val (onToday, others) = schedules.partition { it.repeat.occursOn(today) }
        ScheduleListUiState(
            loading = false,
            today = onToday,
            otherDays = others,
            enforcedScheduleId = state.session?.scheduleId?.takeIf { state.isLocked },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ScheduleListUiState())

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            report(setEnabled.invoke(id, enabled), success = null)
        }
    }

    /** Copies are created disabled so they never overlap the original. */
    fun duplicate(schedule: FocusSchedule, copySuffix: String) {
        viewModelScope.launch {
            val copy = schedule.copy(
                id = UUID.randomUUID().toString(),
                name = (schedule.name + copySuffix).take(ScheduleValidator.MAX_NAME_LENGTH),
                enabled = false,
                createdAt = timeSource.now().toEpochMilli(),
            )
            report(saveSchedule(copy), ScheduleListMessage.Duplicated)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { report(deleteSchedule(id), ScheduleListMessage.Deleted) }
    }

    fun messageShown() {
        _message.value = null
    }

    private fun report(result: ScheduleChangeResult, success: ScheduleListMessage?) {
        _message.value = when (result) {
            ScheduleChangeResult.Success -> success
            ScheduleChangeResult.LockedBySession -> ScheduleListMessage.Locked
            is ScheduleChangeResult.Invalid -> ScheduleListMessage.Error(result.error)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
