package com.example.focuslock.domain.model

import java.time.Duration
import java.time.Instant

/**
 * One concrete lockdown occurrence with absolute start and end instants.
 *
 * [scheduleId] is null for ad-hoc "focus now" sessions.
 */
data class SessionWindow(
    val scheduleId: String?,
    val name: String,
    val start: Instant,
    val end: Instant,
    val strictMode: Boolean,
    val autoStart: Boolean = true,
) {
    init {
        require(end.isAfter(start)) { "Session end must be after its start" }
    }

    /** Stable identifier of this occurrence; used to remember skipped/started/reminded occurrences. */
    val occurrenceKey: String get() = "${scheduleId ?: AD_HOC_ID}$KEY_SEPARATOR${start.toEpochMilli()}"

    val duration: Duration get() = Duration.between(start, end)

    operator fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)

    companion object {
        const val AD_HOC_ID = "adhoc"
        const val KEY_SEPARATOR = '@'

        /** Extracts the start instant encoded in an occurrence key, or null if malformed. */
        fun startOfOccurrenceKey(key: String): Instant? =
            key.substringAfterLast(KEY_SEPARATOR, missingDelimiterValue = "")
                .toLongOrNull()
                ?.let(Instant::ofEpochMilli)
    }
}
