package com.example.focuslock.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focuslock.data.local.AllowedApplicationEntity
import com.example.focuslock.data.local.FocusDatabase
import com.example.focuslock.data.local.toDomain
import com.example.focuslock.data.local.toEntity
import com.example.focuslock.data.repository.RoomHistoryRepository
import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.LockdownPhase
import com.example.focuslock.domain.model.LockdownState
import com.example.focuslock.domain.model.RepeatRule
import com.example.focuslock.domain.model.SessionStatus
import com.example.focuslock.domain.model.SessionWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class FocusDatabaseTest {
    private lateinit var db: FocusDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FocusDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private val schedule = FocusSchedule(
        id = "s1",
        name = "Study",
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(13, 0),
        repeat = RepeatRule.Weekly(RepeatRule.Weekly.WEEKDAYS),
        strictMode = true,
        enabled = true,
        autoStart = true,
        createdAt = 1,
    )

    @Test
    fun schedulesPersistAndUpdate() = runTest {
        val dao = db.scheduleDao()
        dao.upsert(schedule.toEntity(updatedAt = 1))
        assertEquals(schedule, dao.observeAll().first().single().toDomain())

        dao.setEnabled("s1", enabled = false, updatedAt = 2)
        assertEquals(false, dao.getById("s1")?.enabled)

        dao.delete("s1")
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun lockdownStateIsASingleRow() = runTest {
        val dao = db.lockdownStateDao()
        assertNull(dao.get())
        val active = LockdownState(
            phase = LockdownPhase.ACTIVE,
            session = SessionWindow("s1", "Study", Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(9_000), true),
            appliedRestrictions = setOf("no_safe_boot"),
        )
        dao.upsert(active.toEntity(1))
        dao.upsert(active.copy(historyId = 4).toEntity(2))
        assertEquals(active.copy(historyId = 4), dao.get()?.toDomain())
        assertEquals(LockdownPhase.ACTIVE, dao.observe().first()?.toDomain()?.phase)
    }

    @Test
    fun historyTracksLifecycleAndOrphans() = runTest {
        val repository = RoomHistoryRepository(db.sessionHistoryDao())
        val window = SessionWindow("s1", "Study", Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(9_000), false)
        val current = repository.startSession(window, Instant.ofEpochMilli(2_000), EnforcementLevel.DEVICE_OWNER)
        val orphan = repository.startSession(window, Instant.ofEpochMilli(500), EnforcementLevel.SCREEN_PINNING)

        repository.incrementExitAttempts(current)
        repository.incrementRecoveryCount(current)
        repository.closeOrphanedSessions(current, Instant.ofEpochMilli(3_000))

        val sessions = repository.observeRecent(10).first().associateBy { it.id }
        assertEquals(SessionStatus.IN_PROGRESS, sessions.getValue(current).status)
        assertEquals(1, sessions.getValue(current).exitAttempts)
        assertEquals(1, sessions.getValue(current).recoveryCount)
        assertEquals(SessionStatus.INTERRUPTED, sessions.getValue(orphan).status)
        // Start is never recorded before the scheduled start.
        assertEquals(Instant.ofEpochMilli(1_000), sessions.getValue(orphan).startedAt)

        val finished = repository.finishSession(current, Instant.ofEpochMilli(9_000), SessionStatus.COMPLETED)
        assertEquals(SessionStatus.COMPLETED, finished?.status)
        assertEquals(1, repository.observeEndingAfter(Instant.ofEpochMilli(8_000)).first().size.coerceAtMost(1))
    }

    @Test
    fun allowedApplicationsAreReplacedAtomically() = runTest {
        val dao = db.allowedApplicationDao()
        dao.replaceAll(listOf(AllowedApplicationEntity("a", "A", 0), AllowedApplicationEntity("b", "B", 0)))
        dao.replaceAll(listOf(AllowedApplicationEntity("c", "C", 0)))
        assertEquals(listOf("c"), dao.getPackageNames())
    }
}
