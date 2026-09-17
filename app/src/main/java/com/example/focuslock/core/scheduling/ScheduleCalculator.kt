package com.example.focuslock.core.scheduling

import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.SessionWindow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Resolves wall-clock schedules into absolute [SessionWindow]s for a given timezone.
 *
 * Daylight-saving behaviour follows [ZonedDateTime.of]: a local time inside a spring-forward gap is
 * shifted forward by the gap length, and an ambiguous autumn time uses the earlier offset.
 */
object ScheduleCalculator {

    /** How far ahead the calculator looks for the next session (covers any weekly rule). */
    const val LOOKAHEAD_DAYS = 8L

    /** Sessions can cross midnight, so the previous day's occurrence may still be running. */
    private const val LOOKBACK_DAYS = 1L

    fun windowOn(schedule: FocusSchedule, date: LocalDate, zone: ZoneId): SessionWindow? {
        if (!schedule.repeat.occursOn(date)) return null
        val start = ZonedDateTime.of(date, schedule.startTime, zone).toInstant()
        val endDate = if (schedule.crossesMidnight) date.plusDays(1) else date
        val end = ZonedDateTime.of(endDate, schedule.endTime, zone).toInstant()
        // A session collapsed to nothing by a daylight-saving gap is skipped.
        if (!end.isAfter(start)) return null
        return SessionWindow(
            scheduleId = schedule.id,
            name = schedule.name,
            start = start,
            end = end,
            strictMode = schedule.strictMode,
            autoStart = schedule.autoStart,
        )
    }

    /** All occurrences of [schedule] that intersect the half-open range [from, to). */
    fun occurrencesBetween(
        schedule: FocusSchedule,
        from: Instant,
        to: Instant,
        zone: ZoneId,
    ): List<SessionWindow> {
        if (!to.isAfter(from)) return emptyList()
        val firstDate = from.atZone(zone).toLocalDate().minusDays(LOOKBACK_DAYS)
        val lastDate = to.atZone(zone).toLocalDate()
        return generateSequence(firstDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDate) }
            .mapNotNull { windowOn(schedule, it, zone) }
            .filter { it.end.isAfter(from) && it.start.isBefore(to) }
            .toList()
    }

    /** Enabled occurrences running at [now], ordered by start. */
    fun activeWindows(schedules: List<FocusSchedule>, now: Instant, zone: ZoneId): List<SessionWindow> =
        schedules.asSequence()
            .filter { it.enabled }
            .flatMap { occurrencesBetween(it, now, now.plusMillis(1), zone) }
            .filter { now in it }
            .sortedBy { it.start }
            .toList()

    /** Enabled occurrences starting after [now] within [days], ordered by start. */
    fun upcomingWindows(
        schedules: List<FocusSchedule>,
        now: Instant,
        zone: ZoneId,
        days: Long = LOOKAHEAD_DAYS,
    ): List<SessionWindow> {
        val horizon = now.atZone(zone).plusDays(days).toInstant()
        return schedules.asSequence()
            .filter { it.enabled }
            .flatMap { occurrencesBetween(it, now, horizon, zone) }
            .filter { it.start.isAfter(now) }
            .sortedBy { it.start }
            .toList()
    }

    /** Enabled occurrences whose start falls on [date] in [zone], ordered by start. */
    fun windowsOnDate(schedules: List<FocusSchedule>, date: LocalDate, zone: ZoneId): List<SessionWindow> =
        schedules.filter { it.enabled }
            .mapNotNull { windowOn(it, date, zone) }
            .sortedBy { it.start }
}
