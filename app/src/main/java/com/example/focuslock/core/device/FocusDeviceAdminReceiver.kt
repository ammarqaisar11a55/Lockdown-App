package com.example.focuslock.core.device

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.focuslock.R

/**
 * Device admin component. Becomes the Device Owner only through an explicit provisioning step
 * (ADB for development, QR/zero-touch/EMM for production). The app never self-provisions.
 */
class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.admin_disable_warning)

    companion object {
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, FocusDeviceAdminReceiver::class.java)
    }
}
