package com.example.focuslock.domain.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * A user-defined focus session expressed in wall-clock time.
 *
 * Times are stored as local times plus a [RepeatRule] rather than absolute timestamps so that
 * "every weekday at 08:00" keeps meaning 08:00 after timezone and daylight-saving changes.
 * Concrete occurrences are resolved into [SessionWindow]s by the schedule calculator.
 */
data class FocusSchedule(
    val id: String,
    val name: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val repeat: RepeatRule,
    val strictMode: Boolean,
    val enabled: Boolean,
    val autoStart: Boolean,
    val createdAt: Long,
) {
    /** A session whose end is not after its start continues into the next day. */
    val crossesMidnight: Boolean get() = !endTime.isAfter(startTime)

    /** Nominal wall-clock length, ignoring daylight-saving shifts. */
    val nominalDuration: Duration
        get() {
            val raw = Duration.between(startTime, endTime)
            return if (crossesMidnight) raw.plusDays(1) else raw
        }
}

sealed interface RepeatRule {
    fun occursOn(date: LocalDate): Boolean

    data class Once(val date: LocalDate) : RepeatRule {
        override fun occursOn(date: LocalDate): Boolean = date == this.date
    }

    data object Daily : RepeatRule {
        override fun occursOn(date: LocalDate): Boolean = true
    }

    data class Weekly(val days: Set<DayOfWeek>) : RepeatRule {
        override fun occursOn(date: LocalDate): Boolean = date.dayOfWeek in days

        val isWeekdays: Boolean get() = days == WEEKDAYS

        companion object {
            val WEEKDAYS: Set<DayOfWeek> = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            )
        }
    }
}
