package com.example.focuslock.core.scheduling

import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.RepeatRule
import java.time.Instant
import java.time.ZoneId

sealed interface ScheduleValidationError {
    data object BlankName : ScheduleValidationError
    data object NameTooLong : ScheduleValidationError
    data object ZeroLength : ScheduleValidationError
    data object NoDaysSelected : ScheduleValidationError
    data object InThePast : ScheduleValidationError
    data class Overlaps(val conflictingName: String) : ScheduleValidationError

    /** Strict sessions must be unexitable, which Android only allows for a Device Owner. */
    data object StrictNeedsDeviceOwner : ScheduleValidationError
}

object ScheduleValidator {
    const val MAX_NAME_LENGTH = 40

    fun validate(
        schedule: FocusSchedule,
        existing: List<FocusSchedule>,
        now: Instant,
        zone: ZoneId,
        isDeviceOwner: Boolean,
    ): ScheduleValidationError? {
        val name = schedule.name.trim()
        return when {
            name.isEmpty() -> ScheduleValidationError.BlankName
            name.length > MAX_NAME_LENGTH -> ScheduleValidationError.NameTooLong
            schedule.startTime == schedule.endTime -> ScheduleValidationError.ZeroLength
            schedule.strictMode && schedule.enabled && !isDeviceOwner -> ScheduleValidationError.StrictNeedsDeviceOwner
            schedule.repeat is RepeatRule.Weekly && schedule.repeat.days.isEmpty() ->
                ScheduleValidationError.NoDaysSelected
            schedule.repeat is RepeatRule.Once && isOnceInPast(schedule, schedule.repeat, now, zone) ->
                ScheduleValidationError.InThePast
            else -> ScheduleOverlapChecker.findConflict(schedule, existing)
                ?.let { ScheduleValidationError.Overlaps(it.name) }
        }
    }

    private fun isOnceInPast(schedule: FocusSchedule, rule: RepeatRule.Once, now: Instant, zone: ZoneId): Boolean {
        val window = ScheduleCalculator.windowOn(schedule, rule.date, zone) ?: return true
        return !window.start.isAfter(now)
    }
}
