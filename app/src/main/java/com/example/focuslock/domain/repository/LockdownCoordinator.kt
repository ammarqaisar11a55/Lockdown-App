package com.example.focuslock.domain.repository

import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.ReconcileTrigger
import java.time.Duration

/**
 * Entry point for everything that can change lockdown enforcement.
 *
 * All operations are serialized and persisted before device policy is changed.
 */
interface LockdownCoordinator {
    suspend fun reconcile(trigger: ReconcileTrigger): LockdownState

    /** Starts an ad-hoc session immediately. Fails if a session is already active. */
    suspend fun startFocusNow(name: String, duration: Duration, strictMode: Boolean): Boolean

    /**
     * Skips an upcoming occurrence (for example during its pre-lockdown countdown).
     * Returns false when the occurrence is unknown or has already started.
     */
    suspend fun cancelUpcomingSession(occurrenceKey: String): Boolean

    /** Starts a manual-start occurrence that is currently within its scheduled window. */
    suspend fun startPendingSession(occurrenceKey: String): Boolean

    suspend fun recordExitAttempt()
}
