package com.example.focuslock.core.security

import com.example.focuslock.core.common.FocusLogger
import com.example.focuslock.core.common.IoDispatcher
import com.example.focuslock.core.device.LockdownLauncher
import com.example.focuslock.core.device.LockdownPolicy
import com.example.focuslock.core.device.PolicyEnforcer
import com.example.focuslock.core.notification.SessionNotifier
import com.example.focuslock.core.scheduling.ScheduleCalculator
import com.example.focuslock.core.scheduling.TransitionScheduler
import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.domain.model.LockdownPhase
import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.model.SessionStatus
import com.example.focuslock.domain.model.SessionWindow
import com.example.focuslock.domain.repository.AllowedAppsRepository
import com.example.focuslock.domain.repository.HistoryRepository
import com.example.focuslock.domain.repository.LockdownCoordinator
import com.example.focuslock.domain.repository.LockdownStateRepository
import com.example.focuslock.domain.repository.ScheduleRepository
import com.example.focuslock.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Policy Reconciliation Engine.
 *
 * ```
 * persisted state + current time + device policy state -> reconciliation -> correct device state
 * ```
 *
 * Rules:
 *  - Every run is serialized; runs are idempotent and safe to repeat from any trigger.
 *  - State is persisted before device policy changes, so a crash mid-transition is repaired on
 *    the next run (boot, process start, alarm, app open).
 *  - The persisted state is never trusted blindly: expiry is re-evaluated against the clocks and
 *    the device policy is re-verified and repaired when it drifted.
 */
