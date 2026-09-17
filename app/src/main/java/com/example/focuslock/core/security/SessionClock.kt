package com.example.focuslock.core.security

import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.domain.model.MonotonicCheckpoint
import com.example.focuslock.domain.model.SessionWindow
import java.time.Duration
import java.time.Instant

/**
 * Decides whether an active session has ended, resisting wall-clock manipulation.
 *
 * A session ends only when BOTH the wall clock has passed the scheduled end AND, within the same
 * boot, the monotonic clock confirms that the remaining time has actually elapsed. Moving the
 * clock forward therefore cannot end a session early. Across a reboot the monotonic clock resets,
 * so the wall clock is trusted; Device Owner mode additionally blocks manual time changes.
 */
object SessionClock {

    fun checkpoint(session: SessionWindow, now: Instant, elapsedRealtimeMs: Long, bootCount: Int) =
        MonotonicCheckpoint(
            bootCount = bootCount,
            elapsedRealtimeMs = elapsedRealtimeMs,
            remainingMs = Duration.between(now, session.end).toMillis().coerceAtLeast(0),
        )

    /** Re-anchors the checkpoint when the device has rebooted since it was taken. */
    fun refreshForBoot(
        checkpoint: MonotonicCheckpoint?,
        session: SessionWindow,
        now: Instant,
        elapsedRealtimeMs: Long,
        bootCount: Int,
    ): MonotonicCheckpoint {
        val sameBoot = checkpoint != null &&
            bootCount != TimeSource.UNKNOWN_BOOT_COUNT &&
            checkpoint.bootCount == bootCount &&
            elapsedRealtimeMs >= checkpoint.elapsedRealtimeMs
        return if (sameBoot) checkpoint!! else checkpoint(session, now, elapsedRealtimeMs, bootCount)
    }

    fun remaining(
        session: SessionWindow,
        checkpoint: MonotonicCheckpoint?,
        now: Instant,
        elapsedRealtimeMs: Long,
        bootCount: Int,
    ): Duration {
        val wallRemaining = Duration.between(now, session.end).coerceAtLeastZero()
        val monotonicRemaining = monotonicRemaining(checkpoint, elapsedRealtimeMs, bootCount)
            ?: return wallRemaining
        return maxOf(wallRemaining, monotonicRemaining)
    }

    fun isExpired(
        session: SessionWindow,
        checkpoint: MonotonicCheckpoint?,
        now: Instant,
        elapsedRealtimeMs: Long,
        bootCount: Int,
    ): Boolean = remaining(session, checkpoint, now, elapsedRealtimeMs, bootCount).isZero

    private fun monotonicRemaining(checkpoint: MonotonicCheckpoint?, elapsedRealtimeMs: Long, bootCount: Int): Duration? {
        if (checkpoint == null || bootCount == TimeSource.UNKNOWN_BOOT_COUNT) return null
        if (checkpoint.bootCount != bootCount || elapsedRealtimeMs < checkpoint.elapsedRealtimeMs) return null
        val elapsedSinceCheckpoint = elapsedRealtimeMs - checkpoint.elapsedRealtimeMs
        return Duration.ofMillis(checkpoint.remainingMs - elapsedSinceCheckpoint).coerceAtLeastZero()
    }

    private fun Duration.coerceAtLeastZero(): Duration = if (isNegative) Duration.ZERO else this
}
