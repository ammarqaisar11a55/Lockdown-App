package com.example.focuslock.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local database. Schemas are exported to app/schemas; every version bump must ship an explicit
 * Migration. Destructive migration is intentionally never enabled because it would silently
 * wipe an active lockdown state.
 */
@Database(
    entities = [
        FocusScheduleEntity::class,
        AllowedApplicationEntity::class,
        LockdownStateEntity::class,
        SessionHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FocusDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun allowedApplicationDao(): AllowedApplicationDao
    abstract fun lockdownStateDao(): LockdownStateDao
    abstract fun sessionHistoryDao(): SessionHistoryDao

    companion object {
        const val NAME = "focuslock.db"
    }
}
