package com.example.focuslock.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.focuslock.R
import com.example.focuslock.core.time.DurationFormatter
import com.example.focuslock.domain.model.SessionWindow
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Display model for a concrete session occurrence. */
data class SessionItem(
    val occurrenceKey: String,
    val name: String,
    val daysFromToday: Long,
    val dateText: String,
    val timeRange: String,
    val strictMode: Boolean,
    val autoStart: Boolean,
)

fun SessionWindow.toSessionItem(zone: ZoneId, locale: Locale = Locale.getDefault()): SessionItem {
    val startDate = start.atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    return SessionItem(
        occurrenceKey = occurrenceKey,
        name = name,
        daysFromToday = ChronoUnit.DAYS.between(today, startDate),
        dateText = DateTimeFormatter.ofPattern("EEE, MMM d", locale).format(startDate),
        timeRange = "${DurationFormatter.time(start, zone, locale)} → ${DurationFormatter.time(end, zone, locale)}",
        strictMode = strictMode,
        autoStart = autoStart,
    )
}

@Composable
fun SessionItem.dayLabel(): String = when (daysFromToday) {
    0L -> stringResource(R.string.day_today)
    1L -> stringResource(R.string.day_tomorrow)
    else -> dateText
}
