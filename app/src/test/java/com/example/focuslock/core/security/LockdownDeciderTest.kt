package com.example.focuslock.core.security

import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.LockdownPhase
import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.RepeatRule
import com.example.focuslock.domain.model.SessionWindow
import com.example.focuslock.testing.UTC
import com.example.focuslock.testing.WEDNESDAY
import com.example.focuslock.testing.at
import com.example.focuslock.testing.schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LockdownDeciderTest {

    private fun input(
        now: Instant,
        schedules: List<FocusSchedule> = listOf(schedule()),
        state: LockdownState = LockdownState(),
        countdown: Int = 0,
        reminder: Int = 0,
        elapsed: Long = 1_000_000,
        boot: Int = 1,
    ) = DecisionInput(now, UTC, elapsed, boot, state, schedules, countdown, reminder)

    private fun activeState(window: SessionWindow, startedAt: Instant = window.start) = LockdownState(
        phase = LockdownPhase.ACTIVE,
        session = window,
        checkpoint = SessionClock.checkpoint(window, startedAt, 1_000_000, 1),
    )

    private val window = SessionWindow("s1", "Study", at(WEDNESDAY, "08:00"), at(WEDNESDAY, "12:00"), false)

    @Test
    fun `idle before the session`() {
        assertEquals(LockdownTarget.Idle, LockdownDecider.decide(input(at(WEDNESDAY, "07:00"))))
    }

    @Test
    fun `lockdown begins at the scheduled time`() {
        val target = LockdownDecider.decide(input(at(WEDNESDAY, "08:00")))
        assertEquals(LockdownTarget.Active(window, continuing = false), target)
    }

    @Test
    fun `missed start alarm still locks for the remainder of the window`() {
        val target = LockdownDecider.decide(input(at(WEDNESDAY, "10:30")))
        assertEquals(LockdownTarget.Active(window, continuing = false), target)
    }

    @Test
    fun `active session continues even if its schedule was deleted`() {
        val target = LockdownDecider.decide(
            input(at(WEDNESDAY, "09:00"), schedules = emptyList(), state = activeState(window), elapsed = 1_000_000 + HOUR),
        )
        assertEquals(LockdownTarget.Active(window, continuing = true), target)
    }

    @Test
    fun `lockdown ends at the scheduled time`() {
        val target = LockdownDecider.decide(
            input(at(WEDNESDAY, "12:00"), state = activeState(window), elapsed = 1_000_000 + 4 * HOUR),
        )
        assertEquals(LockdownTarget.Idle, target)
    }

    @Test
    fun `clock moved forward keeps the session active`() {
        val target = LockdownDecider.decide(
            input(at(WEDNESDAY, "18:00"), state = activeState(window), elapsed = 1_000_000 + HOUR),
        )
        assertEquals(LockdownTarget.Active(window, continuing = true), target)
    }

    @Test
    fun `reboot after the end releases the session`() {
        val target = LockdownDecider.decide(
            input(at(WEDNESDAY, "12:30"), state = activeState(window), elapsed = 5_000, boot = 2),
        )
        assertEquals(LockdownTarget.Idle, target)
    }

    @Test
    fun `back-to-back sessions start the next one when the first expires`() {
        val schedules = listOf(schedule(id = "s1"), schedule(id = "s2", name = "Gym", start = "12:00", end = "13:00"))
        val target = LockdownDecider.decide(
            input(at(WEDNESDAY, "12:00"), schedules = schedules, state = activeState(window), elapsed = 1_000_000 + 4 * HOUR),
        )
        assertTrue(target is LockdownTarget.Active && !target.continuing && target.window.scheduleId == "s2")
    }

    @Test
    fun `countdown precedes the session and can be skipped`() {
        val countdown = LockdownDecider.decide(input(at(WEDNESDAY, "07:56"), countdown = 5))
        assertEquals(LockdownTarget.Countdown(window), countdown)

        val skipped = LockdownState(skippedOccurrences = setOf(window.occurrenceKey))
        assertEquals(LockdownTarget.Idle, LockdownDecider.decide(input(at(WEDNESDAY, "07:56"), state = skipped, countdown = 5)))
        assertEquals(LockdownTarget.Idle, LockdownDecider.decide(input(at(WEDNESDAY, "08:30"), state = skipped, countdown = 5)))
    }

    @Test
    fun `countdown turns into lockdown at the start time`() {
        val counting = LockdownState(phase = LockdownPhase.COUNTDOWN, session = window)
        assertEquals(
            LockdownTarget.Active(window, continuing = false),
            LockdownDecider.decide(input(at(WEDNESDAY, "08:00"), state = counting, countdown = 5)),
        )
    }

    @Test
    fun `no countdown outside the countdown period`() {
        assertEquals(LockdownTarget.Idle, LockdownDecider.decide(input(at(WEDNESDAY, "07:50"), countdown = 5)))
    }

    @Test
    fun `manual start schedule waits for the user`() {
        val manual = listOf(schedule(autoStart = false))
        val manualWindow = window.copy(autoStart = false)
        assertEquals(
            LockdownTarget.AwaitingStart(manualWindow),
            LockdownDecider.decide(input(at(WEDNESDAY, "09:00"), schedules = manual, countdown = 5)),
        )
        val started = LockdownState(startedOccurrences = setOf(manualWindow.occurrenceKey))
        assertEquals(
            LockdownTarget.Active(manualWindow, continuing = false),
            LockdownDecider.decide(input(at(WEDNESDAY, "09:00"), schedules = manual, state = started)),
        )
    }

    @Test
    fun `manual start schedules never get an automatic countdown`() {
        val manual = listOf(schedule(autoStart = false))
        assertEquals(LockdownTarget.Idle, LockdownDecider.decide(input(at(WEDNESDAY, "07:58"), schedules = manual, countdown = 5)))
    }

    @Test
    fun `next wake-up is the earliest relevant transition`() {
        val idle = input(at(WEDNESDAY, "06:00"), countdown = 5, reminder = 15)
        assertEquals(at(WEDNESDAY, "07:45"), LockdownDecider.nextWakeUp(idle, LockdownTarget.Idle))

        val countdown = input(at(WEDNESDAY, "07:56"), countdown = 5, reminder = 15)
        assertEquals(at(WEDNESDAY, "08:00"), LockdownDecider.nextWakeUp(countdown, LockdownTarget.Countdown(window)))

        val active = input(at(WEDNESDAY, "09:00"), state = activeState(window), elapsed = 1_000_000 + HOUR)
        assertEquals(at(WEDNESDAY, "12:00"), LockdownDecider.nextWakeUp(active, LockdownTarget.Active(window, true)))
    }

    @Test
    fun `next wake-up is null without schedules`() {
        assertNull(LockdownDecider.nextWakeUp(input(at(WEDNESDAY, "06:00"), schedules = emptyList()), LockdownTarget.Idle))
    }

    @Test
    fun `wake-up during a manipulated clock follows the monotonic remaining time`() {
        val manipulated = input(at(WEDNESDAY, "18:00"), state = activeState(window), elapsed = 1_000_000 + HOUR)
        val wake = LockdownDecider.nextWakeUp(manipulated, LockdownTarget.Active(window, true))
        assertEquals(at(WEDNESDAY, "21:00"), wake)
    }

    @Test
    fun `reminder is due once inside the lead time`() {
        val due = LockdownDecider.reminderDue(input(at(WEDNESDAY, "07:50"), reminder = 15))
        assertEquals(window, due)
        val reminded = LockdownState(remindedOccurrences = setOf(window.occurrenceKey))
        assertNull(LockdownDecider.reminderDue(input(at(WEDNESDAY, "07:50"), state = reminded, reminder = 15)))
        assertNull(LockdownDecider.reminderDue(input(at(WEDNESDAY, "07:30"), reminder = 15)))
    }

    @Test
    fun `one-time schedule never locks again after completion`() {
        val once = listOf(schedule(repeat = RepeatRule.Once(WEDNESDAY)))
        assertEquals(LockdownTarget.Idle, LockdownDecider.decide(input(at(WEDNESDAY.plusDays(1), "09:00"), schedules = once)))
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
