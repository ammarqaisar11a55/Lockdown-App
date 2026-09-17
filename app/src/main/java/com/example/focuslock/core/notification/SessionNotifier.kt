package com.example.focuslock.core.notification

import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.domain.model.SessionWindow
import java.time.Duration

/** User-visible session notifications. Kept deliberately sparse. */
interface SessionNotifier {
    fun createChannels()
    fun showReminder(window: SessionWindow, leadTime: Duration)
    fun showCountdown(window: SessionWindow)
    fun clearCountdown()
    fun showAwaitingStart(window: SessionWindow)
    fun clearAwaitingStart()
    fun showActive(window: SessionWindow, enforcement: EnforcementLevel)
    fun clearActive()
    fun showCompleted(name: String, focused: Duration)
}
