package com.example.focuslock.domain

import com.example.focuslock.domain.model.AppMode
import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.domain.model.LockdownPhase
import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.SessionHistory
import com.example.focuslock.domain.model.SessionStatus
import com.example.focuslock.domain.model.SessionWindow
import com.example.focuslock.domain.usecase.AppModeResolver
import com.example.focuslock.testing.WEDNESDAY
import com.example.focuslock.testing.at
import org.junit.Assert.assertEquals
import org.junit.Test

class AppModeResolverTest {
    private val window = SessionWindow("s", "S", at(WEDNESDAY, "08:00"), at(WEDNESDAY, "12:00"), false)
    private val completed = SessionHistory(
        1, "s", "S", window.start, window.start, window.end, window.end,
        SessionStatus.COMPLETED, false, EnforcementLevel.DEVICE_OWNER, 0, 0,
    )

    @Test
    fun `modes are resolved by priority`() {
        val now = at(WEDNESDAY, "12:10")
        assertEquals(
            AppMode.LOCKDOWN,
            AppModeResolver.resolve(false, LockdownState(LockdownPhase.ACTIVE, window), true, completed, now),
        )
        assertEquals(
            AppMode.COUNTDOWN,
            AppModeResolver.resolve(true, LockdownState(LockdownPhase.COUNTDOWN, window), true, null, now),
        )
        assertEquals(AppMode.COMPLETED, AppModeResolver.resolve(true, LockdownState(), true, completed, now))
        assertEquals(AppMode.UNPROVISIONED, AppModeResolver.resolve(false, LockdownState(), true, null, now))
        assertEquals(AppMode.SCHEDULED, AppModeResolver.resolve(true, LockdownState(), true, null, now))
        assertEquals(AppMode.NORMAL, AppModeResolver.resolve(true, LockdownState(), false, null, now))
    }

    @Test
    fun `completed mode expires`() {
        val later = at(WEDNESDAY, "13:00")
        assertEquals(AppMode.SCHEDULED, AppModeResolver.resolve(true, LockdownState(), true, completed, later))
    }
}
