package com.example.focuslock.core.security

import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.domain.model.SessionWindow
import com.example.focuslock.testing.WEDNESDAY
import com.example.focuslock.testing.at
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class SessionClockTest {
    private val session = SessionWindow("s", "Study", at(WEDNESDAY, "08:00"), at(WEDNESDAY, "12:00"), strictMode = false)
    private val start = at(WEDNESDAY, "08:00")
    private val checkpoint = SessionClock.checkpoint(session, start, elapsedRealtimeMs = 10_000, bootCount = 3)

    @Test
    fun `remaining time is computed from timestamps`() {
        val later = start.plus(Duration.ofHours(1))
        val remaining = SessionClock.remaining(session, checkpoint, later, 10_000 + Duration.ofHours(1).toMillis(), 3)
        assertEquals(Duration.ofHours(3), remaining)
    }

    @Test
    fun `session expires at scheduled end`() {
        val end = at(WEDNESDAY, "12:00")
        assertTrue(SessionClock.isExpired(session, checkpoint, end, 10_000 + Duration.ofHours(4).toMillis(), 3))
    }

    @Test
    fun `moving the clock forward does not end the session early`() {
        // Ten real minutes have passed, but the wall clock was moved past the end.
        val manipulated = at(WEDNESDAY, "13:00")
        val elapsed = 10_000 + Duration.ofMinutes(10).toMillis()
        assertFalse(SessionClock.isExpired(session, checkpoint, manipulated, elapsed, 3))
        assertEquals(Duration.ofMinutes(230), SessionClock.remaining(session, checkpoint, manipulated, elapsed, 3))
    }

    @Test
    fun `after reboot the wall clock is trusted`() {
        val afterEnd = at(WEDNESDAY, "12:05")
        assertTrue(SessionClock.isExpired(session, checkpoint, afterEnd, 2_000, bootCount = 4))
    }

    @Test
    fun `unknown boot count falls back to the wall clock`() {
        val afterEnd = at(WEDNESDAY, "12:05")
        assertTrue(SessionClock.isExpired(session, checkpoint, afterEnd, 20_000, TimeSource.UNKNOWN_BOOT_COUNT))
    }

    @Test
    fun `refresh keeps checkpoint within the same boot and re-anchors after reboot`() {
        val later = start.plus(Duration.ofHours(1))
        assertEquals(checkpoint, SessionClock.refreshForBoot(checkpoint, session, later, 50_000, 3))
        val reanchored = SessionClock.refreshForBoot(checkpoint, session, later, 1_000, 4)
        assertEquals(4, reanchored.bootCount)
        assertEquals(Duration.ofHours(3).toMillis(), reanchored.remainingMs)
    }
}
