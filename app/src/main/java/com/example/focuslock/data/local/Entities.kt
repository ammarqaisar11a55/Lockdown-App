package com.example.focuslock.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "focus_schedules")
data class FocusScheduleEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "start_minute") val startMinuteOfDay: Int,
    @ColumnInfo(name = "end_minute") val endMinuteOfDay: Int,
    /** ONCE, DAILY or WEEKLY. */
    @ColumnInfo(name = "repeat_type") val repeatType: String,
    /** ISO-8601 date for ONCE schedules. */
    @ColumnInfo(name = "once_date") val onceDate: String?,
    /** Bit (dayOfWeek.value - 1) set for each WEEKLY day. */
    @ColumnInfo(name = "days_mask") val daysMask: Int,
    @ColumnInfo(name = "strict_mode") val strictMode: Boolean,
    val enabled: Boolean,
    @ColumnInfo(name = "auto_start") val autoStart: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "allowed_applications")
data class AllowedApplicationEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    val label: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/** Single-row table holding the persisted lockdown engine state. */
@Entity(tableName = "lockdown_state")
data class LockdownStateEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val phase: String,
    @ColumnInfo(name = "schedule_id") val scheduleId: String?,
    @ColumnInfo(name = "session_name") val sessionName: String?,
    @ColumnInfo(name = "start_epoch_ms") val startEpochMs: Long?,
    @ColumnInfo(name = "end_epoch_ms") val endEpochMs: Long?,
    @ColumnInfo(name = "strict_mode") val strictMode: Boolean,
    @ColumnInfo(name = "auto_start") val autoStart: Boolean,
    @ColumnInfo(name = "zone_id") val zoneId: String?,
    @ColumnInfo(name = "history_id") val historyId: Long?,
    val enforcement: String?,
    @ColumnInfo(name = "checkpoint_boot_count") val checkpointBootCount: Int?,
    @ColumnInfo(name = "checkpoint_elapsed_ms") val checkpointElapsedMs: Long?,
    @ColumnInfo(name = "checkpoint_remaining_ms") val checkpointRemainingMs: Long?,
    /** Newline-separated sets. Values never contain newlines (package names, restriction keys). */
    @ColumnInfo(name = "applied_restrictions") val appliedRestrictions: String,
    @ColumnInfo(name = "skipped_occurrences") val skippedOccurrences: String,
    @ColumnInfo(name = "started_occurrences") val startedOccurrences: String,
    @ColumnInfo(name = "reminded_occurrences") val remindedOccurrences: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

@Entity(
    tableName = "session_history",
    indices = [Index(value = ["started_at"]), Index(value = ["status"])],
)
data class SessionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "schedule_id") val scheduleId: String?,
    val name: String,
    @ColumnInfo(name = "scheduled_start") val scheduledStart: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "expected_end") val expectedEnd: Long,
    @ColumnInfo(name = "actual_end") val actualEnd: Long?,
    val status: String,
    @ColumnInfo(name = "strict_mode") val strictMode: Boolean,
    val enforcement: String,
    @ColumnInfo(name = "recovery_count") val recoveryCount: Int,
    @ColumnInfo(name = "exit_attempts") val exitAttempts: Int,
)
