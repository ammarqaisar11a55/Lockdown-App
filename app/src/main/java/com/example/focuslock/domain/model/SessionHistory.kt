package com.example.focuslock.domain.model

import java.time.Duration
import java.time.Instant

enum class SessionStatus { IN_PROGRESS, COMPLETED, INTERRUPTED }

data class SessionHistory(
    val id: Long,
    val scheduleId: String?,
    val name: String,
    val scheduledStart: Instant,
    val startedAt: Instant,
    val expectedEnd: Instant,
    val actualEnd: Instant?,
    val status: SessionStatus,
    val strictMode: Boolean,
    val enforcement: EnforcementLevel,
    val recoveryCount: Int,
    val exitAttempts: Int,
) {
    /** Time actually spent locked, never counting beyond the scheduled end. */
    fun focusedDuration(now: Instant): Duration {
        val end = minOf(actualEnd ?: now, expectedEnd)
        return if (end.isAfter(startedAt)) Duration.between(startedAt, end) else Duration.ZERO
    }
}
