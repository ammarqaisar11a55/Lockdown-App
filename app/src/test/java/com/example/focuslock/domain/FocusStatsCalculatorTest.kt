package com.example.focuslock.domain

import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.domain.model.SessionHistory
import com.example.focuslock.domain.model.SessionStatus
import com.example.focuslock.domain.usecase.FocusStatsCalculator
import com.example.focuslock.testing.UTC
import com.example.focuslock.testing.WEDNESDAY
import com.example.focuslock.testing.at
import com.example.focuslock.testing.schedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FocusStatsCalculatorTest {

    private fun session(
        id: Long,
        start: Instant,
        end: Instant,
        actualEnd: Instant? = end,
        status: SessionStatus = SessionStatus.COMPLETED,
        scheduleId: String? = "s1",
        exits: Int = 0,
    ) = SessionHistory(id, scheduleId, "S", start, start, end, actualEnd, status, false, EnforcementLevel.DEVICE_OWNER, 0, exits)

    @Test
    fun `today and week totals`() {
        val now = at(WEDNESDAY, "15:00")
        val history = listOf(
            session(1, at(WEDNESDAY, "08:00"), at(WEDNESDAY, "12:00"), exits = 2),
            session(2, at(WEDNESDAY.minusDays(1), "08:00"), at(WEDNESDAY.minusDays(1), "10:00")),
            // Previous week (Sunday) must not count towards this week.
            session(3, at(WEDNESDAY.minusDays(3), "08:00"), at(WEDNESDAY.minusDays(3), "09:00"), exits = 5),
        )
        val stats = FocusStatsCalculator.compute(history, listOf(schedule(start = "08:00", end = "12:00")), now, UTC)
        assertEquals(Duration.ofHours(4), stats.todayFocused)
        assertEquals(Duration.ofHours(4), stats.todayPlanned)
        assertEquals(Duration.ofHours(6), stats.weekFocused)
        assertEquals(2, stats.weekCompletedSessions)
        assertEquals(2, stats.weekExitAttempts)
        assertEquals(1f, stats.todayProgress)
    }

    @Test
    fun `running session counts up to now`() {
        val now = at(WEDNESDAY, "09:00")
        val history = listOf(session(1, at(WEDNESDAY, "08:00"), at(WEDNESDAY, "12:00"), actualEnd = null, status = SessionStatus.IN_PROGRESS))
        val stats = FocusStatsCalculator.compute(history, listOf(schedule()), now, UTC)
        assertEquals(Duration.ofHours(1), stats.todayFocused)
        assertEquals(0.25f, stats.todayProgress)
    }

    @Test
    fun `session crossing midnight is split across days`() {
        val now = at(WEDNESDAY, "10:00")
        val history = listOf(session(1, at(WEDNESDAY.minusDays(1), "22:00"), at(WEDNESDAY, "02:00")))
        val stats = FocusStatsCalculator.compute(history, emptyList(), now, UTC)
        assertEquals(Duration.ofHours(2), stats.todayFocused)
        assertEquals(Duration.ofHours(4), stats.weekFocused)
    }

    @Test
    fun `ad-hoc sessions count as planned`() {
        val now = at(WEDNESDAY, "10:00")
        val history = listOf(session(1, at(WEDNESDAY, "09:00"), at(WEDNESDAY, "11:00"), actualEnd = null, scheduleId = null))
        val stats = FocusStatsCalculator.compute(history, emptyList(), now, UTC)
        assertEquals(Duration.ofHours(2), stats.todayPlanned)
        assertEquals(0.5f, stats.todayProgress)
    }

    @Test
    fun `no plan means zero progress`() {
        val stats = FocusStatsCalculator.compute(emptyList(), emptyList(), at(WEDNESDAY, "10:00"), UTC)
        assertEquals(0f, stats.todayProgress)
    }
}
