package com.example.focuslock.core.time

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** Abstraction over every clock the lockdown engine reads, so time logic is testable. */
interface TimeSource {
    fun now(): Instant
    fun zone(): ZoneId

    /** Monotonic milliseconds since boot, unaffected by wall-clock changes. */
    fun elapsedRealtimeMs(): Long

    /** Number of boots of this device, or [UNKNOWN_BOOT_COUNT] if unavailable. */
    fun bootCount(): Int

    companion object {
        const val UNKNOWN_BOOT_COUNT = -1
    }
}

class SystemTimeSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : TimeSource {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun bootCount(): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, TimeSource.UNKNOWN_BOOT_COUNT)
}
