package com.example.focuslock.feature.lockdown

import android.content.Context
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focuslock.core.common.TICK_SECOND_MS
import com.example.focuslock.core.common.wallClockTicker
import com.example.focuslock.core.security.SessionClock
import com.example.focuslock.core.time.DurationFormatter
import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.domain.model.AllowedApplication
import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.repository.AllowedAppsRepository
import com.example.focuslock.domain.repository.LockdownCoordinator
import com.example.focuslock.domain.repository.LockdownStateRepository
import com.example.focuslock.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject

data class LockdownDisplay(
    val sessionName: String,
    val remaining: Duration,
    val total: Duration,
    val endTime: String,
    val currentTime: String,
    val dailyGoal: String,
    val motivationalMessage: String,
    val batteryPercent: Int?,
    val allowedApps: List<AllowedApplication>,
    val strictMode: Boolean,
    val enforcement: EnforcementLevel,
)

sealed interface LockdownUiState {
    data object Loading : LockdownUiState
    data object NotLocked : LockdownUiState
    data class Locked(val display: LockdownDisplay) : LockdownUiState
}

@HiltViewModel
class LockdownViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    stateRepository: LockdownStateRepository,
    settingsRepository: SettingsRepository,
    allowedAppsRepository: AllowedAppsRepository,
    private val coordinator: LockdownCoordinator,
    private val timeSource: TimeSource,
) : ViewModel() {

    private var lastExpiryCheckMs = 0L

    val uiState: StateFlow<LockdownUiState> = combine(
        stateRepository.observeState(),
        settingsRepository.settings,
        allowedAppsRepository.observeAllowed(),
        wallClockTicker(TICK_SECOND_MS),
    ) { state, settings, allowed, _ ->
        val session = state.session
        if (!state.isLocked || session == null) return@combine LockdownUiState.NotLocked
        val now = timeSource.now()
        val zone = timeSource.zone()
        val remaining = SessionClock.remaining(
            session,
            state.checkpoint,
            now,
            timeSource.elapsedRealtimeMs(),
            timeSource.bootCount(),
        )
        if (remaining.isZero) requestExpiryCheck()
        LockdownUiState.Locked(
            LockdownDisplay(
                sessionName = session.name,
                remaining = remaining,
                total = session.duration,
                endTime = DurationFormatter.time(session.end, zone),
                currentTime = DurationFormatter.time(now, zone),
                dailyGoal = settings.dailyGoal,
                motivationalMessage = settings.motivationalMessage,
                batteryPercent = batteryPercent(),
                allowedApps = allowed,
                strictMode = session.strictMode,
                enforcement = state.enforcement ?: EnforcementLevel.SCREEN_PINNING,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LockdownUiState.Loading)

    /** Only apps in the persisted allowlist may be launched from the lockdown screen. */
    fun isLaunchAllowed(packageName: String): Boolean {
        val state = uiState.value as? LockdownUiState.Locked ?: return false
        return state.display.allowedApps.any { it.packageName == packageName }
    }

    fun onExitAttempt() {
        viewModelScope.launch { coordinator.recordExitAttempt() }
    }

    fun onResumed() {
        viewModelScope.launch { coordinator.reconcile(ReconcileTrigger.APP_FOREGROUND) }
    }

    /** The countdown reached zero: let the engine end the session exactly on time. */
    private fun requestExpiryCheck() {
        val now = System.currentTimeMillis()
        if (now - lastExpiryCheckMs < EXPIRY_CHECK_INTERVAL_MS) return
        lastExpiryCheckMs = now
        viewModelScope.launch { coordinator.reconcile(ReconcileTrigger.SESSION_TIMER) }
    }

    private fun batteryPercent(): Int? {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return null
        return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..FULL_BATTERY }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val EXPIRY_CHECK_INTERVAL_MS = 5_000L
        const val FULL_BATTERY = 100
    }
}
