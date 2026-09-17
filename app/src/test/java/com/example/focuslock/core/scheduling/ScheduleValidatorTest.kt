package com.example.focuslock.core.scheduling

import com.example.focuslock.domain.model.RepeatRule
import com.example.focuslock.testing.UTC
import com.example.focuslock.testing.WEDNESDAY
import com.example.focuslock.testing.at
import com.example.focuslock.testing.schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleValidatorTest {
    private val now = at(WEDNESDAY, "10:00")

    private fun validate(candidate: com.example.focuslock.domain.model.FocusSchedule, existing: List<com.example.focuslock.domain.model.FocusSchedule> = emptyList()) =
        ScheduleValidator.validate(candidate, existing, now, UTC)

    @Test
    fun `valid schedule passes`() = assertNull(validate(schedule()))

    @Test
    fun `blank name is rejected`() = assertEquals(ScheduleValidationError.BlankName, validate(schedule(name = "  ")))

    @Test
    fun `long name is rejected`() =
        assertEquals(ScheduleValidationError.NameTooLong, validate(schedule(name = "x".repeat(ScheduleValidator.MAX_NAME_LENGTH + 1))))

    @Test
    fun `equal start and end is rejected`() =
        assertEquals(ScheduleValidationError.ZeroLength, validate(schedule(start = "09:00", end = "09:00")))

    @Test
    fun `weekly without days is rejected`() =
        assertEquals(ScheduleValidationError.NoDaysSelected, validate(schedule(repeat = RepeatRule.Weekly(emptySet()))))

    @Test
    fun `one-time schedule that already started is rejected`() =
        assertEquals(ScheduleValidationError.InThePast, validate(schedule(repeat = RepeatRule.Once(WEDNESDAY))))

    @Test
    fun `one-time schedule later today is accepted`() =
        assertNull(validate(schedule(start = "15:00", end = "16:00", repeat = RepeatRule.Once(WEDNESDAY))))

    @Test
    fun `overlap reports the conflicting schedule`() = assertEquals(
        ScheduleValidationError.Overlaps("Gym"),
        validate(schedule(id = "new"), listOf(schedule(id = "old", name = "Gym", start = "11:00", end = "13:00"))),
    )
}
