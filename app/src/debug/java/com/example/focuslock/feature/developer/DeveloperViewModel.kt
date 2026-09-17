package com.example.focuslock.feature.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focuslock.core.common.FocusLogger
import com.example.focuslock.core.common.IoDispatcher
import com.example.focuslock.core.device.DevicePolicyController
import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.domain.model.DevicePolicyState
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.model.RepeatRule
import com.example.focuslock.domain.repository.LockdownCoordinator
import com.example.focuslock.domain.repository.LockdownStateRepository
import com.example.focuslock.domain.usecase.ResetLocalDataUseCase
import com.example.focuslock.domain.usecase.SaveScheduleUseCase
import com.example.focuslock.domain.usecase.ScheduleChangeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

data class DeveloperUiState(
    val policy: DevicePolicyState? = null,
    val lockTaskPermitted: Boolean = false,
    val lockdownState: LockdownState? = null,
    val logs: List<String> = emptyList(),
    val lastResult: String? = null,
)

@HiltViewModel
class DeveloperViewModel @Inject constructor(
    private val controller: DevicePolicyController,
    private val coordinator: LockdownCoordinator,
    private val stateRepository: LockdownStateRepository,
    private val saveSchedule: SaveScheduleUseCase,
    private val resetLocalData: ResetLocalDataUseCase,
    private val timeSource: TimeSource,
    private val logger: FocusLogger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeveloperUiState())
    val uiState: StateFlow<DeveloperUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val policy = withContext(ioDispatcher) { controller.readPolicyState() }
            val permitted = withContext(ioDispatcher) { controller.isLockTaskPermitted() }
            _uiState.update {
                it.copy(
                    policy = policy,
                    lockTaskPermitted = permitted,
                    lockdownState = stateRepository.getState(),
                    logs = logger.recentEntries().takeLast(LOG_LINES).reversed(),
                )
            }
        }
    }

    fun startTestLockdown() = run("Test lockdown") {
        coordinator.startFocusNow("Test session", TEST_SESSION, strictMode = false).toString()
    }

    fun simulateBoot() = run("Boot recovery") {
        coordinator.reconcile(ReconcileTrigger.BOOT).phase.name
    }

    fun triggerAlarm() = run("Schedule trigger") {
        coordinator.reconcile(ReconcileTrigger.ALARM).phase.name
    }

    /** Creates a one-time schedule starting at the next minute boundary plus one. */
    fun scheduleSoon() = run("Schedule in ~2 min") {
        val zone = timeSource.zone()
        val start = timeSource.now().atZone(zone).truncatedTo(ChronoUnit.MINUTES).plusMinutes(SCHEDULE_LEAD_MINUTES)
        val end = start.plusMinutes(SCHEDULE_LENGTH_MINUTES)
        val schedule = FocusSchedule(
            id = UUID.randomUUID().toString(),
            name = "Dev schedule",
            startTime = start.toLocalTime(),
            endTime = end.toLocalTime(),
            repeat = RepeatRule.Once(start.toLocalDate()),
            strictMode = false,
            enabled = true,
            autoStart = true,
            createdAt = timeSource.now().toEpochMilli(),
        )
        when (val result = saveSchedule(schedule)) {
            ScheduleChangeResult.Success -> "created for ${start.toLocalTime()}"
            else -> result.toString()
        }
    }

    fun reset() = run("Reset") { if (resetLocalData()) "cleared" else "refused: session active" }

    private fun run(label: String, block: suspend () -> String) {
        viewModelScope.launch {
            val result = runCatching { block() }.getOrElse { it.javaClass.simpleName }
            _uiState.update { it.copy(lastResult = "$label: $result") }
            refresh()
        }
    }

    private companion object {
        val TEST_SESSION: Duration = Duration.ofSeconds(60)
        const val SCHEDULE_LEAD_MINUTES = 2L
        const val SCHEDULE_LENGTH_MINUTES = 2L
        const val LOG_LINES = 60
    }
}
