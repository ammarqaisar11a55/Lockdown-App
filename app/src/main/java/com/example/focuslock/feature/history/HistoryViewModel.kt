package com.example.focuslock.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focuslock.core.time.DurationFormatter
import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.domain.model.SessionStatus
import com.example.focuslock.domain.repository.HistoryRepository
import com.example.focuslock.domain.usecase.FocusStats
import com.example.focuslock.domain.usecase.FocusStatsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class HistoryEntry(
    val id: Long,
    val name: String,
    val timeRange: String,
    val duration: String,
    val status: SessionStatus,
    val recoveries: Int,
    val exitAttempts: Int,
    val strictMode: Boolean,
)

enum class RelativeDay { TODAY, YESTERDAY, OTHER }

data class HistoryDay(val key: String, val title: String, val relative: RelativeDay, val entries: List<HistoryEntry>)

data class HistoryUiState(
    val loading: Boolean = true,
    val stats: FocusStats = FocusStats.EMPTY,
    val days: List<HistoryDay> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    historyRepository: HistoryRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = historyRepository.observeRecent(HISTORY_LIMIT).map { sessions ->
        val zone = timeSource.zone()
        val now = timeSource.now()
        val today = now.atZone(zone).toLocalDate()
        val days = sessions
            .groupBy { it.startedAt.atZone(zone).toLocalDate() }
            .map { (date, daySessions) ->
                HistoryDay(
                    key = date.toString(),
                    title = DAY_TITLE.withLocale(Locale.getDefault()).format(date),
                    relative = when (date) {
                        today -> RelativeDay.TODAY
                        today.minusDays(1) -> RelativeDay.YESTERDAY
                        else -> RelativeDay.OTHER
                    },
                    entries = daySessions.map { session ->
                        val end = session.actualEnd?.let { minOf(it, session.expectedEnd) } ?: session.expectedEnd
                        HistoryEntry(
                            id = session.id,
                            name = session.name,
                            timeRange = "${DurationFormatter.time(session.startedAt, zone)} → ${DurationFormatter.time(end, zone)}",
                            duration = DurationFormatter.short(session.focusedDuration(now)),
                            status = session.status,
                            recoveries = session.recoveryCount,
                            exitAttempts = session.exitAttempts,
                            strictMode = session.strictMode,
                        )
                    },
                )
            }
        HistoryUiState(
            loading = false,
            stats = FocusStatsCalculator.compute(sessions, emptyList(), now, zone),
            days = days,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState())

    private companion object {
        const val HISTORY_LIMIT = 200
        val DAY_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
