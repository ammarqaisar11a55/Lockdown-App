package com.example.focuslock.data.repository

import com.example.focuslock.core.device.InstalledAppsProvider
import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.data.local.AllowedApplicationDao
import com.example.focuslock.data.local.AllowedApplicationEntity
import com.example.focuslock.data.local.LockdownStateDao
import com.example.focuslock.data.local.ScheduleDao
import com.example.focuslock.data.local.SessionHistoryDao
import com.example.focuslock.data.local.SessionHistoryEntity
import com.example.focuslock.data.local.toDomain
import com.example.focuslock.data.local.toEntity
import com.example.focuslock.domain.model.AllowedApplication
import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.InstalledApplication
import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.SessionHistory
import com.example.focuslock.domain.model.SessionStatus
import com.example.focuslock.domain.model.SessionWindow
import com.example.focuslock.domain.repository.AllowedAppsRepository
import com.example.focuslock.domain.repository.HistoryRepository
import com.example.focuslock.domain.repository.LockdownStateRepository
import com.example.focuslock.domain.repository.ScheduleRepository
import com.example.focuslock.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

class RoomScheduleRepository @Inject constructor(
    private val dao: ScheduleDao,
    private val timeSource: TimeSource,
) : ScheduleRepository {
    override fun observeSchedules(): Flow<List<FocusSchedule>> =
        dao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }

    override suspend fun getSchedules(): List<FocusSchedule> = dao.getAll().mapNotNull { it.toDomain() }

    override suspend fun getSchedule(id: String): FocusSchedule? = dao.getById(id)?.toDomain()

    override suspend fun save(schedule: FocusSchedule) =
        dao.upsert(schedule.toEntity(updatedAt = timeSource.now().toEpochMilli()))

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun setEnabled(id: String, enabled: Boolean) =
        dao.setEnabled(id, enabled, timeSource.now().toEpochMilli())

    override suspend fun deleteAll() = dao.deleteAll()
}

class RoomAllowedAppsRepository @Inject constructor(
    private val dao: AllowedApplicationDao,
    private val installedApps: InstalledAppsProvider,
    private val timeSource: TimeSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AllowedAppsRepository {
    override fun observeAllowed(): Flow<List<AllowedApplication>> =
        dao.observeAll().map { list -> list.map { AllowedApplication(it.packageName, it.label) } }

    override suspend fun getAllowedPackages(): Set<String> =
        withContext(ioDispatcher) {
            // Uninstalled packages are dropped so the lock task allowlist stays valid.
            dao.getPackageNames().filterTo(mutableSetOf(), installedApps::isLaunchable)
        }

    override suspend fun setAllowed(applications: List<AllowedApplication>) {
        val now = timeSource.now().toEpochMilli()
        dao.replaceAll(
            applications.distinctBy { it.packageName }
                .map { AllowedApplicationEntity(it.packageName, it.label.take(MAX_LABEL_LENGTH), now) },
        )
    }

    override suspend fun getLaunchableApplications(): List<InstalledApplication> =
        withContext(ioDispatcher) { installedApps.launchableApplications() }

    override suspend fun deleteAll() = dao.deleteAll()

    private companion object {
        const val MAX_LABEL_LENGTH = 100
    }
}

class RoomLockdownStateRepository @Inject constructor(
    private val dao: LockdownStateDao,
    private val timeSource: TimeSource,
) : LockdownStateRepository {
    override fun observeState(): Flow<LockdownState> =
        dao.observe().map { it?.toDomain() ?: LockdownState() }.distinctUntilChanged()

    override suspend fun getState(): LockdownState = dao.get()?.toDomain() ?: LockdownState()

    override suspend fun saveState(state: LockdownState) =
        dao.upsert(state.toEntity(updatedAt = timeSource.now().toEpochMilli()))
}

class RoomHistoryRepository @Inject constructor(
    private val dao: SessionHistoryDao,
) : HistoryRepository {
    override suspend fun startSession(window: SessionWindow, startedAt: Instant, enforcement: EnforcementLevel): Long =
        dao.insert(
            SessionHistoryEntity(
                scheduleId = window.scheduleId,
                name = window.name,
                scheduledStart = window.start.toEpochMilli(),
                startedAt = maxOf(startedAt, window.start).toEpochMilli(),
                expectedEnd = window.end.toEpochMilli(),
                actualEnd = null,
                status = SessionStatus.IN_PROGRESS.name,
                strictMode = window.strictMode,
                enforcement = enforcement.name,
                recoveryCount = 0,
                exitAttempts = 0,
            ),
        )

    override suspend fun finishSession(id: Long, endedAt: Instant, status: SessionStatus): SessionHistory? {
        dao.finish(id, endedAt.toEpochMilli(), status.name)
        return dao.getById(id)?.toDomain()
    }

    override suspend fun closeOrphanedSessions(currentId: Long?, endedAt: Instant) {
        dao.closeOrphans(
            currentId = currentId,
            endedAt = endedAt.toEpochMilli(),
            inProgress = SessionStatus.IN_PROGRESS.name,
            interrupted = SessionStatus.INTERRUPTED.name,
        )
    }

    override suspend fun incrementExitAttempts(id: Long) = dao.incrementExitAttempts(id)

    override suspend fun incrementRecoveryCount(id: Long) = dao.incrementRecoveryCount(id)

    override fun observeRecent(limit: Int): Flow<List<SessionHistory>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    override fun observeEndingAfter(since: Instant): Flow<List<SessionHistory>> =
        dao.observeEndingAfter(since.toEpochMilli()).map { list -> list.map { it.toDomain() } }

    override suspend fun deleteAll() = dao.deleteAll()
}
