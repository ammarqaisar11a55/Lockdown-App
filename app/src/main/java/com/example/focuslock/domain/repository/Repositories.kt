package com.example.focuslock.domain.repository

import com.example.focuslock.domain.model.AllowedApplication
import com.example.focuslock.domain.model.AppSettings
import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.InstalledApplication
import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.SessionHistory
import com.example.focuslock.domain.model.SessionStatus
import com.example.focuslock.domain.model.SessionWindow
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ScheduleRepository {
    fun observeSchedules(): Flow<List<FocusSchedule>>
    suspend fun getSchedules(): List<FocusSchedule>
    suspend fun getSchedule(id: String): FocusSchedule?
    suspend fun save(schedule: FocusSchedule)
    suspend fun delete(id: String)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun deleteAll()
}

interface AllowedAppsRepository {
    fun observeAllowed(): Flow<List<AllowedApplication>>
    suspend fun getAllowedPackages(): Set<String>
    suspend fun setAllowed(applications: List<AllowedApplication>)
    suspend fun getLaunchableApplications(): List<InstalledApplication>
    suspend fun deleteAll()
}

interface LockdownStateRepository {
    fun observeState(): Flow<LockdownState>
    suspend fun getState(): LockdownState
    suspend fun saveState(state: LockdownState)
}

interface HistoryRepository {
    suspend fun startSession(window: SessionWindow, startedAt: Instant, enforcement: EnforcementLevel): Long
    suspend fun finishSession(id: Long, endedAt: Instant, status: SessionStatus): SessionHistory?

    /** Marks sessions left IN_PROGRESS by a crash (other than [currentId]) as interrupted. */
    suspend fun closeOrphanedSessions(currentId: Long?, endedAt: Instant)
    suspend fun incrementExitAttempts(id: Long)
    suspend fun incrementRecoveryCount(id: Long)
    fun observeRecent(limit: Int): Flow<List<SessionHistory>>
    /** Sessions whose scheduled end is after [since] (i.e. that may overlap the period since then). */
    fun observeEndingAfter(since: Instant): Flow<List<SessionHistory>>
    suspend fun deleteAll()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun get(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
    suspend fun clear()
}
