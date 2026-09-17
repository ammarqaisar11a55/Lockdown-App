package com.example.focuslock.domain.usecase

import com.example.focuslock.core.scheduling.ScheduleOverlapChecker
import com.example.focuslock.core.scheduling.ScheduleValidationError
import com.example.focuslock.core.scheduling.ScheduleValidator
import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.repository.LockdownCoordinator
import com.example.focuslock.domain.repository.LockdownStateRepository
import com.example.focuslock.domain.repository.ScheduleRepository
import javax.inject.Inject

sealed interface ScheduleChangeResult {
    data object Success : ScheduleChangeResult
    data class Invalid(val error: ScheduleValidationError) : ScheduleChangeResult

    /** The schedule is currently being enforced and cannot be changed. */
    data object LockedBySession : ScheduleChangeResult
}

class SaveScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val stateRepository: LockdownStateRepository,
    private val coordinator: LockdownCoordinator,
    private val deviceOwnerStatus: DeviceOwnerStatus,
    private val timeSource: TimeSource,
) {
    suspend operator fun invoke(schedule: FocusSchedule): ScheduleChangeResult {
        if (isEnforced(stateRepository, schedule.id)) return ScheduleChangeResult.LockedBySession
        val normalized = schedule.copy(name = schedule.name.trim())
        val error = ScheduleValidator.validate(
            normalized,
            scheduleRepository.getSchedules(),
            timeSource.now(),
            timeSource.zone(),
            deviceOwnerStatus.isDeviceOwner(),
        )
        if (error != null) return ScheduleChangeResult.Invalid(error)
        scheduleRepository.save(normalized)
        coordinator.reconcile(ReconcileTrigger.SCHEDULES_CHANGED)
        return ScheduleChangeResult.Success
    }
}

class DeleteScheduleUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val stateRepository: LockdownStateRepository,
    private val coordinator: LockdownCoordinator,
) {
    suspend operator fun invoke(id: String): ScheduleChangeResult {
        if (isEnforced(stateRepository, id)) return ScheduleChangeResult.LockedBySession
        scheduleRepository.delete(id)
        coordinator.reconcile(ReconcileTrigger.SCHEDULES_CHANGED)
        return ScheduleChangeResult.Success
    }
}

class SetScheduleEnabledUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val stateRepository: LockdownStateRepository,
    private val coordinator: LockdownCoordinator,
    private val deviceOwnerStatus: DeviceOwnerStatus,
) {
    suspend operator fun invoke(id: String, enabled: Boolean): ScheduleChangeResult {
        if (isEnforced(stateRepository, id)) return ScheduleChangeResult.LockedBySession
        val schedule = scheduleRepository.getSchedule(id) ?: return ScheduleChangeResult.Success
        if (enabled) {
            if (schedule.strictMode && !deviceOwnerStatus.isDeviceOwner()) {
                return ScheduleChangeResult.Invalid(ScheduleValidationError.StrictNeedsDeviceOwner)
            }
            val conflict = ScheduleOverlapChecker.findConflict(schedule.copy(enabled = true), scheduleRepository.getSchedules())
            if (conflict != null) {
                return ScheduleChangeResult.Invalid(ScheduleValidationError.Overlaps(conflict.name))
            }
        }
        scheduleRepository.setEnabled(id, enabled)
        coordinator.reconcile(ReconcileTrigger.SCHEDULES_CHANGED)
        return ScheduleChangeResult.Success
    }
}

/** Whether this app is the Device Owner, read off the main thread. */
fun interface DeviceOwnerStatus {
    suspend fun isDeviceOwner(): Boolean
}

private suspend fun isEnforced(stateRepository: LockdownStateRepository, scheduleId: String): Boolean {
    val state = stateRepository.getState()
    return state.isLocked && state.session?.scheduleId == scheduleId
}
