package com.example.focuslock.core.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.focuslock.core.common.FocusLogger
import com.example.focuslock.core.time.DurationFormatter
import com.example.focuslock.receiver.TransitionAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Keeps exactly one alarm: the next instant at which lockdown state may change.
 * When it fires, the engine reconciles and schedules the following one.
 */
interface TransitionScheduler {
    fun scheduleAt(instant: Instant?)
    fun canScheduleExact(): Boolean
}

class AlarmTransitionScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: FocusLogger,
) : TransitionScheduler {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun scheduleAt(instant: Instant?) {
        val pendingIntent = pendingIntent()
        if (instant == null) {
            alarmManager.cancel(pendingIntent)
            logger.debug(TAG, "No upcoming transition; alarm cleared")
            return
        }
        val triggerAt = maxOf(instant.toEpochMilli(), System.currentTimeMillis() + MIN_DELAY_MS)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            // Without exact-alarm access, Android may defer this; reconciliation on app open,
            // boot and the lockdown screen timer limits the impact.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
        logger.debug(
            TAG,
            "Next transition at ${DurationFormatter.time(Instant.ofEpochMilli(triggerAt), ZoneId.systemDefault())} " +
                "(exact=${canScheduleExact()})",
        )
    }

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, TransitionAlarmReceiver::class.java)
            .setAction(TransitionAlarmReceiver.ACTION_TRANSITION)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val TAG = "SCHEDULER"
        const val REQUEST_CODE = 1001

        /** Avoids scheduling in the past when the instant is "now". */
        const val MIN_DELAY_MS = 1_000L
    }
}
