package com.example.focuslock.domain.usecase

import com.example.focuslock.core.scheduling.ScheduleCalculator
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.SessionHistory
import com.example.focuslock.domain.model.SessionStatus
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class FocusStats(
    val todayFocused: Duration,
    val todayPlanned: Duration,
    val weekFocused: Duration,
    val weekCompletedSessions: Int,
    val weekExitAttempts: Int,
) {
    /** Share of today's planned focus time already completed, 0..1. */
    val todayProgress: Float
        get() = if (todayPlanned.isZero) 0f else (todayFocused.toMillis().toFloat() / todayPlanned.toMillis()).coerceIn(0f, 1f)

    companion object {
        val EMPTY = FocusStats(Duration.ZERO, Duration.ZERO, Duration.ZERO, 0, 0)
    }
}

object FocusStatsCalculator {

    fun weekStart(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone)
            .toInstant()

    fun compute(history: List<SessionHistory>, schedules: List<FocusSchedule>, now: Instant, zone: ZoneId): FocusStats {
        val today = now.atZone(zone).toLocalDate()
        val todayStart = today.atStartOfDay(zone).toInstant()
        val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant()
        val weekStart = weekStart(now, zone)

        val todayFocused = history.sumDurations { focusedWithin(it, todayStart, tomorrowStart, now) }
        val weekFocused = history.sumDurations { focusedWithin(it, weekStart, tomorrowStart, now) }

        val scheduledToday = ScheduleCalculator.windowsOnDate(schedules, today, zone)
            .sumDurations { overlap(it.start, it.end, todayStart, tomorrowStart) }
        val adHocToday = history.filter { it.scheduleId == null }
            .sumDurations { overlap(it.startedAt, it.expectedEnd, todayStart, tomorrowStart) }
        val todayPlanned = maxOf(scheduledToday.plus(adHocToday), todayFocused)

        val weekSessions = history.filter { it.expectedEnd.isAfter(weekStart) }
        return FocusStats(
            todayFocused = todayFocused,
            todayPlanned = todayPlanned,
            weekFocused = weekFocused,
            weekCompletedSessions = weekSessions.count { it.status == SessionStatus.COMPLETED },
            weekExitAttempts = weekSessions.sumOf { it.exitAttempts },
        )
    }

    private fun focusedWithin(session: SessionHistory, from: Instant, to: Instant, now: Instant): Duration {
        val end = minOf(session.actualEnd ?: now, session.expectedEnd)
        return overlap(session.startedAt, end, from, to)
    }

    private fun overlap(start: Instant, end: Instant, from: Instant, to: Instant): Duration {
        val s = maxOf(start, from)
        val e = minOf(end, to)
        return if (e.isAfter(s)) Duration.between(s, e) else Duration.ZERO
    }

    private inline fun <T> Iterable<T>.sumDurations(selector: (T) -> Duration): Duration =
        fold(Duration.ZERO) { acc, item -> acc.plus(selector(item)) }
}
