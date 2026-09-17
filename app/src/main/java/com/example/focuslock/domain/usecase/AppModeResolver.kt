package com.example.focuslock.domain.usecase

import com.example.focuslock.domain.model.AppMode
import com.example.focuslock.domain.model.LockdownPhase
import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.SessionHistory
import com.example.focuslock.domain.model.SessionStatus
import java.time.Duration
import java.time.Instant

object AppModeResolver {
    /** How long the dashboard celebrates a just-finished session. */
    val COMPLETED_DISPLAY_WINDOW: Duration = Duration.ofMinutes(30)

    fun resolve(
        isDeviceOwner: Boolean,
        state: LockdownState,
        hasUpcomingSession: Boolean,
        lastSession: SessionHistory?,
        now: Instant,
    ): AppMode {
        val recentlyCompleted = lastSession?.status == SessionStatus.COMPLETED &&
            lastSession.actualEnd?.let { Duration.between(it, now) <= COMPLETED_DISPLAY_WINDOW } == true
        return when {
            state.isLocked -> AppMode.LOCKDOWN
            state.phase == LockdownPhase.COUNTDOWN -> AppMode.COUNTDOWN
            recentlyCompleted -> AppMode.COMPLETED
            !isDeviceOwner -> AppMode.UNPROVISIONED
            hasUpcomingSession -> AppMode.SCHEDULED
            else -> AppMode.NORMAL
        }
    }
}
