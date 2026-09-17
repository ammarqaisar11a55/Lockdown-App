package com.example.focuslock.core.security

import com.example.focuslock.core.scheduling.ScheduleCalculator
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.LockdownPhase
import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.SessionWindow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Everything the decider needs; no Android types, so it is fully unit-testable. */
data class DecisionInput(
    val now: Instant,
    val zone: ZoneId,
    val elapsedRealtimeMs: Long,
    val bootCount: Int,
    val state: LockdownState,
    val schedules: List<FocusSchedule>,
    val countdownMinutes: Int,
    val reminderMinutes: Int,
)

/** The phase the device should be in right now. */
sealed interface LockdownTarget {
    /** Enforce [window]. [continuing] is true when it is the session already persisted as active. */
    data class Active(val window: SessionWindow, val continuing: Boolean) : LockdownTarget

    /** [window] starts soon and may still be cancelled. */
    data class Countdown(val window: SessionWindow) : LockdownTarget

    /** A manual-start occurrence is due; nothing is enforced until the user starts it. */
    data class AwaitingStart(val window: SessionWindow) : LockdownTarget

    data object Idle : LockdownTarget
}

/**
 * Pure state machine behind the policy reconciliation engine.
 *
 * ```
 * persisted state + current time + schedules  ->  target phase + next wake-up
 * ```
 */
object LockdownDecider {

    fun decide(input: DecisionInput): LockdownTarget {
        val state = input.state
        val current = state.session
        if (state.phase == LockdownPhase.ACTIVE && current != null && !isExpired(input, current)) {
            // A running session is never shortened by schedule edits, deletions or disabling.
            return LockdownTarget.Active(current, continuing = true)
        }

        val activeCandidates = ScheduleCalculator.activeWindows(input.schedules, input.now, input.zone)
            .filterNot { it.occurrenceKey in state.skippedOccurrences }
            .filterNot { current != null && it.occurrenceKey == current.occurrenceKey }

        activeCandidates
            .filter { it.autoStart || it.occurrenceKey in state.startedOccurrences }
            .maxByOrNull { it.end }
            ?.let { return LockdownTarget.Active(it, continuing = false) }

        if (input.countdownMinutes > 0) {
            nextAutoStartWindow(input)
                ?.takeIf { !input.now.isBefore(countdownStart(input, it)) }
                ?.let { return LockdownTarget.Countdown(it) }
        }

        activeCandidates
            .firstOrNull { !it.autoStart }
            ?.let { return LockdownTarget.AwaitingStart(it) }

        return LockdownTarget.Idle
    }

    /**
     * The next instant at which the decision may change, or null when nothing is scheduled.
     * The engine sets a single alarm for this instant and re-runs reconciliation when it fires.
     */
    fun nextWakeUp(input: DecisionInput, target: LockdownTarget): Instant? {
        val candidates = mutableListOf<Instant>()
        when (target) {
            is LockdownTarget.Active -> candidates += input.now.plus(remaining(input, target.window))
            is LockdownTarget.AwaitingStart -> candidates += target.window.end
            is LockdownTarget.Countdown, LockdownTarget.Idle -> Unit
        }
        ScheduleCalculator.upcomingWindows(input.schedules, input.now, input.zone)
            .filterNot { it.occurrenceKey in input.state.skippedOccurrences }
            .take(UPCOMING_WINDOWS_CONSIDERED)
            .forEach { window ->
                candidates += window.start
                if (window.autoStart && input.countdownMinutes > 0) candidates += countdownStart(input, window)
                if (input.reminderMinutes > 0) candidates += window.start.minus(Duration.ofMinutes(input.reminderMinutes.toLong()))
            }
        return candidates.filter { it.isAfter(input.now) }.minOrNull()
    }

    /** Upcoming occurrence that should get a "starts soon" reminder now, if any. */
    fun reminderDue(input: DecisionInput): SessionWindow? {
        if (input.reminderMinutes <= 0) return null
        val lead = Duration.ofMinutes(input.reminderMinutes.toLong())
        return ScheduleCalculator.upcomingWindows(input.schedules, input.now, input.zone)
            .firstOrNull { window ->
                window.occurrenceKey !in input.state.remindedOccurrences &&
                    window.occurrenceKey !in input.state.skippedOccurrences &&
                    !input.now.isBefore(window.start.minus(lead))
            }
    }

    fun remaining(input: DecisionInput, window: SessionWindow): Duration {
        val checkpoint = input.state.checkpoint.takeIf { input.state.session?.occurrenceKey == window.occurrenceKey }
        return SessionClock.remaining(window, checkpoint, input.now, input.elapsedRealtimeMs, input.bootCount)
    }

    private fun isExpired(input: DecisionInput, session: SessionWindow): Boolean =
        SessionClock.isExpired(session, input.state.checkpoint, input.now, input.elapsedRealtimeMs, input.bootCount)

    private fun nextAutoStartWindow(input: DecisionInput): SessionWindow? =
        ScheduleCalculator.upcomingWindows(input.schedules, input.now, input.zone)
            .firstOrNull { it.autoStart && it.occurrenceKey !in input.state.skippedOccurrences }

    private fun countdownStart(input: DecisionInput, window: SessionWindow): Instant =
        window.start.minus(Duration.ofMinutes(input.countdownMinutes.toLong()))

    /** Enough to cover every distinct wake-up reason for the next few sessions. */
    private const val UPCOMING_WINDOWS_CONSIDERED = 3
}
