package com.example.focuslock.core.scheduling

import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.RepeatRule
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Detects schedules whose wall-clock occurrences overlap.
 *
 * Overlap is a wall-clock property, so the comparison uses UTC (no daylight-saving noise).
 * Merge policy: overlapping enabled schedules are rejected at save time. If an overlap still
 * occurs at runtime (for example after a timezone change), the engine keeps the running session
 * and the window that ends last wins.
 */
object ScheduleOverlapChecker {

    /** Any Monday works: two recurring rules overlap iff they overlap within one week. */
    private val REFERENCE_MONDAY: LocalDate = LocalDate.of(2024, 1, 1)
    private const val DAYS_IN_WEEK = 7L

    fun findConflict(candidate: FocusSchedule, existing: List<FocusSchedule>): FocusSchedule? {
        if (!candidate.enabled) return null
        return existing.firstOrNull { other ->
            other.enabled && other.id != candidate.id && overlaps(candidate, other)
        }
    }

    fun overlaps(first: FocusSchedule, second: FocusSchedule): Boolean {
        val dates = referenceDates(first, second)
        if (dates.isEmpty()) return false
        val from = dates.first().atStartOfDay().toInstant(ZoneOffset.UTC)
        val to = dates.last().plusDays(2).atStartOfDay().toInstant(ZoneOffset.UTC)
        val firstWindows = ScheduleCalculator.occurrencesBetween(first, from, to, ZoneOffset.UTC)
        val secondWindows = ScheduleCalculator.occurrencesBetween(second, from, to, ZoneOffset.UTC)
        return firstWindows.any { a ->
            secondWindows.any { b -> a.start.isBefore(b.end) && b.start.isBefore(a.end) }
        }
    }

    private fun referenceDates(first: FocusSchedule, second: FocusSchedule): List<LocalDate> {
        val onceDates = listOf(first.repeat, second.repeat)
            .filterIsInstance<RepeatRule.Once>()
            .map { it.date }
        val anchors = onceDates.ifEmpty {
            (0 until DAYS_IN_WEEK).map { REFERENCE_MONDAY.plusDays(it) }
        }
        // Include the day before each anchor to catch sessions that cross midnight.
        return anchors.flatMap { listOf(it.minusDays(1), it) }.distinct().sorted()
    }
}
