package com.example.focuslock.feature.lockdown

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.example.focuslock.core.device.LockdownLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Device Owner apps are exempt from background activity-start restrictions, so this works from
 * receivers during a Device Owner session. Otherwise Android may block it silently; the ongoing
 * session notification is then the way back.
 */
class ActivityLockdownLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : LockdownLauncher {
    override fun launchLockdownScreen() {
        val intent = Intent(context, LockdownActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Cannot happen for our own component; guarded for safety.
        }
    }
}
