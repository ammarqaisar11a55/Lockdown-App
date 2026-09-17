package com.example.focuslock.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.focuslock.core.common.ApplicationScope
import com.example.focuslock.core.common.goAsync
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.repository.LockdownCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * BOOT_COMPLETED / MY_PACKAGE_REPLACED:
 * load persisted state -> determine current time -> restore lockdown or schedule next transition.
 *
 * These actions are protected broadcasts that only the system can send; the action is still
 * validated, and reconciliation is idempotent, so a spoofed intent cannot change anything.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: LockdownCoordinator

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> ReconcileTrigger.BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> ReconcileTrigger.PACKAGE_REPLACED
            else -> return
        }
        goAsync(scope) { coordinator.reconcile(trigger) }
    }
}

/** Clock, timezone and exact-alarm permission changes all invalidate the scheduled alarm. */
@AndroidEntryPoint
class TimeChangeReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: LockdownCoordinator

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> ReconcileTrigger.TIME_CHANGED
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> ReconcileTrigger.PERMISSION_CHANGED
            else -> return
        }
        goAsync(scope) { coordinator.reconcile(trigger) }
    }
}

/** Not exported: only this app's own PendingIntent can reach it. */
@AndroidEntryPoint
class TransitionAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: LockdownCoordinator

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRANSITION) return
        goAsync(scope) { coordinator.reconcile(ReconcileTrigger.ALARM) }
    }

    companion object {
        const val ACTION_TRANSITION = "com.example.focuslock.action.TRANSITION"
    }
}

/** Handles the "Start session" action of manual-start schedules. Not exported. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: LockdownCoordinator

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START_SESSION) return
        val key = intent.getStringExtra(EXTRA_OCCURRENCE_KEY)
            ?.takeIf { it.length <= MAX_KEY_LENGTH && OCCURRENCE_KEY_PATTERN.matches(it) }
            ?: return
        goAsync(scope) { coordinator.startPendingSession(key) }
    }

    companion object {
        const val ACTION_START_SESSION = "com.example.focuslock.action.START_SESSION"
        const val EXTRA_OCCURRENCE_KEY = "occurrence_key"
        private const val MAX_KEY_LENGTH = 128
        private val OCCURRENCE_KEY_PATTERN = Regex("[A-Za-z0-9-]+@\\d+")
    }
}
