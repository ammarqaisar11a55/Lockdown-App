package com.example.focuslock.data

import com.example.focuslock.data.local.daysFromMask
import com.example.focuslock.data.local.maskFromDays
import com.example.focuslock.data.local.toDomain
import com.example.focuslock.data.local.toEntity
import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.domain.model.LockdownPhase
import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.MonotonicCheckpoint
import com.example.focuslock.domain.model.RepeatRule
import com.example.focuslock.domain.model.SessionWindow
import com.example.focuslock.testing.WEDNESDAY
import com.example.focuslock.testing.at
import com.example.focuslock.testing.schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class MappersTest {

    @Test
    fun `day mask round-trips`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        assertEquals(days, daysFromMask(maskFromDays(days)))
        assertEquals(DayOfWeek.entries.toSet(), daysFromMask(maskFromDays(DayOfWeek.entries.toSet())))
    }

    @Test
    fun `schedules round-trip for every repeat rule`() {
        listOf(
            RepeatRule.Daily,
            RepeatRule.Once(WEDNESDAY),
            RepeatRule.Weekly(RepeatRule.Weekly.WEEKDAYS),
            RepeatRule.Weekly(setOf(DayOfWeek.SATURDAY)),
        ).forEach { rule ->
            val original = schedule(start = "22:15", end = "06:45", repeat = rule, strict = true, autoStart = false)
            assertEquals(original, original.toEntity(updatedAt = 1).toDomain())
        }
    }

    @Test
    fun `corrupt schedule rows are dropped`() {
        val entity = schedule().toEntity(1)
        assertNull(entity.copy(repeatType = "HOURLY").toDomain())
        assertNull(entity.copy(repeatType = "ONCE", onceDate = "not-a-date").toDomain())
        assertNull(entity.copy(startMinuteOfDay = 5000).toDomain())
    }

    @Test
    fun `lockdown state round-trips`() {
        val state = LockdownState(
            phase = LockdownPhase.ACTIVE,
            session = SessionWindow("s1", "Study", at(WEDNESDAY, "08:00"), at(WEDNESDAY, "12:00"), strictMode = true),
            zoneId = "UTC",
            historyId = 7,
            enforcement = EnforcementLevel.DEVICE_OWNER,
            checkpoint = MonotonicCheckpoint(3, 1000, 2000),
            appliedRestrictions = setOf("no_safe_boot", "no_config_date_time"),
            skippedOccurrences = setOf("a@1"),
            startedOccurrences = setOf("b@2"),
            remindedOccurrences = setOf("c@3", "d@4"),
        )
        assertEquals(state, state.toEntity(updatedAt = 0).toDomain())
    }

    @Test
    fun `active phase without a session is repaired to idle`() {
        val corrupt = LockdownState().toEntity(0).copy(phase = LockdownPhase.ACTIVE.name)
        assertEquals(LockdownPhase.IDLE, corrupt.toDomain().phase)
        val unknown = LockdownState().toEntity(0).copy(phase = "EXPLODED")
        assertEquals(LockdownPhase.IDLE, unknown.toDomain().phase)
    }
}
