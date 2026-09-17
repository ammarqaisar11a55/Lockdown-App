package com.example.focuslock.core.scheduling

import com.example.focuslock.domain.model.RepeatRule
import com.example.focuslock.testing.NEW_YORK
import com.example.focuslock.testing.UTC
import com.example.focuslock.testing.WEDNESDAY
import com.example.focuslock.testing.at
import com.example.focuslock.testing.schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class ScheduleCalculatorTest {

    @Test
    fun `daily schedule is active inside its window only`() {
        val daily = listOf(schedule(start = "08:00", end = "12:00"))
        assertEquals(1, ScheduleCalculator.activeWindows(daily, at(WEDNESDAY, "08:00"), UTC).size)
        assertEquals(1, ScheduleCalculator.activeWindows(daily, at(WEDNESDAY, "11:59"), UTC).size)
        assertTrue(ScheduleCalculator.activeWindows(daily, at(WEDNESDAY, "12:00"), UTC).isEmpty())
        assertTrue(ScheduleCalculator.activeWindows(daily, at(WEDNESDAY, "07:59"), UTC).isEmpty())
    }

    @Test
    fun `window end is exclusive and start inclusive`() {
        val window = ScheduleCalculator.windowOn(schedule(), WEDNESDAY, UTC)!!
        assertTrue(at(WEDNESDAY, "08:00") in window)
        assertTrue(at(WEDNESDAY, "12:00") !in window)
    }

    @Test
    fun `weekly schedule only occurs on selected days`() {
        val weekdays = schedule(repeat = RepeatRule.Weekly(RepeatRule.Weekly.WEEKDAYS))
        assertEquals(1, ScheduleCalculator.activeWindows(listOf(weekdays), at(WEDNESDAY, "09:00"), UTC).size)
        val saturday = WEDNESDAY.plusDays(3)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
        assertTrue(ScheduleCalculator.activeWindows(listOf(weekdays), at(saturday, "09:00"), UTC).isEmpty())
    }

    @Test
    fun `one-time schedule occurs only on its date`() {
        val once = schedule(repeat = RepeatRule.Once(WEDNESDAY))
        assertEquals(1, ScheduleCalculator.activeWindows(listOf(once), at(WEDNESDAY, "09:00"), UTC).size)
        assertTrue(ScheduleCalculator.activeWindows(listOf(once), at(WEDNESDAY.plusDays(1), "09:00"), UTC).isEmpty())
        assertNull(ScheduleCalculator.upcomingWindows(listOf(once), at(WEDNESDAY, "13:00"), UTC).firstOrNull())
    }

    @Test
    fun `session crossing midnight is active after midnight`() {
        val night = schedule(start = "22:00", end = "02:00")
        val afterMidnight = at(WEDNESDAY.plusDays(1), "01:30")
        val active = ScheduleCalculator.activeWindows(listOf(night), afterMidnight, UTC).single()
        assertEquals(at(WEDNESDAY, "22:00"), active.start)
        assertEquals(at(WEDNESDAY.plusDays(1), "02:00"), active.end)
    }

    @Test
    fun `overnight once schedule belongs to its start date`() {
        val night = schedule(start = "22:00", end = "02:00", repeat = RepeatRule.Once(WEDNESDAY))
        assertEquals(1, ScheduleCalculator.activeWindows(listOf(night), at(WEDNESDAY.plusDays(1), "01:00"), UTC).size)
    }

    @Test
    fun `disabled schedules are ignored`() {
        val disabled = schedule(enabled = false)
        assertTrue(ScheduleCalculator.activeWindows(listOf(disabled), at(WEDNESDAY, "09:00"), UTC).isEmpty())
        assertTrue(ScheduleCalculator.upcomingWindows(listOf(disabled), at(WEDNESDAY, "06:00"), UTC).isEmpty())
    }

    @Test
    fun `upcoming windows are ordered and span date boundaries`() {
        val schedules = listOf(
            schedule(id = "a", start = "14:00", end = "17:00"),
            schedule(id = "b", start = "08:00", end = "12:00"),
        )
        val upcoming = ScheduleCalculator.upcomingWindows(schedules, at(WEDNESDAY, "13:00"), UTC, days = 2)
        assertEquals(listOf("a", "b", "a", "b"), upcoming.take(4).map { it.scheduleId })
        assertEquals(at(WEDNESDAY.plusDays(1), "08:00"), upcoming[1].start)
    }

    @Test
    fun `timezone change moves the absolute instant but keeps wall-clock time`() {
        val daily = schedule(start = "08:00", end = "12:00")
        val utcWindow = ScheduleCalculator.windowOn(daily, WEDNESDAY, UTC)!!
        val tokyo = ZoneId.of("Asia/Tokyo")
        val tokyoWindow = ScheduleCalculator.windowOn(daily, WEDNESDAY, tokyo)!!
        assertEquals(Duration.ofHours(9), Duration.between(tokyoWindow.start, utcWindow.start))
        assertEquals(8, tokyoWindow.start.atZone(tokyo).hour)
    }

    @Test
    fun `spring forward gap shifts start forward`() {
        // 2024-03-10 02:00-03:00 does not exist in New York.
        val date = LocalDate.of(2024, 3, 10)
        val window = ScheduleCalculator.windowOn(schedule(start = "02:30", end = "04:00"), date, NEW_YORK)!!
        assertEquals(3, window.start.atZone(NEW_YORK).hour)
        assertEquals(Duration.ofMinutes(30), window.duration)
    }

    @Test
    fun `session collapsed by a daylight saving gap is skipped`() {
        val date = LocalDate.of(2024, 3, 10)
        assertNull(ScheduleCalculator.windowOn(schedule(start = "02:30", end = "03:15"), date, NEW_YORK))
    }

    @Test
    fun `fall back day lengthens an overnight session by an hour`() {
        // 2024-11-03 01:00-02:00 repeats in New York.
        val date = LocalDate.of(2024, 11, 2)
        val window = ScheduleCalculator.windowOn(schedule(start = "23:00", end = "03:00"), date, NEW_YORK)!!
        assertEquals(Duration.ofHours(5), window.duration)
    }

    @Test
    fun `windows on date only include sessions starting that day`() {
        val schedules = listOf(schedule(id = "a"), schedule(id = "b", start = "20:00", end = "21:00"))
        assertEquals(2, ScheduleCalculator.windowsOnDate(schedules, WEDNESDAY, UTC).size)
    }
}
