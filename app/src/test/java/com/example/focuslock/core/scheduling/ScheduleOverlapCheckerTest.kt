package com.example.focuslock.core.scheduling

import com.example.focuslock.domain.model.RepeatRule
import com.example.focuslock.testing.WEDNESDAY
import com.example.focuslock.testing.schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ScheduleOverlapCheckerTest {

    @Test
    fun `overlapping daily schedules conflict`() {
        assertTrue(
            ScheduleOverlapChecker.overlaps(
                schedule(id = "a", start = "08:00", end = "12:00"),
                schedule(id = "b", start = "11:00", end = "13:00"),
            ),
        )
    }

    @Test
    fun `adjacent schedules do not conflict`() {
        assertFalse(
            ScheduleOverlapChecker.overlaps(
                schedule(id = "a", start = "08:00", end = "12:00"),
                schedule(id = "b", start = "12:00", end = "14:00"),
            ),
        )
    }

    @Test
    fun `multiple sessions per day are allowed`() {
        val existing = listOf(
            schedule(id = "a", start = "08:00", end = "12:00"),
            schedule(id = "b", start = "14:00", end = "17:00"),
        )
        assertNull(ScheduleOverlapChecker.findConflict(schedule(id = "c", start = "20:00", end = "22:00"), existing))
    }

    @Test
    fun `different weekdays do not conflict`() {
        val monday = schedule(id = "a", repeat = RepeatRule.Weekly(setOf(DayOfWeek.MONDAY)))
        val tuesday = schedule(id = "b", repeat = RepeatRule.Weekly(setOf(DayOfWeek.TUESDAY)))
        assertFalse(ScheduleOverlapChecker.overlaps(monday, tuesday))
    }

    @Test
    fun `overnight session conflicts with next morning session`() {
        val sundayNight = schedule(id = "a", start = "22:00", end = "09:00", repeat = RepeatRule.Weekly(setOf(DayOfWeek.SUNDAY)))
        val mondayMorning = schedule(id = "b", start = "08:00", end = "10:00", repeat = RepeatRule.Weekly(setOf(DayOfWeek.MONDAY)))
        assertTrue(ScheduleOverlapChecker.overlaps(sundayNight, mondayMorning))
    }

    @Test
    fun `saturday overnight session conflicts with sunday morning across the week wrap`() {
        val saturdayNight = schedule(id = "a", start = "23:00", end = "02:00", repeat = RepeatRule.Weekly(setOf(DayOfWeek.SATURDAY)))
        val sundayEarly = schedule(id = "b", start = "01:00", end = "03:00", repeat = RepeatRule.Weekly(setOf(DayOfWeek.SUNDAY)))
        assertTrue(ScheduleOverlapChecker.overlaps(saturdayNight, sundayEarly))
    }

    @Test
    fun `one-time session conflicts with a daily schedule on the same day`() {
        val once = schedule(id = "a", start = "09:00", end = "10:00", repeat = RepeatRule.Once(WEDNESDAY))
        assertTrue(ScheduleOverlapChecker.overlaps(once, schedule(id = "b")))
    }

    @Test
    fun `one-time session does not conflict with a weekly schedule on another day`() {
        val once = schedule(id = "a", repeat = RepeatRule.Once(WEDNESDAY))
        val friday = schedule(id = "b", repeat = RepeatRule.Weekly(setOf(DayOfWeek.FRIDAY)))
        assertFalse(ScheduleOverlapChecker.overlaps(once, friday))
    }

    @Test
    fun `one-time sessions on different dates do not conflict`() {
        val first = schedule(id = "a", repeat = RepeatRule.Once(WEDNESDAY))
        val second = schedule(id = "b", repeat = RepeatRule.Once(WEDNESDAY.plusDays(1)))
        assertFalse(ScheduleOverlapChecker.overlaps(first, second))
    }

    @Test
    fun `disabled schedules and the schedule itself are ignored`() {
        val candidate = schedule(id = "a")
        assertNull(ScheduleOverlapChecker.findConflict(candidate, listOf(schedule(id = "b", enabled = false))))
        assertNull(ScheduleOverlapChecker.findConflict(candidate, listOf(schedule(id = "a"))))
        assertNull(ScheduleOverlapChecker.findConflict(candidate.copy(enabled = false), listOf(schedule(id = "b"))))
        assertEquals("b", ScheduleOverlapChecker.findConflict(candidate, listOf(schedule(id = "b")))?.id)
    }
}
