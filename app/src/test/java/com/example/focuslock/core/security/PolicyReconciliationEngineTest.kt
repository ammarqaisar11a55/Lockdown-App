package com.example.focuslock.core.security

import com.example.focuslock.domain.model.AppSettings
import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.domain.model.LockdownPhase
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.model.RepeatRule
import com.example.focuslock.domain.model.SessionStatus
import com.example.focuslock.testing.FakeAllowedAppsRepository
import com.example.focuslock.testing.FakeHistoryRepository
import com.example.focuslock.testing.FakeLauncher
import com.example.focuslock.testing.FakeNotifier
import com.example.focuslock.testing.FakePolicyEnforcer
import com.example.focuslock.testing.FakeScheduleRepository
import com.example.focuslock.testing.FakeSettingsRepository
import com.example.focuslock.testing.FakeStateRepository
import com.example.focuslock.testing.FakeTimeSource
import com.example.focuslock.testing.FakeTransitionScheduler
import com.example.focuslock.testing.NoopLogger
import com.example.focuslock.testing.WEDNESDAY
import com.example.focuslock.testing.at
import com.example.focuslock.testing.schedule
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class PolicyReconciliationEngineTest {

    // "Device" state that survives process death.
    private val schedules = FakeScheduleRepository(listOf(schedule()))
    private val stateRepository = FakeStateRepository()
    private val history = FakeHistoryRepository()
    private val allowedApps = FakeAllowedAppsRepository()
    private val settings = FakeSettingsRepository()
    private val enforcer = FakePolicyEnforcer()
    private val scheduler = FakeTransitionScheduler()
    private val notifier = FakeNotifier()
    private val launcher = FakeLauncher()
    private val time = FakeTimeSource(now = at(WEDNESDAY, "07:00"))

    /** A new engine instance models a fresh process (process death). */
    private fun newEngine() = PolicyReconciliationEngine(
        scheduleRepository = schedules,
        stateRepository = stateRepository,
        historyRepository = history,
        allowedAppsRepository = allowedApps,
        settingsRepository = settings,
        enforcer = enforcer,
        transitionScheduler = scheduler,
        notifier = notifier,
        launcher = launcher,
        timeSource = time,
        logger = NoopLogger,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private val engine = newEngine()

    @Test
    fun `idle reconcile schedules the start alarm and applies nothing`() = runTest {
        val state = engine.reconcile(ReconcileTrigger.PROCESS_START)
        assertEquals(LockdownPhase.IDLE, state.phase)
        assertEquals(at(WEDNESDAY, "08:00"), scheduler.scheduled)
        assertEquals(0, enforcer.applyCount)
    }

    @Test
    fun `session start persists state before applying policy`() = runTest {
        var persistedPhaseAtApply: LockdownPhase? = null
        enforcer.onApply = { persistedPhaseAtApply = stateRepository.state.value.phase }
        time.now = at(WEDNESDAY, "08:00")

        val state = engine.reconcile(ReconcileTrigger.ALARM)

        assertEquals(LockdownPhase.ACTIVE, persistedPhaseAtApply)
        assertTrue(state.isLocked)
        assertEquals(EnforcementLevel.DEVICE_OWNER, state.enforcement)
        assertEquals(setOf("com.allowed"), enforcer.appliedPolicy?.allowedPackages)
        assertTrue(state.appliedRestrictions.isNotEmpty())
        assertEquals(1, launcher.launches)
        assertEquals(at(WEDNESDAY, "12:00"), scheduler.scheduled)
        assertEquals(SessionStatus.IN_PROGRESS, history.byId(state.historyId!!).status)
    }

    @Test
    fun `session end releases exactly the restrictions it added and records completion`() = runTest {
        enforcer.restrictions = setOf("preexisting")
        time.now = at(WEDNESDAY, "08:00")
        val active = engine.reconcile(ReconcileTrigger.ALARM)
        val historyId = active.historyId!!

        time.advance(Duration.ofHours(4))
        val ended = engine.reconcile(ReconcileTrigger.ALARM)

        assertEquals(LockdownPhase.IDLE, ended.phase)
        assertEquals(setOf("preexisting"), enforcer.restrictions)
        assertTrue(ended.appliedRestrictions.isEmpty())
        assertEquals(SessionStatus.COMPLETED, history.byId(historyId).status)
        assertTrue(notifier.events.contains("completed:Study:240"))
        assertEquals(at(WEDNESDAY.plusDays(1), "08:00"), scheduler.scheduled)
    }

    @Test
    fun `process death restores the active session`() = runTest {
        time.now = at(WEDNESDAY, "08:00")
        engine.reconcile(ReconcileTrigger.ALARM)
        time.advance(Duration.ofMinutes(30))

        val restored = newEngine().reconcile(ReconcileTrigger.PROCESS_START)

        assertTrue(restored.isLocked)
        assertEquals(1, history.byId(restored.historyId!!).recoveryCount)
        assertEquals(1, history.sessions.value.size)
    }

    @Test
    fun `reboot during a session restores lockdown and relaunches the screen`() = runTest {
        time.now = at(WEDNESDAY, "08:00")
        engine.reconcile(ReconcileTrigger.ALARM)
        enforcer.lockTaskActive = true
        val launchesBefore = launcher.launches

        time.reboot(offMinutes = 5)
        enforcer.lockTaskActive = false // lock task mode does not survive a reboot
        val restored = newEngine().reconcile(ReconcileTrigger.BOOT)

        assertTrue(restored.isLocked)
        assertEquals(2, restored.checkpoint?.bootCount)
        assertEquals(launchesBefore + 1, launcher.launches)
    }

    @Test
    fun `reboot after the scheduled end completes the session`() = runTest {
        time.now = at(WEDNESDAY, "08:00")
        engine.reconcile(ReconcileTrigger.ALARM)

        time.reboot(offMinutes = 5 * 60)
        val state = newEngine().reconcile(ReconcileTrigger.BOOT)

        assertEquals(LockdownPhase.IDLE, state.phase)
        assertEquals(SessionStatus.COMPLETED, history.sessions.value.single().status)
    }

    @Test
    fun `phone off during the whole session never locks afterwards`() = runTest {
        time.now = at(WEDNESDAY, "13:00")
        val state = engine.reconcile(ReconcileTrigger.BOOT)
        assertEquals(LockdownPhase.IDLE, state.phase)
        assertTrue(history.sessions.value.isEmpty())
    }

    @Test
    fun `policy drift is repaired`() = runTest {
        time.now = at(WEDNESDAY, "08:00")
        engine.reconcile(ReconcileTrigger.ALARM)
        enforcer.restrictions = emptySet() // something removed our restrictions

        time.advance(Duration.ofMinutes(1))
        engine.reconcile(ReconcileTrigger.APP_FOREGROUND)

        assertEquals(2, enforcer.applyCount)
        assertTrue(enforcer.restrictions.isNotEmpty())
    }

    @Test
    fun `leftover policy is released when no session is active`() = runTest {
        enforcer.apply(com.example.focuslock.core.device.LockdownPolicy(emptySet(), false), emptySet())
        engine.reconcile(ReconcileTrigger.PROCESS_START)
        assertNull(enforcer.appliedPolicy)
        assertEquals(1, enforcer.releaseCount)
    }

    @Test
    fun `deleting or disabling the running schedule does not end the session`() = runTest {
        time.now = at(WEDNESDAY, "08:00")
        engine.reconcile(ReconcileTrigger.ALARM)
        schedules.deleteAll()
        time.advance(Duration.ofMinutes(10))
        assertTrue(engine.reconcile(ReconcileTrigger.SCHEDULES_CHANGED).isLocked)
    }

    @Test
    fun `time change mid-session keeps it locked`() = runTest {
        time.now = at(WEDNESDAY, "08:00")
        engine.reconcile(ReconcileTrigger.ALARM)
        time.elapsedMs += Duration.ofMinutes(5).toMillis()
        time.now = at(WEDNESDAY, "23:00")
        assertTrue(engine.reconcile(ReconcileTrigger.TIME_CHANGED).isLocked)
    }

    @Test
    fun `without device owner the session falls back to screen pinning`() = runTest {
        enforcer.deviceOwner = false
        time.now = at(WEDNESDAY, "08:00")
        val state = engine.reconcile(ReconcileTrigger.ALARM)
        assertTrue(state.isLocked)
        assertEquals(EnforcementLevel.SCREEN_PINNING, state.enforcement)
        assertTrue(state.appliedRestrictions.isEmpty())
    }

    @Test
    fun `gaining device owner mid-session upgrades enforcement`() = runTest {
        enforcer.deviceOwner = false
        time.now = at(WEDNESDAY, "08:00")
        engine.reconcile(ReconcileTrigger.ALARM)
        enforcer.deviceOwner = true
        val state = engine.reconcile(ReconcileTrigger.APP_FOREGROUND)
        assertEquals(EnforcementLevel.DEVICE_OWNER, state.enforcement)
    }

    @Test
    fun `countdown can be cancelled but a started session cannot`() = runTest {
        settings.update { AppSettings(countdownMinutes = 5, reminderMinutes = 0) }
        time.now = at(WEDNESDAY, "07:57")
        val countdown = engine.reconcile(ReconcileTrigger.ALARM)
        assertEquals(LockdownPhase.COUNTDOWN, countdown.phase)
        val key = countdown.session!!.occurrenceKey

        assertTrue(engine.cancelUpcomingSession(key))
        time.now = at(WEDNESDAY, "08:00")
        assertFalse(engine.reconcile(ReconcileTrigger.ALARM).isLocked)

        // Tomorrow's occurrence starts normally and cannot be cancelled once running.
        time.now = at(WEDNESDAY.plusDays(1), "08:00")
        val active = engine.reconcile(ReconcileTrigger.ALARM)
        assertTrue(active.isLocked)
        assertFalse(engine.cancelUpcomingSession(active.session!!.occurrenceKey))
        assertTrue(engine.reconcile(ReconcileTrigger.USER_ACTION).isLocked)
    }

    @Test
    fun `countdown is followed by lockdown when not cancelled`() = runTest {
        settings.update { AppSettings(countdownMinutes = 5, reminderMinutes = 0) }
        time.now = at(WEDNESDAY, "07:57")
        assertEquals(LockdownPhase.COUNTDOWN, engine.reconcile(ReconcileTrigger.ALARM).phase)
        assertEquals(at(WEDNESDAY, "08:00"), scheduler.scheduled)

        time.advance(Duration.ofMinutes(3))
        val state = engine.reconcile(ReconcileTrigger.ALARM)
        assertTrue(state.isLocked)
        assertEquals("s1", state.session!!.scheduleId)
        assertEquals(1, launcher.launches)
    }

    @Test
    fun `unknown occurrence keys cannot be cancelled`() = runTest {
        assertFalse(engine.cancelUpcomingSession("s1@0"))
        assertTrue(stateRepository.state.value.skippedOccurrences.isEmpty())
    }

    @Test
    fun `focus now starts immediately and is refused while locked`() = runTest {
        schedules.deleteAll()
        assertTrue(engine.startFocusNow("Deep work", Duration.ofMinutes(25), strictMode = true))
        val state = stateRepository.state.value
        assertTrue(state.isLocked)
        assertNull(state.session!!.scheduleId)
        assertTrue(enforcer.appliedPolicy!!.strictMode)
        assertEquals(time.now.plus(Duration.ofMinutes(25)), scheduler.scheduled)

        assertFalse(engine.startFocusNow("Again", Duration.ofMinutes(25), strictMode = false))
        assertFalse(engine.startFocusNow("Too short", Duration.ofSeconds(1), strictMode = false))
    }

    @Test
    fun `scheduled session waits for a running ad-hoc session and then starts`() = runTest {
        time.now = at(WEDNESDAY, "07:30")
        engine.startFocusNow("Warm-up", Duration.ofMinutes(45), strictMode = false)

        time.advance(Duration.ofMinutes(35)) // 08:05, scheduled window already open
        val stillAdHoc = engine.reconcile(ReconcileTrigger.ALARM)
        assertNull(stillAdHoc.session!!.scheduleId)

        time.advance(Duration.ofMinutes(10)) // 08:15, ad-hoc ended
        val scheduled = engine.reconcile(ReconcileTrigger.ALARM)
        assertEquals("s1", scheduled.session!!.scheduleId)
        assertEquals(2, history.sessions.value.size)
    }

    @Test
    fun `manual start sessions start only on request`() = runTest {
        schedules.save(schedule(autoStart = false))
        time.now = at(WEDNESDAY, "08:30")
        assertFalse(engine.reconcile(ReconcileTrigger.ALARM).isLocked)
        assertTrue(notifier.events.contains("awaiting:Study"))

        val key = "s1@${at(WEDNESDAY, "08:00").toEpochMilli()}"
        assertTrue(engine.startPendingSession(key))
        assertTrue(stateRepository.state.value.isLocked)
        assertFalse(engine.startPendingSession("s1@123"))
    }

    @Test
    fun `reminder is posted once`() = runTest {
        settings.update { AppSettings(countdownMinutes = 0, reminderMinutes = 15) }
        time.now = at(WEDNESDAY, "07:45")
        engine.reconcile(ReconcileTrigger.ALARM)
        engine.reconcile(ReconcileTrigger.APP_FOREGROUND)
        assertEquals(1, notifier.events.count { it.startsWith("reminder:") })
    }

    @Test
    fun `exit attempts are recorded against the running session`() = runTest {
        time.now = at(WEDNESDAY, "08:00")
        val state = engine.reconcile(ReconcileTrigger.ALARM)
        engine.recordExitAttempt()
        engine.recordExitAttempt()
        assertEquals(2, history.byId(state.historyId!!).exitAttempts)
    }

    @Test
    fun `sessions orphaned by a crash are marked interrupted`() = runTest {
        val orphanId = history.startSession(
            com.example.focuslock.domain.model.SessionWindow(null, "Lost", at(WEDNESDAY, "05:00"), at(WEDNESDAY, "06:00"), false),
            at(WEDNESDAY, "05:00"),
            EnforcementLevel.DEVICE_OWNER,
        )
        engine.reconcile(ReconcileTrigger.PROCESS_START)
        assertEquals(SessionStatus.INTERRUPTED, history.byId(orphanId).status)
    }

    @Test
    fun `old occurrence bookkeeping is pruned`() = runTest {
        stateRepository.saveState(
            stateRepository.state.value.copy(skippedOccurrences = setOf("s1@0", "garbage")),
        )
        val state = engine.reconcile(ReconcileTrigger.PROCESS_START)
        assertTrue(state.skippedOccurrences.isEmpty())
    }

    @Test
    fun `one-time schedule locks once`() = runTest {
        schedules.deleteAll()
        schedules.save(schedule(id = "once", repeat = RepeatRule.Once(WEDNESDAY)))
        time.now = at(WEDNESDAY, "08:00")
        assertTrue(engine.reconcile(ReconcileTrigger.ALARM).isLocked)
        time.advance(Duration.ofHours(4))
        assertFalse(engine.reconcile(ReconcileTrigger.ALARM).isLocked)
        assertNull(scheduler.scheduled)
        assertNotNull(history.sessions.value.single().actualEnd)
    }
}
