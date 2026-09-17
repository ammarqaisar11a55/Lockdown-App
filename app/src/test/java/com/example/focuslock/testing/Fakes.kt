package com.example.focuslock.testing

import com.example.focuslock.core.common.FocusLogger
import com.example.focuslock.core.device.AppliedPolicy
import com.example.focuslock.core.device.LockdownLauncher
import com.example.focuslock.core.device.LockdownPolicy
import com.example.focuslock.core.device.PolicyEnforcer
import com.example.focuslock.core.device.RestrictionPolicy
import com.example.focuslock.core.notification.SessionNotifier
import com.example.focuslock.core.scheduling.TransitionScheduler
import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.domain.model.AllowedApplication
import com.example.focuslock.domain.model.AppSettings
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
import com.example.focuslock.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class FakeTimeSource(
    var now: Instant,
    var zone: ZoneId = UTC,
    var elapsedMs: Long = 1_000_000L,
    var boot: Int = 1,
) : TimeSource {
    override fun now(): Instant = now
    override fun zone(): ZoneId = zone
    override fun elapsedRealtimeMs(): Long = elapsedMs
    override fun bootCount(): Int = boot

    /** Advances wall clock and monotonic clock together, as real time does. */
    fun advance(duration: Duration) {
        now = now.plus(duration)
        elapsedMs += duration.toMillis()
    }

    fun reboot(offMinutes: Long = 1) {
        now = now.plus(Duration.ofMinutes(offMinutes))
        elapsedMs = 5_000L
        boot += 1
    }
}

class FakeScheduleRepository(initial: List<FocusSchedule> = emptyList()) : ScheduleRepository {
    val schedules = MutableStateFlow(initial)
    override fun observeSchedules(): Flow<List<FocusSchedule>> = schedules
    override suspend fun getSchedules() = schedules.value
    override suspend fun getSchedule(id: String) = schedules.value.firstOrNull { it.id == id }
    override suspend fun save(schedule: FocusSchedule) {
        schedules.value = schedules.value.filterNot { it.id == schedule.id } + schedule
    }
    override suspend fun delete(id: String) {
        schedules.value = schedules.value.filterNot { it.id == id }
    }
    override suspend fun setEnabled(id: String, enabled: Boolean) {
        schedules.value = schedules.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }
    override suspend fun deleteAll() {
        schedules.value = emptyList()
    }
}

class FakeStateRepository : LockdownStateRepository {
    val state = MutableStateFlow(LockdownState())
    val history = mutableListOf<LockdownState>()
    override fun observeState(): Flow<LockdownState> = state
    override suspend fun getState() = state.value
    override suspend fun saveState(state: LockdownState) {
        this.state.value = state
        history += state
    }
}

class FakeHistoryRepository : HistoryRepository {
    val sessions = MutableStateFlow<List<SessionHistory>>(emptyList())
    private var nextId = 1L

    override suspend fun startSession(window: SessionWindow, startedAt: Instant, enforcement: EnforcementLevel): Long {
        val id = nextId++
        sessions.value = sessions.value + SessionHistory(
            id = id,
            scheduleId = window.scheduleId,
            name = window.name,
            scheduledStart = window.start,
            startedAt = maxOf(startedAt, window.start),
            expectedEnd = window.end,
            actualEnd = null,
            status = SessionStatus.IN_PROGRESS,
            strictMode = window.strictMode,
            enforcement = enforcement,
            recoveryCount = 0,
            exitAttempts = 0,
        )
        return id
    }

    override suspend fun finishSession(id: Long, endedAt: Instant, status: SessionStatus): SessionHistory? {
        update(id) { it.copy(actualEnd = endedAt, status = status) }
        return byId(id)
    }

    override suspend fun closeOrphanedSessions(currentId: Long?, endedAt: Instant) {
        sessions.value = sessions.value.map {
            if (it.status == SessionStatus.IN_PROGRESS && it.id != currentId) {
                it.copy(status = SessionStatus.INTERRUPTED, actualEnd = endedAt)
            } else {
                it
            }
        }
    }

    override suspend fun incrementExitAttempts(id: Long) = update(id) { it.copy(exitAttempts = it.exitAttempts + 1) }
    override suspend fun incrementRecoveryCount(id: Long) = update(id) { it.copy(recoveryCount = it.recoveryCount + 1) }
    override fun observeRecent(limit: Int): Flow<List<SessionHistory>> = sessions.map { it.reversed().take(limit) }
    override fun observeEndingAfter(since: Instant): Flow<List<SessionHistory>> =
        sessions.map { list -> list.filter { it.expectedEnd.isAfter(since) } }
    override suspend fun deleteAll() {
        sessions.value = emptyList()
    }

