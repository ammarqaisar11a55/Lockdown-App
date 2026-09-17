package com.example.focuslock.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.focuslock.MainActivity
import com.example.focuslock.R
import com.example.focuslock.core.time.DurationFormatter
import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.domain.model.SessionWindow
import com.example.focuslock.feature.lockdown.LockdownActivity
import com.example.focuslock.receiver.NotificationActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject

class FocusNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : SessionNotifier {

    private val manager = NotificationManagerCompat.from(context)

    override fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    context.getString(R.string.channel_reminders),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    CHANNEL_SESSION,
                    context.getString(R.string.channel_session),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            ),
        )
    }

    override fun showReminder(window: SessionWindow, leadTime: Duration) {
        val minutes = leadTime.toMinutes().coerceAtLeast(1)
        notify(
            ID_REMINDER,
            builder(CHANNEL_REMINDERS)
                .setContentTitle(window.name)
                .setContentText(context.resources.getQuantityString(R.plurals.notification_reminder, minutes.toInt(), minutes))
                .setContentIntent(openApp())
                .setAutoCancel(true),
        )
    }

    override fun showCountdown(window: SessionWindow) {
        manager.cancel(ID_REMINDER)
        notify(
            ID_COUNTDOWN,
            builder(CHANNEL_SESSION)
                .setContentTitle(context.getString(R.string.notification_countdown_title))
                .setContentText(context.getString(R.string.notification_countdown_text, window.name, time(window)))
                .setWhen(window.start.toEpochMilli())
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp()),
        )
    }

    override fun clearCountdown() = manager.cancel(ID_COUNTDOWN)

    override fun showAwaitingStart(window: SessionWindow) {
        val start = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_START_SESSION)
            .putExtra(NotificationActionReceiver.EXTRA_OCCURRENCE_KEY, window.occurrenceKey)
        val startIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_START_SESSION,
            start,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        notify(
            ID_AWAITING_START,
            builder(CHANNEL_REMINDERS)
                .setContentTitle(window.name)
                .setContentText(context.getString(R.string.notification_awaiting_start, endTime(window)))
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp())
                .addAction(0, context.getString(R.string.action_start_session), startIntent),
        )
    }

    override fun clearAwaitingStart() = manager.cancel(ID_AWAITING_START)

    override fun showActive(window: SessionWindow, enforcement: EnforcementLevel) {
        manager.cancel(ID_REMINDER)
        val intent = Intent(context, LockdownActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN_LOCKDOWN,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        notify(
            ID_ACTIVE,
            builder(CHANNEL_SESSION)
                .setContentTitle(context.getString(R.string.notification_active_title))
                .setContentText(context.getString(R.string.notification_active_text, endTime(window)))
                .setOngoing(enforcement == EnforcementLevel.SCREEN_PINNING)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(contentIntent),
        )
    }

    override fun clearActive() = manager.cancel(ID_ACTIVE)

    override fun showCompleted(name: String, focused: Duration) {
        notify(
            ID_COMPLETED,
            builder(CHANNEL_REMINDERS)
                .setContentTitle(context.getString(R.string.notification_completed_title))
                .setContentText(
                    context.getString(R.string.notification_completed_text, DurationFormatter.short(focused)),
                )
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        context.getString(R.string.notification_completed_big, name, DurationFormatter.short(focused)),
                    ),
                )
                .setContentIntent(openApp())
                .setAutoCancel(true),
        )
    }

    private fun builder(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        if (!canPost()) return
        try {
            manager.notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; notifications are best-effort.
        }
    }

    private fun canPost(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && manager.areNotificationsEnabled()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun time(window: SessionWindow) = DurationFormatter.time(window.start, ZoneId.systemDefault())
    private fun endTime(window: SessionWindow) = DurationFormatter.time(window.end, ZoneId.systemDefault())

    companion object {
        const val CHANNEL_REMINDERS = "session_reminders"
        const val CHANNEL_SESSION = "session_status"
        private const val ID_REMINDER = 10
        private const val ID_COUNTDOWN = 11
        private const val ID_ACTIVE = 12
        private const val ID_COMPLETED = 13
        private const val ID_AWAITING_START = 14
        private const val REQUEST_OPEN_APP = 2001
        private const val REQUEST_OPEN_LOCKDOWN = 2002
        private const val REQUEST_START_SESSION = 2003
    }
}
