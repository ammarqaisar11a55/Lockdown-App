package com.example.focuslock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM focus_schedules ORDER BY start_minute, created_at")
    fun observeAll(): Flow<List<FocusScheduleEntity>>

    @Query("SELECT * FROM focus_schedules ORDER BY start_minute, created_at")
    suspend fun getAll(): List<FocusScheduleEntity>

    @Query("SELECT * FROM focus_schedules WHERE id = :id")
    suspend fun getById(id: String): FocusScheduleEntity?

    @Upsert
    suspend fun upsert(entity: FocusScheduleEntity)

    @Query("DELETE FROM focus_schedules WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE focus_schedules SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("DELETE FROM focus_schedules")
    suspend fun deleteAll()
}

@Dao
interface AllowedApplicationDao {
    @Query("SELECT * FROM allowed_applications ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<AllowedApplicationEntity>>

    @Query("SELECT package_name FROM allowed_applications")
    suspend fun getPackageNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<AllowedApplicationEntity>)

    @Query("DELETE FROM allowed_applications")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<AllowedApplicationEntity>) {
        deleteAll()
        insertAll(entities)
    }
}

@Dao
interface LockdownStateDao {
    @Query("SELECT * FROM lockdown_state WHERE id = ${LockdownStateEntity.SINGLETON_ID}")
    fun observe(): Flow<LockdownStateEntity?>

    @Query("SELECT * FROM lockdown_state WHERE id = ${LockdownStateEntity.SINGLETON_ID}")
    suspend fun get(): LockdownStateEntity?

    @Upsert
    suspend fun upsert(entity: LockdownStateEntity)
}

@Dao
interface SessionHistoryDao {
    @Insert
    suspend fun insert(entity: SessionHistoryEntity): Long

    @Query("SELECT * FROM session_history WHERE id = :id")
    suspend fun getById(id: Long): SessionHistoryEntity?

    @Query("UPDATE session_history SET actual_end = :endedAt, status = :status WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long, status: String)

    @Query(
        "UPDATE session_history SET status = :interrupted, actual_end = :endedAt " +
            "WHERE status = :inProgress AND (:currentId IS NULL OR id != :currentId)",
    )
    suspend fun closeOrphans(currentId: Long?, endedAt: Long, inProgress: String, interrupted: String): Int

    @Query("UPDATE session_history SET exit_attempts = exit_attempts + 1 WHERE id = :id")
    suspend fun incrementExitAttempts(id: Long)

    @Query("UPDATE session_history SET recovery_count = recovery_count + 1 WHERE id = :id")
    suspend fun incrementRecoveryCount(id: Long)

    @Query("SELECT * FROM session_history ORDER BY started_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionHistoryEntity>>

    @Query("SELECT * FROM session_history WHERE expected_end > :since ORDER BY started_at DESC")
    fun observeEndingAfter(since: Long): Flow<List<SessionHistoryEntity>>

    @Query("DELETE FROM session_history")
    suspend fun deleteAll()
}
