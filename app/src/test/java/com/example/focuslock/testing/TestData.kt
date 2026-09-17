package com.example.focuslock.testing

import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.RepeatRule
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

val UTC: ZoneId = ZoneId.of("UTC")
val NEW_YORK: ZoneId = ZoneId.of("America/New_York")

/** 2024-09-18 is a Wednesday. */
val WEDNESDAY: LocalDate = LocalDate.of(2024, 9, 18)

fun schedule(
    id: String = "s1",
    name: String = "Study",
    start: String = "08:00",
    end: String = "12:00",
    repeat: RepeatRule = RepeatRule.Daily,
    strict: Boolean = false,
    enabled: Boolean = true,
    autoStart: Boolean = true,
) = FocusSchedule(
    id = id,
    name = name,
    startTime = LocalTime.parse(start),
    endTime = LocalTime.parse(end),
    repeat = repeat,
    strictMode = strict,
    enabled = enabled,
    autoStart = autoStart,
    createdAt = 0,
)

fun at(date: LocalDate, time: String, zone: ZoneId = UTC) =
    ZonedDateTime.of(date, LocalTime.parse(time), zone).toInstant()
