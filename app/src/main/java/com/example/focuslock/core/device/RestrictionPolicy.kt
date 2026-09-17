package com.example.focuslock.core.device

import android.app.admin.DevicePolicyManager
import android.os.UserManager

/** What a lockdown session asks the device to enforce. */
data class LockdownPolicy(
    val allowedPackages: Set<String>,
    val strictMode: Boolean,
)

/**
 * Maps a [LockdownPolicy] to concrete Device Owner configuration.
 *
 * Every entry is a documented, public DevicePolicyManager / UserManager capability.
 * Emergency functionality is deliberately preserved:
 *  - LOCK_TASK_FEATURE_GLOBAL_ACTIONS keeps the power menu (emergency, power off).
 *  - LOCK_TASK_FEATURE_KEYGUARD keeps the secure lock screen and its emergency dialer.
 *  - The default dialer is always allowlisted (see [lockTaskPackages]).
 * Factory reset and recovery mode are never restricted.
 */
object RestrictionPolicy {

    /** Applied in every Device Owner session. */
    val STANDARD_RESTRICTIONS: Set<String> = setOf(
        // Safe mode would boot without third-party apps and therefore without enforcement.
        UserManager.DISALLOW_SAFE_BOOT,
        // Blocks force-stop, clear-data and disable from Settings.
        UserManager.DISALLOW_APPS_CONTROL,
        UserManager.DISALLOW_UNINSTALL_APPS,
        // Protects timestamp-based enforcement from manual clock changes.
        UserManager.DISALLOW_CONFIG_DATE_TIME,
    )

    /** Additional restrictions for Strict Mode sessions. */
    val STRICT_RESTRICTIONS: Set<String> = setOf(
        UserManager.DISALLOW_INSTALL_APPS,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_USER_SWITCH,
    )

    /**
     * @param allowDebugging debug builds keep ADB usable so developers can always recover a
     * test device; release Strict Mode sessions disable debugging features.
     */
    fun userRestrictions(strictMode: Boolean, allowDebugging: Boolean): Set<String> = buildSet {
        addAll(STANDARD_RESTRICTIONS)
        if (strictMode) {
            addAll(STRICT_RESTRICTIONS)
            if (!allowDebugging) add(UserManager.DISALLOW_DEBUGGING_FEATURES)
        }
    }

    fun lockTaskFeatures(strictMode: Boolean): Int {
        val base = DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
            DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD or
            // HOME is routed to the lockdown screen through a persistent preferred activity.
            DevicePolicyManager.LOCK_TASK_FEATURE_HOME
        // Notifications and Overview (recents) are never enabled: both are escape routes.
        return if (strictMode) base else base or DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
    }

    fun lockTaskPackages(ownPackage: String, essential: Set<String>, allowed: Set<String>): List<String> =
        (listOf(ownPackage) + essential + allowed).filter { it.isNotBlank() }.distinct()
}
