package com.example.focuslock.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focuslock.core.common.IoDispatcher
import com.example.focuslock.core.common.TICK_MINUTE_MS
import com.example.focuslock.core.common.TICK_SECOND_MS
import com.example.focuslock.core.common.wallClockTicker
import com.example.focuslock.core.device.DevicePolicyController
import com.example.focuslock.core.scheduling.ScheduleCalculator
import com.example.focuslock.core.security.DecisionInput
import com.example.focuslock.core.security.LockdownDecider
import com.example.focuslock.core.security.LockdownTarget
import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.domain.model.AppMode
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.LockdownPhase
import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.SessionHistory
import com.example.focuslock.domain.repository.HistoryRepository
import com.example.focuslock.domain.repository.LockdownCoordinator
import com.example.focuslock.domain.repository.LockdownStateRepository
import com.example.focuslock.domain.repository.ScheduleRepository
import com.example.focuslock.domain.repository.SettingsRepository
import com.example.focuslock.domain.usecase.AppModeResolver
import com.example.focuslock.domain.usecase.FocusStats
import com.example.focuslock.domain.usecase.FocusStatsCalculator
import com.example.focuslock.ui.components.SessionItem
import com.example.focuslock.ui.components.toSessionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import javax.inject.Inject

data class CountdownInfo(val item: SessionItem, val remaining: Duration)

enum class DashboardMessage { FOCUS_STARTED, FOCUS_REFUSED, SKIPPED, SKIP_REFUSED, START_REFUSED }

data class DashboardUiState(
    val loading: Boolean = true,
    val mode: AppMode = AppMode.NORMAL,
    val isDeviceOwner: Boolean = false,
    val stats: FocusStats = FocusStats.EMPTY,
    val countdown: CountdownInfo? = null,
    val awaitingStart: SessionItem? = null,
    val next: SessionItem? = null,
    val tomorrow: List<SessionItem> = emptyList(),
    val hasSchedules: Boolean = false,
)

private data class DashboardSources(
    val schedules: List<FocusSchedule>,
    val state: LockdownState,
    val weekHistory: List<SessionHistory>,
    val lastSession: SessionHistory?,
    val countdownMinutes: Int,
    val reminderMinutes: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    scheduleRepository: ScheduleRepository,
    stateRepository: LockdownStateRepository,
    historyRepository: HistoryRepository,
    settingsRepository: SettingsRepository,
    private val coordinator: LockdownCoordinator,
    private val policyController: DevicePolicyController,
    private val timeSource: TimeSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val deviceOwner = MutableStateFlow(false)
    private val _messages = MutableStateFlow<DashboardMessage?>(null)
    val messages: StateFlow<DashboardMessage?> = _messages.asStateFlow()

    private val sources = combine(
        scheduleRepository.observeSchedules(),
        stateRepository.observeState(),
        historyRepository.observeEndingAfter(FocusStatsCalculator.weekStart(timeSource.now(), timeSource.zone())),
        historyRepository.observeRecent(limit = 1),
        settingsRepository.settings,
    ) { schedules, state, week, recent, settings ->
        DashboardSources(schedules, state, week, recent.firstOrNull(), settings.countdownMinutes, settings.reminderMinutes)
    }

    // Tick every second only while a countdown is visible; otherwise once a minute.
    private val ticker = stateRepository.observeState()
        .map { it.phase == LockdownPhase.COUNTDOWN }
        .distinctUntilChanged()
        .flatMapLatest { counting -> wallClockTicker(if (counting) TICK_SECOND_MS else TICK_MINUTE_MS) }

    val uiState: StateFlow<DashboardUiState> = combine(sources, deviceOwner, ticker) { src, owner, _ ->
        buildState(src, owner)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DashboardUiState())

    fun refresh() {
        viewModelScope.launch {
            deviceOwner.value = withContext(ioDispatcher) { policyController.isDeviceOwner() }
        }
    }

    fun startFocusNow(minutes: Int, strictMode: Boolean, name: String) {
        viewModelScope.launch {
            val started = coordinator.startFocusNow(name, Duration.ofMinutes(minutes.toLong()), strictMode)
            _messages.value = if (started) DashboardMessage.FOCUS_STARTED else DashboardMessage.FOCUS_REFUSED
        }
    }

    fun skipUpcoming(occurrenceKey: String) {
        viewModelScope.launch {
            val skipped = coordinator.cancelUpcomingSession(occurrenceKey)
            _messages.value = if (skipped) DashboardMessage.SKIPPED else DashboardMessage.SKIP_REFUSED
        }
    }

    fun startPending(occurrenceKey: String) {
        viewModelScope.launch {
            if (!coordinator.startPendingSession(occurrenceKey)) _messages.value = DashboardMessage.START_REFUSED
        }
    }

    fun messageShown() = _messages.update { null }

    private fun buildState(src: DashboardSources, owner: Boolean): DashboardUiState {
        val now = timeSource.now()
        val zone = timeSource.zone()
        val input = DecisionInput(
            now = now,
            zone = zone,
            elapsedRealtimeMs = timeSource.elapsedRealtimeMs(),
            bootCount = timeSource.bootCount(),
            state = src.state,
            schedules = src.schedules,
            countdownMinutes = src.countdownMinutes,
            reminderMinutes = src.reminderMinutes,
        )
        val target = LockdownDecider.decide(input)
        val upcoming = ScheduleCalculator.upcomingWindows(src.schedules, now, zone, days = UPCOMING_DAYS)
            .filterNot { it.occurrenceKey in src.state.skippedOccurrences }
            .map { it.toSessionItem(zone) }
        val countdown = (target as? LockdownTarget.Countdown)?.let {
            CountdownInfo(it.window.toSessionItem(zone), Duration.between(now, it.window.start).coerceAtLeast(Duration.ZERO))
        }
        return DashboardUiState(
            loading = false,
            mode = AppModeResolver.resolve(owner, src.state, upcoming.isNotEmpty(), src.lastSession, now),
            isDeviceOwner = owner,
            stats = FocusStatsCalculator.compute(src.weekHistory, src.schedules, now, zone),
            countdown = countdown,
            awaitingStart = (target as? LockdownTarget.AwaitingStart)?.window?.toSessionItem(zone),
            next = upcoming.firstOrNull { it.occurrenceKey != countdown?.item?.occurrenceKey },
            tomorrow = upcoming.filter { it.daysFromToday == 1L },
            hasSchedules = src.schedules.isNotEmpty(),
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val UPCOMING_DAYS = 7L
    }
}