@Singleton
class PolicyReconciliationEngine @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val stateRepository: LockdownStateRepository,
    private val historyRepository: HistoryRepository,
    private val allowedAppsRepository: AllowedAppsRepository,
    private val settingsRepository: SettingsRepository,
    private val enforcer: PolicyEnforcer,
    private val transitionScheduler: TransitionScheduler,
    private val notifier: SessionNotifier,
    private val launcher: LockdownLauncher,
    private val timeSource: TimeSource,
    private val logger: FocusLogger,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LockdownCoordinator {

    private val mutex = Mutex()

    override suspend fun reconcile(trigger: ReconcileTrigger): LockdownState =
        serialized { reconcileLocked(trigger) }

    override suspend fun startFocusNow(name: String, duration: Duration, strictMode: Boolean): Boolean =
        serialized {
            if (duration < MIN_AD_HOC_DURATION || duration > MAX_AD_HOC_DURATION) return@serialized false
            val current = reconcileLocked(ReconcileTrigger.USER_ACTION)
            if (current.isLocked) return@serialized false
            val input = buildInput()
            val window = SessionWindow(
                scheduleId = null,
                name = name.trim().take(MAX_NAME_LENGTH).ifEmpty { DEFAULT_AD_HOC_NAME },
                start = input.now,
                end = input.now.plus(duration),
                strictMode = strictMode,
            )
            logger.info(TAG, "Ad-hoc session requested (${duration.toMinutes()} min)")
            beginSession(input.state, window, input)
            reconcileLocked(ReconcileTrigger.USER_ACTION)
            true
        }

    override suspend fun cancelUpcomingSession(occurrenceKey: String): Boolean = serialized {
        val state = reconcileLocked(ReconcileTrigger.USER_ACTION)
        val input = buildInput()
        val isUpcoming = ScheduleCalculator.upcomingWindows(input.schedules, input.now, input.zone)
            .any { it.occurrenceKey == occurrenceKey }
        val isRunning = state.session?.occurrenceKey == occurrenceKey && state.isLocked
        if (!isUpcoming || isRunning) {
            logger.warn(TAG, "Cancel refused: occurrence is not upcoming")
            return@serialized false
        }
        stateRepository.saveState(state.copy(skippedOccurrences = state.skippedOccurrences + occurrenceKey))
        logger.info(TAG, "Upcoming occurrence skipped")
        reconcileLocked(ReconcileTrigger.USER_ACTION)
        true
    }

    override suspend fun startPendingSession(occurrenceKey: String): Boolean = serialized {
        val input = buildInput()
        val pending = ScheduleCalculator.activeWindows(input.schedules, input.now, input.zone)
            .any { it.occurrenceKey == occurrenceKey && !it.autoStart }
        if (!pending || occurrenceKey in input.state.skippedOccurrences) return@serialized false
        stateRepository.saveState(input.state.copy(startedOccurrences = input.state.startedOccurrences + occurrenceKey))
        reconcileLocked(ReconcileTrigger.USER_ACTION).isLocked
    }

    override suspend fun recordExitAttempt() {
        serialized {
            val state = stateRepository.getState()
            val historyId = state.historyId
            if (state.isLocked && historyId != null) historyRepository.incrementExitAttempts(historyId)
        }
    }

    private suspend fun <T> serialized(block: suspend () -> T): T =
        withContext(ioDispatcher) { mutex.withLock { block() } }

    private suspend fun reconcileLocked(trigger: ReconcileTrigger): LockdownState {
        val input = buildInput()
        val target = LockdownDecider.decide(input)
        logger.debug(TAG, "Reconcile trigger=$trigger phase=${input.state.phase} target=${target.javaClass.simpleName}")

        var state = when (target) {
            is LockdownTarget.Active ->
                if (target.continuing) {
                    maintainSession(input.state, target.window, trigger)
                } else {
                    beginSession(endSessionIfLocked(input.state, input), target.window, input)
                }
            is LockdownTarget.Countdown ->
                endSessionIfLocked(input.state, input).copy(phase = LockdownPhase.COUNTDOWN, session = target.window)
            is LockdownTarget.AwaitingStart, LockdownTarget.Idle ->
                endSessionIfLocked(input.state, input).copy(phase = LockdownPhase.IDLE, session = null)
        }

        if (state.phase != LockdownPhase.ACTIVE) state = releaseLeftovers(state)
        historyRepository.closeOrphanedSessions(state.historyId, input.now)
        state = updateNotifications(state, target, input)

        stateRepository.saveState(state)
        transitionScheduler.scheduleAt(LockdownDecider.nextWakeUp(input.copy(state = state), target))
        return state
    }

    private suspend fun buildInput(): DecisionInput {
        val now = timeSource.now()
        val elapsed = timeSource.elapsedRealtimeMs()
        val bootCount = timeSource.bootCount()
        val settings = settingsRepository.get()
        val stored = stateRepository.getState()
        val session = stored.session
        val state = stored.pruneOccurrenceKeys(now).let { pruned ->
            if (pruned.isLocked && session != null) {
                pruned.copy(checkpoint = SessionClock.refreshForBoot(pruned.checkpoint, session, now, elapsed, bootCount))
            } else {
                pruned
            }
        }
        return DecisionInput(
            now = now,
            zone = timeSource.zone(),
            elapsedRealtimeMs = elapsed,
            bootCount = bootCount,
            state = state,
            schedules = scheduleRepository.getSchedules(),
            countdownMinutes = settings.countdownMinutes,
            reminderMinutes = settings.reminderMinutes,
        )
    }

    private suspend fun beginSession(state: LockdownState, window: SessionWindow, input: DecisionInput): LockdownState {
        val provisional = if (enforcer.isDeviceOwner()) {
            EnforcementLevel.DEVICE_OWNER
        } else {
            EnforcementLevel.SCREEN_PINNING
        }
        val historyId = historyRepository.startSession(window, input.now, provisional)
        var active = state.copy(
            phase = LockdownPhase.ACTIVE,
            session = window,
            zoneId = input.zone.id,
            historyId = historyId,
            enforcement = provisional,
            checkpoint = SessionClock.checkpoint(window, input.now, input.elapsedRealtimeMs, input.bootCount),
        )
        // Persist before touching device policy: a crash from here on is recovered as ACTIVE.
        stateRepository.saveState(active)
        logger.info(TAG, "Session started (strict=${window.strictMode})")

        val applied = enforcer.apply(policyFor(window), active.appliedRestrictions)
        active = active.copy(enforcement = applied.enforcement, appliedRestrictions = applied.restrictions)
        stateRepository.saveState(active)

        notifier.showActive(window, applied.enforcement)
        launcher.launchLockdownScreen()
        logger.info(TAG, "Lock task requested (${applied.enforcement})")
        return active
    }

    private suspend fun maintainSession(
        state: LockdownState,
        window: SessionWindow,
        trigger: ReconcileTrigger,
    ): LockdownState {
        var current = state
        val policy = policyFor(window)
        if (enforcer.needsRepair(policy, current.enforcement)) {
            logger.warn(TAG, "Device policy drift detected; re-applying")
            val applied = enforcer.apply(policy, current.appliedRestrictions)
            current = current.copy(enforcement = applied.enforcement, appliedRestrictions = applied.restrictions)
            stateRepository.saveState(current)
        }
        if (trigger.isRecovery) {
            current.historyId?.let { historyRepository.incrementRecoveryCount(it) }
            logger.info(TAG, "Session recovered after $trigger")
        }
        if (!enforcer.isLockTaskActive()) launcher.launchLockdownScreen()
        notifier.showActive(window, current.enforcement ?: EnforcementLevel.SCREEN_PINNING)
        return current
    }

    private suspend fun endSessionIfLocked(state: LockdownState, input: DecisionInput): LockdownState {
        val session = state.session
        if (!state.isLocked || session == null) return state
        // Release first; the state still lists owned restrictions until release has happened.
        enforcer.release(state.appliedRestrictions)
        val record = state.historyId?.let { historyRepository.finishSession(it, input.now, SessionStatus.COMPLETED) }
        val ended = state.copy(
            phase = LockdownPhase.IDLE,
            session = null,
            zoneId = null,
            historyId = null,
            enforcement = null,
            checkpoint = null,
            appliedRestrictions = emptySet(),
        )
        stateRepository.saveState(ended)
        notifier.clearActive()
        notifier.showCompleted(session.name, record?.focusedDuration(input.now) ?: session.duration)
        logger.info(TAG, "Session completed")
        return ended
    }

    private fun releaseLeftovers(state: LockdownState): LockdownState {
        if (!enforcer.hasLeftovers(state.appliedRestrictions)) return state
        logger.warn(TAG, "Leftover session policy found while idle; releasing")
        enforcer.release(state.appliedRestrictions)
        return state.copy(appliedRestrictions = emptySet())
    }

    private fun updateNotifications(state: LockdownState, target: LockdownTarget, input: DecisionInput): LockdownState {
        if (target is LockdownTarget.Countdown) notifier.showCountdown(target.window) else notifier.clearCountdown()
        if (target is LockdownTarget.AwaitingStart) notifier.showAwaitingStart(target.window) else notifier.clearAwaitingStart()
        if (!state.isLocked) notifier.clearActive()

        val reminder = LockdownDecider.reminderDue(input.copy(state = state)) ?: return state
        if (target !is LockdownTarget.Countdown) {
            notifier.showReminder(reminder, Duration.between(input.now, reminder.start))
        }
        return state.copy(remindedOccurrences = state.remindedOccurrences + reminder.occurrenceKey)
    }

    private suspend fun policyFor(window: SessionWindow) =
        LockdownPolicy(allowedAppsRepository.getAllowedPackages(), window.strictMode)

    private fun LockdownState.pruneOccurrenceKeys(now: Instant): LockdownState {
        val cutoff = now.minus(OCCURRENCE_KEY_RETENTION)
        fun Set<String>.pruned() = filterTo(mutableSetOf()) { key ->
            SessionWindow.startOfOccurrenceKey(key)?.isAfter(cutoff) == true
        }
        return copy(
            skippedOccurrences = skippedOccurrences.pruned(),
            startedOccurrences = startedOccurrences.pruned(),
            remindedOccurrences = remindedOccurrences.pruned(),
        )
    }

    companion object {
        private const val TAG = "LOCKDOWN_ENGINE"
        private const val MAX_NAME_LENGTH = 40
        private const val DEFAULT_AD_HOC_NAME = "Focus"
        val MIN_AD_HOC_DURATION: Duration = Duration.ofSeconds(30)
        val MAX_AD_HOC_DURATION: Duration = Duration.ofHours(12)

        /** Occurrence bookkeeping is only relevant for sessions around "now". */
        private val OCCURRENCE_KEY_RETENTION: Duration = Duration.ofDays(2)
    }
}