    fun byId(id: Long) = sessions.value.first { it.id == id }

    private fun update(id: Long, transform: (SessionHistory) -> SessionHistory) {
        sessions.value = sessions.value.map { if (it.id == id) transform(it) else it }
    }
}

class FakeAllowedAppsRepository(var packages: Set<String> = setOf("com.allowed")) : AllowedAppsRepository {
    override fun observeAllowed(): Flow<List<AllowedApplication>> =
        MutableStateFlow(packages.map { AllowedApplication(it, it) })
    override suspend fun getAllowedPackages() = packages
    override suspend fun setAllowed(applications: List<AllowedApplication>) {
        packages = applications.map { it.packageName }.toSet()
    }
    override suspend fun getLaunchableApplications() = emptyList<InstalledApplication>()
    override suspend fun deleteAll() {
        packages = emptySet()
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings(countdownMinutes = 0, reminderMinutes = 0)) : SettingsRepository {
    private val flow = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = flow
    override suspend fun get() = flow.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        flow.value = transform(flow.value)
    }
    override suspend fun clear() {
        flow.value = AppSettings()
    }
}

/**
 * Simulates the device: policy survives process death and reboot (as real Device Owner policy
 * does), lock task mode does not survive reboot.
 */
class FakePolicyEnforcer(var deviceOwner: Boolean = true) : PolicyEnforcer {
    var appliedPolicy: LockdownPolicy? = null
    var restrictions: Set<String> = emptySet()
    var lockTaskActive = false
    var applyCount = 0
    var releaseCount = 0
    /** Invoked inside apply() to observe what had been persisted beforehand. */
    var onApply: () -> Unit = {}

    override fun isDeviceOwner() = deviceOwner
    override fun isLockTaskActive() = lockTaskActive

    override fun apply(policy: LockdownPolicy, alreadyApplied: Set<String>): AppliedPolicy {
        onApply()
        applyCount++
        if (!deviceOwner) return AppliedPolicy(EnforcementLevel.SCREEN_PINNING, alreadyApplied)
        appliedPolicy = policy
        val wanted = RestrictionPolicy.userRestrictions(policy.strictMode, allowDebugging = false)
        val added = wanted - restrictions
        restrictions = restrictions + wanted
        return AppliedPolicy(EnforcementLevel.DEVICE_OWNER, alreadyApplied + added)
    }

    override fun release(applied: Set<String>) {
        releaseCount++
        restrictions = restrictions - applied
        appliedPolicy = null
        lockTaskActive = false
    }

    override fun needsRepair(policy: LockdownPolicy, enforcement: EnforcementLevel?): Boolean {
        if (!deviceOwner) return false
        if (enforcement != EnforcementLevel.DEVICE_OWNER) return true
        return appliedPolicy != policy ||
            !restrictions.containsAll(RestrictionPolicy.userRestrictions(policy.strictMode, allowDebugging = false))
    }

    override fun hasLeftovers(applied: Set<String>) = deviceOwner && (applied.isNotEmpty() || appliedPolicy != null)
}

class FakeTransitionScheduler : TransitionScheduler {
    var scheduled: Instant? = null
    var exact = true
    override fun scheduleAt(instant: Instant?) {
        scheduled = instant
    }
    override fun canScheduleExact() = exact
}

class FakeNotifier : SessionNotifier {
    val events = mutableListOf<String>()
    override fun createChannels() = Unit
    override fun showReminder(window: SessionWindow, leadTime: Duration) {
        events += "reminder:${window.name}:${leadTime.toMinutes()}"
    }
    override fun showCountdown(window: SessionWindow) {
        events += "countdown:${window.name}"
    }
    override fun clearCountdown() = Unit
    override fun showAwaitingStart(window: SessionWindow) {
        events += "awaiting:${window.name}"
    }
    override fun clearAwaitingStart() = Unit
    override fun showActive(window: SessionWindow, enforcement: EnforcementLevel) {
        events += "active:${window.name}"
    }
    override fun clearActive() = Unit
    override fun showCompleted(name: String, focused: Duration) {
        events += "completed:$name:${focused.toMinutes()}"
    }
}

class FakeLauncher : LockdownLauncher {
    var launches = 0
    override fun launchLockdownScreen() {
        launches++
    }
}

object NoopLogger : FocusLogger {
    override fun debug(tag: String, message: String) = Unit
    override fun info(tag: String, message: String) = Unit
    override fun warn(tag: String, message: String) = Unit
    override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    override fun recentEntries() = emptyList<String>()
}
