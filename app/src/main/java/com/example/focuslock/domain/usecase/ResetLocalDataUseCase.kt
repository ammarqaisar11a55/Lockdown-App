package com.example.focuslock.domain.usecase

import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.repository.AllowedAppsRepository
import com.example.focuslock.domain.repository.HistoryRepository
import com.example.focuslock.domain.repository.LockdownCoordinator
import com.example.focuslock.domain.repository.LockdownStateRepository
import com.example.focuslock.domain.repository.ScheduleRepository
import com.example.focuslock.domain.repository.SettingsRepository
import javax.inject.Inject

/** Clears all local data. Refused while a session is enforced. */
class ResetLocalDataUseCase @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val allowedAppsRepository: AllowedAppsRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val stateRepository: LockdownStateRepository,
    private val coordinator: LockdownCoordinator,
) {
    suspend operator fun invoke(): Boolean {
        if (coordinator.reconcile(ReconcileTrigger.USER_ACTION).isLocked) return false
        scheduleRepository.deleteAll()
        allowedAppsRepository.deleteAll()
        historyRepository.deleteAll()
        settingsRepository.clear()
        stateRepository.saveState(LockdownState())
        coordinator.reconcile(ReconcileTrigger.USER_ACTION)
        return true
    }
}
