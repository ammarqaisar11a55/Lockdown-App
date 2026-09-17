package com.example.focuslock.core.device

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.focuslock.R
import com.example.focuslock.core.common.AllowDebugging
import com.example.focuslock.core.scheduling.TransitionScheduler
import com.example.focuslock.domain.model.Capability
import com.example.focuslock.domain.model.CapabilityId
import com.example.focuslock.domain.model.CapabilityStatus
import com.example.focuslock.domain.model.DeviceCapabilities
import com.example.focuslock.receiver.BootReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

/** Detects what this particular device and installation can enforce. No vendor-specific hacks. */
class DeviceCapabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: DevicePolicyController,
    private val transitionScheduler: TransitionScheduler,
    @AllowDebugging private val allowDebugging: Boolean,
) {

    fun check(): DeviceCapabilities {
        val owner = controller.isDeviceOwner()
        val capabilities = listOf(
            deviceOwner(owner),
            lockTask(owner),
            bootRecovery(owner),
            notifications(),
            exactAlarms(),
            applicationManagement(owner),
            automaticTime(owner),
            battery(),
        ) + listOfNotNull(pinningLock(owner))
        // Shown whether or not the device is managed, so users know what a session would enforce.
        val supported = listOf(LOCK_TASK_LABEL) +
            RestrictionPolicy.userRestrictions(strictMode = true, allowDebugging = allowDebugging).sorted()
        return DeviceCapabilities(capabilities, supported, manufacturerNote())
    }

    private fun deviceOwner(owner: Boolean) = when {
        owner -> capability(CapabilityId.DEVICE_OWNER, CapabilityStatus.OK, R.string.cap_device_owner_ok)
        controller.isAdminActive() ->
            capability(CapabilityId.DEVICE_OWNER, CapabilityStatus.WARNING, R.string.cap_device_owner_admin_only)
        else -> capability(CapabilityId.DEVICE_OWNER, CapabilityStatus.UNAVAILABLE, R.string.cap_device_owner_missing)
    }

    private fun lockTask(owner: Boolean) = when {
        owner -> capability(CapabilityId.LOCK_TASK, CapabilityStatus.OK, R.string.cap_lock_task_ok)
        else -> capability(CapabilityId.LOCK_TASK, CapabilityStatus.WARNING, R.string.cap_lock_task_pinning)
    }

    private fun bootRecovery(owner: Boolean): Capability {
        val receiverState = context.packageManager.getComponentEnabledSetting(
            ComponentName(context, BootReceiver::class.java),
        )
        val receiverEnabled = receiverState != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        return when {
            !receiverEnabled -> capability(CapabilityId.BOOT_RECOVERY, CapabilityStatus.UNAVAILABLE, R.string.cap_boot_disabled)
            owner -> capability(CapabilityId.BOOT_RECOVERY, CapabilityStatus.OK, R.string.cap_boot_ok)
            else -> capability(CapabilityId.BOOT_RECOVERY, CapabilityStatus.WARNING, R.string.cap_boot_partial)
        }
    }

    private fun notifications(): Capability {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val enabled = permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        return if (enabled) {
            capability(CapabilityId.NOTIFICATIONS, CapabilityStatus.OK, R.string.cap_notifications_ok)
        } else {
            capability(CapabilityId.NOTIFICATIONS, CapabilityStatus.WARNING, R.string.cap_notifications_off)
        }
    }

    private fun exactAlarms() = if (transitionScheduler.canScheduleExact()) {
        capability(CapabilityId.EXACT_ALARMS, CapabilityStatus.OK, R.string.cap_exact_alarms_ok)
    } else {
        capability(CapabilityId.EXACT_ALARMS, CapabilityStatus.WARNING, R.string.cap_exact_alarms_off)
    }

    private fun applicationManagement(owner: Boolean) = if (owner) {
        capability(CapabilityId.APPLICATION_MANAGEMENT, CapabilityStatus.OK, R.string.cap_app_management_ok)
    } else {
        capability(CapabilityId.APPLICATION_MANAGEMENT, CapabilityStatus.UNAVAILABLE, R.string.cap_app_management_missing)
    }

    private fun automaticTime(owner: Boolean) = if (owner) {
        capability(CapabilityId.AUTOMATIC_TIME, CapabilityStatus.OK, R.string.cap_time_ok)
    } else {
        capability(CapabilityId.AUTOMATIC_TIME, CapabilityStatus.WARNING, R.string.cap_time_guard_only)
    }

    private fun battery(): Capability {
        val power = context.getSystemService(PowerManager::class.java)
        return if (power.isIgnoringBatteryOptimizations(context.packageName)) {
            capability(CapabilityId.BATTERY, CapabilityStatus.OK, R.string.cap_battery_ok)
        } else {
            capability(CapabilityId.BATTERY, CapabilityStatus.WARNING, R.string.cap_battery_optimized)
        }
    }

    /** The app cannot read or change this system option; it can only point the user to it. */
    private fun pinningLock(owner: Boolean): Capability? = if (owner) {
        null
    } else {
        capability(CapabilityId.PINNING_LOCK, CapabilityStatus.WARNING, R.string.cap_pinning_lock)
    }

    private fun manufacturerNote(): String? {
        val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return if (AGGRESSIVE_BACKGROUND_MANUFACTURERS.any { manufacturer.contains(it) }) {
            context.getString(R.string.cap_manufacturer_note, Build.MANUFACTURER)
        } else {
            null
        }
    }

    private fun capability(id: CapabilityId, status: CapabilityStatus, detail: Int) =
        Capability(id, status, context.getString(detail))

    private companion object {
        const val LOCK_TASK_LABEL = "lock_task_mode"

        /** Vendors publicly documented (dontkillmyapp.com) to restrict background work beyond AOSP. */
        val AGGRESSIVE_BACKGROUND_MANUFACTURERS = listOf("xiaomi", "huawei", "honor", "oppo", "vivo", "realme", "oneplus", "meizu", "asus")
    }
}
