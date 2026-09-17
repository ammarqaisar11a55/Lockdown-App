package com.example.focuslock.domain.model

/** Persisted lifecycle phase of the lockdown engine. */
enum class LockdownPhase {
    /** No session is running or imminent. */
    IDLE,

    /** A session starts soon; it can still be cancelled. */
    COUNTDOWN,

    /** A session is being enforced; it cannot be cancelled from the app. */
    ACTIVE,
}

/** How strongly the running session is enforced on this device. */
enum class EnforcementLevel {
    /** Device Owner lock task mode with user restrictions. */
    DEVICE_OWNER,

    /** Ordinary app: Android screen pinning, which the user can leave. */
    SCREEN_PINNING,
}

/**
 * Anchor for detecting wall-clock manipulation within a single boot.
 *
 * [remainingMs] was the time left when [elapsedRealtimeMs] was sampled during boot [bootCount].
 */
data class MonotonicCheckpoint(
    val bootCount: Int,
    val elapsedRealtimeMs: Long,
    val remainingMs: Long,
)

/**
 * The single source of truth for lockdown enforcement, persisted locally.
 *
 * The in-memory copy is never trusted on its own: it is reconciled against current time and the
 * actual device policy state by the policy reconciliation engine.
 */
data class LockdownState(
    val phase: LockdownPhase = LockdownPhase.IDLE,
    val session: SessionWindow? = null,
    val zoneId: String? = null,
    val historyId: Long? = null,
    val enforcement: EnforcementLevel? = null,
    val checkpoint: MonotonicCheckpoint? = null,
    /** User restrictions this app added (and therefore owns and must remove). */
    val appliedRestrictions: Set<String> = emptySet(),
    /** Occurrences cancelled during their pre-lockdown countdown. */
    val skippedOccurrences: Set<String> = emptySet(),
    /** Occurrences of manual-start schedules that the user chose to start. */
    val startedOccurrences: Set<String> = emptySet(),
    /** Occurrences for which the "starts soon" reminder was already posted. */
    val remindedOccurrences: Set<String> = emptySet(),
) {
    val isLocked: Boolean get() = phase == LockdownPhase.ACTIVE && session != null
}
