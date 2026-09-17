package com.example.focuslock.feature.schedule

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.focuslock.R
import com.example.focuslock.core.scheduling.ScheduleValidationError
import com.example.focuslock.domain.model.RepeatRule
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

fun formatTime(time: LocalTime, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time)

/** "8:00" / "20:00": time without the AM/PM marker, for compact labels. */
fun formatShortTime(time: LocalTime, locale: Locale = Locale.getDefault()): String =
    formatTime(time, locale).replace(Regex("[\\s\\u00a0\\u202f]?[AaPp]\\.?\\s?[Mm]\\.?"), "").trim()

fun formatDays(days: Set<DayOfWeek>, locale: Locale = Locale.getDefault()): String =
    DayOfWeek.entries.filter { it in days }.joinToString(" ") { it.getDisplayName(TextStyle.SHORT, locale) }

@Composable
fun RepeatRule.label(): String = when (this) {
    is RepeatRule.Once -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(date)
    RepeatRule.Daily -> stringResource(R.string.repeat_daily)
    is RepeatRule.Weekly -> if (isWeekdays) stringResource(R.string.repeat_weekdays) else formatDays(days)
}

@Composable
fun ScheduleValidationError.message(): String = when (this) {
    ScheduleValidationError.BlankName -> stringResource(R.string.error_blank_name)
    ScheduleValidationError.NameTooLong -> stringResource(R.string.error_name_too_long)
    ScheduleValidationError.ZeroLength -> stringResource(R.string.error_zero_length)
    ScheduleValidationError.NoDaysSelected -> stringResource(R.string.error_no_days)
    ScheduleValidationError.InThePast -> stringResource(R.string.error_in_past)
    is ScheduleValidationError.Overlaps -> stringResource(R.string.error_overlap, conflictingName)
}
