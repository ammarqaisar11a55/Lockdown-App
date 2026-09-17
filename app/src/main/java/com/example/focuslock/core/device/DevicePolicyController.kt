package com.example.focuslock.core.device

import com.example.focuslock.domain.model.DevicePolicyState

/**
 * The only gateway to DevicePolicyManager. UI and business logic never call it directly.
 *
 * Every mutating call is a no-op (and logged) when the app is not the Device Owner.
 */
interface DevicePolicyController {
    fun isDeviceOwner(): Boolean
    fun isAdminActive(): Boolean

    /** Whether the system would currently let this app enter lock task without user confirmation. */
    fun isLockTaskPermitted(): Boolean
    fun isInLockTaskMode(): Boolean

    /** Brings up the lockdown screen, which enters lock task mode (an Activity-bound API). */
    fun startLockTaskMode()

    /** Empties the lock task allowlist; the system then exits lock task mode for this app. */
    fun stopLockTaskMode()
    fun setLockTaskPackages(packages: List<String>)
    fun setLockTaskFeatures(features: Int)

    /**
     * Adds [restrictions] that are not already in place.
     * @return the restrictions this call actually added (the caller owns and must remove them).
     */
    fun applyRestrictions(restrictions: Set<String>): Set<String>
    fun removeRestrictions(restrictions: Set<String>)

    /** Routes the HOME intent to the lockdown screen while enabled. */
    fun setHomeOverride(enabled: Boolean)
    fun setUninstallBlocked(blocked: Boolean)

    /** Functions Android must keep reachable during lockdown (currently the dialer apps). */
    fun essentialPackages(): Set<String>
    fun readPolicyState(): DevicePolicyState

    /** Development/support path: gives up Device Owner. Refused while a session is active by callers. */
    fun clearDeviceOwner(): Boolean

    companion object {
        /** Pseudo-restriction recorded when this app switched automatic time on. */
        const val AUTO_TIME_MARKER = "focuslock:auto_time"
    }
}
