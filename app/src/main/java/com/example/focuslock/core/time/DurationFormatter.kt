package com.example.focuslock.core.time

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DurationFormatter {
    private const val SECONDS_PER_MINUTE = 60L
    private const val MINUTES_PER_HOUR = 60L

    // Duration.toMinutesPart()/toSecondsPart() need API 31; minSdk is 28, so compute manually.
    private fun Duration.minutesPart(): Long = toMinutes() % MINUTES_PER_HOUR
    private fun Duration.secondsPart(): Long = seconds % SECONDS_PER_MINUTE

    /** "02:47:18" */
    fun clock(duration: Duration): String {
        val safe = if (duration.isNegative) Duration.ZERO else duration
        return String.format(
            Locale.ROOT,
            "%02d:%02d:%02d",
            safe.toHours(),
            safe.minutesPart(),
            safe.secondsPart(),
        )
    }

    /** "4h 12m", "45m", "0m" */
    fun short(duration: Duration): String {
        val totalMinutes = (if (duration.isNegative) Duration.ZERO else duration).toMinutes()
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        return if (hours > 0) String.format(Locale.ROOT, "%dh %02dm", hours, minutes) else "${minutes}m"
    }

    /** Spoken form for screen readers: "2 hours 47 minutes". */
    fun spoken(duration: Duration): String {
        val safe = if (duration.isNegative) Duration.ZERO else duration
        val hours = safe.toHours()
        val minutes = safe.minutesPart()
        val parts = buildList {
            if (hours > 0) add(if (hours == 1L) "1 hour" else "$hours hours")
            if (minutes > 0 || hours == 0L) add(if (minutes == 1L) "1 minute" else "$minutes minutes")
        }
        val seconds = safe.seconds
        return if (seconds < SECONDS_PER_MINUTE) "less than a minute" else parts.joinToString(" ")
    }

    fun time(instant: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(zone))

    fun date(instant: Instant, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEEE, MMMM d", locale).format(instant.atZone(zone))
}
