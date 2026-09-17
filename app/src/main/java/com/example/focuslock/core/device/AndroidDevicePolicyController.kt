package com.example.focuslock.core.device

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import com.example.focuslock.core.common.FocusLogger
import com.example.focuslock.domain.model.DevicePolicyState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDevicePolicyController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val launcher: LockdownLauncher,
    private val logger: FocusLogger,
) : DevicePolicyController {

    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val admin: ComponentName = FocusDeviceAdminReceiver.componentName(context)
    private val homeAlias = ComponentName(context, HOME_ALIAS_CLASS)
    private val packageName: String = context.packageName

    override fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(packageName)

    override fun isAdminActive(): Boolean = dpm.isAdminActive(admin)

    override fun isLockTaskPermitted(): Boolean = dpm.isLockTaskPermitted(packageName)

    override fun isInLockTaskMode(): Boolean =
        activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    override fun startLockTaskMode() = launcher.launchLockdownScreen()

    override fun stopLockTaskMode() = setLockTaskPackages(emptyList())

    override fun setLockTaskPackages(packages: List<String>) = asOwner("setLockTaskPackages") {
        dpm.setLockTaskPackages(admin, packages.toTypedArray())
    }

    override fun setLockTaskFeatures(features: Int) = asOwner("setLockTaskFeatures") {
        dpm.setLockTaskFeatures(admin, features)
    }

    override fun applyRestrictions(restrictions: Set<String>): Set<String> {
        if (!isDeviceOwner()) return emptySet()
        val added = mutableSetOf<String>()
        val existing = runCatching { dpm.getUserRestrictions(admin) }.getOrNull()
        restrictions.forEach { restriction ->
            if (existing?.getBoolean(restriction) == true) return@forEach
            asOwner("addUserRestriction $restriction") {
                dpm.addUserRestriction(admin, restriction)
                added += restriction
            }
        }
        if (enableAutomaticTime()) added += DevicePolicyController.AUTO_TIME_MARKER
        return added
    }

    override fun removeRestrictions(restrictions: Set<String>) {
        restrictions.forEach { restriction ->
            if (restriction == DevicePolicyController.AUTO_TIME_MARKER) {
                setAutomaticTime(enabled = false)
            } else {
                asOwner("clearUserRestriction $restriction") { dpm.clearUserRestriction(admin, restriction) }
            }
        }
    }

    override fun setHomeOverride(enabled: Boolean) {
        if (!isDeviceOwner()) return
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(homeAlias, state, PackageManager.DONT_KILL_APP)
        asOwner("persistent preferred home") {
            if (enabled) {
                val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
                dpm.addPersistentPreferredActivity(admin, filter, homeAlias)
            } else {
                dpm.clearPackagePersistentPreferredActivities(admin, packageName)
            }
        }
    }

    override fun setUninstallBlocked(blocked: Boolean) = asOwner("setUninstallBlocked") {
        dpm.setUninstallBlocked(admin, packageName, blocked)
    }

    override fun essentialPackages(): Set<String> {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return emptySet()
        return buildSet {
            telecom.defaultDialerPackage?.let(::add)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) telecom.systemDialerPackage?.let(::add)
        }
    }

    override fun readPolicyState(): DevicePolicyState {
        val owner = isDeviceOwner()
        val restrictionsBundle = if (owner) runCatching { dpm.getUserRestrictions(admin) }.getOrNull() else null
        val activeRestrictions = restrictionsBundle?.keySet()
            ?.filter { restrictionsBundle.getBoolean(it) }
            ?.toSet()
            .orEmpty()
        val lockTaskPackages = if (owner) {
            runCatching { dpm.getLockTaskPackages(admin).toSet() }.getOrDefault(emptySet())
        } else {
            emptySet()
        }
        return DevicePolicyState(
            isDeviceOwner = owner,
            isAdminActive = isAdminActive(),
            lockTaskPackages = lockTaskPackages,
            lockTaskActive = isInLockTaskMode(),
            activeRestrictions = activeRestrictions,
            uninstallBlocked = owner && runCatching { dpm.isUninstallBlocked(admin, packageName) }.getOrDefault(false),
            homeOverrideEnabled = context.packageManager.getComponentEnabledSetting(homeAlias) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )
    }

    @Suppress("DEPRECATION") // The only public API for a Device Owner to relinquish itself.
    override fun clearDeviceOwner(): Boolean {
        if (!isDeviceOwner()) return false
        return runCatching {
            dpm.clearDeviceOwnerApp(packageName)
            true
        }.onFailure { logger.error(TAG, "clearDeviceOwnerApp failed", it) }.getOrDefault(false)
    }

    /** @return true if automatic time was off and this call switched it on. */
    private fun enableAutomaticTime(): Boolean {
        if (!isDeviceOwner()) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (dpm.getAutoTimeEnabled(admin)) {
                    false
                } else {
                    dpm.setAutoTimeEnabled(admin, true)
                    true
                }
            } else {
                @Suppress("DEPRECATION")
                if (dpm.autoTimeRequired) {
                    false
                } else {
                    dpm.setAutoTimeRequired(admin, true)
                    true
                }
            }
        }.onFailure { logger.warn(TAG, "Automatic time could not be enforced: ${it.javaClass.simpleName}") }
            .getOrDefault(false)
    }

    private fun setAutomaticTime(enabled: Boolean) = asOwner("automatic time") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dpm.setAutoTimeEnabled(admin, enabled)
        } else {
            @Suppress("DEPRECATION")
            dpm.setAutoTimeRequired(admin, enabled)
        }
    }

    private inline fun asOwner(operation: String, block: () -> Unit) {
        if (!isDeviceOwner()) {
            logger.debug(TAG, "Skipped $operation: not Device Owner")
            return
        }
        try {
            block()
        } catch (e: SecurityException) {
            logger.error(TAG, "$operation rejected", e)
        } catch (e: IllegalArgumentException) {
            logger.error(TAG, "$operation invalid", e)
        }
    }

    companion object {
        private const val TAG = "DEVICE_POLICY"

        /** Matches the activity-alias declared in AndroidManifest.xml. */
        const val HOME_ALIAS_CLASS = "com.example.focuslock.feature.lockdown.LockdownHomeAlias"
    }
}
