package com.example.focuslock.data.local

import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.LockdownPhase
import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.MonotonicCheckpoint
import com.example.focuslock.domain.model.RepeatRule
import com.example.focuslock.domain.model.SessionHistory
import com.example.focuslock.domain.model.SessionStatus
import com.example.focuslock.domain.model.SessionWindow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private const val MINUTES_PER_HOUR = 60
private const val SET_SEPARATOR = "\n"
private const val REPEAT_ONCE = "ONCE"
private const val REPEAT_DAILY = "DAILY"
private const val REPEAT_WEEKLY = "WEEKLY"

internal fun FocusScheduleEntity.toDomain(): FocusSchedule? {
    val repeat = when (repeatType) {
        REPEAT_ONCE -> onceDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let(RepeatRule::Once)
        REPEAT_DAILY -> RepeatRule.Daily
        REPEAT_WEEKLY -> RepeatRule.Weekly(daysFromMask(daysMask))
        else -> null
    } ?: return null
    return FocusSchedule(
        id = id,
        name = name,
        startTime = minuteToTime(startMinuteOfDay) ?: return null,
        endTime = minuteToTime(endMinuteOfDay) ?: return null,
        repeat = repeat,
        strictMode = strictMode,
        enabled = enabled,
        autoStart = autoStart,
        createdAt = createdAt,
    )
}

internal fun FocusSchedule.toEntity(updatedAt: Long): FocusScheduleEntity {
    val (type, date, mask) = when (val rule = repeat) {
        is RepeatRule.Once -> Triple(REPEAT_ONCE, rule.date.toString(), 0)
        RepeatRule.Daily -> Triple(REPEAT_DAILY, null, 0)
        is RepeatRule.Weekly -> Triple(REPEAT_WEEKLY, null, maskFromDays(rule.days))
    }
    return FocusScheduleEntity(
        id = id,
        name = name,
        startMinuteOfDay = startTime.hour * MINUTES_PER_HOUR + startTime.minute,
        endMinuteOfDay = endTime.hour * MINUTES_PER_HOUR + endTime.minute,
        repeatType = type,
        onceDate = date,
        daysMask = mask,
        strictMode = strictMode,
        enabled = enabled,
        autoStart = autoStart,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

internal fun daysFromMask(mask: Int): Set<DayOfWeek> =
    DayOfWeek.entries.filterTo(mutableSetOf()) { mask and (1 shl (it.value - 1)) != 0 }

internal fun maskFromDays(days: Set<DayOfWeek>): Int =
    days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

private fun minuteToTime(minute: Int): LocalTime? =
    runCatching { LocalTime.of(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR) }.getOrNull()

internal fun LockdownStateEntity.toDomain(): LockdownState {
    val parsedPhase = enumValueOrNull<LockdownPhase>(phase) ?: LockdownPhase.IDLE
    val session = if (startEpochMs != null && endEpochMs != null && endEpochMs > startEpochMs) {
        SessionWindow(
            scheduleId = scheduleId,
            name = sessionName.orEmpty(),
            start = Instant.ofEpochMilli(startEpochMs),
            end = Instant.ofEpochMilli(endEpochMs),
            strictMode = strictMode,
            autoStart = autoStart,
        )
    } else {
        null
    }
    val checkpoint = if (checkpointBootCount != null && checkpointElapsedMs != null && checkpointRemainingMs != null) {
        MonotonicCheckpoint(checkpointBootCount, checkpointElapsedMs, checkpointRemainingMs)
    } else {
        null
    }
    return LockdownState(
        // A phase without a session is corrupt; fall back to IDLE and let reconciliation repair it.
        phase = if (session == null) LockdownPhase.IDLE else parsedPhase,
        session = session,
        zoneId = zoneId,
        historyId = historyId,
        enforcement = enforcement?.let { enumValueOrNull<EnforcementLevel>(it) },
        checkpoint = checkpoint,
        appliedRestrictions = appliedRestrictions.decodeSet(),
        skippedOccurrences = skippedOccurrences.decodeSet(),
        startedOccurrences = startedOccurrences.decodeSet(),
        remindedOccurrences = remindedOccurrences.decodeSet(),
    )
}

internal fun LockdownState.toEntity(updatedAt: Long) = LockdownStateEntity(
    phase = phase.name,
    scheduleId = session?.scheduleId,
    sessionName = session?.name,
    startEpochMs = session?.start?.toEpochMilli(),
    endEpochMs = session?.end?.toEpochMilli(),
    strictMode = session?.strictMode ?: false,
    autoStart = session?.autoStart ?: true,
    zoneId = zoneId,
    historyId = historyId,
    enforcement = enforcement?.name,
    checkpointBootCount = checkpoint?.bootCount,
    checkpointElapsedMs = checkpoint?.elapsedRealtimeMs,
    checkpointRemainingMs = checkpoint?.remainingMs,
    appliedRestrictions = appliedRestrictions.encodeSet(),
    skippedOccurrences = skippedOccurrences.encodeSet(),
    startedOccurrences = startedOccurrences.encodeSet(),
    remindedOccurrences = remindedOccurrences.encodeSet(),
    updatedAt = updatedAt,
)

internal fun SessionHistoryEntity.toDomain() = SessionHistory(
    id = id,
    scheduleId = scheduleId,
    name = name,
    scheduledStart = Instant.ofEpochMilli(scheduledStart),
    startedAt = Instant.ofEpochMilli(startedAt),
    expectedEnd = Instant.ofEpochMilli(expectedEnd),
    actualEnd = actualEnd?.let(Instant::ofEpochMilli),
    status = enumValueOrNull<SessionStatus>(status) ?: SessionStatus.INTERRUPTED,
    strictMode = strictMode,
    enforcement = enumValueOrNull<EnforcementLevel>(enforcement) ?: EnforcementLevel.SCREEN_PINNING,
    recoveryCount = recoveryCount,
    exitAttempts = exitAttempts,
)

private fun Set<String>.encodeSet(): String =
    filter { it.isNotBlank() && !it.contains(SET_SEPARATOR) }.sorted().joinToString(SET_SEPARATOR)

private fun String.decodeSet(): Set<String> =
    split(SET_SEPARATOR).filterTo(mutableSetOf()) { it.isNotBlank() }

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
